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

package org.tunescontrol.daap;

import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;

import org.tunescontrol.util.ThreadExecutor;

import android.os.Handler;
import android.util.Log;

public class Session {

   public final static String TAG = Session.class.toString();

   protected String host;
   public String sessionId;
   public long databaseId, musicId;
   public String databasePersistentId;
   protected Status singleton = null;
   protected List<Status> listeners = new LinkedList<Status>();
   public List<Playlist> playlists = new LinkedList<Playlist>();

   public Session(String host, String pairingGuid) throws Exception {
      // start a session with the itunes server

      this.host = host;

      // http://192.168.254.128:3689/login?pairing-guid=0x0000000000000001
      Log.d(TAG, String.format("tryign login for host=%s and guid=%s", host, pairingGuid));
      Response login = RequestHelper.requestParsed(String.format("%s/login?pairing-guid=0x%s", this.getRequestBase(), pairingGuid), false);
      this.sessionId = login.getNested("mlog").getNumberString("mlid");
      Log.d(TAG, String.format("found session-id=%s", this.sessionId));

      // http://192.168.254.128:3689/databases?session-id=1301749047
      Response databases = RequestHelper.requestParsed(String.format("%s/databases?session-id=%s", this.getRequestBase(), this.sessionId), false);
      this.databaseId = databases.getNested("avdb").getNested("mlcl").getNested("mlit").getNumberLong("miid");
      this.databasePersistentId = databases.getNested("avdb").getNested("mlcl").getNested("mlit").getNumberHex("mper");
      Log.d(TAG, String.format("found database-id=%s", this.databaseId));

      // fetch playlists to find the overall magic "Music" playlist
      Response playlists = RequestHelper.requestParsed(String.format(
               "%s/databases/%d/containers?session-id=%s&meta=dmap.itemname,dmap.itemcount,dmap.itemid,dmap.persistentid,daap.baseplaylist,com.apple.itunes.special-playlist,com.apple.itunes.smart-playlist,com.apple.itunes.saved-genius,dmap.parentcontainerid,dmap.editcommandssupported", this
                        .getRequestBase(), this.databaseId, this.sessionId), false);

      for (Response resp : playlists.getNested("aply").getNested("mlcl").findArray("mlit")) {
         String name = resp.getString("minm");
         if (name.equals("Music")) {
            this.musicId = resp.getNumberLong("miid");
         }
         else
         {
        	 // get a list of playlists, filter out some non-music iTunes playlists
        	 if (name.equals("Films") || name.equals("TV Programmes") || name.equals("iTunes U") || (resp.getNumberLong("abpl") == 1))
        		 //Ignore
        	 {}
        	 else
        	 {
        		 Log.d(TAG, String.format("found playlist=%s", name));
        		 this.playlists.add(new Playlist(resp.getNumberLong("miid"), name, resp.getNumberLong("mimc"), resp.getNumberHex("mper")));
        	 }
         }
      }
      Log.d(TAG, String.format("found music-id=%s", this.musicId));

      /*
       *  aply  --+
        mstt   4      000000c8 == 200
        muty   1      00 == 0
        mtco   4      0000000c == 12
        mrco   4      0000000c == 12
        mlcl  --+
                mlit  --+
                        miid   4      0000360b == 13835
                        mper   8      a0d34e8b826151fd == 11588692627249254909
                        minm   16     75736572e2809973204c696272617279
                        abpl   1      01 == 1
                        mpco   4      00000000 == 0
                        meds   4      00000000 == 0
                        mimc   4      000017f5 == 6133
                mlit  --+
                        miid   4      0000692c == 26924
                        mper   8      a0d34e8b82615207 == 11588692627249254919
                        minm   5      Music
                        aeSP   1      01 == 1
                        mpco   4      00000000 == 0
                        aePS   1      06 == 6
                        meds   4      00000000 == 0
                        mimc   4      000017d8 == 6104

       */
   }

   private Status createStatus(Handler handler) {
      final Status stat = new Status(this, handler);
      this.registerStatus(stat);
      stat.fetchUpdate();
      return stat;

   }

   public synchronized Status singletonStatus(Handler handler) {
      if (singleton == null || singleton.destroyThread)
         singleton = this.createStatus(handler);
      return singleton;
   }

