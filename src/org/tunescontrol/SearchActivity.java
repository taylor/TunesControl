/*
 * Copyright (C) 2008 Jeffrey Sharkey, http://jsharkey.org/ This program is free
 * software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program
 * is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.tunescontrol;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.RequestHelper;
import org.tunescontrol.daap.Response;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.ResponseParser.TagListener;
import org.tunescontrol.util.RecentProvider;
import org.tunescontrol.util.ThreadExecutor;
import org.tunescontrol.util.UserTask;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

public class SearchActivity extends Activity {

   public final static String TAG = SearchActivity.class.toString();

   protected BackendService backend;
   protected Session session;
   protected Library library;
   protected String query;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();
         session = backend.getSession();

         if (session == null)
            return;

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

   public final static int FORCE_TOP = 2, REMOVE_FOOTER = 3;

   public Handler resultsUpdated = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         if (msg.what == FORCE_TOP)
            list.setSelection(0);
         if (msg.what == REMOVE_FOOTER) {
            list.removeFooterView(adapter.footerView);
            list.requestLayout();
         }
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

      // make sure that we release usertask
      // UserTask.resume();
      synchronized (adapter.scrollWait) {
         adapter.scrolling = false;
         adapter.scrollWait.notifyAll();
      }

   }

   protected Bitmap blank;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.gen_list);

      this.list = (ListView) this.findViewById(android.R.id.list);
      this.query = this.getIntent().getStringExtra(SearchManager.QUERY);
      // this.query = "jes";

      // store search in recent history
      SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, RecentProvider.AUTHORITY, RecentProvider.MODE);
      suggestions.saveRecentQuery(query, null);

      this.blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

      this.registerForContextMenu(this.list);

      // final Animation slide = AnimationUtils.loadAnimation(this,
      // R.anim.slide_right);

      // perform search based on incoming string
      // also have an adapter that paginates results

      this.list.setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // launch off event to play this search result
            if (session != null)
               session.controlPlaySearch(query, position);
            	setResult(RESULT_OK);
            	finish();
            // launch view off on nice animation to show that we listened to
            // user
            // view.startAnimation(slide);

         }
      });

   }

   @Override
   public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

      final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

      try {
         // create context menu to play entire artist
         final Response resp = (Response) adapter.getItem(info.position);
         menu.setHeaderTitle(resp.getString("minm"));
         final String artistName = resp.getString("asar");

         final MenuItem play = menu.add(R.string.search_menu_play_found);
         play.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                session.controlPlaySearch(query, info.position);
            	setResult(RESULT_OK);
            	finish();
               return true;
            }
         });

         final MenuItem queue = menu.add(R.string.search_menu_open_artist);
         queue.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent(SearchActivity.this, AlbumsActivity.class);
                intent.putExtra(Intent.EXTRA_TITLE, artistName);
                SearchActivity.this.startActivity(intent);
               return true;
            }
         });

         /*final MenuItem browse = menu.add(R.string.search_menu_open_album);
         browse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
               Intent intent = new Intent(SearchActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, albumid);
               SearchActivity.this.startActivity(intent);
               return true;
            }
         });*/
      } catch (Exception e) {
         Log.w(TAG, "onCreateContextMenu:" + e.getMessage());
      }

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
         if (resp.containsKey("minm"))
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
         if (position < results.size())
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
         if (this.totalResults > this.getCount()) {

            ThreadExecutor.runTask(new Runnable() {
               public void run() {
                  Log.d(TAG, "getView() is triggering a new page to be loaded");
                  totalResults = library.readSearch(SearchAdapter.this, search, getCount(), PAGE_SIZE);
                  int j = 0;
                  j++;
               }
            });

         } else {
            // change our footer view to say no more results
            resultsUpdated.sendEmptyMessage(REMOVE_FOOTER);
            // this.footerView.setVisibility(View.GONE);
            // this.footerView.requestLayout();
         }

      }

      public View getView(int position, View convertView, ViewGroup parent) {

         if (convertView == null)
            convertView = inflater.inflate(R.layout.item_album, parent, false);

         try {
            // otherwise show normal search result
            Response resp = (Response) this.getItem(position);

            String title = resp.getString("minm");
            String caption = String.format("%s - %s", resp.getString("asar"), resp.getString("asal"));

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(title);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(caption);

            // TODO: fetch artwork from local cache

            // start a usertask to fetch the album art
            // blank out any current art first
            ((ImageView) convertView.findViewById(android.R.id.icon)).setImageBitmap(blank);

            new LoadPhotoTask().execute(Integer.valueOf(position), Integer.valueOf((int) resp.getNumberLong("miid")));

         } catch (Exception e) {
            Log.d(TAG, String.format("onCreate Error: %s", e.getMessage()));
         }

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
         if (this.fetchRequested)
            return;
         if (firstVisibleItem + visibleItemCount == totalItemCount)
            this.triggerPage();

      }

      public boolean scrolling = false;
      public Object scrollWait = new Object();

      public void onScrollStateChanged(AbsListView view, int scrollState) {
         if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            synchronized (scrollWait) {
               scrolling = false;
               scrollWait.notifyAll();
            }
         } else {
            synchronized (scrollWait) {
               scrolling = true;
            }
         }
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
            if (memcache.containsKey(itemid)) {
               bitmap = memcache.get(itemid).get();
            }

            if (bitmap == null) {

               // if scrolling, wait until weve finished
               if (adapter.scrolling) {
                  // Log.w(TAG, "waiting because someone says were scrolling");
                  synchronized (adapter.scrollWait) {
                     adapter.scrollWait.wait();
                  }

                  // dont bother fetching if weve left screen
                  if (position < list.getFirstVisiblePosition() || position > list.getLastVisiblePosition()) {
                     // Log.w(TAG, "requested isnt on screen anymore");
                     return new Object[] {};
                  }

               }

               // fetch the album cover from itunes
               bitmap = RequestHelper.requestThumbnail(session, itemid);

               // if SOMEHOW (404, etc) this image was still null, then save as
               // blank
               if (bitmap == null)
                  bitmap = blank;

               // try removing any stale references
               memcache.remove(itemid);
               memcache.put(itemid, new SoftReference<Bitmap>(bitmap));

            }

         } catch (Exception e) {
            Log.d(TAG, String.format("onCreate Error: %s", e.getMessage()));
         }

         return new Object[] { position, bitmap };

      }

      @Override
      public void end(Object[] result) {

         if (result.length == 0)
            return;

         // update gui to show the newly-fetched albumart
         int position = ((Integer) result[0]).intValue();
         Bitmap bitmap = (Bitmap) result[1];

         // skip if bitmap wasnt found
         if (bitmap == null)
            return;

         // skip updating this item if outside of bounds
         if (position < list.getFirstVisiblePosition() || position > list.getLastVisiblePosition())
            return;

         // find actual position and update view
         int visible = position - list.getFirstVisiblePosition();
         View view = list.getChildAt(visible);
         ((ImageView) view.findViewById(android.R.id.icon)).setImageBitmap(bitmap);

      }
   }

}
