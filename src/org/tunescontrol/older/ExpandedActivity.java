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

package org.tunescontrol.older;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.tunescontrol.BackendService;
import org.tunescontrol.R;
import org.tunescontrol.BackendService.BackendBinder;
import org.tunescontrol.R.drawable;
import org.tunescontrol.R.layout;
import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.RequestHelper;
import org.tunescontrol.daap.Response;
import org.tunescontrol.daap.ResponseParser;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.Status;
import org.tunescontrol.daap.ResponseParser.TagListener;
import org.tunescontrol.util.UserTask;

import android.app.Activity;
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
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ExpandableListView.OnChildClickListener;

public class ExpandedActivity extends Activity implements OnScrollListener {
	
	public final static String TAG = ExpandedActivity.class.toString();

	protected BackendService backend;
	protected Session session;
	protected Library library;
	
	protected ExpandableListView list;
	protected AlbumAdapter adapter;
	
	public final static int HIDEPROG = 2;
	
	public Handler finished = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == HIDEPROG) {
				Log.d(TAG, "orly? someone told me to hide progress");
				ExpandedActivity.this.setProgressBarIndeterminateVisibility(false);
				ExpandedActivity.this.setProgressBarVisibility(false);
			}
		}
	};
	
	public ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			backend = ((BackendService.BackendBinder)service).getService();
			session = backend.getSession();
			
			if(session == null) return;
			
			// begin search now that we have a backend
			library = new Library(session);
			new Thread(new Runnable() {
				public void run() {
					library.readAlbums(adapter);
					// notify gui to stop spinning when finished
					finished.sendEmptyMessage(HIDEPROG);
				}
			}).start();
			
		}

		public void onServiceDisconnected(ComponentName className) {
			backend = null;
			session = null;
			
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
		
		this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		this.setProgressBarVisibility(true);
		this.setProgressBarIndeterminateVisibility(true);

		setContentView(R.layout.gen_expanded);

		this.blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		
		this.adapter = new AlbumAdapter(this);
		
		this.list = (ExpandableListView) this.findViewById(android.R.id.list);
		this.list.setAdapter(adapter);
		
		
		// handle user clicking on album (should queue up that album)
		// normal click should visit album details
		// long-press should bring up menu to start playing
		
		this.registerForContextMenu(list);

		this.list.setOnChildClickListener(new OnChildClickListener() {
			public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
				// launch activity to browse track details for this albums
				// TODO: expand this out to show individual tracks
				Response resp = (Response)adapter.getChild(groupPosition, childPosition);
				String albumid = resp.getNumberString("mper");
				session.controlPlayAlbum(albumid, 0);
				return false;
			}
		});
		

		// handle creating fast-scroller for items
		mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

		mScrollThumb = new ScrollThumb(this, this.list);
		mScrollThumb.setVisibility(View.INVISIBLE);
		
        mThumbLayout = new WindowManager.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_APPLICATION,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);
        mThumbLayout.x = 294;
        mThumbLayout.gravity = Gravity.TOP;
        
        list.setOnScrollListener(this);

        finished.post(new Runnable() {

			public void run() {
				mReady = true;
				WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
						LayoutParams.WRAP_CONTENT,
						LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.TYPE_APPLICATION,
						WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
								| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
						PixelFormat.TRANSLUCENT);
				//mWindowManager.addView(mDialogText, lp);
				Point globalOffset = new Point();
				Rect r = new Rect();
				boolean offset = list.getGlobalVisibleRect(r, globalOffset);
				mThumbLayout.x = 294;
				mThumbLayout.y = globalOffset.y;
				mThumbLayout.gravity = Gravity.TOP;
				mWindowManager.addView(mScrollThumb, mThumbLayout);
			}
		});

	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo)menuInfo;
		
		int group = list.getPackedPositionGroup(info.packedPosition),
			child = list.getPackedPositionChild(info.packedPosition);
		
		// go fetch the needed artist/albumid information
		switch(list.getPackedPositionType(info.packedPosition)) {

		case ExpandableListView.PACKED_POSITION_TYPE_GROUP:
			// only option for groups is play
			final String artist = (String)adapter.getGroup(group);
			menu.setHeaderTitle(artist);
			
			MenuItem play = menu.add("Play artist");
			play.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					session.controlPlayArtist(artist);
					return true;
				}
			});

			break;
			
		case ExpandableListView.PACKED_POSITION_TYPE_CHILD:
			// for a given album we could browse tracks or play
			
			Response resp = (Response)adapter.getChild(group, child);
			menu.setHeaderTitle(resp.getString("minm"));
			final String albumid = resp.getNumberString("mper");
			
			MenuItem play2 = menu.add("Play album");
			play2.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					session.controlPlayAlbum(albumid, 0);
					return true;
				}
			});

			MenuItem browse = menu.add("Browse tracks");
			browse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					return true;
				}
			});

			break;
			
		}
		
	}
	
    public void onScrollStateChanged(AbsListView view, int scrollState) {
	}

	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		int lastItem = firstVisibleItem + visibleItemCount - 1;
		if (mReady) {
			//char firstLetter = getCurrentFirstLetter(firstVisibleItem);

			if (!mShowing) {// && firstLetter != mPrevLetter) {

				mShowing = true;
				mScrollThumb.setVisibility(View.VISIBLE);

			}
			//mDialogText.setText(((Character) firstLetter).toString());
			//mHandler.removeCallbacks(mRemoveWindow);
			//mHandler.postDelayed(mRemoveWindow, 3000);
			//mPrevLetter = firstLetter;
			mScrollThumb.onItemScroll(firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem expand = menu.add("Expand all");
		expand.setIcon(android.R.drawable.ic_menu_more);
		expand.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {

				for (int i = 0; i < adapter.getGroupCount(); i++) {
					list.expandGroup(i);
				}

				return true;
			}
		});

		MenuItem collapse = menu.add("Collapse all");
		collapse.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		collapse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {

				for (int i = 0; i < adapter.getGroupCount(); i++) {
					list.collapseGroup(i);
				}

				return true;
			}
		});
		
		MenuItem refresh = menu.add("Refresh");
		refresh.setIcon(android.R.drawable.ic_menu_rotate);
		refresh.setEnabled(false);
		refresh.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				return true;
			}
		});
		
		return true;
	}

	void jumpToAlphabet(char alphabet) {
		Log.i(TAG, "Alphabet = " + alphabet);
		int groupCount = adapter.getGroupCount();
		for(int i = 0; i < groupCount; i++) {
			if(((String)adapter.getGroup(i)).charAt(0) == alphabet) {
				list.setSelection(list.getFlatListPosition(list.getPackedPositionForGroup(i)));
				Log.i(TAG, "Setting alpha based index to " + i);
				return;
			}
		}
	}
	
    public class AlbumAdapter extends BaseExpandableListAdapter implements TagListener {
    	
    	public Handler updateHandler = new Handler() {
    		@Override
    		public void handleMessage(Message msg) {
    			AlbumAdapter.this.notifyDataSetChanged();
    		}
    	};

    	protected final Context context;
		protected final LayoutInflater inflater;

		protected String[] artists = new String[] {};
		protected Map<String, List<Response>> tree = new HashMap<String, List<Response>>();
		
		/*
		 * agal/mlcl/mlit*

            mlit  --+
                    miid   4      00000025 == 37
                    mper   8      a150fef71188fb88 == 11624070975347817352
                    minm   13     New Surrender
                    asaa   8      Anberlin
                    mimc   4      0000000c == 12



		 */
		
		public AlbumAdapter(Context context) {
			this.context = context;
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		public final static int UPDATE_EVERY = 10;
		public int dirtyCount = 0;

		public void foundTag(String tag, Response resp) {
			
			String artist = resp.getString("asaa");
			if(artist == null) return;
			
			// create artist subtree if doesnt exist
			if(!tree.containsKey(artist))
				tree.put(artist, new LinkedList<Response>());
			
			tree.get(artist).add(resp);
			
			// notify changed when dirty enough, or when forced
			if(dirtyCount++ > UPDATE_EVERY) {
				this.buildArtists();
				this.updateHandler.sendEmptyMessage(-1);

				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				dirtyCount = 0;
			}
		
		}
		
		public void searchDone() {
			// one final update when finished
			this.buildArtists();
			this.updateHandler.sendEmptyMessage(-1);
		}
		
		protected synchronized void buildArtists() {
			this.artists = tree.keySet().toArray(new String[] {});
			Arrays.sort(this.artists);
		}
		
		
		public Object getChild(int groupPosition, int childPosition) {
			return tree.get(artists[groupPosition]).get(childPosition);
		}

		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}

		public int getChildrenCount(int groupPosition) {
			return tree.get(artists[groupPosition]).size();
		}

		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			if(convertView == null)
				convertView = this.inflater.inflate(R.layout.item_album, parent, false);
			
			Response child = (Response)this.getChild(groupPosition, childPosition);
			String title = child.getString("minm");
			String caption = String.format("%d tracks", child.getNumberLong("mimc"));

			((TextView)convertView.findViewById(android.R.id.text1)).setText(title);
			((TextView)convertView.findViewById(android.R.id.text2)).setText(caption);

			// go load image art
			((ImageView)convertView.findViewById(android.R.id.icon)).setImageBitmap(blank);
			new LoadPhotoTask().execute(new Integer(groupPosition), new Integer(childPosition), new Integer((int)child.getNumberLong("miid")));
			
			return convertView;
		}

		public Object getGroup(int groupPosition) {
			return artists[groupPosition];
		}

		public int getGroupCount() {
			return artists.length;
		}

		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			if(convertView == null)
				convertView = this.inflater.inflate(R.layout.item_artist, parent, false);
			
			String group = (String)this.getGroup(groupPosition);
			((TextView)convertView.findViewById(android.R.id.text1)).setText(group);
			
			return convertView;
		}

		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}

		public boolean hasStableIds() {
			return true;
		}

	}

	protected Map<Integer, SoftReference<Bitmap>> memcache = new HashMap<Integer, SoftReference<Bitmap>>();
	
    private class LoadPhotoTask extends UserTask<Object, Void, Object[]> {
		public Object[] doInBackground(Object... params) {

			Integer groupPosition = (Integer) params[0];
			Integer childPosition = (Integer) params[1];
			Integer itemid = (Integer) params[2];

			Bitmap bitmap = null;
			try {
				
				
				// first check if we have an in-memory cache of this bitmap
				if(memcache.containsKey(itemid)) {
					bitmap = memcache.get(itemid).get();
				}
				
				if(bitmap != null) {
					Log.d(TAG, String.format("MEMORY cache hit for %s", itemid.toString()));
				} else {
					
					File cache = new File(ExpandedActivity.this.getCacheDir(), itemid.toString());
					if(cache.exists()) {
						
						// first check if we have a local cache
						Log.d(TAG, String.format("disk cache hit for %s", itemid.toString()));
						bitmap = BitmapFactory.decodeFile(cache.toString());

					} else {
					
						// fetch the album cover from itunes
						byte[] raw = RequestHelper.request(String.format("%s/databases/%d/groups/%d/extra_data/artwork?session-id=%s&mw=55&mh=55&group-type=albums",
								session.getRequestBase(), session.databaseId, itemid, session.sessionId), false);
						bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
						//GET /databases/38/groups/233/extra_data/artwork?session-id=2031664365&revision-number=201&mw=55&mh=55&group-type=albums HTTP/1.1\r\n
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
			
			return new Object[] { groupPosition, childPosition, bitmap };

		}

		@Override
		public void end(Object[] result) {
			
			// update gui to show the newly-fetched albumart
			int groupPosition = ((Integer)result[0]).intValue();
			int childPosition = ((Integer)result[1]).intValue();
			Bitmap bitmap = (Bitmap)result[2];

			// skip if bitmap wasnt found
			if(bitmap == null)
				return;
			
			try {
				// skip updating this item if outside of bounds
				int position = list.getFlatListPosition(list.getPackedPositionForChild(groupPosition, childPosition));
				if(position < list.getFirstVisiblePosition() || position > list.getLastVisiblePosition())
					return;
				
				// find actual position and update view
				int visible = position - list.getFirstVisiblePosition();
				View view = list.getChildAt(visible);
				((ImageView)view.findViewById(android.R.id.icon)).setImageBitmap(bitmap);
				
			} catch(Exception e) {
				// we probably ran into an item thats now collapsed, just ignore
			}
			
		}
	}
    
    private WindowManager mWindowManager;
    //private TextView mDialogText;
    private boolean mShowing;
    private boolean mReady;
    private ScrollThumb mScrollThumb;
    private WindowManager.LayoutParams mThumbLayout;

    
    public class ScrollThumb extends ImageView {
		ExpandableListView listView;
		int firstY = -10000;
		int mTraverseHeight;
		int mThumbHeight = 64;
		boolean mAccInitiated;
		boolean mAlphaMode = true;
		private static final int ALPHABET_LENGTH = 26;

		public ScrollThumb(Context context, ExpandableListView listView) {
			super(context, null);

			setImageDrawable(context.getResources().getDrawable(R.drawable.scrollbar_state2));
			setFocusable(false);
			this.listView = listView;
			// TODO: Get thumb height
		}

		public void onItemScroll(int firstVisibleItem, int visibleItemCount,
				int totalItemCount) {
			if (mAccInitiated) {
				mAccInitiated = false;
				return;
			}
			//Log.i(TAG, "Not initiated by touch");
			if (mTraverseHeight <= 0) {
				mTraverseHeight = listView.getMeasuredHeight() - mThumbHeight;
			}
			Point globalOffset = new Point();
			Rect r = new Rect();
			boolean offset = listView.getGlobalVisibleRect(r, globalOffset);
			if(totalItemCount == 0) totalItemCount = 1;
			int rawY = (mTraverseHeight * firstVisibleItem) / totalItemCount + globalOffset.y;
			mThumbLayout.y = rawY - 25;
			mWindowManager.updateViewLayout(this, mThumbLayout);
		}

		public boolean onTouchEvent(MotionEvent event) {
			if (mTraverseHeight <= 0) {
				mTraverseHeight = listView.getMeasuredHeight() - 64;
			}
			Point globalOffset = new Point();
			Rect r = new Rect();
			boolean offset = listView.getGlobalVisibleRect(r, globalOffset);
			// Log.i("ListView", "" + listView.getMeasuredHeight() + "," +
			// listView.getHeight() + ","
			// + listView.getCount());
			// Log.i("ThumbPos", "" + event.getRawX() + "," + event.getRawY());
			int rawY = (int) event.getRawY();
			// Log.i(TAG, "Touched at " + rawY);
			mThumbLayout.y = (int) event.getRawY() - mThumbHeight / 2;
			int fy = (mThumbLayout.y - globalOffset.y);
			if (fy < 0) {
				fy = 0;
			}
			if (fy >= mTraverseHeight) {
				fy = mTraverseHeight - 1;
			}
			mThumbLayout.y -= 25;
			if (mThumbLayout.y < 25) {
				mThumbLayout.y = 25;
			}
			mWindowManager.updateViewLayout(this, mThumbLayout);
			if (mAlphaMode) {
				fy = (fy * ALPHABET_LENGTH) / mTraverseHeight;
				if (fy > ALPHABET_LENGTH - 1) {
					fy = ALPHABET_LENGTH - 1;
				}
				jumpToAlphabet((char) (65 + fy));
			} else {
				fy = (fy + 2) & 0xFFFC;
				fy = (int) ((listView.getCount() * (float) fy) / mTraverseHeight);
				listView.setSelectionFromTop(fy, 0);
			}
			// Log.i(TAG, "Setting selection to " + fy);
			mAccInitiated = true;
			//mDialogText.setVisibility(View.VISIBLE);
			return true;
		}
	}

    
}
