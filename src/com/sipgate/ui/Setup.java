package com.sipgate.ui;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.oauth.OAuthException;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.sipgate.R;
import com.sipgate.R.id;
import com.sipgate.api.types.MobileExtension;
import com.sipgate.exceptions.FeatureNotAvailableException;
import com.sipgate.exceptions.OAuthAccessProtectedResourceException;
import com.sipgate.exceptions.OAuthMissingContextException;
import com.sipgate.models.SipgateProvisioningData;
import com.sipgate.models.SipgateProvisioningExtension;
import com.sipgate.sipua.ui.Receiver;
import com.sipgate.util.ApiServiceProvider;
import com.sipgate.util.SettingsClient;

public class Setup extends Activity implements OnClickListener { 
	private final String TAG = "Setup";
	private Spinner extensionSpinner;
	private HashMap<String, SipgateProvisioningExtension> extensionsMap = new HashMap<String, SipgateProvisioningExtension>();
	private Button okButton;
	private String registrar = null;
	private String outboundProxy = null;
	private boolean isVoiceAccount = false;
	
	private MobileExtension mobileExtension = null;
	
	private SettingsClient settingsClient = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sipgate_setup);
		
		isVoiceAccount = isVoiceAccount();
		
		okButton = (Button) findViewById(id.okButton);
		okButton.setOnClickListener(this);
		
		this.settingsClient = SettingsClient.getInstance(getApplicationContext());
		if (!this.settingsClient.isProvisioned()) {

			if (isVoiceAccount) {
				prepareVoiceSetup();
			} else {
				prepareTeamSetup();
			}
		}
		else {
			//Intent intent = new Intent(this, Sipgate.class);
			Intent intent = new Intent(this, com.sipgate.ui.SipgateFrames.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}
		
	}
	
	private void setTeamLayoutVisible(boolean visible) {
		View teamlayout = (View) findViewById(R.id.setupTeamLayout);
		
		if (visible) {
			teamlayout.setVisibility(View.VISIBLE);
		} else {
			teamlayout.setVisibility(View.GONE);
		}
	}
	private void setVoiceLayoutVisible(boolean visible) {
		View voicelayout = (View) findViewById(R.id.setupVoiceLayout);
		
		if (visible) {
			voicelayout.setVisibility(View.VISIBLE);
		} else {
			voicelayout.setVisibility(View.GONE);
		}
	}
	
	private void prepareTeamSetup() {
		
		setTeamLayoutVisible(true);
		setVoiceLayoutVisible(false);
		
		SipgateProvisioningData provisioningData = getProvisioningData();
		
		if (provisioningData == null) {
			Log.e(TAG, "no provisioningdata for team setup");
			return;
		}
	
		ArrayList<SipgateProvisioningExtension> extensions = null;
		ArrayList<String> aliases = new ArrayList<String>();
		
		this.registrar = provisioningData.getRegistrar();
		this.outboundProxy = provisioningData.getOutboundProxy();

		do {
			
			extensions = provisioningData.getExtensions();
		
			if (extensions != null) {
				
				for (SipgateProvisioningExtension extension : extensions) {
					if (extension.getAlias().length() > 0) {
						extensionsMap.put(extension.getAlias(), extension);
						aliases.add(extension.getAlias());
					} else {
						extensionsMap.put(extension.getSipid(), extension);
						aliases.add(extension.getSipid());
					}
				}
			}
		
		} while (extensions == null);
		
		if (extensions != null) {
			extensionSpinner = (Spinner) findViewById(com.sipgate.R.id.extensionList);
			
	        MyArrayAdapter adapter = new MyArrayAdapter(this,R.layout.sipgate_spinner_row,R.id.text, aliases);
	        extensionSpinner.setAdapter( adapter );
		}
		else {
			AlertDialog m_AlertDlg = new AlertDialog.Builder(this)
				.setMessage(getApplicationContext().getString(R.string.sipgate_extension_try_later))
				.setTitle(getApplicationContext().getString(R.string.sipgate_extension_try_later_title))
				.setIcon(R.drawable.icon22)
				.setCancelable(true)
				.show();
		}

	}

	private SipgateProvisioningData getProvisioningData() {
		SipgateProvisioningData provisioningData = null;
		
		try{
			provisioningData = ApiServiceProvider.getInstance(this).getProvisioningData();
		}
		catch(Exception e){
			Log.e(TAG, e.getLocalizedMessage());
		}
		return provisioningData;
	}
	
	private void prepareVoiceSetup() {
		setTeamLayoutVisible(false);
		setVoiceLayoutVisible(true);
		prefillNumberView();
		List<MobileExtension> extensions = retrieveMobileExtensions();

		if (extensions == null || extensions.isEmpty()) {
			Log.d(TAG, "no mobile extensions yet");
		}
		
		Iterator<MobileExtension> i = extensions.iterator();
		
		while (i.hasNext()) {
			mobileExtension = i.next();
			if (mobileExtension != null) {
				Log.d(TAG, "found a mobile extension");
				break;
			}
		}
		
		if (mobileExtension != null) { // mobile extension is already there. dont bother the user anymore. just set it up
			setupMobileExtension(null); // number already set. and we dont know it anyway
			finishExtensionConfiguration(mobileExtension.getExtensionId(), mobileExtension.getPassword(), this.outboundProxy, this.registrar, mobileExtension.getAlias());
			return;
		}
	}


	private void prefillNumberView() {
		EditText numberText = (EditText) findViewById(R.id.mobilePhoneNumberText);
		TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		String lineNumber = tm.getLine1Number();
		numberText.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
		if (lineNumber != null) {
			numberText.setText(lineNumber);
		}
	}

	private List<MobileExtension> retrieveMobileExtensions() {

		try {
			 return ApiServiceProvider.getInstance(this).getMobileExtensions();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthAccessProtectedResourceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthMissingContextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FeatureNotAvailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return null;
	}

	
	private boolean isVoiceAccount() {
		try {
			String baseProducType = ApiServiceProvider.getInstance(this).getBaseProductType();
			
			return (baseProducType != null && baseProducType.equals("voice"));
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthAccessProtectedResourceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OAuthMissingContextException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FeatureNotAvailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		
		return false;
		
	}
	
	
	private class MyArrayAdapter extends ArrayAdapter<String>
	{
	    public MyArrayAdapter(Context context, int resource,int textViewResourceId, ArrayList<String> objects)
	    {
	            super(context, resource, textViewResourceId, objects);
	    }       
	}

	private void setupMobileExtension(String phoneNumber) {
		String model = android.os.Build.MODEL;
		String vendor = android.os.Build.MANUFACTURER;
		String firmware = android.os.Build.VERSION.RELEASE;

		SipgateProvisioningData provisioningData = getProvisioningData();
		if (provisioningData == null) {
			Log.e(TAG, "unable to get provisioning data");
			return;
		}
		
		if (mobileExtension == null) {
			try {
				mobileExtension = ApiServiceProvider.getInstance(this).setupMobileExtension(phoneNumber, model, vendor, firmware);
			} catch (FeatureNotAvailableException e) {
				e.printStackTrace();
				return;
			}
			if (mobileExtension == null) {
				Log.e(TAG, "unable to setup mobile extension");
				return;
			}
		}

		ArrayList<SipgateProvisioningExtension> extensions = provisioningData.getExtensions();
		ArrayList<String> aliases = new ArrayList<String>();

		this.registrar = provisioningData.getRegistrar();
		this.outboundProxy = provisioningData.getOutboundProxy();

		if (extensions != null) {
			Iterator<SipgateProvisioningExtension> i = extensions.iterator();
			while (i.hasNext()) {
				SipgateProvisioningExtension pExt = i.next();
				if (pExt != null && mobileExtension.getExtensionId() != null && mobileExtension.getExtensionId().equals(pExt.getSipid())) { // we found provisioningdata for the just created extension
					mobileExtension.setAlias(pExt.getAlias());
				}
			}
		}

		if (mobileExtension.getAlias() == null) {
			Log.w(TAG, "mobileextension does not have an alias");
		}

	}
	
	public void onClick(View v) {
		
		String username = null;
		String password = null;
		String outboundproxy = null;
		String registrar = null;
		String alias = null;

		if (isVoiceAccount) {
			
			try {
				String phoneNumber;

				EditText mobilePhoneNumberText = (EditText) findViewById(R.id.mobilePhoneNumberText);
				phoneNumber = mobilePhoneNumberText.getText().toString();
				phoneNumber = PhoneNumberUtils.stripSeparators(phoneNumber);
				setupMobileExtension(phoneNumber);

				if (mobileExtension != null) {
					// success. let's go on.
					finishExtensionConfiguration(mobileExtension.getExtensionId(), mobileExtension.getPassword(), this.outboundProxy, this.registrar, mobileExtension.getAlias());
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			// that's it. setting up the extension failed. we should be sorry.
			Toast.makeText(this, getResources().getString(R.string.sipgate_error_unable_to_create_mobile_extension), Toast.LENGTH_SHORT).show();
		} else {
			alias = (String) extensionSpinner.getSelectedItem();
			SipgateProvisioningExtension extension = extensionsMap.get(alias);


			settingsClient.registerExtension(extension.getSipid(), extension.getPassword(),
					extension.getAlias(), this.outboundProxy, this.registrar);
			username = extension.getSipid();
			password = extension.getPassword();
			outboundproxy = this.outboundProxy;
			registrar = this.registrar;
			alias = extension.getAlias();
			
			finishExtensionConfiguration(username, password, outboundproxy,
					registrar, alias);
		}
	}

	private void finishExtensionConfiguration(String username, String password,
			String outboundproxy, String registrar, String alias) {
		SettingsClient settingsClient = SettingsClient.getInstance(getApplicationContext());
		
		if(this.settingsClient.isFirstRegistration()){
			settingsClient.registerExtension(username, password, alias, outboundproxy, registrar);
		} else {
			settingsClient.reregisterExtension(username, password, alias, outboundproxy, registrar);
		}

		Log.d(TAG, "finishing extension configuration: " + username + " " + password + " " + outboundproxy + " " + registrar + " " + alias);
		
		Receiver.engine(this).halt();
		Receiver.engine(this).StartEngine();
		
		try {
			//Intent intent = new Intent(this, com.sipgate.ui.Sipgate.class);
			Intent intent = new Intent(this, com.sipgate.ui.SipgateFrames.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}

}


