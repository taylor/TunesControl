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

import org.tunescontrol.daap.Session;
import org.tunescontrol.daap.Status;
import org.tunescontrol.daap.UpdateHelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ViewSwitcher.ViewFactory;

public class ControlActivity extends Activity implements ViewFactory {

   public final static String TAG = ControlActivity.class.toString();

   protected BackendService backend;
   protected Session session;
   protected Status status;
   protected boolean dragging = false;
   protected String showingAlbumId = null;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         backend = ((BackendService.BackendBinder) service).getService();

         if (!agreed)
            return;

         Log.w(TAG, "onServiceConnected");

         session = backend.getSession();
         if (session == null) {
            // we couldnt connect with library, so launch picker
            ControlActivity.this.startActivityForResult(new Intent(ControlActivity.this, LibraryActivity.class), 1);

         } else {

            // obey request to automatically pause
            // if(shouldPause) {
            // session.controlPlayPause();
            // }

            // TODO: fix this horrid hack
            // for some reason we arent correctly disposing of the session
            // threads we create
            // so we purge any existing ones before creating a new one
            // session.purgeAllStatus();
            // status = session.createStatus(statusUpdate);
            status = session.singletonStatus(statusUpdate);
            status.updateHandler(statusUpdate);

            // push update through to make sure we get updated
            statusUpdate.sendEmptyMessage(Status.UPDATE_TRACK);

         }

      }

      public void onServiceDisconnected(ComponentName className) {

         // make sure we clean up our handler-specific status
         // if(status != null && session != null) {
         // session.deleteStatus(status);
         // }

         Log.w(TAG, "onServiceDisconnected");

         status.updateHandler(null);

         backend = null;
         session = null;
         status = null;

      }
   };

   protected Handler statusUpdate = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         // Log.d(TAG, String.format("statusUpdate type=%d", msg.what));

         // update gui based on severity
         switch (msg.what) {
         case Status.UPDATE_TRACK:
            trackName.setText(status.getTrackName());
            trackArtist.setText(status.getTrackArtist());
            trackAlbum.setText(status.getTrackAlbum());

            // fade new details up if requested
            if (fadeUpNew)
               fadeview.keepAwake();

         case Status.UPDATE_COVER:

            boolean forced = (msg.what == Status.UPDATE_COVER);
            boolean shouldUpdate = (status.albumId != showingAlbumId) && !status.coverEmpty;
            if (forced)
               shouldUpdate = true;

            // only update coverart if different than already shown
            Log.d(TAG, String.format("someone sending us art for albumid=%s, value=%s, what=%d", status.albumId, status.coverCache, msg.what));
            if (shouldUpdate) {
               if (status.coverEmpty) {
                  // fade down if no coverart
                  cover.setImageDrawable(new ColorDrawable(Color.BLACK));
               } else if (status.coverCache != null) {
                  // fade over to new coverart
                  cover.setImageDrawable(new BitmapDrawable(status.coverCache));
               }
               showingAlbumId = status.albumId;
            }

         case Status.UPDATE_STATE:
            controlPause.setImageResource((status.getPlayStatus() == Status.STATE_PLAYING) ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            seekBar.setMax(status.getProgressTotal());

            // show notification when playing/hide when paused
            // switch(status.getPlayStatus()) {
            // case Status.STATE_PLAYING:
            //					
            // Intent intent = new Intent(ControlActivity.this,
            // ControlActivity.class);
            // intent.putExtra(Intent.EXTRA_KEY_EVENT, PAUSE);
            // PendingIntent pending =
            // PendingIntent.getActivity(ControlActivity.this, -1, intent,
            // PendingIntent.FLAG_CANCEL_CURRENT);
            //					
            // Resources res = ControlActivity.this.getResources();
            // String title = res.getString(R.string.notif_title);
            // String caption = res.getString(R.string.notif_caption,
            // status.getTrackName(), status.getTrackArtist());
            //					
            // Notification notif = new
            // Notification(R.drawable.stat_notify_musicplayer, null,
            // System.currentTimeMillis());
            // notif.flags = Notification.FLAG_ONGOING_EVENT;
            // notif.setLatestEventInfo(ControlActivity.this, title, caption,
            // pending);
            //					
            // notifman.notify(NOTIF_PLAYING, notif);
            // break;
            // case Status.STATE_PAUSED:
            // notifman.cancel(NOTIF_PLAYING);
            // break;
            // }

            // TODO: update menu items for shuffle, etc?

         case Status.UPDATE_PROGRESS:
            if (ignoreNextTick) {
               ignoreNextTick = false;
               return;
            }
            seekPosition.setText(formatTime(status.getProgress()));
            seekRemain.setText("-" + formatTime(status.getRemaining()));
            if (!dragging)
               seekBar.setProgress(status.getProgress());

         }

      }
   };

   protected Handler doubleTapHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         if (status == null)
            return;

         // launch tracks view for current album
         Intent intent = new Intent(ControlActivity.this, NowPlayingActivity.class);
         intent.putExtra(Intent.EXTRA_TITLE, "");
         ControlActivity.this.startActivity(intent);

      }
   };

   
   protected TextView trackName, trackArtist, trackAlbum, seekPosition, seekRemain;
   protected SeekBar seekBar;

   protected ImageSwitcher cover;
   protected ImageButton controlPrev, controlPause, controlNext;

   protected SimpleDateFormat format = new SimpleDateFormat("m:ss");
   protected Date date = new Date(0);

   protected synchronized String formatTime(int seconds) {
      date.setTime(seconds * 1000);
      return format.format(date);
   }

   public View makeView() {
      ImageView view = new ImageView(this);
      view.setScaleType(ImageView.ScaleType.CENTER_CROP);
      view.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
      return view;
   }

   protected View volume;
   protected ProgressBar volumeBar;
   protected Toast volumeToast;

   protected FadeView fadeview;

   protected boolean stayConnected = false, fadeDetails = true, fadeUpNew = true, vibrate = true;

   @Override
   public void onStart() {
      super.onStart();

      this.stayConnected = this.prefs.getBoolean(this.getString(R.string.pref_background), this.stayConnected);
      this.fadeDetails = this.prefs.getBoolean(this.getString(R.string.pref_fade), this.fadeDetails);
      this.fadeUpNew = this.prefs.getBoolean(this.getString(R.string.pref_fadeupnew), this.fadeUpNew);
      this.vibrate = this.prefs.getBoolean(this.getString(R.string.pref_vibrate), this.vibrate);

      this.fadeview.allowFade = this.fadeDetails;
      this.fadeview.keepAwake();

      Intent service = new Intent(this, BackendService.class);

      if (this.stayConnected) {
         // if were running background service, start now
         this.startService(service);

      } else {
         // otherwise make sure we kill the static background service
         this.stopService(service);

      }

      // regardless of stayConnected, were always going to need a bound backend
      // for this activity
      this.bindService(service, connection, Context.BIND_AUTO_CREATE);

   }

   @Override
   public void onStop() {
      super.onStop();
      // if(session != null)
      // session.purgeAllStatus();
      if (!this.stayConnected && session != null)
         session.purgeAllStatus();
      this.unbindService(connection);

   }

   protected Vibrator vibrator;
   protected SharedPreferences prefs;
   protected boolean agreed = false;

   public final static String EULA = "eula";

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {

      if (resultCode == Activity.RESULT_OK) {
         // yay they agreed, so store that info
         Editor edit = prefs.edit();
         edit.putBoolean(EULA, true);
         edit.commit();
         this.agreed = true;
      } else {
         // user didnt agree, so close
         this.finish();
      }

   }

   public final static int VIBRATE_LEN = 150;
   protected boolean ignoreNextTick = false;

   // public boolean shouldPause = false;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      // before we go any further, make sure theyve agreed to EULA
      this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
      this.agreed = prefs.getBoolean(EULA, false);
      if (!this.agreed) {
         // show eula wizard
         this.startActivityForResult(new Intent(this, WizardActivity.class), 1);

      }

      // check for updates
      new UpdateHelper(this);

      setContentView(R.layout.act_control);
      
      
      // this.notifman =
      // (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
      // this.shouldPause =
      // PAUSE.equals(this.getIntent().getStringExtra(Intent.EXTRA_KEY_EVENT));

      this.vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

      // prepare volume toast view
      LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      
      
      this.volume = inflater.inflate(R.layout.toa_volume, null, false);

      this.volumeBar = (ProgressBar) this.volume.findViewById(R.id.volume);

      this.volumeToast = new Toast(this);
      this.volumeToast.setDuration(Toast.LENGTH_SHORT);
      this.volumeToast.setGravity(Gravity.CENTER, 0, 0);
      this.volumeToast.setView(this.volume);

      // pull out interesting controls
      this.trackName = (TextView) findViewById(R.id.info_title);
      this.trackArtist = (TextView) findViewById(R.id.info_artist);
      this.trackAlbum = (TextView) findViewById(R.id.info_album);

      this.cover = (ImageSwitcher) findViewById(R.id.cover);
      this.cover.setFactory(this);
      this.cover.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
      this.cover.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));

      this.seekBar = (SeekBar) findViewById(R.id.seek);
      this.seekPosition = (TextView) findViewById(R.id.seek_position);
      this.seekRemain = (TextView) findViewById(R.id.seek_remain);

      this.controlPrev = (ImageButton) findViewById(R.id.control_prev);
      this.controlPause = (ImageButton) findViewById(R.id.control_pause);
      this.controlNext = (ImageButton) findViewById(R.id.control_next);

      this.seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
         public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
         }

         public void onStartTrackingTouch(SeekBar seekBar) {
            dragging = true;
         }

         public void onStopTrackingTouch(SeekBar seekBar) {
            // scan to location in song
            dragging = false;
            session.controlProgress(seekBar.getProgress());
            ignoreNextTick = true;
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.controlPrev.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            session.controlPrev();
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.controlNext.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            session.controlNext();
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.controlPause.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            session.controlPlayPause();
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.fadeview = (FadeView) findViewById(R.id.fadeview);
      this.fadeview.startFade();

      this.fadeview.doubleTapHandler = this.doubleTapHandler;

      // cover.setImageDrawable(this.getResources().getDrawable(R.drawable.folder));

   }

   protected long cachedTime = -1;
   protected long cachedVolume = -1;

   // keep volume cache for 10 seconds
   public final static long CACHE_TIME = 10000;

   protected void incrementVolume(long increment) {

      // try assuming a cached volume instead of requesting it each time
      if (System.currentTimeMillis() - cachedTime > CACHE_TIME) {
         this.cachedVolume = this.status.getVolume();
         this.cachedTime = System.currentTimeMillis();
      }

      // increment the volume and send control signal off
      this.cachedVolume += increment;
      this.session.controlVolume(this.cachedVolume);

      // update our volume gui and show
      this.volumeBar.setProgress((int) this.cachedVolume);
      this.volume.invalidate();
      this.volumeToast.show();

   }

   @Override
   public boolean onKeyDown(int keyCode, KeyEvent event) {
      // check for volume keys
      if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
         this.incrementVolume(+5);
         return true;
      } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
         this.incrementVolume(-5);
         return true;
      }

      // regardless of key, make sure we keep view alive
      this.fadeview.keepAwake();

      return super.onKeyDown(keyCode, event);
   }
   
   // get rid of volume rocker default sound effect
   @Override
   public boolean onKeyUp(int keycode, KeyEvent event) {
       switch (keycode) {
           case KeyEvent.KEYCODE_VOLUME_DOWN:
           case KeyEvent.KEYCODE_VOLUME_UP:
               break;
           default:
               return super.onKeyUp(keycode, event);
       }
       return true;
   }

   protected MenuItem repeat, shuffle;

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);

      MenuItem search = menu.add(R.string.control_menu_search);
      search.setIcon(android.R.drawable.ic_menu_search);
      search.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            ControlActivity.this.onSearchRequested();
            return true;
         }
      });

      MenuItem artists = menu.add(R.string.control_menu_artists);
      artists.setIcon(R.drawable.ic_search_category_music_artist);
      Intent artistIntent = new Intent(ControlActivity.this, BrowseActivity.class);
      artistIntent.putExtra("windowType",BaseBrowseActivity.RESULT_SWITCH_TO_ARTISTS);
      artists.setIntent(artistIntent);

      MenuItem playlists = menu.add("Playlists");
      playlists.setIcon(R.drawable.ic_search_category_music_song);
      Intent playlistIntent = new Intent(ControlActivity.this, BrowseActivity.class);
      playlistIntent.putExtra("windowType",BaseBrowseActivity.RESULT_SWITCH_TO_PLAYLISTS);
      playlists.setIntent(playlistIntent);

      this.repeat = menu.add("");
      this.repeat.setIcon(android.R.drawable.ic_menu_rotate);
      this.repeat.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {

            if (session == null || status == null)
               return true;

            // correctly rotate through states
            switch (status.getRepeat()) {
            case Status.REPEAT_ALL:
               session.controlRepeat(Status.REPEAT_OFF);
               break;
            case Status.REPEAT_OFF:
               session.controlRepeat(Status.REPEAT_SINGLE);
               break;
            case Status.REPEAT_SINGLE:
               session.controlRepeat(Status.REPEAT_ALL);
               break;
            }

            return true;
         }
      });

      this.shuffle = menu.add("");
      this.shuffle.setIcon(R.drawable.ic_menu_shuffle);
      this.shuffle.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {

            if (session == null || status == null)
               return true;

            // correctly rotate through states
            switch (status.getShuffle()) {
            case Status.SHUFFLE_OFF:
               session.controlShuffle(Status.SHUFFLE_ON);
               break;
            case Status.SHUFFLE_ON:
               session.controlShuffle(Status.SHUFFLE_OFF);
               break;
            }

            return true;
         }
      });

      MenuItem pick = menu.add(R.string.control_menu_pick);
      pick.setIcon(android.R.drawable.ic_menu_share);
      pick.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            // launch off library picking
            ControlActivity.this.startActivity(new Intent(ControlActivity.this, LibraryActivity.class));
            return true;
         }
      });

      MenuItem settings = menu.add("Settings");
      settings.setIcon(android.R.drawable.ic_menu_preferences);
      settings.setIntent(new Intent(ControlActivity.this, PrefsActivity.class));

      MenuItem about = menu.add("About");
      settings.setIcon(android.R.drawable.ic_menu_help);
      about.setIntent(new Intent(ControlActivity.this, WizardActivity.class));

      return true;
   }

   @Override
   public boolean onPrepareOptionsMenu(Menu menu) {
      super.onPrepareOptionsMenu(menu);

      if (session == null || status == null)
         return true;

      switch (status.getRepeat()) {
      case Status.REPEAT_ALL:
         this.repeat.setTitle(R.string.control_menu_repeat_none);
         break;
      case Status.REPEAT_OFF:
         this.repeat.setTitle(R.string.control_menu_repeat_one);
         break;
      case Status.REPEAT_SINGLE:
         this.repeat.setTitle(R.string.control_menu_repeat_all);
         break;
      }

      switch (status.getShuffle()) {
      case Status.SHUFFLE_OFF:
         this.shuffle.setTitle(R.string.control_menu_shuffle_on);
         break;
      case Status.SHUFFLE_ON:
         this.shuffle.setTitle(R.string.control_menu_shuffle_off);
         break;
      }

      return true;
   }

}