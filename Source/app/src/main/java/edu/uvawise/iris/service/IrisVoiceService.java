package edu.uvawise.iris.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import edu.uvawise.iris.MainActivity;
import edu.uvawise.iris.R;

import static java.lang.Thread.sleep;

/**
 * Created by Bryan on 11/16/2015.
 */
public class IrisVoiceService extends Service implements TextToSpeech.OnInitListener {
    private NotificationManager mNotificationManager;
    private Timer mTimer = new Timer();

    private static boolean isRunning = false;

    TextToSpeech textToSpeech;
    private int speechStatus = 0;


    private static final String TAG = IrisVoiceService.class.getSimpleName();



    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Voice Service Created.");
        showNotification();
        isRunning = true;
        mTimer.scheduleAtFixedRate(new MessageReaderTask(), 3000L, 3000L);
        textToSpeech=new TextToSpeech(getApplicationContext(), this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY; // Run until explicitly stopped.
    }

    /**
     * Display a notification in the notification bar.
     */
    private void showNotification() {

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);

        mBuilder.setSmallIcon(R.drawable.ic_stat_iris);
        mBuilder.setContentTitle("Iris Service is Running");
        mBuilder.setContentText("Emails will be read out as they come in.");
        mBuilder.setContentIntent(contentIntent);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notif = mBuilder.build();
        notif.flags = notif.flags
                | Notification.FLAG_ONGOING_EVENT;
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(0, notif);

    }


    public static boolean isRunning()
    {
        return isRunning;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mNotificationManager.cancelAll(); // Cancel the persistent notification.
        if (mTimer != null) {mTimer.cancel();}
        isRunning = false;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        Log.i(TAG, "Service Stopped.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onInit(int status) {
        speechStatus = status;
        if (status == TextToSpeech.SUCCESS) {
            Log.d("TTS", "TTS SUCCESS");
            if (textToSpeech == null) {
                textToSpeech = new TextToSpeech(getApplicationContext(), this);
                textToSpeech.setSpeechRate(0.8f);
            }
            int result = textToSpeech.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
                Intent installIntent = new Intent();
                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
            Log.d("TTS", "Language set");
            textToSpeech.speak("Test", TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private class MessageReaderTask extends TimerTask {
        @Override
        public void run() {
            Log.i(TAG, "Timer doing work.");
            try {


            } catch (Throwable t) { //you should always ultimately catch all exceptions in timer tasks.
                Log.e("TimerTick", "Timer Tick Failed.", t);
            }
        }
    }
}