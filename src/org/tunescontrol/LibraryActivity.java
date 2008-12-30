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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.DNSConstants;
import javax.jmdns.impl.DNSEntry;
import javax.jmdns.impl.DNSRecord;
import javax.jmdns.impl.JmDNSImpl;

import org.tunescontrol.SearchActivity.SearchAdapter;
import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.PairingServer;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.Status;



import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

public class LibraryActivity extends Activity {
	
	// show a list of all libraries found on local wifi network
	// should have refresh button easly accessibly, and also detect wifi issues

	
	public final static String TAG = LibraryActivity.class.toString();
	
	public static JmDNS jmdns;
	public static JmDNSImpl impl;
	protected ServiceListener listener;
	protected ServiceInfo info;
	
	public final static String TOUCH_ABLE_TYPE = "_touch-able._tcp.local.",
		DACP_TYPE = "_dacp._tcp.local.",
		REMOTE_TYPE = "_touch-remote._tcp.local.",
		HOSTNAME = "meowbox4";
	
	protected BackendService backend;
	
	public ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			backend = ((BackendService.BackendBinder)service).getService();
			
		}

		public void onServiceDisconnected(ComponentName className) {
			backend = null;
			
		}
	};
	
	// this screen will run a network query of all libraries
	// upon selection it will try authenticating with that library, and launch the pairing activity if failed

	protected void startProbe() throws Exception {
		
		if(this.jmdns != null)
			this.stopProbe();
		
		adapter.known.clear();
		adapter.notifyDataSetChanged();

		// figure out our wifi address, otherwise bail
		WifiManager wifi = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
		
		WifiInfo wifiinfo = wifi.getConnectionInfo();
		int intaddr = wifiinfo.getIpAddress();
		
		byte[] byteaddr = new byte[] { (byte)(intaddr & 0xff), (byte)(intaddr >> 8 & 0xff), (byte)(intaddr >> 16 & 0xff), (byte)(intaddr >> 24 & 0xff) };
		InetAddress addr = InetAddress.getByAddress(byteaddr);
		
		Log.d(TAG, String.format("found intaddr=%d, addr=%s", intaddr, addr.toString()));

		this.jmdns = JmDNS.create(addr, HOSTNAME);
		this.jmdns.addServiceListener(TOUCH_ABLE_TYPE, listener);
		this.jmdns.addServiceListener(DACP_TYPE, listener);
		
		this.impl = (JmDNSImpl)jmdns;
		
		
	}
	
	protected void stopProbe() {
		
		this.jmdns.removeServiceListener(TOUCH_ABLE_TYPE, listener);
		this.jmdns.removeServiceListener(DACP_TYPE, listener);
		this.jmdns.close();
		this.jmdns = null;
		this.impl = null;
		
		// trigger adapter refresh
		
	}
	
	public final static int DONE = 3;
	
	public Handler resultsUpdated = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(msg.obj != null)
				adapter.notifyFound((String)msg.obj);
			adapter.notifyDataSetChanged();
		}
	};
	
	public final static int DELAY = 500;
	
	protected ListView list;
	protected LibraryAdapter adapter;

	@Override
	public void onStart() {
		super.onStart();
		this.bindService(new Intent(this, BackendService.class), connection, Context.BIND_AUTO_CREATE);

	}
	
	@Override
	public void onStop() {
		super.onStop();
		this.unbindService(connection);
	
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// someone thinks they are ready to pair with us
		if(resultCode == Activity.RESULT_CANCELED)
			return;
		
		String address = data.getStringExtra(BackendService.EXTRA_ADDRESS);
		String library = data.getStringExtra(BackendService.EXTRA_LIBRARY);
		String code = data.getStringExtra(BackendService.EXTRA_CODE);
		Log.d(TAG, String.format("onActivityResult with address=%s, library=%s, code=%s and resultcode=%d", address, library, code, resultCode));

		try {
			
			// check to see if we can actually authenticate against the library
			backend.setLibrary(address, library, code);
			
			// if successful, then throw back to controlactivity
			//LibraryActivity.this.startActivity(new Intent(LibraryActivity.this, ControlActivity.class));
			LibraryActivity.this.setResult(Activity.RESULT_OK);
			LibraryActivity.this.finish();

		} catch(Exception e) {
			
			Log.e(TAG, String.format("ohhai we had problemz, probably still unpaired"), e);
			
			// we probably had a pairing issue, so start the pairing server and wait for trigger
			Intent intent = new Intent(LibraryActivity.this, PairingActivity.class);
			intent.putExtra(BackendService.EXTRA_ADDRESS, address);
			intent.putExtra(BackendService.EXTRA_LIBRARY, library);
			LibraryActivity.this.startActivityForResult(intent, 1);
			
		}
		
		
	}
	
	// there should be a single backend service that holds current session information
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gen_list);
		
		this.adapter = new LibraryAdapter(this);
		
		this.list = (ListView)this.findViewById(android.R.id.list);
		this.list.addHeaderView(adapter.footerView, null, false);
		this.list.setAdapter(adapter);
		
		this.list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// read ip/port from caption if present
				// pass off to backend to try creating pairing session
				
				if(backend == null) return;

				String caption = ((TextView)view.findViewById(android.R.id.text2)).getText().toString();
				String[] split = caption.split("-");
				if(split.length < 2) return;
				
				String address = split[0].trim();
				String library = split[1].trim();

				// push off fake result to try login
				// this will start the pairing process if needed
				Intent shell = new Intent();
				shell.putExtra(BackendService.EXTRA_ADDRESS, address);
				shell.putExtra(BackendService.EXTRA_LIBRARY, library);
				onActivityResult(-1, Activity.RESULT_OK, shell);
				
			}
		});
		
		// launch dns backend and fire a service search
		// on arrival update our adapter, but wait on actual dns resolution until user clicks
		// on click try negotiating with the library
		
		this.listener = new ServiceListener() {

			@Override
			public void serviceAdded(ServiceEvent event) {
				
				// someone is yelling about their touch-able service (prolly itunes)
				// go figure out what their ip address is
				
				final String name = event.getName(),
					type = event.getType();
				
				// trigger delayed gui event
				// needs to be delayed because jmdns hasnt parsed txt info yet
				
				Log.w(TAG, String.format("serviceAdded(event=\n%s\n)", event.toString()));
				
				String address = String.format("%s.%s", name, type);
				resultsUpdated.sendMessageDelayed(Message.obtain(resultsUpdated, -1, address), DELAY);
				
			}

			public void serviceRemoved(ServiceEvent event) {
			}

			public void serviceResolved(ServiceEvent event) {
			}
			
		};
		
		try {
			this.startProbe();
		} catch (Exception e) {
			e.printStackTrace();
		}


	
