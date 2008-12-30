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

package org.tunescontrol.daap;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

public class Status {
	
	// handles status information, including background timer thread
	// also subscribes to keep-alive event updates

	public final static String TAG = Status.class.toString();
	
	public final static int REPEAT_OFF = 0,
		REPEAT_SINGLE = 1,
		REPEAT_ALL = 2,
		SHUFFLE_OFF = 0,
		SHUFFLE_ON = 1,
		STATE_PAUSED = 3,
		STATE_PLAYING = 4;


	protected int repeatStatus = REPEAT_OFF,
		shuffleStatus = SHUFFLE_OFF,
		playStatus = STATE_PAUSED;

	protected String trackName = "",
		trackArtist = "",
		trackAlbum = "";
	
	protected long progressTotal = 0,
		progressRemain = 0;
	
	public String albumId = "";
	
	protected final Session session;
	protected Handler update = null;
	
	protected int failures = 0;
	public final static int MAX_FAILURES = 20; 
	
	public void updateHandler(Handler handler) {
		this.update = handler;
	}
	
	public final static int UPDATE_PROGRESS = 2,
		UPDATE_STATE = 3,
		UPDATE_TRACK = 4,
		UPDATE_COVER = 5;
	
	protected boolean destroyThread = false;
	
	protected final Thread progress = new Thread(new Runnable() {
		public void run() {
			while(true) {
				// when in playing state, keep moving progress forward
				while(playStatus == STATE_PLAYING) {
					Log.d(TAG, "thread entering playing loop");
					if(destroyThread) return;
					long anchor = System.currentTimeMillis();
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// someone jolted us awake during playing, which means track changed
						Log.d(TAG, "someone jolted us during STATE_PLAYING loop");
						continue;
					}
					
					if(destroyThread) return;
					
					// update progress and gui
					progressRemain -= (System.currentTimeMillis() - anchor);
					if(update != null)
						update.sendEmptyMessage(UPDATE_PROGRESS);
					
					// trigger a forced update if we seem to gone past end of song
					if(progressRemain <= 0) {
						Log.d(TAG, "suggesting that we fetch new song");
						fetchUpdate();
					}
				}
				
				// keep sleeping while in paused state
				while(playStatus == STATE_PAUSED) {
					Log.d(TAG, "thread entering paused loop");
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// someone probably jolted us awake with a status update
						Log.d(TAG, "someone jolted us during STATE_PAUSED loop");
					}

					if(destroyThread) return;
				}

				// one final sleep to make sure we behave nicely in case of unknown status
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Log.d(TAG, "someone jolted us during OVERALL loop");
				}
				
				if(destroyThread) return;

			}
			
		}
	});
	
	protected final Thread keepalive = new Thread(new Runnable() {
		public void run() {
			while(true) {
				try {
					// sleep a few seconds to make sure we dont kill stuff
					Thread.sleep(1000);
					if(destroyThread) return;
					
					// try fetching next revision update using socket keepalive approach
					// using the next revision-number will make itunes keepalive until something happens
					// http://192.168.254.128:3689/ctrl-int/1/playstatusupdate?revision-number=1&session-id=1034286700
					parseUpdate(RequestHelper.requestParsed(String.format("%s/ctrl-int/1/playstatusupdate?revision-number=%d&session-id=%s",
							session.getRequestBase(), revision, session.sessionId), true));
				} catch (Exception e) {
					// TODO: check for normal timeout here instead of destroying all the time
					//Log.e(TAG, "something bad happened in keepalive thread, so killing");
					e.printStackTrace();
					if(failures++ > MAX_FAILURES)
						destroy();
					//destroy();
				}

			}
		}
	});
	
	public void destroy() {
		// destroy our internal thread
		Log.d(TAG, "trying to destroy internal status thread");
		if(this.destroyThread) return;
		this.destroyThread = true;
		this.progress.interrupt();
		this.progress.stop();
		this.keepalive.interrupt();
		this.keepalive.stop();
	}
	
	public Status(Session session, Handler update) {
		this.session = session;
		this.update = update;
		
		// create two threads, one for backend keep-alive updates
		// and a second one to update running time and fire gui events
		
		this.progress.start();
		this.keepalive.start();
		
		// keep our status updated with server however we need to
		// end thread when getting any 404 responses, etc
		
		// http://192.168.254.128:3689/ctrl-int/1/playstatusupdate?revision-number=1&session-id=1940361390
		// call handleUpdate() with any responses

		
	}
	
	protected long revision = 1;
	protected int errors = 0;
	
	public void fetchUpdate() {
		// force a status update, will pass along to parseUpdate()
		new Thread(new Runnable() {
			public void run() {
				try {
					// using revision-number=1 will make sure we return instantly
					// http://192.168.254.128:3689/ctrl-int/1/playstatusupdate?revision-number=1&session-id=1034286700
					parseUpdate(RequestHelper.requestParsed(String.format("%s/ctrl-int/1/playstatusupdate?revision-number=%d&session-id=%s",
							session.getRequestBase(), 1, session.sessionId), false));
				} catch (Exception e) {
					e.printStackTrace();
					//destroy();
					if(failures++ > MAX_FAILURES)
						destroy();
				}
			}
		}).start();
		
	}
	
	
	
	protected void parseUpdate(Response resp) throws Exception {
		/*
		 *  cmst  --+
			mstt   4      000000c8 == 200
			cmsr   4      00000079 == 121	[revisionnum, version control]
			caps   1      04 == 4		[3=paused, 4=playing]
			cash   1      01 == 1		[1=shuffle]
			carp   1      00 == 0		[repeat, 2=all, 1=only, 0=off]
			cavc   1      01 == 1
			caas   4      00000002 == 2
			caar   4      00000006 == 6
			canp   16     00000026000000ea0000010300000065
			cann   38     The Night of Your Life is When You Die
			cana   14     Capital Lights
			canl   19     This is an Outrage!
			cang   14     Christian Rock
			asai   8      df4b61d9be01973b	[album id]
			cmmk   4      00000001 == 1
			cant   4      00014813 == 83987
			cast   4      0002eb58 == 191320
		 */
		
	
		// keep track of the worst update that could happen
		int updateType = UPDATE_PROGRESS;
		
		resp = resp.getNested("cmst");
		this.revision = resp.getNumberLong("cmsr");
		
		int playStatus = (int)resp.getNumberLong("caps");
		int shuffleStatus = (int)resp.getNumberLong("cash");
		int repeatStatus = (int)resp.getNumberLong("carp");

		// update state if changed
		if (playStatus != this.playStatus
				|| shuffleStatus != this.shuffleStatus
				|| repeatStatus != this.repeatStatus) {
			updateType = UPDATE_STATE;
			this.playStatus = playStatus;
			this.shuffleStatus = shuffleStatus;
			this.repeatStatus = repeatStatus;

			Log.d(TAG, "about to interrupt #1");
			this.progress.interrupt();
		}

		String trackName = resp.getString("cann");
		String trackArtist = resp.getString("cana");
		String trackAlbum = resp.getString("canl");
		
		this.albumId = resp.getNumberString("asai");

		// update if track changed
		if (!trackName.equals(this.trackName)
				|| !trackArtist.equals(this.trackArtist)
				|| !trackAlbum.equals(this.trackAlbum)) {
			updateType = UPDATE_TRACK;
			this.trackName = trackName;
			this.trackArtist = trackArtist;
			this.trackAlbum = trackAlbum;
			
			// clear any coverart cache
			this.coverCache = null;
			this.fetchCover();
			
			// tell our progress updating thread about a new track
			// this makes sure he doesnt count progress from last song against this new one
			Log.d(TAG, "about to interrupt #2");
			this.progress.interrupt();
		}
		
		this.progressRemain = resp.getNumberLong("cant");
		this.progressTotal = resp.getNumberLong("cast");
		
		// send off updated event to gui
		if(update != null)
			this.update.sendEmptyMessage(updateType);
		
		// TODO: retrigger the keepalive thread with new revision number
		
		
	}


	public boolean coverEmpty = true;
	public Bitmap coverCache = null;
	
	public void fetchCover() {
		if(coverCache == null) {
			// spawn thread to fetch coverart
			new Thread(new Runnable() {
				public void run() {
					try {
						// http://192.168.254.128:3689/ctrl-int/1/nowplayingartwork?mw=320&mh=320&session-id=1940361390
						coverCache = RequestHelper.requestBitmap(String.format("%s/ctrl-int/1/nowplayingartwork?mw=320&mh=320&session-id=%s",
								session.getRequestBase(), session.sessionId));
					} catch (Exception e) {
						e.printStackTrace();
					}
					coverEmpty = (coverCache == null);
					if(update != null)
						update.sendEmptyMessage(UPDATE_COVER);
				}
			}).start();
		}
	}
	
	public long getVolume() {
		try {
			// http://192.168.254.128:3689/ctrl-int/1/getproperty?properties=dmcp.volume&session-id=130883770
			Response resp = RequestHelper.requestParsed(String.format("%s/ctrl-int/1/getproperty?properties=dmcp.volume&session-id=%s",
					session.getRequestBase(), session.sessionId), false);
			return resp.getNested("cmgt").getNumberLong("cmvo");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
		
		/*
		 *  cmgt  --+
	        mstt   4      000000c8 == 200
	        cmvo   4      00000054 == 84

		 */

	}
	
	public int getProgress() {
		return (int)((this.progressTotal - this.progressRemain) / 1000);
	}
	
	public int getRemaining() {
		return (int)(this.progressRemain / 1000);
	}
	
	public int getProgressTotal() {
		return (int)(this.progressTotal / 1000);
	}
	
	public int getShuffle() {
		return this.shuffleStatus;
	}
	
	public int getRepeat() {
		return this.repeatStatus;
	}
	
	public int getPlayStatus() {
		return this.playStatus;
	}
	
	public String getTrackName() {
		return this.trackName;
	}
	
	public String getTrackArtist() {
		return this.trackArtist;
	}

	public String getTrackAlbum() {
		return this.trackAlbum;
	}

	public long getRating() {
		// http://192.168.254.128:3689/ctrl-int/1/getproperty?properties=dmcp.volume&session-id=130883770
		return -1;
	}



}
