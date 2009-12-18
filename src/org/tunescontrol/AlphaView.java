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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class AlphaView extends View {

   public interface AlphaScrollable {
      public void scrollAlpha(char prefix);
   }

   public AlphaScrollable target;

   public AlphaView(Context context) {
      super(context);
   }

   public AlphaView(Context context, AttributeSet attrs) {
      super(context, attrs);
   }

   public AlphaView(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
   }

   protected String[] alpha = new String[] { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z" };

   protected boolean touching = false;

   // try capturing touches to jump to letters
   // will actively adjust list location
   public boolean onTouchEvent(MotionEvent event) {

      float margin = this.getWidth() / 4;
      float fracy = (event.getY() - (margin * 2)) / (this.getHeight() - (margin * 4));

      int approx = (int) (alpha.length * fracy);
      // Log.d("ALPHA", String.format("approx=%s", alpha[approx]));

      if (approx < 0 || approx >= alpha.length)
         return true;

      // jump to alpha location in list

      // find first letter location in list
      this.target.scrollAlpha(alpha[approx].charAt(0));

      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
         this.touching = true;
         this.invalidate();
         break;

      case MotionEvent.ACTION_UP:
         this.touching = false;
         this.invalidate();
         break;

      }

      return true;

   }

   protected void onDraw(Canvas canvas) {

      // show shadow only when being actively touched

      float width = this.getWidth(), height = this.getHeight();
      float margin = this.getWidth() / 4;

      float spacing = (height - (margin * 4)) / alpha.length;

      int shadowColor = Color.argb(128, 0, 0, 0);
      GradientDrawable shadow = new GradientDrawable(Orientation.LEFT_RIGHT, new int[] { shadowColor, shadowColor });
      shadow.setShape(GradientDrawable.RECTANGLE);
      shadow.setCornerRadius(10);

      shadow.setBounds((int) margin, (int) margin, (int) (width - margin), (int) (height - margin));

      if (touching)
         shadow.draw(canvas);

      // draw alphabet index
      // remember that text is drawn from bottom

      Paint paint = new Paint();
      paint.setColor(Color.argb(255, 255, 255, 255));
      paint.setFakeBoldText(true);
      paint.setFlags(Paint.ANTI_ALIAS_FLAG);

      // measure to figure out perfect text height
      // W is a nice average letter
      // read new metrics to get exact pixel dimensions
      FontMetricsInt fm = paint.getFontMetricsInt();
      //float charHeight = Math.abs(fm.top) + Math.abs(fm.descent);
      float startTop = (margin * 2) + Math.abs(fm.top);

      for (int i = 0; i < alpha.length; i++) {
         float charWidth = paint.measureText(alpha[i]);
         float centered = (width - charWidth) / 2;

         canvas.drawText(alpha[i], centered, (i * spacing) + startTop, paint);

      }

   }

}