//		// wait a hiccup to finish remaining packets, then try finding txt info
//		new Thread(new Runnable() {
//			public void run() {
//				
//				try {
//					Thread.sleep(500);
//					
//					// try finding txt records
//					DNSEntry entry = impl.getCache().get(name + "." + type, DNSConstants.TYPE_TXT, DNSConstants.CLASS_IN);
//					Log.w(TAG, String.format("yay found entry=%s", entry.toString()));
//					
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//				
//				
//				
//			}
//		}).start();
		
		
	}
	
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem refresh = menu.add(R.string.library_menu_refresh);
		refresh.setIcon(android.R.drawable.ic_menu_rotate);
		refresh.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				
				try {
					startProbe();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return true;
			}
		});
		
		MenuItem manual = menu.add(R.string.library_menu_manual);
		manual.setIcon(android.R.drawable.ic_menu_manage);
		manual.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				
				LayoutInflater inflater = (LayoutInflater)LibraryActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				final View view = inflater.inflate(R.layout.dia_text, null);
				final TextView address = (TextView)view.findViewById(android.R.id.text1);
				final TextView code = (TextView)view.findViewById(android.R.id.text2);
				
				new AlertDialog.Builder(LibraryActivity.this)
				.setView(view)
				.setPositiveButton(R.string.library_manual_pos, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						// try connecting to this specific ip address
						Intent shell = new Intent();
						shell.putExtra(BackendService.EXTRA_ADDRESS, address.getText().toString());
						shell.putExtra(BackendService.EXTRA_LIBRARY, "0");
						shell.putExtra(BackendService.EXTRA_CODE, code.getText().toString());
						onActivityResult(-1, Activity.RESULT_OK, shell);
						
					}
				})
				.setNegativeButton(R.string.library_manual_neg, new DialogInterface.OnClickListener() {
				    public void onClick(DialogInterface dialog, int which) {
				    }
				}).create().show();


				return true;
			}
		});
		
		MenuItem forget = menu.add(R.string.library_menu_forget);
		forget.setIcon(android.R.drawable.ic_menu_delete);
		forget.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {

				new AlertDialog.Builder(LibraryActivity.this)
				.setMessage(R.string.library_forget)
				.setPositiveButton(R.string.library_forget_pos, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						backend.pairdb.deleteAll();
						
					}
				})
				.setNegativeButton(R.string.library_forget_neg, new DialogInterface.OnClickListener() {
				    public void onClick(DialogInterface dialog, int which) {
				    }
				}).create().show();
				
				return true;
			}
		});
		
		return true;
    }
    

	public class LibraryAdapter extends BaseAdapter {
		
		protected Context context;
		protected LayoutInflater inflater;
		
		public View footerView;
		
		protected List<String> known = new LinkedList<String>();
		
		public LibraryAdapter(Context context) {
			this.context = context;
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			
			this.footerView = inflater.inflate(R.layout.item_network, null, false);
			
		}
		
		public void notifyFound(String address) {
			known.add(address);
		}
		
		public Object getItem(int position) {
			return known.get(position);
		}

		public boolean hasStableIds() {
			return true;
		}

		public int getCount() {
			return known.size();
		}
		 
		public long getItemId(int position) {
			return position;
		}
		
		
		public View getView(int position, View convertView, ViewGroup parent) {
			
			if(convertView == null)
				convertView = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
			
			
			try {
			
				// fetch the dns txt record to get library info
				String dnsname = (String)this.getItem(position);
				DNSRecord.Text txtrec = (DNSRecord.Text)impl.getCache().get(dnsname, DNSConstants.TYPE_TXT, DNSConstants.CLASS_IN);
				
				// try finding useful txt header strings
				String[] headers = new String(txtrec.text).split("[\u0000-\u001f]");
				String type = "", title = "", library = "";
				for(String header : headers) {
					if(header.startsWith("DvTy"))
						type = header.substring(5);
					if(header.startsWith("CtlN"))
						title = header.substring(5);
					if(header.startsWith("DbId"))
						library = header.substring(5);
				}
	
				// find the parent computer running this service
				DNSRecord.Service srvrec = (DNSRecord.Service)impl.getCache().get(dnsname, DNSConstants.TYPE_SRV, DNSConstants.CLASS_IN);
				String hostname = srvrec.server;
				int hostport = srvrec.port;
	
				// finally, resolve A record for parent host computer
				DNSRecord.Address arec = (DNSRecord.Address)impl.getCache().get(hostname, DNSConstants.TYPE_A, DNSConstants.CLASS_IN);
				String addr = arec.getAddress().toString().replaceFirst("[^0-9\\.]", "");
				
				
				((TextView)convertView.findViewById(android.R.id.text1)).setText(title);
				((TextView)convertView.findViewById(android.R.id.text2)).setText(String.format("%s - %s", addr, library));
				
			} catch(Exception e) {
				e.printStackTrace();
				((TextView)convertView.findViewById(android.R.id.text1)).setText("");
				((TextView)convertView.findViewById(android.R.id.text2)).setText("");
			}
			
			return convertView;
			
			
		}

	}
	

}
