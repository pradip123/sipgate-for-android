package com.sipgate.service;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.sipgate.R;
import com.sipgate.db.CallDataDBObject;
import com.sipgate.db.SipgateDBAdapter;
import com.sipgate.db.VoiceMailDataDBObject;
import com.sipgate.util.ApiServiceProvider;
import com.sipgate.util.ApiServiceProvider.API_FEATURE;
import com.sipgate.util.NotificationClient;
import com.sipgate.util.NotificationClient.NotificationType;

/**
 * 
 * @author Marcus Hunger
 * @author Karsten Knuth
 * @version 1.1
 *
 */
public class SipgateBackgroundService extends Service implements EventService {
	
	public static final String ACTION_NEWEVENTS = "action_newEvents";
	public static final String ACTION_START_ON_BOOT = "com.sipgate.service.SipgateBackgroundService";
	public static final int REQUEST_NEWEVENTS = 0;

	private static final long CALLLIST_REFRESH_INTERVAL = 60000;
	private static final long VOICEMAIL_REFRESH_INTERVAL = 60000;
		
	private static final String TAG = "SipgateBackgroundService";
	
	private boolean serviceEnabled = false;
	
	private Timer voiceMailRefreshTimer = null;
	private Timer callListRefreshTimer = null;
	
	private Set<PendingIntent> onNewVoiceMailsTriggers = new HashSet<PendingIntent>();
	private Set<PendingIntent> onNewCallsTriggers = new HashSet<PendingIntent>();

	private NotificationClient notifyClient = null;
	private ApiServiceProvider apiClient = null;
	
	private SipgateDBAdapter sipgateDBAdapter = null;
	
	private CallDataDBObject oldCallDataDBObject = null;
	private VoiceMailDataDBObject oldVoiceMailDataDBObject = null;
	
	private	int unreadCounter = 0;
	
	/**
	 * @since 1.0
	 */
	public void onCreate() 
	{
		super.onCreate();
	
		notifyClient = new NotificationClient(this); 
		apiClient = ApiServiceProvider.getInstance(getApplicationContext());
		
		startService();
	}

