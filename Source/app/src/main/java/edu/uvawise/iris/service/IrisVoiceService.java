package edu.uvawise.iris.service;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Timer;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import edu.uvawise.iris.MainActivity;
import edu.uvawise.iris.R;
import edu.uvawise.iris.sync.IrisContentProvider;
import edu.uvawise.iris.utils.Constants;
import edu.uvawise.iris.utils.GmailUtils;


/**
 * Created by Bryan on 11/16/2015.
 */
public class IrisVoiceService extends Service implements TextToSpeech.OnInitListener {
    private static final String TAG = IrisVoiceService.class.getSimpleName();
    private static boolean isRunning = false;
    private final JsonFactory JSON_FACTORY = new JacksonFactory();
    TextToSpeech textToSpeech;
    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);
    private WindowManager windowManager;
    private NotificationManager mNotificationManager;
    private Timer mTimer = new Timer();
    private List<Message> queuedMessages = new ArrayList<>();
    private List<MimeMessage> queuedMimeMessages = new ArrayList<>();
    private LinearLayout root;
    private TextView fromView;
    private TextView subjectView;
    private TextView bodyView;
    private String currentMessageID = "";

    private MessagesObserver messagesObserver = new MessagesObserver(new Handler());

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Voice Service Created.");
        showNotification();
        isRunning = true;
        getContentResolver().
                registerContentObserver(
                        IrisContentProvider.MESSAGES_URI,
                        true,
                        messagesObserver);
        textToSpeech = new TextToSpeech(this, this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater =
                (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        root = new LinearLayout(this);
        root.setClickable(false);
        root.setFocusableInTouchMode(false);
        root.setFocusable(false);
        root.setVisibility(View.GONE);

        View messageView = inflater.inflate(R.layout.window_message_display, root, true);
        messageView.setFocusableInTouchMode(false);
        messageView.setFocusable(false);
        messageView.setClickable(false);
        messageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "MessageViewTouch");
                v.setVisibility(View.GONE);
                return false;
            }
        });

        fromView = (TextView) messageView.findViewById(R.id.fromTextView);
        subjectView = (TextView) messageView.findViewById(R.id.subjectTextView);
        bodyView = (TextView) messageView.findViewById(R.id.bodyTextView);
        Button keepButton = (Button) messageView.findViewById(R.id.keepButton);
        Button deleteButton = (Button) messageView.findViewById(R.id.deleteButton);


        keepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queuedMessages.remove(0);
                queuedMimeMessages.remove(0);
                readCurrentMessage();
                if (currentMessageID != null && !currentMessageID.equals(""))
                    GmailUtils.removeLabelFromMessage(getApplicationContext(),
                            currentMessageID,
                            "UNREAD");
            }
        });

        //TODO: Remove messages from local database and notify contentobservers
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queuedMessages.remove(0);
                queuedMimeMessages.remove(0);
                readCurrentMessage();
                if (currentMessageID != null && !currentMessageID.equals("")) {
                    GmailUtils.deleteMessage(getApplicationContext(), currentMessageID);
                    ContentResolver contentResolver = getContentResolver();
                    int result = contentResolver.delete(IrisContentProvider.MESSAGES_URI,IrisContentProvider.MESSAGE_ID+" = ?", new String[] {currentMessageID});
                    Log.d(TAG,"Deleted: "+result);
                    contentResolver.notifyChange(
                            IrisContentProvider.MESSAGES_URI, // URI where data was modified
                            null,                           // No local observer
                            false);
                }
            }
        });


        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        windowManager.addView(root, params);

    }

    private void readCurrentMessage() {
        if (queuedMessages.size() >= 1 && queuedMessages.size() >= 1) {
            String addS = "";
            try {
                if (queuedMimeMessages.get(0).getFrom()[0] != null) {
                    InternetAddress add;
                    add = new InternetAddress(queuedMimeMessages.get(0).getFrom()[0].toString());

                    if (add.getPersonal() != null) {
                        addS = add.getPersonal();
                    } else if (add.getAddress() != null) {
                        addS = add.getAddress();
                    } else {
                        addS = queuedMimeMessages.get(0).getFrom()[0].toString();
                    }
                }

                fromView.setText(addS);
                subjectView.setText(queuedMimeMessages.get(0).getSubject());
                bodyView.setText(queuedMessages.get(0).getSnippet());
                currentMessageID = queuedMessages.get(0).getId();
                textToSpeech.speak("New email from: " + addS, TextToSpeech.QUEUE_ADD, null);
                textToSpeech.speak("Subject: " + queuedMimeMessages.get(0).getSubject(), TextToSpeech.QUEUE_ADD, null);
                textToSpeech.speak("Body: " + queuedMessages.get(0).getSnippet(), TextToSpeech.QUEUE_ADD, null);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        } else {
            root.setVisibility(View.GONE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        if (intent != null) {


            String[] jsonMessages = null;
            jsonMessages = intent.getStringArrayExtra(Constants.INTENT_DATA_MESSAGES_ADDED);

            if (jsonMessages != null) {
                Log.d(TAG, "Got the data on the BG service.");
                Message msg;
                MimeMessage mimeMsg;
                for (String json : jsonMessages) {
                    try {
                        msg = JSON_FACTORY.fromString(json, Message.class);
                        mimeMsg = new MimeMessage(session, new ByteArrayInputStream(Base64.decodeBase64(msg.getRaw())));
                        queuedMessages.add(msg);
                        queuedMimeMessages.add(mimeMsg);
                    } catch (IOException | MessagingException e) {
                        Log.e(TAG, "Error Parsing JSON.");
                        e.printStackTrace();
                    }
                }
                Log.d(TAG, "Messages Added");
            }
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (root != null) windowManager.removeView(root);
        mNotificationManager.cancelAll(); // Cancel the persistent notification.
        if (mTimer != null) {
            mTimer.cancel();
        }
        isRunning = false;
        getContentResolver().unregisterContentObserver(messagesObserver);
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
        if (status == TextToSpeech.SUCCESS) {
            Log.d("TTS", "TTS SUCCESS");
            if (textToSpeech == null) {
                textToSpeech = new TextToSpeech(getApplicationContext(), this);
                textToSpeech.setSpeechRate(1.1f);
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
        }
    }


    class MessagesObserver extends ContentObserver {
        public MessagesObserver(Handler handler) {
            super(handler);
        }


        @Override
        public void onChange(boolean selfChange) {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            try {
                if (!queuedMessages.isEmpty()) {
                    Message msg;
                    MimeMessage mimeMsg;
                    String address = "";
                    for (int i = 0; i < queuedMessages.size(); i++) {
                        msg = queuedMessages.get(i);
                        mimeMsg = queuedMimeMessages.get(i);
                        if (mimeMsg.getFrom()[0] != null) {
                            InternetAddress add;
                            add = new InternetAddress(mimeMsg.getFrom()[0].toString());

                            if (add.getPersonal() != null) {
                                address = add.getPersonal();
                            } else if (add.getAddress() != null) {
                                address = add.getAddress();
                            } else {
                                address = mimeMsg.getFrom()[0].toString();
                            }

                        }
                    }
                    if (root.getVisibility() != View.VISIBLE) {
                        readCurrentMessage();
                        root.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error when checking for incoming email.", t);
            }
        }
    }
}