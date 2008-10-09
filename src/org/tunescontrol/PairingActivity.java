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

import java.io.IOException;
import java.util.Hashtable;

import javax.jmdns.ServiceInfo;

import org.tunescontrol.daap.PairingServer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PairingActivity extends Activity {
	
	public final static String TAG = PairingActivity.class.toString();
	
	protected PairingServer server;
	protected ServiceInfo pairservice;
	
	protected String address, library;

	public Handler paired = new Handler() {
		@Override
		public void handleMessage(Message msg) {

			// someone has paried with us, so try returning with result
			// also be sure to pack along the pairing code used
			
			Intent packed = new Intent();
			packed.putExtra(BackendService.EXTRA_ADDRESS, address);
			packed.putExtra(BackendService.EXTRA_LIBRARY, library);
			packed.putExtra(BackendService.EXTRA_CODE, (String)msg.obj);
			setResult(Activity.RESULT_OK, packed);
			finish();
			
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// show dialog to user, explaining what happens next
		setContentView(R.layout.act_pairing);
		
		this.address = this.getIntent().getStringExtra(BackendService.EXTRA_ADDRESS);
		this.library = this.getIntent().getStringExtra(BackendService.EXTRA_LIBRARY);
		
		// this activity should start the pairing service
		// the pairing server will report to us when someone tries pairing
		server = new PairingServer(paired);

		Hashtable values = new Hashtable();
		values.put("DvNm", "Android device");
		values.put("RemV", "10000");
		values.put("DvTy", "iPod");
		values.put("RemN", "Remote");
		values.put("txtvers", "1");
		values.put("Pair", "0000000000000001");
		
		// NOTE: this "Pair" above is *not* the guid--we generate and return that in PairingServer
		
		pairservice = ServiceInfo.create(LibraryActivity.REMOTE_TYPE, "0000000000000000000000000000000000000006", 1024, 0, 0, values);

	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		new Thread(new Runnable() {
			public void run() {
				try {
					server.start();
					LibraryActivity.jmdns.registerService(pairservice);
				} catch (IOException e1) {
					e1.printStackTrace();
				}

			}
		}).start();
		
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		new Thread(new Runnable() {
			public void run() {
				server.stop();
				LibraryActivity.jmdns.unregisterService(pairservice);

			}
		}).start();
	
	}

}
