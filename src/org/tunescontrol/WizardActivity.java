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

package org.tunescontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;


public class WizardActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_wizard);
		
		Button next = (Button)this.findViewById(R.id.action_next);
		next.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				WizardActivity.this.setResult(Activity.RESULT_OK);
				WizardActivity.this.finish();
			}
		});
		
		Button prev = (Button)this.findViewById(R.id.action_prev);
		prev.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				WizardActivity.this.setResult(Activity.RESULT_CANCELED);
				WizardActivity.this.finish();
			}
		});
		
		
	}
	
}