	/**
	 * @since 1.0
	 */
	private void startService() 
	{
		if (serviceEnabled) {
			return;
		}
		
		serviceEnabled = true;
		
		voiceMailRefreshTimer = new Timer();  
		callListRefreshTimer = new Timer();  
		
		if (hasVmListFeature()) {
			voiceMailRefreshTimer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					Log.v(TAG, "voicemail timertask started");
					if(serviceEnabled) {
						Log.d(TAG, "get vms");
						refreshVoicemailEvents();
					}
				}
			}, 0, VOICEMAIL_REFRESH_INTERVAL);
			
		}
		
		callListRefreshTimer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				Log.v(TAG, "calllist timertask started");
				if(serviceEnabled) {
					refreshCallEvents();
				}
			}

		}, 0, CALLLIST_REFRESH_INTERVAL);
	}

	/**
	 * @since 1.0
	 */
	public void stopService() 
	{
		Log.d(TAG,"stopservice");
		
		if (voiceMailRefreshTimer != null){
			Log.d(TAG,"voiceMailRefreshTimer.cancel");
			voiceMailRefreshTimer.cancel();
		}			
		
		if (callListRefreshTimer != null){
			Log.d(TAG,"callListRefreshTimer.cancel");
			callListRefreshTimer.cancel();
		}	
		
		Log.d(TAG,"cancel notifications");
		
		notifyClient.deleteNotification(NotificationType.VOICEMAIL);
		notifyClient.deleteNotification(NotificationType.CALL);
	}
	
	/**
	 * @since 1.0
	 */
	public void onDestroy() 
	{
		Log.d(TAG,"onDestroy");
		
		stopService();
	}

	/**
	 * @since 1.0
	 */
	private void refreshVoicemailEvents() 
	{
		Log.v(TAG, "refreshVoicemailEvents() -> start");
		
		try {
			notifyIfUnreadsVoiceMails(apiClient.getVoiceMails(), this);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
		Log.v(TAG, "refreshVoicemailEvents() -> finish");
	}
	
	/**
	 * @author graef
	 * @since 1.1
	 */
	private void refreshCallEvents() 
	{
		Log.v(TAG, "refreshCallEvents() -> start");
		
		try {
			notifyIfUnreadsCalls(apiClient.getCalls());
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
		Log.v(TAG, "refreshCallEvents() -> finish");
	}

	/**
	 * @author graef
	 * @param Vector with voiceMailDataDBObjects
	 * @since 1.1
	 */
	
	public void notifyIfUnreadsVoiceMails(Vector<VoiceMailDataDBObject> newVoiceMailDataDBObjects, Context ctx) 
	{
		Log.d(TAG, "notifyIfUnreadVoiceMails");
		
		if (newVoiceMailDataDBObjects == null)
		{
			Log.i(TAG, "notifyIfUnreadVoiceMails() -> voiceMailDataDBObjects is null");
			return;
		}
		
		unreadCounter = 0;
		
		try
		{
			sipgateDBAdapter = new SipgateDBAdapter(ctx);
			
			Vector<VoiceMailDataDBObject> oldVoiceMailDataDBObjects = sipgateDBAdapter.getAllVoiceMailData();
			
			sipgateDBAdapter.startTransaction();
			
			for (VoiceMailDataDBObject oldVoiceMailDataDBObject : oldVoiceMailDataDBObjects) 
			{
				if (!newVoiceMailDataDBObjects.contains(oldVoiceMailDataDBObject))
				{
					sipgateDBAdapter.delete(oldVoiceMailDataDBObject);
				}
			}
					
			for (VoiceMailDataDBObject newVoiceMailDataDBObject : newVoiceMailDataDBObjects) 
			{
				if (oldVoiceMailDataDBObjects.contains(newVoiceMailDataDBObject))
				{	
					oldVoiceMailDataDBObject = oldVoiceMailDataDBObjects.elementAt(oldVoiceMailDataDBObjects.indexOf(newVoiceMailDataDBObject));
										
					newVoiceMailDataDBObject.setSeen(oldVoiceMailDataDBObject.getSeen());
					
					sipgateDBAdapter.update(newVoiceMailDataDBObject);

					if (!newVoiceMailDataDBObject.isRead() && !newVoiceMailDataDBObject.isSeen())
					{
						unreadCounter++;
					}
				}
				else
				{
					sipgateDBAdapter.insert(newVoiceMailDataDBObject);
					
					if (!newVoiceMailDataDBObject.isRead())
					{
						unreadCounter++;
					}
				}
			}
			
			sipgateDBAdapter.commitTransaction();
		}
		catch (Exception e)
		{
			Log.e(TAG, "notifyIfUnreadsVoiceMails()", e);
			
			if (sipgateDBAdapter != null && sipgateDBAdapter.inTransaction())
			{
				sipgateDBAdapter.rollbackTransaction();
			}
		}
		finally
		{
			if (sipgateDBAdapter != null)
			{
				sipgateDBAdapter.close();
			}
		}
	
		if (unreadCounter > 0) 
		{
			createNewVoiceMailNotification(unreadCounter);
		
			Log.d(TAG, "new unread voicemails: " + unreadCounter);
		}
		else
		{
			removeNewVoiceMailNotification();
		}
		
		for (PendingIntent pendingIntent: onNewVoiceMailsTriggers)
		{
			try 
			{
				Log.d(TAG, "notifying refresh voice mails to activity");
				pendingIntent.send();
			} 
			catch (CanceledException e) 
			{
				e.printStackTrace();
			}			
		}
	}
	
	/**
	 * @author graef
	 * @param Vector with callDataDBObjects
	 * @since 1.1
	 */
	
	public void notifyIfUnreadsCalls(Vector<CallDataDBObject> callDataDBObjects) 
	{
		Log.d(TAG, "notifyIfUnreadCalls");
		
		if (callDataDBObjects == null)
		{
			Log.i(TAG, "notifyIfUnreadsCalls() -> callDataDBObjects is null");
			return;
		}
		
		unreadCounter = 0;
		
		try
		{
			sipgateDBAdapter = new SipgateDBAdapter(this);
			
			oldCallDataDBObject = null;
					
			for (CallDataDBObject currentDataDBObject : callDataDBObjects)
			{
				oldCallDataDBObject = sipgateDBAdapter.getCallDataDBObjectById(currentDataDBObject.getId());
				
				if (currentDataDBObject.getRead() == -1)
				{
					if (oldCallDataDBObject == null)
					{
						currentDataDBObject.setRead(false);
					}
					else
					{
						currentDataDBObject.setRead(oldCallDataDBObject.isRead());
					}
				}
				
				if (oldCallDataDBObject == null && !currentDataDBObject.isRead() && currentDataDBObject.getDirection() == CallDataDBObject.INCOMING)
				{
					unreadCounter++;
				}
				
				oldCallDataDBObject = null;
			}
			
			sipgateDBAdapter.deleteAllCallDBObjects();
			sipgateDBAdapter.insertAllCallDBObjects(callDataDBObjects);
		}
		catch (Exception e)
		{
			Log.e(TAG, "notifyIfUnreadsCalls()", e);
		}
		finally
		{
			if (sipgateDBAdapter != null)
			{
				sipgateDBAdapter.close();
			}
		}
		
		if (unreadCounter > 0) 
		{
			createNewCallNotification(unreadCounter);
			
			Log.d(TAG, "new unread calls: " + unreadCounter);
		}
		else
		{
			removeNewCallNotification();
		}
		
		for (PendingIntent pendingIntent: onNewCallsTriggers)
		{
			try 
			{
				Log.d(TAG, "notifying refresh calls to activity");
				pendingIntent.send();
			} 
			catch (CanceledException e) 
			{
				e.printStackTrace();
			}			
		}
	}
		
	/**
	 * 
	 * @param unreadCounter
	 * @since 1.0
	 */
	private void createNewVoiceMailNotification(int unreadCounter) 
	{
		if (notifyClient != null)
		{
			notifyClient.setNotification(NotificationClient.NotificationType.VOICEMAIL, R.drawable.statusbar_voicemai_48, buildVoicemailNotificationString(unreadCounter));
		}
	}
	
	/**
	 * 
	 * @param unreadCounter
	 * @since 1.1
	 */
	private void createNewCallNotification(int unreadCounter) 
	{
		if (notifyClient != null)
		{
			notifyClient.setNotification(NotificationClient.NotificationType.CALL, R.drawable.statusbar_icon_calllist, buildCallNotificationString(unreadCounter));
		}
	}
	
	/**
	 * 
	 * @param unreadCounter
	 * @since 1.1
	 */
	private void removeNewCallNotification() 
	{
		if (notifyClient != null)
		{
			notifyClient.deleteNotification(NotificationClient.NotificationType.CALL);
		}
	}
	
	/**
	 * 
	 * @param unreadCounter
	 * @since 1.1
	 */
	private void removeNewVoiceMailNotification() 
	{
		if (notifyClient != null)
		{
			notifyClient.deleteNotification(NotificationClient.NotificationType.VOICEMAIL);
		}
	}
	
	/**
	 * 
	 * @param unreadCounter
	 * @return
	 * @since 1.0
	 */
	private String buildVoicemailNotificationString(int unreadCounter) 
	{
		if(unreadCounter == 1) {
			return String.format((String) getResources().getText(R.string.sipgate_a_new_voicemail), Integer.valueOf(unreadCounter));
		} else {
			return String.format((String) getResources().getText(R.string.sipgate_new_voicemails), Integer.valueOf(unreadCounter));
		}
	}
	
	/**
	 * 
	 * @param unreadCounter
	 * @return
	 * @since 1.1
	 */
	private String buildCallNotificationString(int unreadCounter) 
	{
		if(unreadCounter == 1 ) {
			return String.format((String) getResources().getText(R.string.sipgate_a_new_call), Integer.valueOf(unreadCounter));
		} else {
			return String.format((String) getResources().getText(R.string.sipgate_new_calls), Integer.valueOf(unreadCounter));
		}
	}
	
	/**
	 * 
	 * @since 1.0
	 */
	public IBinder asBinder() 
	{
		return null;
	}

	/**
	 * 
	 * @since 1.0
	 */
	public void registerOnVoiceMailsIntent(PendingIntent i) throws RemoteException 
	{
		Log.d(TAG, "registering on voice events intent");
		onNewVoiceMailsTriggers.add(i);
	}

	/**
	 * 
	 * @since 1.0
	 */
	public void unregisterOnVoiceMailsIntent(PendingIntent i) throws RemoteException 
	{
		Log.d(TAG, "unregistering on voice events intent");
		onNewVoiceMailsTriggers.remove(i);
	}
	
	/**
	 * 
	 * @since 1.1
	 */
	public void registerOnCallsIntent(PendingIntent i) throws RemoteException 
	{
		Log.d(TAG, "registering on call events intent");
		onNewCallsTriggers.add(i);
	}

	/**
	 * 
	 * @since 1.1
	 */
	public void unregisterOnCallsIntent(PendingIntent i) throws RemoteException 
	{
		Log.d(TAG, "unregistering on call events intent");
		onNewCallsTriggers.remove(i);
	}

	/**
	 * 
	 * @since 1.0
	 */
	@Override
	public IBinder onBind(Intent arg0) 
	{
		final EventService service = this;

		/**
		 * 
		 * @since 1.0
		 */
		return new Stub() 
		{

			/**
			 * 
			 * @since 1.0
			 */
			public void unregisterOnVoiceMailsIntent(PendingIntent i) throws RemoteException 
			{
				service.unregisterOnVoiceMailsIntent(i);
			}

			/**
			 * 
			 * @since 1.0
			 */
			public void registerOnVoiceMailsIntent(PendingIntent i)	throws RemoteException 
			{
				service.registerOnVoiceMailsIntent(i);
			}
			
			/**
			 * 
			 * @since 1.1
			 */
			public void unregisterOnCallsIntent(PendingIntent i) throws RemoteException 
			{
				service.unregisterOnCallsIntent(i);
			}

			/**
			 * 
			 * @since 1.1
			 */
			public void registerOnCallsIntent(PendingIntent i) throws RemoteException 
			{
				service.registerOnCallsIntent(i);
			}
			
			/**
			 * 
			 * @since 1.0
			 */
			@Override
			public void refreshVoicemails() throws RemoteException 
			{
				service.refreshVoicemails();
			}
			
			/**
			 * 
			 * @since 1.0
			 */
			@Override
			public void refreshCalls() throws RemoteException 
			{
				service.refreshCalls();
			}
		};
	}

	/**
	 * 
	 * @since 1.0
	 */
	public void refreshVoicemails() throws RemoteException
	{
		refreshVoicemailEvents();
	}
	
	/**
	 * 
	 * @since 1.1
	 */
	public void refreshCalls() throws RemoteException 
	{
		refreshCallEvents();
	}
	
	/**
	 * 
	 * @return
	 * @since 1.1
	 */
	private boolean hasVmListFeature() 
	{
		try {
			return ApiServiceProvider.getInstance(getApplicationContext()).featureAvailable(API_FEATURE.VM_LIST);
		} catch (Exception e) {
			Log.w(TAG, "startScanService() exception in call to featureAvailable() -> " + e.getLocalizedMessage());
		}
		
		return false;
	}
}
