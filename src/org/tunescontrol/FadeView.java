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

package org.tunescontrol;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

public class FadeView extends RelativeLayout {

   public final static String TAG = FadeView.class.toString();

   protected boolean foundViews = false;
   protected View info, seek;

   public boolean allowFade = true;

   public Handler fadeDownHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         assertViews();
         info.clearAnimation();
         info.startAnimation(fadeDown);
         seek.clearAnimation();
         seek.startAnimation(fadeDown);
      }
   };

   public FadeView(Context context) {
      super(context);
      this.prepare(context);
   }

   public FadeView(Context context, AttributeSet attrs) {
      super(context, attrs);
      this.prepare(context);
   }

   public FadeView(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
      this.prepare(context);
   }

   protected final static long FADE_DELAY = 5000;

   protected Animation fadeDown, fadeUp;
   protected Timer fadeTimer = null;

   protected void prepare(Context context) {
      this.setClickable(true);
      this.setLongClickable(true);

      this.fadeUp = AnimationUtils.loadAnimation(context, R.anim.fade_up);
      this.fadeDown = AnimationUtils.loadAnimation(context, R.anim.fade_down);

   }

   protected void assertViews() {
      if (this.foundViews)
         return;
      this.info = this.findViewById(R.id.info_box);
      this.seek = this.findViewById(R.id.seek_box);
      this.foundViews = true;
   }

   protected enum AnimationState {
      VISIBLE, GONE, FADING_UP, FADING_DOWN
   }

   protected AnimationState state = AnimationState.VISIBLE;

   protected void onAnimationEnd() {
      super.onAnimationEnd();
      if (state.equals(AnimationState.FADING_DOWN)) {
         state = AnimationState.GONE;
      } else if (state.equals(AnimationState.FADING_UP)) {
         state = AnimationState.VISIBLE;
      }
   }

   public boolean onInterceptTouchEvent(MotionEvent event) {

      // always return false to let events fall through as needed
      this.handleEvent(event);
      return false;

   }

   public Handler doubleTapHandler = null;

   protected long lastDown = -1;
   public final static long DOUBLE_TIME = 500;

   public boolean onTouchEvent(MotionEvent event) {

      // always return true because we are last resort to grab up event
      this.handleEvent(event);

      // watch for double-taps which should move to tracks
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
         long nowDown = System.currentTimeMillis();
         // Log.d(TAG,
         // String.format("searching for doubletap with now=%d, last=%d, delta=%d",
         // nowDown, lastDown, nowDown - lastDown));
         if (nowDown - lastDown < DOUBLE_TIME) {
            // activate double-tap event
            if (doubleTapHandler != null)
               doubleTapHandler.sendEmptyMessage(-1);

         } else {
            lastDown = nowDown;
         }

      }

      return true;

   }

   public void keepAwake() {
      this.bringIn();
      this.startFade();
   }

   public void bringIn() {
      // fade in controls if not already visible
      if (this.fadeTimer != null)
         this.fadeTimer.cancel();

      if (state.equals(AnimationState.GONE) || state.equals(AnimationState.FADING_DOWN)) {
         state = AnimationState.FADING_UP;
         this.assertViews();
         info.clearAnimation();
         info.startAnimation(fadeUp);
         seek.clearAnimation();
         seek.startAnimation(fadeUp);
      }
   }

   public void startFade() {
      if (!this.allowFade)
         return;

      if (this.fadeTimer != null)
         this.fadeTimer.cancel();

      this.fadeTimer = new Timer();
      this.fadeTimer.schedule(new TimerTask() {
         @Override
         public void run() {
            if (state.equals(AnimationState.FADING_UP) || state.equals(AnimationState.VISIBLE)) {
               state = AnimationState.FADING_DOWN;
               fadeDownHandler.sendEmptyMessage(-1);
            }
         }
      }, FADE_DELAY);
   }

   protected void handleEvent(MotionEvent event) {

      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
         // fade in controls if not already visible
         this.bringIn();
         break;

      case MotionEvent.ACTION_UP:
         // start the fade-out timer
         this.startFade();
         break;
      }

   }

}