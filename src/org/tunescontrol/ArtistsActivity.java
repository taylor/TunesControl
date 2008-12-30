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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.RequestHelper;
import org.tunescontrol.daap.Response;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.ResponseParser.TagListener;
import org.tunescontrol.older.ExpandedActivity;
import org.tunescontrol.older.AlphaView.AlphaScrollable;
import org.tunescontrol.util.UserTask;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView.OnChildClickListener;

public class ArtistsActivity extends ListActivity implements AlphaScrollable {
	

	public final static String TAG = ExpandedActivity.class.toString();

	protected BackendService backend;
	protected Session session;
	protected Library library;
	
	protected ArtistsAdapter adapter;
	
	public ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			backend = ((BackendService.BackendBinder)service).getService();
			session = backend.getSession();
			
			if(session == null) return;
			
			// if we already have artists, then leave alone
			if(adapter.results.size() > 0) return;
			//adapter.results.clear();
			
			// begin search now that we have a backend
			library = new Library(session);
			new Thread(new Runnable() {
				public void run() {
					library.readArtists(adapter);
				}
			}).start();
			
		}

		public void onServiceDisconnected(ComponentName className) {
			backend = null;
			session = null;
			
		}
	};
	
	public Handler resultsUpdated = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			adapter.notifyDataSetChanged();
		}
	};

	
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
	
	protected Bitmap blank;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gen_list_fast);
		
		((TextView)this.findViewById(android.R.id.empty)).setText(R.string.artists_empty);

		// register ourselves with the alpha view
		//AlphaView alpha = (AlphaView)this.findViewById(R.id.alpha);
		//alpha.setVisibility(View.VISIBLE);
		//alpha.target = this;

		this.adapter = new ArtistsAdapter(this);
		this.setListAdapter(adapter);
		
		this.registerForContextMenu(this.getListView());

		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// launch activity to browse track details for this albums
				try {
					Response resp = (Response)adapter.getItem(position);
					final String artist = resp.getString("mlit");
					
					Intent intent = new Intent(ArtistsActivity.this, AlbumsActivity.class);
					intent.putExtra(Intent.EXTRA_TITLE, artist);
				ArtistsActivity.this.startActivity(intent);
				} catch(Exception e) {
					e.printStackTrace();
				}
				
			}
		});
		
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
		
		// create context menu to play entire artist
		try {
			Response resp = (Response)adapter.getItem(info.position);
			final String artist = resp.getString("mlit");
			menu.setHeaderTitle(artist);
			
			MenuItem play = menu.add(R.string.artists_menu_play);
			play.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					session.controlPlayArtist(artist);
					return true;
				}
			});
	
			MenuItem browse = menu.add(R.string.artists_menu_browse);
			browse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					Intent intent = new Intent(ArtistsActivity.this, AlbumsActivity.class);
					intent.putExtra(Intent.EXTRA_TITLE, artist);
					ArtistsActivity.this.startActivity(intent);
					return true;
				}
			});
		} catch(Exception e) {
			e.printStackTrace();
		}
		

	}
	
	protected char startsWith(int index) {
		if(index < 0 || index >= adapter.nice.size()) return ' ';
		return adapter.nice.get(index).charAt(0);
	}

	public void scrollAlpha(char prefix) {
		
		// perform a smart search where we "guess" the approximate letter location
		// then move around in the right direction until we find the start of a letter
		
		int size = adapter.getCount();
		int actual = (prefix - 'A');
		int index = (actual * size) / 26;
		int original = index;
		
		char current = startsWith(index);
		
		if(current >= prefix) {
			// we need to walk backwards until we find the first item before the prefix
			while(current >= prefix && index > 0) {
				index--;
				current = startsWith(index);
			}
			
		} else if(current < prefix) {
			// we need to walk forwards until we find the first item with the prefix
			while(current < prefix && index < size - 1) {
				index++;
				current = startsWith(index);
			}
			
		}
		
		// one way or another, we now have the starting index
		Log.d(TAG, String.format("scrollAlpha start=%d, end=%d, delta=%d", original, index, Math.abs(original - index)));
		
		this.getListView().setSelectionFromTop(index, 0);
		
//		for(int i = 0; i < size; i++) {
//			Response resp = (Response)adapter.getItem(i);
//			String key = resp.getString("mlit");
//			if(key.length() == 0) continue;
//			if(key.charAt(0) == prefix) {
//				this.getListView().setSelectionFromTop(i, 0);
//				return;
//			}
//		}
		
	}


	public class ArtistsAdapter extends BaseAdapter implements TagListener {
		
		protected Context context;
		protected LayoutInflater inflater;
		
		protected List<Response> results = new LinkedList<Response>();
		public List<String> nice = new ArrayList<String>();
		
		public ArtistsAdapter(Context context) {
			this.context = context;
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			
		}
		
		public void foundTag(String tag, Response resp) {
			try {
				// add a found search result to our list
				if(resp.containsKey("mlit")) {
					String mlit = resp.getString("mlit");
					if(mlit.length() > 0 && !mlit.startsWith("mshc")) {
						results.add(resp);
						nice.add(mlit.replaceAll("The ", "").toUpperCase());
					}
			}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		public void searchDone() {
			resultsUpdated.removeMessages(-1);
			resultsUpdated.sendEmptyMessage(-1);
		}

		public Object getItem(int position) {
			return results.get(position);
		}

		public boolean hasStableIds() {
			return true;
		}

		public int getCount() {
			return results.size();
		}
		 
		public long getItemId(int position) {
			return position;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			
			if(convertView == null)
				convertView = inflater.inflate(R.layout.item_artist, parent, false);

			try {
				// otherwise show normal search result
				Response resp = (Response)this.getItem(position);
				
				String title = resp.getString("mlit");
			((TextView)convertView.findViewById(android.R.id.text1)).setText(title);
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			
			/*
	abro  --+
	        mstt   4      000000c8 == 200
	        muty   1      00 == 0
	        mtco   4      000001ea == 490
	        mrco   4      000001ea == 490
	        abar  --+
	                mlit   11     Aaron Shust
	                mlit   10     Acceptance
	                mlit   15     Acues & Elitist

			 */
			
			
			return convertView;
			
			
		}


	}
}
