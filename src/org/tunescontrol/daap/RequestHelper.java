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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class RequestHelper {
	
	// handle packaging a request off to itunes
	// based on the enum type we might ask for incremental data (searches, full album list)
	// also think about handling keep-alive requests
	// this class also maintains session information
	
	// also consider handling image requests separately?  (for caching)
	// we ask for cover art two ways:
	
	public static byte[] requestSearch(Session session, String search, int start, int end) throws Exception {
		// http://192.168.254.128:3689/databases/36/containers/113/items?session-id=1535976870&revision-number=61&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum&type=music&sort=name&include-sort-headers=1&query='dmap.itemname:*sea*'&index=0-7
		// doesnt seem to listen to &sort=name
		String encodedSearch = URLEncoder.encode(search).replaceAll("\\+", "%20");
		return request(String.format("%s/databases/%d/containers/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum&type=music&include-sort-headers=1&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:4','com.apple.itunes.mediakind:8')+('dmap.itemname:*%s*','daap.songartist:*%s*','daap.songalbum:*%s*'))&sort=name&index=%d-%d",
						session.getRequestBase(), session.databaseId, session.musicId,
						session.sessionId, encodedSearch, encodedSearch, encodedSearch, start, end), false);
	}
	
	public static byte[] requestTracks(Session session, String albumid) throws Exception {
		//http://192.168.254.128:3689/databases/36/containers/113/items?session-id=1301749047&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songalbumid:11624070975347817354'
		return request(String.format("%s/databases/%d/containers/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,daap.songartist,daap.songalbum,daap.songalbum,daap.songtime,daap.songtracknumber&type=music&sort=album&query='daap.songalbumid:%s'",
				session.getRequestBase(), session.databaseId,
				session.musicId, session.sessionId, albumid), false);
	}
	
	public static byte[] requestAlbums(Session session, int start, int end) throws Exception {
		// http://192.168.254.128:3689/databases/36/groups?session-id=1034286700&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1
		return request(String.format("%s/databases/%d/containers/%d/items?session-id=%s&meta=dmap.itemname,dmap.itemid,dmap.persistentid,daap.songartist&type=music&group-type=albums&sort=artist&include-sort-headers=1&index=%d-%d",
				session.getRequestBase(), session.databaseId,
				session.musicId, session.sessionId, start, end), false);
	}
	
	public static byte[] requestPlaylists(Session session) throws Exception {
		// http://192.168.254.128:3689/databases/36/containers?session-id=1686799903&meta=dmap.itemname,dmap.itemcount,dmap.itemid,dmap.persistentid,daap.baseplaylist,com.apple.itunes.special-playlist,com.apple.itunes.smart-playlist,com.apple.itunes.saved-genius,dmap.parentcontainerid,dmap.editcommandssupported
		return request(String.format("%s/databases/%d/containers?session-id=%s&meta=dmap.itemname,dmap.itemcount,dmap.itemid,dmap.persistentid,daap.baseplaylist,com.apple.itunes.special-playlist,com.apple.itunes.smart-playlist,com.apple.itunes.saved-genius,dmap.parentcontainerid,dmap.editcommandssupported",
				session.getRequestBase(), session.databaseId,
				session.musicId, session.sessionId), false);
	}

	public static Response requestParsed(String url, boolean keepalive) throws Exception {
		return ResponseParser.performParse(request(url, keepalive));
	}
	
	public static void attemptRequest(String url) {
		try {
			request(url, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//public static byte[] buffer = new byte[1024];
	//public static byte[] buffer2 = new byte[1024];
	
	public static byte[] request(String remote, boolean keepalive) throws Exception {
		
		Log.d("RequestHelper", String.format("started request(remote=%s)", remote));

		byte[] buffer = new byte[1024];
		
		URL url = new URL(remote);
		URLConnection connection = url.openConnection();
		connection.setRequestProperty("Viewer-Only-Client", "1");
		if(!keepalive) {
			connection.setConnectTimeout(2000);
			connection.setReadTimeout(2000);
		}
		connection.connect();
		InputStream is = connection.getInputStream();
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		int bytesRead;
		while ((bytesRead = is.read(buffer)) != -1) {
			os.write(buffer, 0, bytesRead);
		}

		os.flush();
		os.close();
		is.close();
		
		Log.d("RequestHelper", String.format("finished request(remote=%s, size=%d)", remote, os.size()));
		
		return os.toByteArray();
		
	}
	
	public static Bitmap requestThumbnail(Session session, int itemid) throws Exception {
		
		// http://192.168.254.128:3689/databases/38/items/2854/extra_data/artwork?session-id=788509571&revision-number=196&mw=55&mh=55
		byte[] raw = request(String.format("%s/databases/%d/items/%d/extra_data/artwork?session-id=%s&mw=55&mh=55",
				session.getRequestBase(), session.databaseId, itemid, session.sessionId), false);
		return BitmapFactory.decodeByteArray(raw, 0, raw.length);
		
	}
	
	public static Bitmap requestBitmap(String remote) throws Exception {
		
		byte[] raw = request(remote, false);
		return BitmapFactory.decodeByteArray(raw, 0, raw.length);

	}
	



}
