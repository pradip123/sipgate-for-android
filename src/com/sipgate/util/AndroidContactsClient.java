package com.sipgate.util;

import java.util.ArrayList;

import android.app.Activity;
import android.os.Build;

import com.sipgate.interfaces.ContactsInterface;
import com.sipgate.models.SipgateContact;

public class AndroidContactsClient implements ContactsInterface {

	private ContactsInterface contactsInterface = null;
	
	public AndroidContactsClient(Activity activity) {
		if (Integer.parseInt(Build.VERSION.SDK) >= 5){
			this.contactsInterface = new AndroidContactsClient2x(activity);
		}
		else{
			this.contactsInterface = new AndroidContactsClient1x(activity);
		}	
	}
	
	public ArrayList<SipgateContact> getContacts() {
		return this.contactsInterface.getContacts();
	}

	
	public SipgateContact getContact(Integer index) {
		return this.contactsInterface.getContact(index);
	}
	
	public SipgateContact getContactById(Integer id) {
		return this.contactsInterface.getContactById(id);
	}
	
	public String getContactName(String phoneNumber) {
		return this.contactsInterface.getContactName(phoneNumber);
	}

	@Override
	public int getCount() {
		return this.contactsInterface.getCount();
	}
	
}
