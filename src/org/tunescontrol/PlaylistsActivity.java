package org.tunescontrol;

import java.util.LinkedList;
import java.util.List;

import org.tunescontrol.daap.Library;
import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.Session.Playlist;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PlaylistsActivity extends BaseBrowseActivity {

   public final static String TAG = AlbumsActivity.class.toString();

   protected BackendService backend;
   protected Session session;
   protected Library library;
   protected ListView list;
   protected PlaylistsAdapter adapter;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();
         session = backend.getSession();

         if (session == null)
            return;

         adapter.results.clear();

         // begin search now that we have a backend
         library = new Library(session);
         library.readPlaylists(adapter);

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

      ((TextView) this.findViewById(android.R.id.empty)).setText(R.string.playlists_empty);
      this.list = this.getListView();
      this.adapter = new PlaylistsAdapter(this);
      this.setListAdapter(adapter);

      this.registerForContextMenu(this.getListView());

      this.getListView().setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            try {
               final Playlist ply = (Playlist) adapter.getItem(position);
               final String playlistid = Long.toString(ply.getID());

               Intent intent = new Intent(PlaylistsActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, "");
               intent.putExtra("Playlist", playlistid);
               intent.putExtra("PlaylistPersistentId", ply.getPersistentId());
               intent.putExtra("AllAlbums", false);
               PlaylistsActivity.this.startActivityForResult(intent, 1);

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
         final Playlist ply = (Playlist) adapter.getItem(info.position);
         menu.setHeaderTitle(ply.getName());
         final String playlistid = Long.toString(ply.getID());

         final MenuItem browse = menu.add(R.string.albums_menu_browse);
         browse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
               Intent intent = new Intent(PlaylistsActivity.this, TracksActivity.class);
               intent.putExtra(Intent.EXTRA_TITLE, "");
               intent.putExtra("Playlist", playlistid);
               intent.putExtra("PlaylistPersistentId", ply.getPersistentId());
               intent.putExtra("AllAlbums", false);
               PlaylistsActivity.this.startActivityForResult(intent, 1);

               return true;
            }

         });
      } catch (Exception e) {
         Log.w(TAG, "onCreateContextMenu:" + e.getMessage());
      }

   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

      // Handle switching to playlists
      Log.d(TAG, String.format(" PlaylistActivity onAcivityResult resultCode = %d", resultCode));
      if (resultCode == BaseBrowseActivity.RESULT_SWITCH_TO_PLAYLISTS)
         return;

      super.onActivityResult(requestCode, resultCode, intent);
   }

   public class PlaylistsAdapter extends BaseAdapter {

      protected Context context;
      protected LayoutInflater inflater;
      protected final List<Playlist> results = new LinkedList<Playlist>();

      public PlaylistsAdapter(Context context) {
         this.context = context;
         this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      }

      public int getCount() {
         return results.size();
      }

      public Object getItem(int position) {
         return results.get(position);
      }

      public long getItemId(int position) {
         return position;
      }

      public View getView(int position, View convertView, ViewGroup parent) {
         try {
            convertView = this.inflater.inflate(R.layout.item_playlist, parent, false);
            Playlist ply = (Playlist) this.getItem(position);
            String title = ply.getName();
            String caption = PlaylistsActivity.this.getResources().getString(R.string.playlists_playlist_caption,
                     ply.getCount());

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(title);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(caption);

         } catch (Exception e) {
            Log.w(TAG, "getView:" + e.getMessage());
         }

         return convertView;
      }

      public void foundPlaylist(Playlist ply) {
         results.add(ply);
      }

      public void searchDone() {
         resultsUpdated.removeMessages(-1);
         resultsUpdated.sendEmptyMessage(-1);
      }

      public boolean hasStableIds() {
         return true;
      }
   }
}
