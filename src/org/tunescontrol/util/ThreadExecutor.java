package org.tunescontrol.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.Process;
import android.util.Log;

/**
 * ThreadPoolExecutor to reduce resources when running threads.  Starts with a
 * pool size of 4 and can grow to 50.  Uses java.util.Concurrency cleverness
 * to provide the quality pooling and thread safety.
 */
public class ThreadExecutor {
   private static final String TAG = ThreadExecutor.class.toString();
   private static final int CORE_POOL_SIZE = 4;
   private static final int MAXIMUM_POOL_SIZE = 50;
   private static final int KEEP_ALIVE = 10;
   private static final BlockingQueue<Runnable> sWorkQueue = new LinkedBlockingQueue<Runnable>(MAXIMUM_POOL_SIZE);

   private static final ThreadFactory sThreadFactory = new ThreadFactory() {
      private final AtomicInteger mCount = new AtomicInteger(1);

      public Thread newThread(Runnable r) {
         final String threadName = "ThreadExecutor #" + mCount.getAndIncrement();
         Log.d(TAG, String.format("Creating Thread: %s", threadName));
         final Thread thread = new Thread(r, threadName);
         Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
         return thread;
      }
   };
   private static final ThreadPoolExecutor sExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, sWorkQueue, sThreadFactory);

   public static void runTask(Runnable task) {
      sExecutor.execute(task);
      Log.d(TAG, String.format("Task count = %d", sWorkQueue.size()));
   }

}
