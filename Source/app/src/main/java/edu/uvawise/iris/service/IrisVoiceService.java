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
import edu.uvawise.iris.utils.GmailUtils;


/**
 * The background service which monitors for new Gmail messages and reads them aloud.
 */
public class IrisVoiceService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = IrisVoiceService.class.getSimpleName(); //LOG TAG

    //Key that defines the messages that are being passed via intent
    public static final String INTENT_DATA_MESSAGES_ADDED = "IntentPassedData_MessagesAdded";

    private static boolean isRunning = false; //Is the service running?

    private TextToSpeech textToSpeech; //Our text to speech engine. Does the talking.

    //Objects needed for creating MimeMessages
    private final Properties props = new Properties();
    private final Session session = Session.getDefaultInstance(props, null);
    private final JsonFactory JSON_FACTORY = new JacksonFactory();

    //Handle Notifications and the overlay.
    private WindowManager windowManager;
    private NotificationManager mNotificationManager;

    //Messages that are currently queued for reading and displaying on the overlay
    private List<Message> queuedMessages = new ArrayList<>();
    private List<MimeMessage> queuedMimeMessages = new ArrayList<>();

    //Views for message overlay
    private LinearLayout root;
    private TextView fromView;
    private TextView subjectView;
    private TextView bodyView;

    //The message that the overlay is currently displaying
    private String currentMessageID = "";

    private String currentMessageAccount = "";

    //Message observer that listens for new messages.
    private MessagesObserver messagesObserver = new MessagesObserver(new Handler());

    /**
     * Ran when the services is created.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Voice Service Created.");

        isRunning = true; //We are up and running now.
        showNotification(); //So show the notification

        //Register our message observer to start monitoring
        getContentResolver().
                registerContentObserver(
                        IrisContentProvider.MESSAGES_URI,
                        true,
                        messagesObserver);


        textToSpeech = new TextToSpeech(this, this); //Initialize our text to speech engine

        //Setup the overlay layout and parameters.
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupOverlay();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        windowManager.addView(root, params); //Add the overlay to the window.

    }

    /**
     * Ran when an activity explicit calls startService()
     * @param intent The intent sent
     * @param flags The flags sent
     * @param startId The start id
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        //We only want to work with the intent if it exists
        if (intent != null) {

            //Check for embedded new messages in the intent
            String[] jsonMessages = null;
            jsonMessages = intent.getStringArrayExtra(INTENT_DATA_MESSAGES_ADDED);

            //If there are messages, lets parse them and add them to the queue.
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
     * Ran when the service is explicitly stopped by the app or the OS.
     * Release resources here.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (root != null) windowManager.removeView(root); //Remove our overlay
        mNotificationManager.cancelAll(); // Cancel the persistent notification.
        isRunning = false; //We are no longer running
        getContentResolver().unregisterContentObserver(messagesObserver); //Remove our observer

        //Stop the text to speech engine.
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        Log.i(TAG, "Service Stopped.");
    }

    /**
     * Unused but required due to inheritance.
     * @param intent
     * @return
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Called when the text to speech engine is initialized.
     * @param status the engine status code
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TTS SUCCESS");
            //Initial setup of the text engine.
            if (textToSpeech == null) {
                textToSpeech = new TextToSpeech(getApplicationContext(), this);
                textToSpeech.setSpeechRate(1.1f);
            }
            int result = textToSpeech.setLanguage(Locale.US); //Set the locale of the text engine

            //Check for language issues
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "This Language is not supported");
                Intent installIntent = new Intent();
                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
            Log.d(TAG, "Language set");
        }
    }

    /**
     * Is the service running?
     * @return True if yes. Otherwise, false.
     */
    public static boolean isRunning() {
        return isRunning;
    }

    /**
     * Read the current message in the overlay.
     */
    private void readCurrentMessage() {
        //Only try to read if we have messages in the queue. Derp.
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
                //Set overlay data and read
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
            root.setVisibility(View.GONE); //If no messages hide the overlay.
        }
    }

    /**
     * Display a notification in the notification bar while service is running.
     */
    private void showNotification() {

        //Setup action that will launch the app when the main notification is clicked.
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, mainIntent, 0);

        //Setup notification info
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setSmallIcon(R.drawable.ic_stat_iris);
        mBuilder.setContentTitle("Iris Service is Running");
        mBuilder.setContentText("Emails will be read out as they come in.");
        mBuilder.setContentIntent(contentIntent);


        //Setup action to pause the service via the notification.
        Intent pauseIntent = new Intent(this, MainActivity.class);
        pauseIntent.putExtra(MainActivity.METHOD_TO_CALL, MainActivity.PAUSE_SERVICE);
        pauseIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        pauseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pausePendingIntent = PendingIntent.getActivity(this, 1, pauseIntent, 0);
        mBuilder.addAction(R.drawable.ic_stat_pause, "Pause Reading Service", pausePendingIntent);

        //Set flags
        Notification notif = mBuilder.build();
        notif.flags = notif.flags
                | Notification.FLAG_ONGOING_EVENT;
        notif.flags |= Notification.FLAG_AUTO_CANCEL;

        //Show notification
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, notif);

    }

    /**
     * Setup the layout and views for the message overlay.
     */
    private void setupOverlay(){
        LayoutInflater inflater =
                (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        //Setup our root view.
        root = new LinearLayout(this);
        root.setClickable(false);
        root.setFocusableInTouchMode(false);
        root.setFocusable(false);
        root.setVisibility(View.GONE);

        //Load XML layout.
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

        //Fetch the Views from the xml.
        fromView = (TextView) messageView.findViewById(R.id.fromTextView);
        subjectView = (TextView) messageView.findViewById(R.id.subjectTextView);
        bodyView = (TextView) messageView.findViewById(R.id.bodyTextView);
        Button keepButton = (Button) messageView.findViewById(R.id.keepButton);
        Button deleteButton = (Button) messageView.findViewById(R.id.deleteButton);

        //Archive the current message if keep button is clicked.
        keepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queuedMessages.remove(0);
                queuedMimeMessages.remove(0);
                readCurrentMessage();
                //if (currentMessageID != null && !currentMessageID.equals(""))
                    //GmailUtils.removeLabelFromMessage(getApplicationContext(),
                    //        currentMessageID,
                    //        "UNREAD");
            }
        });

        //Delete the current message if the delete button is clicked.
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queuedMessages.remove(0);
                queuedMimeMessages.remove(0);
                readCurrentMessage();
                if (currentMessageID != null && !currentMessageID.equals("")) {
                    //GmailUtils.deleteMessage(getApplicationContext(), currentMessageID);
                    ContentResolver contentResolver = getContentResolver();
                    int result = contentResolver.delete(IrisContentProvider.MESSAGES_URI, IrisContentProvider.MESSAGE_ID + " = ?", new String[]{currentMessageID});
                    Log.d(TAG, "Deleted: " + result);
                    contentResolver.notifyChange(
                            IrisContentProvider.MESSAGES_URI, // URI where data was modified
                            null,                           // No local observer
                            false);
                }
            }
        });

    }

    /**
     * Observer that watches for new messages and adds them to the queue.
     */
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
            //If we have queued messages, show the overlay and start reading.
            try {
                if (!queuedMessages.isEmpty()) {
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