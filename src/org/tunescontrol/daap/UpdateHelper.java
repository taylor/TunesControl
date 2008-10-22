package org.tunescontrol.daap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONObject;
import org.tunescontrol.ControlActivity;
import org.tunescontrol.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class UpdateHelper implements Runnable {
	
	// expecting response back from server in format:
	// {"version": 1.0, "features": "Brand new interface with over 9,000 improvements.", "target": "search?q=searchterms"}
	// another target could be "details?id=yourexactmarketid"

	public final static String UPDATE_URL = "http://dacp.jsharkey.org/version";
	public final static double THIS_VERSION = 1.0;

	protected final Context context;
	protected final String userAgent;
	
	public UpdateHelper(Context context) {
		this.context = context;
		this.userAgent = String.format("%s %f", context.getResources().getString(R.string.app_name), THIS_VERSION);
		
		// spawn thread to check for update
		new Thread(this).start();
		
	}

	public void run() {
		try {
			JSONObject json = new JSONObject(UpdateHelper.getUrl(UPDATE_URL, userAgent));
			Message.obtain(versionHandler, -1, json).sendToTarget();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	

	public Handler versionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {

			// handle version update message
			if(!(msg.obj instanceof JSONObject)) return;
			JSONObject json = (JSONObject)msg.obj;
			
			final double version = json.optDouble("version");
			final String features = json.optString("features");
			final String target = "market://" + json.optString("target");
			
			// skip if we're already good enough
			if(version <= THIS_VERSION) return;
			
			new AlertDialog.Builder(context)
				.setTitle("New version")
				.setMessage(features)
				.setPositiveButton("Yes, upgrade", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
						context.startActivity(intent);
	                }
	            })
	            .setNegativeButton("Not right now", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int which) {
	                }
	            }).create().show();
			
		}

	};

	public static String getUrl(String tryUrl, String userAgent) throws Exception {
		
		URL url = new URL(tryUrl);
		URLConnection connection = url.openConnection();
		connection.setConnectTimeout(6000);
		connection.setReadTimeout(6000);
		connection.setRequestProperty("User-Agent", userAgent);
		connection.connect();
		
		InputStream is = connection.getInputStream();
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		int bytesRead;
		byte[] buffer = new byte[1024];
		while ((bytesRead = is.read(buffer)) != -1) {
			os.write(buffer, 0, bytesRead);
		}

		os.flush();
		os.close();
		is.close();

		return new String(os.toByteArray());
		
	}





	
	
}
