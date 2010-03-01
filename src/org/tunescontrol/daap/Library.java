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
import java.util.regex.Pattern;

import org.tunescontrol.daap.ResponseParser.TagListener;

import android.util.Log;

public class Library {

	public final static String TAG = Library.class.toString();
	protected final static int RESULT_INCREMENT = 50;
	protected final static Pattern MLIT_PATTERN = Pattern.compile("mlit");

	// library keeps track of albums/tracks from itunes also caches requests as needed
	protected final Session session;

	public Library(Session session) {
		this.session = session;

	}

	public long readSearch(TagListener listener, String search, long start, long items) {

		long total = -1;
		long hits = -1;
		try {

			// http://192.168.254.128:3689/databases/36/containers/113/items?session-id=1535976870&revision-number=61&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum&type=music&sort=name&include-sort-headers=1&query='dmap.itemname:*sea*'&index=0-7
			//byte[] raw = RequestHelper.requestSearch(session, search, (int) start, (int) (start + items));
			byte[] raw = RequestHelper.request(String.format("%s/databases/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query=('dmap.itemname:*%s*','daap.songartist:*%s*','daap.songalbum:*%s*')", session.getRequestBase(),
					session.databaseId, session.sessionId, search, search, search), false);
			//hits = ResponseParser.performSearch(raw, listener, MLIT_PATTERN, true);
			// parse list, passing off events in the process
			Response resp = ResponseParser.performParse(raw, listener, MLIT_PATTERN);
			// apso or adbs
			total = resp.getNested("apso").getNumberLong("mtco");

		} catch (Exception e) {
			Log.w(TAG, "readSearch Exception:" + e.getMessage());
		}

		Log.d(TAG, String.format("readSearch() finished start=%d, items=%d, total=%d", start, items, total));

		return hits;
	}

	public void readArtists(TagListener listener) {
		// check if we have a local cache create a wrapping taglistener to create
		// local cache
		try {
			Log.d(TAG, "readArtists() requesting...");

			// request ALL artists for performance
			// GET /databases/%d/browse/artists?session-id=%s&include-sort-headers=1&index=%d-%d
			byte[] raw = RequestHelper.request(String.format("%s/databases/%d/browse/artists?session-id=%s&include-sort-headers=1", session.getRequestBase(), session.databaseId, session.sessionId), false);

			// parse list, passing off events in the process
			int hits = ResponseParser.performSearch(raw, listener, MLIT_PATTERN, true);
			Log.d(TAG, String.format("readArtists() total=%d", hits));
			raw = null;

		} catch (Exception e) {
			Log.w(TAG, "readArtists Exception:" + e.getMessage());
		}
	}

	public void readAlbums(TagListener listener, String artist) {

		final String encodedArtist = URLEncoder.encode(artist).replaceAll("\\+", "%20");

		try {

			// make albums request for this artist
			// http://192.168.254.128:3689/databases/36/groups?session-id=1034286700&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1
			// http://192.168.254.128:3689/databases/36/groups?session-id=1598562931&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1&query='daap.songartist:*%s*'

			byte[] raw = RequestHelper.request(String.format("%s/databases/%d/groups?session-id=%s&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1&query='daap.songartist:%s'", session.getRequestBase(), session.databaseId,
					session.sessionId, encodedArtist), false);

			// parse list, passing off events in the process
			ResponseParser.performSearch(raw, listener, MLIT_PATTERN, false);

		} catch (Exception e) {
			Log.w(TAG, "readAlbums Exception:" + e.getMessage());
		}

	}

	public void readAlbums(TagListener listener) {

		// check if we have a local cache create a wrapping taglistener to create
		// local cache

		try {
			long start = 0, total = Integer.MAX_VALUE;

			byte[] raw = null;

			// continue making requests until we've found all
			do {
				Log.d(TAG, String.format("readAlbums() requesting start=%d of total=%d", start, total));

				// make partial album list request
				// http://192.168.254.128:3689/databases/36/groups?session-id=1034286700&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1&index=0-50
				raw = RequestHelper.request(String.format("%s/databases/%d/groups?session-id=%s&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1&index=%d-%d", session.getRequestBase(), session.databaseId,
						session.sessionId, start, start + RESULT_INCREMENT), false);

				// parse list, passing off events in the process
				final int hits = ResponseParser.performSearch(raw, listener, MLIT_PATTERN, false);
				raw = null;
				start += RESULT_INCREMENT;

				if (hits == 0)
					break;

			} while (start < total);

		} catch (Exception e) {
			Log.w(TAG, "readAlbums Exception:" + e.getMessage());
		}

		/*
		 * agal  --+
              mstt   4      000000c8 == 200
              muty   1      00 == 0
              mtco   4      00000017 == 23
              mrco   4      00000017 == 23
              mlcl  --+

		 */

	}

	public void readTracks(String albumid, TagListener listener) {

		// check if we have a local cache
		// create a wrapping taglistener to create local cache

		try {
			String temp = String.format("%s/databases/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songalbumid:%s'", session.getRequestBase(),
					session.databaseId, session.sessionId, albumid);

			// make tracks list request
			// http://192.168.254.128:3689/databases/36/containers/113/items?session-id=1301749047&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songalbumid:11624070975347817354'
			byte[] raw = RequestHelper.request(temp, false);

			// parse list, passing off events in the process
			ResponseParser.performSearch(raw, listener, MLIT_PATTERN, false);

		} catch (Exception e) {
			Log.w(TAG, "readTracks Exception:" + e.getMessage());
		}

	}

	public void readAllTracks(String artist, TagListener listener) {

		// check if we have a local cache
		// create a wrapping taglistener to create local cache

		final String encodedArtist = URLEncoder.encode(artist).replaceAll("\\+", "%20");

		try {
			/*String temp = String.format("%s/databases/%d/containers/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songalbumid:%s'", session.getRequestBase(),
	                 session.databaseId, session.musicId, session.sessionId, albumid);*/
			// make tracks list request
			// http://192.168.254.128:3689/databases/36/containers/113/items?session-id=1301749047&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songalbumid:11624070975347817354'
			byte[] raw = RequestHelper.request(String.format("%s/databases/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songartist:%s'", session.getRequestBase(),
					session.databaseId, session.sessionId, encodedArtist), false);

			// parse list, passing off events in the process
			ResponseParser.performSearch(raw, listener, MLIT_PATTERN, false);

		} catch (Exception e) {
			Log.w(TAG, "readTracks Exception:" + e.getMessage());
		}
	}

}
