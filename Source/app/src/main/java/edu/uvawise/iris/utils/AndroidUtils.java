package edu.uvawise.iris.utils;

import android.Manifest;
import android.app.Activity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

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

    /**
     * Check to see if the app has the permission requested
     * @return True if we have permission.
     */
    public static boolean hasPermission(Context context, String permission, Activity activity) {
        boolean result = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED;
        if (result) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.SYSTEM_ALERT_WINDOW},
                    0);
        } else {
            return true;
        }
        return false;
    }

}
