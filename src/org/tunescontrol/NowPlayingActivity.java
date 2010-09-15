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

public class NowPlayingActivity extends ListActivity {
	
	public final static String TAG = TracksActivity.class.toString();
	protected BackendService backend;
	protected Session session;
	protected Library library;
	protected NowPlayingAdapter adapter;
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
			library.readNowPlaying(albumid, adapter);
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
		setContentView(R.layout.act_nowplaying);

		this.albumid = this.getIntent().getStringExtra(Intent.EXTRA_TITLE);
		
		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				session.controlPlayIndex(albumid,position);
				setResult(RESULT_OK,new Intent());
				finish();
			}
		});
		

		this.adapter = new NowPlayingAdapter(this);
		this.setListAdapter(adapter);
	}
	
	protected class NowPlayingAdapter extends BaseAdapter implements TagListener {
		protected Context context;
		protected LayoutInflater inflater;
		protected SimpleDateFormat format = new SimpleDateFormat("m:ss");
		protected Date date = new Date(0);

		protected List<Response> results = new LinkedList<Response>();
		
		public NowPlayingAdapter(Context context) {
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
			if (convertView == null)
				convertView = inflater.inflate(R.layout.item_nowplaying_track, parent, false);

			try {

				// otherwise show normal search result
				Response resp = (Response) this.getItem(position);

				String title = resp.getString("minm");
				String artist = resp.getString("asar");
				date.setTime(resp.getNumberLong("astm"));
				String length = format.format(date);

				((TextView) convertView.findViewById(android.R.id.text1)).setText(title);
				((TextView) convertView.findViewById(android.R.id.text2)).setText(length);
				((TextView) convertView.findViewById(R.id.artist)).setText(artist);

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
		
	}
}
