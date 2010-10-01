package com.sipgate.service;

import com.sipgate.api.types.Event;

interface EventService {
	void registerOnVoiceMailsIntent(in PendingIntent i);
	void unregisterOnVoiceMailsIntent(in PendingIntent i);
	void registerOnCallsIntent(in PendingIntent i);
	void unregisterOnCallsIntent(in PendingIntent i);
	
	void refreshVoicemails();
	void refreshCalls();
}
