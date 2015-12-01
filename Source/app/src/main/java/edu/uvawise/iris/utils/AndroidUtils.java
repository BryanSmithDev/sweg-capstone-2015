package edu.uvawise.iris.utils;

import android.content.Context;
import android.os.Handler;

/**
 * Miscellaneous Android Helper Methods.
 */
public abstract class AndroidUtils {

    /**
     * Run a some task on the UI thread.
     * @param context The context
     * @param r The runnable object that will be ran on the UI thread.
     */
    public static void runOnUiThread(Context context, Runnable r) {
        Handler handler = new Handler(context.getMainLooper());
        handler.post(r);
    }
}
