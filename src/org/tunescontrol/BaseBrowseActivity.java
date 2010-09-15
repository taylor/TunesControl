package org.tunescontrol;


import android.app.ListActivity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

public class BaseBrowseActivity extends ListActivity {

	public static final int RESULT_SWITCH_TO_ARTISTS = RESULT_FIRST_USER+1;
	public static final int RESULT_SWITCH_TO_PLAYLISTS = RESULT_FIRST_USER+2;
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);
		
		//If canceled stay at current level
		if (resultCode == RESULT_CANCELED)
			return;
		
		//Otherwise pass this back up the chain
		this.setResult(resultCode, intent);
		this.finish();
	}
	
	 @Override
	   public boolean onCreateOptionsMenu(Menu menu) {
	      super.onCreateOptionsMenu(menu);


	      MenuItem artists = menu.add(R.string.control_menu_artists);
	      artists.setIcon(R.drawable.ic_search_category_music_artist);
	      artists.setOnMenuItemClickListener(new OnMenuItemClickListener() {
		         public boolean onMenuItemClick(MenuItem item) {
		        	 BaseBrowseActivity.this.setResult(RESULT_SWITCH_TO_ARTISTS);
		        	 BaseBrowseActivity.this.finish();
		        	 return true;
		         }
	      });

	      MenuItem playlists = menu.add("Playlists");
	      playlists.setIcon(R.drawable.ic_search_category_music_song);
	      playlists.setOnMenuItemClickListener(new OnMenuItemClickListener() {
		         public boolean onMenuItemClick(MenuItem item) {
		        	 BaseBrowseActivity.this.setResult(RESULT_SWITCH_TO_PLAYLISTS);
		        	 BaseBrowseActivity.this.finish();
		        	 return true;
		         }
	      });
	      return true;
	   }
}
