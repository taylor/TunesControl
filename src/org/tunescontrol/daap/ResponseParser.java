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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.regex.Pattern;

import android.util.Log;



public class ResponseParser {

	public final static String TAG = ResponseParser.class.toString();
	
	public final static Pattern BRANCHES = Pattern.compile("(cmst|mlog|agal|mlcl|mshl|mlit|abro|abar|apso|caci|avdb|cmgt|aply|adbs)");
	public final static Pattern STRINGS = Pattern.compile("(minm|cann|cana|canl|asaa|asal|asar)");
	
	public interface TagListener {
		public void foundTag(String tag, Response resp);
		public void searchDone();
	}

	public static int performSearch(byte[] raw, TagListener listener, Pattern listenFor, boolean haltmlit) throws IOException {
		DataInputStream stream = new DataInputStream(new ByteArrayInputStream(raw));
		ResponseParser parser = new ResponseParser(stream, listener, listenFor);
		int hits = parser.search(stream.available(), haltmlit);
		listener.searchDone();
		return hits;
	}
	
	public static Response performParse(byte[] raw, TagListener listener, Pattern listenFor) throws IOException {
		DataInputStream stream = new DataInputStream(new ByteArrayInputStream(raw));
		ResponseParser parser = new ResponseParser(stream, listener, listenFor);
		Response resp = parser.parse(stream.available());
		listener.searchDone();
		return resp;
	}
	
	public static Response performParse(byte[] raw) throws IOException {
		DataInputStream stream = new DataInputStream(new ByteArrayInputStream(raw));
		ResponseParser parser = new ResponseParser(stream, null, null);
		return parser.parse(stream.available());
	}
	
	protected final DataInputStream raw;
	protected final TagListener listener;
	protected final Pattern listenFor;

	protected ResponseParser(DataInputStream raw, TagListener listener, Pattern listenFor) {
		this.raw = raw;
		this.listener = listener;
		this.listenFor = listenFor;
		
	}
	
	protected int search(int handle, boolean haltmlit) throws IOException {
		
		int progress = 0;
		int hits = 0;
		
		// loop until done with the section weve been assigned
		while(handle > 0) {
			String key = readString(4);
			int length = raw.readInt();
			handle -= 8 + length;
			progress += 8 + length;
			
			// check if we need to handle mlit special-case where it doesnt branch
			if(haltmlit && key.equals("mlit")) {
				Response resp = new Response();
				resp.put(key, readString(length));
				this.listener.foundTag(key, resp);
				hits++;
				
			} else if(BRANCHES.matcher(key).matches()) {
				if(this.listenFor.matcher(key).matches()) {
					// parse and report if interesting branches
					this.listener.foundTag(key, parse(length));
					hits++;
				} else {
					// recurse searching for other branches
					hits += this.search(length, haltmlit);
				}
					
			} else {
				// otherwise discard data
				readString(length);
				
			}
			
		}
		
		return hits;
		
	}
	
	protected Response parse(int handle) throws IOException {
		
		Response resp = new Response();
		int progress = 0;
		
		// loop until done with the section weve been assigned
		while(handle > 0) {
			String key = readString(4);
			int length = raw.readInt();
			handle -= 8 + length;
			progress += 8 + length;
			
			// handle key collisions by using index notation
			String nicekey = resp.containsKey(key) ? String.format("%s[%06d]", key, progress) : key;

			if(BRANCHES.matcher(key).matches()) {
				// recurse off to handle branches
				Response branch = parse(length);
				resp.put(nicekey, branch);
				
				// pass along to listener if needed
				if(listener != null)
					if(this.listenFor.matcher(key).matches())
						this.listener.foundTag(key, branch);
				
			} else if(STRINGS.matcher(key).matches())  {
				// force handling as string
				resp.put(nicekey, readString(length));
				
			} else if(length == 1 || length == 2 || length == 4 || length == 8) {
				// handle parsing unsigned bytes, ints, longs
				resp.put(nicekey, new BigInteger(1, readRaw(length)));
				
			} else {
				// fallback to just parsing as string
				resp.put(nicekey, readString(length));
				
			}
			
		}
		
		return resp;
	}
	
	protected byte[] readRaw(int length) throws IOException {
		byte[] buf = new byte[length];
		raw.read(buf, 0, length);
		return buf;
	}
	
	protected String readString(int length) throws IOException {
		byte[] key = new byte[length];
		raw.read(key, 0, length);
		return new String(key);
	}
	
}
