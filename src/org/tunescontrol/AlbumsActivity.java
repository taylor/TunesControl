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
import org.tunescontrol.util.ThreadExecutor;
import org.tunescontrol.util.UserTask;

import android.app.ListActivity;
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
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class AlbumsActivity extends ListActivity {

   public final static String TAG = AlbumsActivity.class.toString();

   protected BackendService backend;
   protected Session session;
   protected Library library;
   protected ListView list;
   protected AlbumsAdapter adapter;
   protected String artist;
   protected Bitmap blank;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();
         session = backend.getSession();

         if (session == null)
            return;

         adapter.results.clear();

         // begin search now that we have a backend
         library = new Library(session);
         ThreadExecutor.runTask(new Runnable() {
            public void run() {
               library.readAlbums(adapter, artist);
            }
         });

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

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.gen_list);

      ((TextView) this.findViewById(android.R.id.empty)).setText(R.string.albums_empty);

      this.artist = this.getIntent().getStringExtra(Intent.EXTRA_TITLE);
      this.list = this.getListView();
      this.adapter = new AlbumsAdapter(this);
      this.setListAdapter(adapter);

      this.registerForContextMenu(this.getListView());

      this.getListView().setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            try {
               // launch activity to browse track details for this albums
               final Response resp = (Response) adapter.getItem(position);
               final String albumid = resp.getNumberString("mper");

               Intent intent = new Intent(AlbumsActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, albumid);
               AlbumsActivity.this.startActivity(intent);
            } catch (Exception e) {
               Log.w(TAG, "onCreate:" + e.getMessage());
            }

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
         final String albumid = resp.getNumberString("mper");

         final MenuItem play = menu.add(R.string.albums_menu_play);
         play.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
               session.controlPlayAlbum(albumid, 0);
               return true;
            }
         });

         final MenuItem browse = menu.add(R.string.albums_menu_browse);
         browse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
               Intent intent = new Intent(AlbumsActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, albumid);
               AlbumsActivity.this.startActivity(intent);

               return true;
            }
         });
      } catch (Exception e) {
         Log.w(TAG, "onCreateContextMenu:" + e.getMessage());
      }

   }

   public class AlbumsAdapter extends BaseAdapter implements TagListener {

      protected Context context;
      protected LayoutInflater inflater;
      protected final List<Response> results = new LinkedList<Response>();

      public AlbumsAdapter(Context context) {
         this.context = context;
         this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      }

      public void foundTag(String tag, Response resp) {
         // add a found search result to our list
         if (resp.containsKey("minm"))
            results.add(resp);
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

         if (convertView == null)
            convertView = this.inflater.inflate(R.layout.item_album, parent, false);

         try {
            Response child = (Response) this.getItem(position);
            String title = child.getString("minm");
            String caption = AlbumsActivity.this.getResources().getString(R.string.albums_album_caption, child.getNumberLong("mimc"));

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(title);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(caption);

            // go load image art
            ((ImageView) convertView.findViewById(android.R.id.icon)).setImageBitmap(blank);
            new LoadPhotoTask().execute(Integer.valueOf(position), Integer.valueOf((int)child.getNumberLong("miid")));
         } catch (Exception e) {
            Log.w(TAG, "getView:" + e.getMessage());
         }

         return convertView;

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

            if (bitmap != null) {
               Log.d(TAG, String.format("MEMORY cache hit for %s", itemid.toString()));
            } else {

               // fetch the album cover from itunes
               byte[] raw = RequestHelper.request(String.format("%s/databases/%d/groups/%d/extra_data/artwork?session-id=%s&mw=55&mh=55&group-type=albums",
                     session.getRequestBase(), session.databaseId, itemid, session.sessionId), false);
               bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
              
               // if SOMEHOW (404, etc) this image was still null, then save as blank
               if (bitmap == null)
                  bitmap = blank;

               // try removing any stale references
               memcache.remove(itemid);
               memcache.put(itemid, new SoftReference<Bitmap>(bitmap));
            }
         } catch (Exception e) {
            Log.w(TAG, "LoadPhotoTask:" + e.getMessage());
         }

         return new Object[] { position, bitmap };
      }

      @Override
      public void end(Object[] result) {

         // update gui to show the newly-fetched albumart
         int position = ((Integer) result[0]).intValue();
         Bitmap bitmap = (Bitmap) result[1];

         // skip if bitmap wasnt found
         if (bitmap == null)
            return;

         try {
            // skip updating this item if outside of bounds
            if (position < list.getFirstVisiblePosition() || position > list.getLastVisiblePosition())
               return;

            // find actual position and update view
            int visible = position - list.getFirstVisiblePosition();
            View view = list.getChildAt(visible);
            ((ImageView) view.findViewById(android.R.id.icon)).setImageBitmap(bitmap);

         } catch (Exception e) {
            // we probably ran into an item thats now collapsed, just ignore
            Log.d(TAG, "end:" + e.getMessage());
         }

      }
   }

}
