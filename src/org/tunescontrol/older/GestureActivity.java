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

package org.tunescontrol.older;

import org.tunescontrol.R;
import org.tunescontrol.R.id;
import org.tunescontrol.R.layout;

import android.app.Activity;
import android.content.Context;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class GestureActivity extends Activity {
	
	public final static String TAG = GestureActivity.class.toString();
	
	View top, left, bottom, right;
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		Log.w(TAG, "TOUCH");
		return false;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_gesture);
		
		this.top = this.findViewById(R.id.top);
		this.left = this.findViewById(R.id.left);
		this.bottom = this.findViewById(R.id.bottom);
		this.right = this.findViewById(R.id.right);
		
		
		// in our case were listening for specific gestures and changing colors based on action
		// reset after a few seconds to make sure we detect multiple
		
		SensorManager sensors = (SensorManager)this.getSystemService(Context.SENSOR_SERVICE);
		
		sensors.registerListener(new SensorListener() {

			@Override
			public void onAccuracyChanged(int sensor, int accuracy) {
			}
			
			protected float[] history = new float[5];
			protected float total = 0;
			protected int historyCursor = 0;

			@Override
			public void onSensorChanged(int sensor, float[] values) {
				// check our list of values in each dimension for any positive or negative action
				// work with one dimension to start with
				// keep log of last 10 values
				
				// subtract current value before overwriting
				total -= history[historyCursor];
				history[historyCursor] = values[1];
				total += history[historyCursor];
				historyCursor = (historyCursor + 1) % history.length;
				
				float mean = total / history.length;
				float min = Float.MAX_VALUE;
				float max = Float.MIN_VALUE;
				
				float sum = 0;
				for (int i = 0; i < history.length; i++) {
					final float v = history[i] - mean;
					sum += v * v;
					if(v < min) min = v;
					if(v > max) max = v;
				}
				float stdev = (float)(Math.sqrt(sum / (history.length - 1)));
				
				// check distance from first->last and average->last
				int first = (historyCursor + 1) % history.length;
				float fl = history[first] - history[historyCursor];
				float fa = mean - history[historyCursor];
				
				
				Log.d(TAG, String.format("mean=%f, stdev=%f, min=%f, max=%f, fl=%f, fa=%f", mean, stdev, min, max, fl, fa));
				
				// TODO Auto-generated method stub
				
			}

		}, SensorManager.SENSOR_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME);
		
		
	}
	
}
