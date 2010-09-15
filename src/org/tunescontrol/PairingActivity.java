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

import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;

import javax.jmdns.ServiceInfo;

import org.tunescontrol.daap.PairingServer;
import org.tunescontrol.util.ThreadExecutor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PairingActivity extends Activity {

   public final static String TAG = PairingActivity.class.toString();

   private volatile PairingServer pairingServer;
   private ServiceInfo pairservice;
   private String address, library;

   public Handler paired = new Handler() {
      @Override
      public void handleMessage(Message msg) {

         // someone has paried with us, so try returning with result
         // also be sure to pack along the pairing code used

         Intent packed = new Intent();
         packed.putExtra(BackendService.EXTRA_ADDRESS, address);
         packed.putExtra(BackendService.EXTRA_LIBRARY, library);
         packed.putExtra(BackendService.EXTRA_CODE, (String) msg.obj);
         setResult(Activity.RESULT_OK, packed);
         finish();

      }
   };

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      // show dialog to user, explaining what happens next
      setContentView(R.layout.act_pairing);
      Log.d(TAG, "Begin pairing process...");

      this.address = this.getIntent().getStringExtra(BackendService.EXTRA_ADDRESS);
      this.library = this.getIntent().getStringExtra(BackendService.EXTRA_LIBRARY);

      // this activity should start the pairing service
      // the pairing server will report to us when someone tries pairing
      pairingServer = new PairingServer(paired);

      Random random = new Random();
      int id = random.nextInt(100000);
      final Hashtable values = new Hashtable();
      values.put("DvNm", "Android " + id);
      values.put("RemV", "10000");
      values.put("DvTy", "iPod");
      values.put("RemN", "Remote");
      values.put("txtvers", "1");
      byte[] pair = new byte[8];
      random.nextBytes(pair);
      values.put("Pair", toHex(pair));

      // NOTE: this "Pair" above is *not* the guid--we generate and return that
      // in PairingServer

      byte[] name = new byte[20];
      random.nextBytes(name);
      pairservice = ServiceInfo.create(LibraryActivity.REMOTE_TYPE, toHex(name), PairingServer.PORT, 0, 0, values);
   }

   private static final char[] _nibbleToHex = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f' };

   private static String toHex(byte[] code) {
      StringBuilder result = new StringBuilder(2 * code.length);

      for (int i = 0; i < code.length; i++) {
         int b = code[i] & 0xFF;
         result.append(_nibbleToHex[b / 16]);
         result.append(_nibbleToHex[b % 16]);
      }

      return result.toString();
   }

   @Override
   public void onStart() {
      super.onStart();

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            try {
               Log.i(TAG, "Starting PairingServer...");
               pairingServer.start();
               LibraryActivity.getZeroConf().registerService(pairservice);
            } catch (IOException ex) {
               Log.w(TAG, ex);
            }
         }
      });

   }

   @Override
   public void onStop() {
      super.onStop();

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            Log.i(TAG, "Stopping PairingServer...");
            pairingServer.destroy();
            pairingServer = null;
            LibraryActivity.getZeroConf().unregisterService(pairservice);
         }
      });

   }

}
