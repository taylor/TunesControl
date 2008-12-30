/*
	Copyright (C) 2008 Jeffrey Sharkey, http://jsharkey.org/
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.tunescontrol;

import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.Status;
import org.tunescontrol.util.PairingDatabase;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class BackendService extends Service {
	
	public final static String TAG = BackendService.class.toString();
	public final static String PREFS = "tunescontrol",
		PREF_LASTADDR = "lastaddress";
	
	public final static String EXTRA_LIBRARY = "library",
		EXTRA_ADDRESS = "address",
		EXTRA_CODE = "code";
	
	// this service keeps a single session active that others can attach to
	// also handles incoming control information from libraryactivity

	protected Session session = null;
	
	protected String lastaddress = null;
	
	public Session getSession() {
		// make sure we have an active session
		// create from last-known connection if needed
		
		if(session == null) {
			// try finding last library opened by user
			this.lastaddress = prefs.getString(PREF_LASTADDR, null);
			Log.d(TAG, String.format("tried looking for lastaddr=%s", lastaddress));
			if(this.lastaddress != null) {
				try {
					this.setLibrary(this.lastaddress, null, null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		return session;
		
	}
	
	public void setLibrary(String address, String library, String code) throws Exception {
		// try starting a new session
		// if failed, launch pairing activity
		
		// open new session to selected host
		// TODO: BIG HUGE RED WARNING FLAGS
		// this should be replaced with a MUCH more secure guid generation method
		// along with backend db for guid storage
		// this was mostly kept around to make debugging easier
		//this.session = new Session(ip, "0000000000000001");
		
		// try looking up code in database if null
		if(code == null) {
			if(library != null) {
				code = pairdb.findCodeLibrary(library);
			} else if(address != null) {
				code = pairdb.findCodeAddress(address);
			}
		}
		
		Log.d(TAG, String.format("trying to create session with address=%s, library=%s, code=%s", address, library, code));
		
		this.session = new Session(address, code);
		
		// if we made it past this point, then we logged in successfully yay
		Log.d(TAG, "yay found session!  were gonna update our db a new code maybe?");
		
		// if we have a library, we should make sure that its stored in our db
		// create a new entry, otherwise just update the ip address
		if(library != null) {
			if(!pairdb.libraryExists(library)) {
				pairdb.insertCode(address, library, code);
			} else {
				pairdb.updateAddress(library, address);
			}
		}
		
		// save this ip address to help us start faster
		Editor edit = prefs.edit();
		edit.putString(PREF_LASTADDR, address);
		edit.commit();
		
		
	}
	
	protected SharedPreferences prefs;
	protected PairingDatabase pairdb;
	
	@Override
	public void onCreate() {
		
		Log.d(TAG, "starting backend service");

		this.prefs = this.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		this.pairdb = new PairingDatabase(this);

	}
	
	@Override
	public void onDestroy() {
		// close any dns services and current status threads
		// store information about last-connected library
		
		Log.d(TAG, "stopping backend service");

		this.pairdb.close();

		
	}
	
	

	public class BackendBinder extends Binder {
		public BackendService getService() {
			return BackendService.this;
		}
	}

	private final IBinder binder = new BackendBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	

	
	
}
