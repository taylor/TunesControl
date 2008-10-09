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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PairingServer extends Thread {
	
	// the pairing service waits for any incoming requests from itunes
	// it always returns a valid pairing code
	
	public final static String TAG = PairingServer.class.toString();
	
	//                                               0    1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16
	public static byte[] PAIRING_RAW = new byte[] { 0x63,0x6d,0x70,0x61,0x00,0x00,0x00,0x3a,0x63,0x6d,0x70,0x67,0x00,0x00,0x00,0x08,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x63,0x6d,0x6e,0x6d,0x00,0x00,0x00,0x16,0x41,0x64,0x6d,0x69,0x6e,0x69,0x73,0x74,0x72,0x61,0x74,0x6f,0x72,(byte)0xe2,(byte)0x80,(byte)0x99,0x73,0x20,0x69,0x50,0x6f,0x64,0x63,0x6d,0x74,0x79,0x00,0x00,0x00,0x04,0x69,0x50,0x6f,0x64 };
	
	// this is a hack so that we always return to the "latest" handler
	protected static Handler paired;
	
	protected ServerSocket server;
	protected Random random = new Random();
	
	public PairingServer(Handler paired) {
		PairingServer.paired = paired;
	}
	
	public void destroy() {
		try {
			this.server.close();
			this.stop();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected final static byte[] CHAR_TABLE = new byte[] { (byte) '0',
			(byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5',
			(byte) '6', (byte) '7', (byte) '8', (byte) '9', (byte) 'A',
			(byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F' };
	
	
	protected String toHex(byte[] code) {
		// somewhat borrowed from rgagnon.com
		byte[] result = new byte[2 * code.length];
		int index = 0;
		for(byte b : code) {
			int v = b & 0xff;
			result[index++] = CHAR_TABLE[v >>> 4];
			result[index++] = CHAR_TABLE[v & 0xf];
		}
		return new String(result);
	}
	
	public void run() {
		try {

			// start listening on a specific port for any requests
			this.server = new ServerSocket(1024);
			
		    while(true) {
		    	Socket socket = server.accept();
		    	
//		    	BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//				while(br.ready())
//					br.readLine();
//				br.close();
				
				// we dont care about checking the incoming pairing md5 from itunes
				// and we always just accept the pairing
				
				OutputStream output = socket.getOutputStream();
				
				// edit our local PAIRING_RAW to return the correct guid
				//byte[] code = new byte[] {0x00,0x00,(byte)0x00,(byte)0x00,0x00,(byte)0x00,0x00,(byte)0x02};
				byte[] code = new byte[8];
				random.nextBytes(code);
				System.arraycopy(code, 0, PAIRING_RAW, 16, 8);
				String niceCode = toHex(code);
				
				byte[] header = String.format("HTTP/1.1 200 OK\r\nContent-Length: %d\r\n\r\n", PAIRING_RAW.length).getBytes();
				byte[] reply = new byte[header.length + PAIRING_RAW.length];
				
				System.arraycopy(header, 0, reply, 0, header.length);
				System.arraycopy(PAIRING_RAW, 0, reply, header.length, PAIRING_RAW.length);

				output.write(reply);
				
				Log.w(TAG, "ohhai someone paired with meeee!");
				
				// trigger a handler that says weve been paired
				// pass back the random code we generated
				paired.sendMessage(Message.obtain(paired, -1, niceCode));
				
				output.flush();
				output.close();
				
				
		    }
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		
	}
	
	

}
