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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.Response;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.ResponseParser.TagListener;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class TracksActivity extends ListActivity {

   public final static String TAG = TracksActivity.class.toString();
   protected BackendService backend;
   protected Session session;
   protected Library library;
   protected TracksAdapter adapter;
   protected String albumid;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();
         session = backend.getSession();

         if (session == null)
            return;

         adapter.results.clear();

         // begin search now that we have a backend
         library = new Library(session);
         library.readTracks(albumid, adapter);
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

      ((TextView) this.findViewById(android.R.id.empty)).setText(R.string.tracks_empty);

      // show tracklist for specified album
      // set out list adapter to albums found

      this.albumid = this.getIntent().getStringExtra(Intent.EXTRA_TITLE);
      // this.albumid = "11588692627249261480";

      this.getListView().setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            // assuming track order, begin playing this track
            if (session != null)
               session.controlPlayAlbum(albumid, position);

         }
      });

      this.adapter = new TracksAdapter(this);
      this.setListAdapter(adapter);

   }

   public class TracksAdapter extends BaseAdapter implements TagListener {

      protected Context context;
      protected LayoutInflater inflater;

      protected List<Response> results = new LinkedList<Response>();

      public TracksAdapter(Context context) {
         this.context = context;
         this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      }

      public void foundTag(String tag, Response resp) {
         // add a found search result to our list
         if (resp.containsKey("minm"))
            results.add(resp);
         this.searchDone();
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

      protected SimpleDateFormat format = new SimpleDateFormat("m:ss");
      protected Date date = new Date(0);

      public View getView(int position, View convertView, ViewGroup parent) {

         if (convertView == null)
            convertView = inflater.inflate(R.layout.item_track, parent, false);

         try {

            // otherwise show normal search result
            Response resp = (Response) this.getItem(position);

            String title = resp.getString("minm");
            date.setTime(resp.getNumberLong("astm"));
            String length = format.format(date);

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(title);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(length);

         } catch (Exception e) {
            Log.d(TAG, String.format("onCreate Error: %s", e.getMessage()));
         }

         /*
                mlit  --+
                        mikd   1      02 == 2
                        asal   12     Dance or Die
                        asar   14     Family Force 5
                        astm   4      0003d5d6 == 251350
                        astn   2      0001
                        miid   4      0000005b == 91
                        minm   12     dance or die

          */

         return convertView;

      }

   }

}