   public String getRequestBase() {
      return String.format("http://%s:3689", host);
   }

   public void registerStatus(Status status) {
      this.listeners.add(status);
   }

   public void purgeAllStatus() {
      for (Status status : listeners)
         status.destroy();
      listeners.clear();
      this.singleton = null;
   }

   protected void notifyStatus() {
      for (Status status : listeners)
         status.fetchUpdate();
   }

   // some control helper functions
   // these should also invalidate any status listeners

   protected void fireAction(final String url, final boolean notify) {
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(url);
            if (notify)
               notifyStatus();
         }
      });
   }

   public void controlPlayPause() {
      // http://192.168.254.128:3689/ctrl-int/1/playpause?session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/playpause?session-id=%s", this.getRequestBase(), this.sessionId), true);
   }

   public void controlNext() {
      // http://192.168.254.128:3689/ctrl-int/1/nextitem?session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/nextitem?session-id=%s", this.getRequestBase(), this.sessionId), true);
   }

   public void controlPrev() {
      // http://192.168.254.128:3689/ctrl-int/1/previtem?session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/previtem?session-id=%s", this.getRequestBase(), this.sessionId), true);
   }

   public void controlVolume(long volume) {
      // http://192.168.254.128:3689/ctrl-int/1/setproperty?dmcp.volume=100.000000&session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dmcp.volume=%s&session-id=%s", this.getRequestBase(), volume, this.sessionId), false);
   }

   public void controlProgress(int progressSeconds) {
      // http://192.168.254.128:3689/ctrl-int/1/setproperty?dacp.playingtime=82784&session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dacp.playingtime=%d&session-id=%s", this.getRequestBase(), progressSeconds * 1000, this.sessionId), true);
   }

   public void controlShuffle(int shuffleMode) {
      // /ctrl-int/1/setproperty?dacp.shufflestate=1&session-id=1873217009
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dacp.shufflestate=%d&session-id=%s", this.getRequestBase(), shuffleMode, this.sessionId), false);
      for (Status status : listeners)
         status.shuffleStatus = shuffleMode;
   }

   public void controlRepeat(int repeatMode) {
      // /ctrl-int/1/setproperty?dacp.repeatstate=2&session-id=1873217009
      // HTTP/1.1
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dacp.repeatstate=%d&session-id=%s", this.getRequestBase(), repeatMode, this.sessionId), false);
      for (Status status : listeners)
         status.repeatStatus = repeatMode;
   }

   public void controlRating(long rating) {
      // where rating 0-100
      // /ctrl-int/1/setproperty?dacp.userrating=100&database-spec='dmap.persistentid:16090061681534800669'&playlist-spec='dmap.persistentid:16090061681534800670'&song-spec='dmap.itemid:0x57'&session-id=1873217009
   }

   public void controlPlayAlbum(final String albumid, final int tracknum) {

      // http://192.168.254.128:3689/ctrl-int/1/cue?command=clear&session-id=130883770
      // http://192.168.254.128:3689/ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:32')+'daap.songartist:Family%20Force%205')&index=0&sort=album&session-id=130883770
      // /ctrl-int/1/cue?command=play&query='daap.songalbumid:16621530181618739404'&index=11&sort=album&session-id=514488449

      // GET
      // /ctrl-int/1/playspec?database-spec='dmap.persistentid:16621530181618731553'&playlist-spec='dmap.persistentid:9378496334192532210'&dacp.shufflestate=1&session-id=514488449
      // (zero based index into playlist)

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query='daap.songalbumid:%s'&index=%d&sort=album&session-id=%s", getRequestBase(), albumid, tracknum, sessionId));

            notifyStatus();
         }
      });

   }
   
   public void controlQueueAlbum(final String albumid) {


	      ThreadExecutor.runTask(new Runnable() {
	         public void run() {
	            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=add&query='daap.songalbumid:%s'&session-id=%s", getRequestBase(), albumid, sessionId));

	            notifyStatus();
	         }
	      });

	   }

   public void controlPlayArtist(String artist,int index) {

      // http://192.168.254.128:3689/ctrl-int/1/cue?command=clear&session-id=130883770
      // /ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:32')+'daap.songartist:Family%20Force%205')&index=0&sort=album&session-id=130883770
      // /ctrl-int/1/cue?command=play&query='daap.songartist:%s'&index=0&sort=album&session-id=%s

      final String encodedArtist = URLEncoder.encode(artist).replaceAll("\\+", "%20");
      final int encodedIndex = index;
      
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query='daap.songartist:%s'&index=%d&sort=album&session-id=%s", getRequestBase(), encodedArtist, encodedIndex, sessionId));

            notifyStatus();
         }
      });

   }
   
   public void controlQueueArtist(String artist) {

	      final String encodedArtist = URLEncoder.encode(artist).replaceAll("\\+", "%20");

	      ThreadExecutor.runTask(new Runnable() {
	         public void run() {
	            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=add&query='daap.songartist:%s'&session-id=%s", getRequestBase(), encodedArtist, sessionId));
	            notifyStatus();
	         }
	      });

	   }
   
   public void controlQueueTrack(final String track) {

	      ThreadExecutor.runTask(new Runnable() {
	         public void run() {
	            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=add&query='dmap.itemid:%s'&session-id=%s", getRequestBase(), track, sessionId));
	            notifyStatus();
	         }
	      });

	   }

   public void controlPlaySearch(final String search, final int index) {
      // /ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:4','com.apple.itunes.mediakind:8')+'dmap.itemname:*F*')&index=4&sort=name&session-id=1550976127
      // /ctrl-int/1/cue?command=play&query='dmap.itemname:*%s*'&index=%d&sort=name&session-id=%s

      final String encodedSearch = URLEncoder.encode(search).replaceAll("\\+", "%20");

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query=('dmap.itemname:*%s*','daap.songartist:*%s*','daap.songalbum:*%s*')&type=music&sort=artist&index=%d&session-id=%s", getRequestBase(),
                     encodedSearch, encodedSearch, encodedSearch, index, sessionId));
            notifyStatus();
         }
      });
   }
   
   public void controlPlayPlaylist(final String playlistPersistentId, final String containerItemId) {
	   // /ctrl-int/1/playspec?database-spec='dmap.persistentid:0x9031099074C14E05'&container-spec='dmap.persistentid:0xA1E1854E0B9A1B'&container-item-spec='dmap.containeritemid:0x1b47'&session-id=7491138
	   
	  final String databasePersistentId = this.databasePersistentId;

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format(
                     "%s/ctrl-int/1/playspec?database-spec='dmap.persistentid:0x%s'&container-spec='dmap.persistentid:0x%s'&container-item-spec='dmap.containeritemid:%s'&session-id=%s", getRequestBase(),
                     databasePersistentId, playlistPersistentId, containerItemId, sessionId));
            notifyStatus();
         }
      });
   
   }
   

   public void controlPlayIndex(final String albumid, final int tracknum) {
	   //Attempt to play from current now playing list, otherwise try to play album
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            
            try {
            	RequestHelper.request(String.format("%s/ctrl-int/1/cue?command=play&index=%d&sort=album&session-id=%s", getRequestBase(), tracknum, sessionId), false);
            	//on iTunes this generates a 501 Not Implemented response
        	} catch (Exception e) {
        		if (albumid != "") //Fall back to choosing from the current album if there is one
        		{
                    RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
        			RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query='daap.songalbumid:%s'&index=%d&sort=album&session-id=%s", getRequestBase(), albumid, tracknum, sessionId));
        		}
    		}
            notifyStatus();
         }
      });

   }
//
//   public void controlQueuePlaylist(final String playlistPersistentId) {
//	   
//   
//   }
   
   public class Playlist {
	   protected long ID;
	   protected String name, persistentId;
	   protected long count;
	   
	   public Playlist(long ID, String name, long count, String persistentId)
	   {
		   this.ID = ID;
		   this.name = name;
		   this.count = count;
		   this.persistentId = persistentId;
	   }
	   
	   public long getID()
	   {
		   return this.ID;
	   }
	   
	   public String getName()
	   {
		   return this.name;
	   }
	   
	   public long getCount()
	   {
		   return this.count;
	   }

	   public String getPersistentId()
	   {
		   return this.persistentId;
	   }
   }

}
