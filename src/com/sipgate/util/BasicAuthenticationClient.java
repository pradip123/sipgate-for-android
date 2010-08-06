package com.sipgate.util;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;

import android.util.Log;

import com.sipgate.exceptions.AccessProtectedResourceException;
import com.sipgate.exceptions.AuthenticationErrorException;
import com.sipgate.exceptions.NetworkProblemException;
import com.sipgate.exceptions.RestClientException;
import com.sipgate.interfaces.RestAuthenticationInterface;

public class BasicAuthenticationClient implements RestAuthenticationInterface {
	
	@SuppressWarnings("unused")
	private DefaultRedirectHandler redirectHandler = new DefaultRedirectHandler();
	
	
	private final String TAG = "BasicAuthenticationClient";
	
	private String user = null;
	private String pass = null;
	
	public BasicAuthenticationClient(String user, String pass) {
		super();

		this.user = user;
		this.pass = pass;
	}
	
	private InputStream accessProtectedResource(String url) throws AccessProtectedResourceException, AuthenticationErrorException, NetworkProblemException {
		return this.accessProtectedResource("GET", url);
	}

	private InputStream accessProtectedResource(String httpMethod, String url) throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(httpMethod, url, null);
	}
	

	@SuppressWarnings("rawtypes")
	private String appendUrlParameters(String url, Collection<? extends Entry> params) {
		
		StringBuilder sb = new StringBuilder(url);
		
		if (params != null){
			Iterator<? extends Entry> i = params.iterator();
			while (i.hasNext()) {
				Entry entry = (Entry) i.next();
				
				sb.append("&"+entry.getKey());
				sb.append("="+entry.getValue());
			}
		}
		return sb.toString();
	}
	
	
	@SuppressWarnings("rawtypes")
	private HttpPost createPostRequest(String url, Collection<? extends Entry> params) {
		HttpPost ret = new HttpPost(url);
		
		List<NameValuePair> plist = new ArrayList<NameValuePair>(params.size());
		Iterator<? extends Entry> i = params.iterator();
		while (i.hasNext()) {
			Entry e = i.next();
			plist.add(new BasicNameValuePair((String) e.getKey(), (String) e.getValue()));
		}
		
		try {
			ret.setEntity(new UrlEncodedFormEntity(plist));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ret;
	}
	
	
	
    @SuppressWarnings("rawtypes")
	private InputStream accessProtectedResource(String httpMethod, String urlString, Collection<? extends Entry> params) throws AccessProtectedResourceException, NetworkProblemException {

    	URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e1) {
			throw new AccessProtectedResourceException();
		}
		String username = this.user;
		String password = this.pass;

		//int serverPort = 443;
		//if (url.getPort() != -1) {
		//	serverPort = url.getPort();
		//}
		
        DefaultHttpClient httpClient = new DefaultHttpClient();

        System.err.println("access p resource. username: " + username + " password: " + password);

        httpClient.getCredentialsProvider().setCredentials(new AuthScope(url.getHost(), AuthScope.ANY_PORT), new UsernamePasswordCredentials(username, password));

	
		if (urlString.contains("?")) {
			urlString += "&"+Constants.API_VERSION_SUFFIX; // TODO FIXME
		} else {
			urlString += "?"+Constants.API_VERSION_SUFFIX; // TODO FIXME
		}
		
		HttpRequestBase request = null;
		
		if (httpMethod.equals("GET")){
			Log.d(TAG, "getting " + urlString);
			urlString = appendUrlParameters(urlString, params);
			request = new HttpGet(urlString);
		} else if (httpMethod.equals("PUT")) {
			urlString = appendUrlParameters(urlString, params);
			request = new HttpPut(urlString);
		} else if (httpMethod.equals("POST")) {
			request = createPostRequest(urlString, params);
		} else {
			throw new AccessProtectedResourceException("unknown method");	
		}
		
		HttpResponse response = null;
		InputStream inputStream = null;
		try {

		/*	httpClient.setRedirectHandler(new DefaultRedirectHandler());
			response = httpClient.execute(request);

			if (response == null) {
				throw new AccessProtectedResourceException("no response");

			} else if (response.getStatusLine().getStatusCode() == 200) {
				Log.v(TAG, "successful request to '"+urlString+"'");
			} else {
				Log.w(TAG, "API returned "+response.getStatusLine().getStatusCode()+" - "+response.getStatusLine().getReasonPhrase());
				throw new RestClientException(response.getStatusLine());
			}
*/
			do {
				response = httpClient.execute(request);
				StatusLine statusLine = response.getStatusLine();
				
				Log.d(TAG, "is get: " + (request.getClass().equals(HttpGet.class)) + " " + request.getClass().getName());
				
				if (statusLine.getStatusCode() == 307 && request.getClass().equals(HttpGet.class)) {
					HttpParams httpParams = response.getParams();
					request = new HttpGet((String) httpParams.getParameter("Location"));
					response = null;
				} else if (statusLine.getStatusCode() == 200) {
					Log.v(TAG, "successful request to '"+urlString+"'");
				} else if (statusLine.getStatusCode() == 401) {
					Log.w(TAG, "cannot authenticate");
					throw new AuthenticationErrorException();
				} else {
					Log.w(TAG, "API returned "+statusLine.getStatusCode()+" - "+statusLine.getReasonPhrase());
					throw new RestClientException(statusLine);
				}
			} while (response == null);

			inputStream = response.getEntity().getContent();
		} catch (UnknownHostException e) {
			Log.e(this.getClass().getSimpleName(), "accessProtectedResource(): "+e.getLocalizedMessage());
			throw new NetworkProblemException();
		} catch (Exception e) {
			Log.e(this.getClass().getSimpleName(), "accessProtectedResource(): "+e.getLocalizedMessage());
			throw new AccessProtectedResourceException();
		}

		return inputStream;
	}

	@Override
	public InputStream getBillingBalance() throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(Constants.API_20_BASEURL + "/my/billing/balance/?complexity=full");
	}

	@Override
	public InputStream getCalls() throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(Constants.API_20_BASEURL + "/my/events/calls/?complexity=full");
	}

	@Override
	public InputStream getProvisioningData() throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(Constants.API_20_BASEURL + "/my/settings/extensions/?complexity=full");
	}

	@Override
	public InputStream getEvents() throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(Constants.API_20_BASEURL + "/my/events/?complexity=full");
	}
	
	public InputStream getVoicemail(String voicemail) throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(voicemail);
	}
	
	public void setVoicemailRead(String voicemail) throws AccessProtectedResourceException, NetworkProblemException {
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("value", "true");
		accessProtectedResource("PUT", voicemail+"/?value=true"/*, params*/);
	}

	@Override
	public InputStream getMobileExtensions() throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(Constants.API_20_BASEURL + "/my/settings/mobile/extensions/");
	}

	@Override
	public InputStream getBaseProductType() throws AccessProtectedResourceException, NetworkProblemException {
		return accessProtectedResource(Constants.API_20_BASEURL + "/my/settings/baseproducttype/");
	}

	@Override
	public InputStream setupMobileExtension(String phoneNumber, String model, String vendor, String firmware)
			throws AccessProtectedResourceException, NetworkProblemException {
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("phoneNumber", phoneNumber);
		params.put("model", model);
		params.put("vendor", vendor);
		params.put("firmware", firmware);
		return accessProtectedResource("POST", Constants.API_20_BASEURL + "/my/settings/mobile/extensions/", params.entrySet());
	}
}
