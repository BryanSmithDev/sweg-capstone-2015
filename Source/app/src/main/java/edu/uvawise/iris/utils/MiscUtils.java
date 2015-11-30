package edu.uvawise.iris.utils;

import android.content.Context;
import android.os.Handler;

/**
 * Created by Bryan on 11/29/2015.
 */
public abstract class MiscUtils {

    public static void runOnUiThread(Context context, Runnable r) {
        Handler handler = new Handler(context.getMainLooper());
        handler.post(r);
    }
}
