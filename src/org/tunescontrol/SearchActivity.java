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
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.RequestHelper;
import org.tunescontrol.daap.Response;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.Status;
import org.tunescontrol.daap.ResponseParser.TagListener;
import org.tunescontrol.util.UserTask;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

import java.lang.ref.SoftReference;

public class SearchActivity extends Activity {
	
	public final static String TAG = SearchActivity.class.toString();
	
	protected BackendService backend;
	protected Session session;
	protected Library library;
	
	protected String query;
	
	public ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			backend = ((BackendService.BackendBinder)service).getService();
			session = backend.getSession();
			
			if(session == null) return;
			
			// begin search now that we have a backend
			library = new Library(session);
			
			adapter = new SearchAdapter(SearchActivity.this, library, query);
			adapter.triggerPage();
			
			list.setOnScrollListener(adapter);
			list.addFooterView(adapter.footerView, null, false);
			list.setAdapter(adapter);
			
		}

		public void onServiceDisconnected(ComponentName className) {
			backend = null;
			session = null;
			
		}
	};
	
	public final static int FORCE_TOP = 2,
		REMOVE_FOOTER = 3;
	
	public Handler resultsUpdated = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == FORCE_TOP)
				list.setSelection(0);
			if(msg.what == REMOVE_FOOTER)
				list.removeFooterView(adapter.footerView);
			adapter.notifyDataSetChanged();
		}
	};
	
	protected ListView list;
	protected SearchAdapter adapter;

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
		setContentView(R.layout.gen_list);
		
		this.list = (ListView)this.findViewById(android.R.id.list);
		this.query = this.getIntent().getStringExtra(SearchManager.QUERY);
		//this.query = "jes";
		
		this.blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		
		//final Animation slide = AnimationUtils.loadAnimation(this, R.anim.slide_right);
		
		// perform search based on incoming string
		// also have an adapter that paginates results
		
		this.list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// launch off event to play this search result
				if(session != null)
					session.controlPlaySearch(query, position);
				
				// launch view off on nice animation to show that we listened to user
				//view.startAnimation(slide);
				
			}
		});
		
	}
	
	public class SearchAdapter extends BaseAdapter implements TagListener, OnScrollListener {
		
		public final static int PAGE_SIZE = 10;

		protected Context context;
		protected LayoutInflater inflater;
		
		protected Library library;
		protected String search;
		
		protected List<Response> results = new LinkedList<Response>();
		protected long totalResults = 1;
		
		public final View footerView;
		
		public SearchAdapter(Context context, Library library, String search) {
			this.context = context;
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			this.footerView = this.inflater.inflate(R.layout.item_fetch, null, false);
			
			this.library = library;
			this.search = search;
			
		}
		
		public void foundTag(String tag, Response resp) {
			// add a found search result to our list
			if(resp.containsKey("minm"))
				results.add(resp);
		}
		
		protected boolean firstFetch = true;
		protected boolean fetchRequested = false;

		public void searchDone() {
			// force update of cursor data sources
			resultsUpdated.sendEmptyMessage(firstFetch ? FORCE_TOP : -1);
			this.fetchRequested = false;
			this.firstFetch = false;
		}

		public Object getItem(int position) {
			if(position < results.size())
				return results.get(position);
			return null;
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
		
		public void triggerPage() {

			this.fetchRequested = true;

			// trigger fetch of next page of results if available
			// TODO: somehow make sure we dont double-trigger this from the gui
			if(this.totalResults > this.getCount()) {
				
				new Thread(new Runnable() {
					public void run() {
						Log.d(TAG, "getView() is triggering a new page to be loaded");
						totalResults = library.readSearch(SearchAdapter.this, search, getCount(), PAGE_SIZE);
					}
				}).start();
				
			} else {
				// change our footer view to say no more results
				resultsUpdated.sendEmptyMessage(REMOVE_FOOTER);
//				this.footerView.setVisibility(View.GONE);
//				this.footerView.requestLayout();
			}

		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			
			if(convertView == null)
				convertView = inflater.inflate(R.layout.item_album, parent, false);

			// otherwise show normal search result
			Response resp = (Response)this.getItem(position);
			
			String title = resp.getString("minm");
			String caption = String.format("%s - %s", resp.getString("asar"), resp.getString("asal"));
			
			((TextView)convertView.findViewById(android.R.id.text1)).setText(title);
			((TextView)convertView.findViewById(android.R.id.text2)).setText(caption);
			
			// TODO: fetch artwork from local cache
			
			// start a usertask to fetch the album art
			// blank out any current art first
			((ImageView)convertView.findViewById(android.R.id.icon)).setImageBitmap(blank);
			new LoadPhotoTask().execute(new Integer(position), new Integer((int)resp.getNumberLong("miid")));
			
			
			/*
			 mlit  --+
                    mikd   1      02 == 2
                    asal   11     B Collision
                    asar   18     David Crowder Band
                    miid   4      00000d96 == 3478
                    minm   59     Be Lifted Or Hope Rising (w/Shane & Shane/Robbie Seay Band)

			 */
			
			
			// inflate views as needed
			// fetch more results if paginating item requested, and if more exist 
			
			return convertView;
			
			
		}

		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			// trigger more search results when hitting the last item
			if(this.fetchRequested) return;
			if(firstVisibleItem + visibleItemCount == totalItemCount)
				this.triggerPage();

		}

		public void onScrollStateChanged(AbsListView view, int scrollState) {
		}

	}
	
	protected Map<Integer, SoftReference<Bitmap>> memcache = new HashMap<Integer, SoftReference<Bitmap>>();
	
    private class LoadPhotoTask extends UserTask<Object, Void, Object[]> {
		public Object[] doInBackground(Object... params) {

			Integer position = (Integer) params[0];
			Integer itemid = (Integer) params[1];

			Bitmap bitmap = null;
			try {
				
				
				// first check if we have an in-memory cache of this bitmap
				if(memcache.containsKey(itemid)) {
					bitmap = memcache.get(itemid).get();
				}
				
				if(bitmap != null) {
					//Log.d(TAG, String.format("MEMORY cache hit for %s", itemid.toString()));
				} else {
					
					File cache = new File(SearchActivity.this.getCacheDir(), itemid.toString());
					if(cache.exists()) {
						
						// first check if we have a local cache
						//Log.d(TAG, String.format("disk cache hit for %s", itemid.toString()));
						bitmap = BitmapFactory.decodeFile(cache.toString());

					} else {
					
						// fetch the album cover from itunes
						byte[] raw = RequestHelper.request(String.format("%s/databases/%d/items/%d/extra_data/artwork?session-id=%s&mw=55&mh=55",
								session.getRequestBase(), session.databaseId, itemid, session.sessionId), false);
						bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
		
						// cache the image locally so we can find it faster in future
						OutputStream out = new FileOutputStream(cache);
						out.write(raw);
						out.close();
						
					}
					
					// if SOMEHOW (404, etc) this image was still null, then save as blank
					if(bitmap == null)
						bitmap = blank;

					// try removing any stale references
					memcache.remove(itemid);
					memcache.put(itemid, new SoftReference<Bitmap>(bitmap));
					
				}
				
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return new Object[] { position, bitmap };

		}

		@Override
		public void end(Object[] result) {
			
			// update gui to show the newly-fetched albumart
			int position = ((Integer)result[0]).intValue();
			Bitmap bitmap = (Bitmap)result[1];

			// skip if bitmap wasnt found
			if(bitmap == null)
				return;
			
			// skip updating this item if outside of bounds
			if(position < list.getFirstVisiblePosition() || position > list.getLastVisiblePosition())
				return;
			
			// find actual position and update view
			int visible = position - list.getFirstVisiblePosition();
			View view = list.getChildAt(visible);
			((ImageView)view.findViewById(android.R.id.icon)).setImageBitmap(bitmap);

		}
	}

}
