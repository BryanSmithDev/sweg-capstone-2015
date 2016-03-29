package edu.uvawise.iris.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ModifyMessageRequest;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.uvawise.iris.R;
import edu.uvawise.iris.sync.GmailAccount;
import edu.uvawise.iris.sync.IrisContentProvider;

/**
 * Collection of Gmail Helper Methods. Simplifies reuse of GMail API Tasks.
 */
public abstract class GmailUtils {

    private static String TAG = GmailUtils.class.getSimpleName(); //LOG TAG

    //Our GMAIL API Permission Scopes.
    public static final String[] SCOPES = {GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_MODIFY};


    /**
     * Gets the Google Account Credential with Gmail scopes
     *
     * @param context     The context to run in.
     * @param accountName The name of the account to retrieve
     * @throws IOException
     * @throws GoogleAuthException
     */
    public static GoogleAccountCredential getGmailAccountCredential(Context context, String accountName) throws IOException, GoogleAuthException {
        return getGoogleAccountCredential(context, accountName, Arrays.asList(SCOPES));
    }


    /**
     * Get an initial, blank Gmail credential
     *
     * @param context The context to run in.
     * @return
     */
    public static GoogleAccountCredential getInitialGmailAccountCredential(Context context) {
        return GoogleAccountCredential.usingOAuth2(context, Arrays.asList(SCOPES));
    }


    /**
     * Gets the OAuth2 token for the specified account.
     *
     * @param context     The context to run in.
     * @param accountName The account name to get the token for.
     * @param scope       The scopes needed for the account.
     */
    public static String getToken(Context context, String accountName, List<String> scope)
            throws IOException, GoogleAuthException {
        if (scope == null) scope = Arrays.asList(SCOPES);
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scope);
        credential.setSelectedAccountName(accountName);
        return credential.getToken();
    }


    /**
     * Gets the Gmail API Service
     *
     * @param credential The account credential that will give permission to the API
     * @return The Gmail Service.
     */
    public static Gmail getGmailService(GoogleAccountCredential credential) {
        return new com.google.api.services.gmail.Gmail.Builder(
                AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential).setApplicationName("Iris").build();
    }


    /**
     * Show a notification stating that Iris needs permission for the selected account. Clicking
     * the notification will prompt them to give access via Google's API
     *
     * @param context The context to run in.
     * @param e       The permission intent
     * @param account The account name that needs permission.
     */
    public static void permissionNotification(Context context, Intent e, String account) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, e, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context).setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setContentText(context.getString(R.string.alert_permission_msg, account))
                .setContentTitle(context.getString(R.string.alert_permission_title))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setTicker(context.getString(R.string.alert_permission_title));
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }


    /**
     * Remove a notification from the notification shade.
     *
     * @param context        The context to run in.
     * @param notificationId The ID of the notification
     */
    public static void cancelPermissionNotification(Context context, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }


    /**
     * Gets the google account credential.
     *
     * @param context     The context to run in.
     * @param accountName The name of the account to retrieve.
     * @param scope       The scopes needed from the account.
     */
    public static GoogleAccountCredential getGoogleAccountCredential(
            Context context, String accountName, List<String> scope) throws IOException, GoogleAuthException {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scope);
        credential.setSelectedAccountName(accountName);
        credential.getToken();
        return credential;
    }

    /**
     * Is Gmail set to sync? Based on saved preference value only.
     * @param context the context
     * @return True if sync is currently enabled.
     */
    public static boolean isSyncing(Context context){
        return PrefUtils.getBoolean(context,R.string.pref_key_gmail_syncing,false);
    }

    /**
     * Set the isSyncing shared preference
     * @param context the context
     * @param value The value to set.
     */
    public static void setIsSyncing(Context context, boolean value){
        PrefUtils.setBoolean(context,R.string.pref_key_gmail_syncing,value);
    }


    /**
     * Archives a group of Gmail messages
     * @param context the context
     * @param IDs The list of IDs to archive
     */
    public static void archiveMessages(final Context context, final String userID, final Collection<String> IDs) {
        removeLabelFromMessages(context, userID, IDs, "INBOX");
    }

    /**
     * Archives a Gmail message
     * @param context the context
     * @param ID The ID of the message to archive
     */
    public static void archiveMessage(final Context context, final String userID, final String ID) {
        archiveMessages(context, userID, Collections.singletonList(ID));
    }

    /**
     * Deletes a group of Gmail Messages
     * @param context the context
     * @param IDs The list of IDs to delete
     */
    public static void deleteMessages(final Context context, final String userID, final Collection<String> IDs) {
        if (IDs.size() < 1) return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Delete Thread Running");
                GoogleAccountCredential credential = null;
                BigInteger histID;
                try {
                    credential = GmailUtils.getGmailAccountCredential(context, userID);

                    final Gmail gmail = GmailUtils.getGmailService(credential);
                    for (String id : IDs) {
                        Log.d(TAG, "Deleting from server: " + id);
                        gmail.users().messages().trash(userID, id).execute();
                    }
                    histID = gmail.users().getProfile(userID).execute().getHistoryId();
                    if (histID != null) {
                        setCurrentHistoryID(context, userID, histID);
                    }
                } catch (IOException | GoogleAuthException e) {
                    AndroidUtils.runOnUiThread(context, new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Error deleting message(s).", Toast.LENGTH_SHORT).show();
                        }
                    });
                    e.printStackTrace();
                }

            }
        }).start();
    }

    /**
     * Delete a Gmail Message
     * @param context The context
     * @param ID The ID of the message to delete
     */
    public static void deleteMessage(final Context context,final String userID, final String ID) {
        deleteMessages(context, userID, Collections.singletonList(ID));
    }

    /**
     * Remove a label from a group of messages
     * @param context The context
     * @param IDs The list of IDs to remove the label from
     * @param label The label to remove
     */
    public static void removeLabelFromMessages(final Context context, final String userID, final Collection<String> IDs, final String label) {
        if (IDs.size() < 1) return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Label removing Thread Running");
                GoogleAccountCredential credential = null;
                BigInteger histID;
                try {
                    credential = getGmailAccountCredential(context, userID);

                    final Gmail gmail = GmailUtils.getGmailService(credential);
                    for (String id : IDs) {
                        Log.d(TAG, "Removing label " + label + " from " + id);
                        ModifyMessageRequest request = new ModifyMessageRequest();
                        request.setRemoveLabelIds(Collections.singletonList(label));
                        gmail.users().messages().modify(userID, id, request).execute();
                    }
                    histID = gmail.users().getProfile(userID).execute().getHistoryId();
                    if (histID != null) {
                        setCurrentHistoryID(context, userID,  histID);
                    }
                } catch (IOException | GoogleAuthException e) {
                    AndroidUtils.runOnUiThread(context, new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Error removing label from message(s).", Toast.LENGTH_SHORT).show();
                        }
                    });
                    e.printStackTrace();
                }

            }
        }).start();
    }

    public static BigInteger getCurrentHistoryID(Context context, String userID) {
        Cursor histCursor = context.getContentResolver().query(IrisContentProvider.ACCOUNT_URI,
                new String[]{IrisContentProvider.CURR_HIST_ID},
                IrisContentProvider.USER_ID + " = ?",
                new String[]{userID},
                null);
        if (histCursor == null) return new BigInteger("0");
        histCursor.moveToFirst();
        BigInteger result = new BigInteger(histCursor.getString(0));
        histCursor.close();
        return result;

    }

    public static ArrayList<GmailAccount> getGmailAccounts(Context context){

        Cursor accCursor = context.getContentResolver().query(IrisContentProvider.ACCOUNT_URI,null,null,null,null);
        ArrayList<GmailAccount> accounts = new ArrayList<>();

        if (accCursor == null) return null;
        accCursor.moveToFirst();

        int i =0;
        while (!accCursor.isAfterLast()) {
            GmailAccount temp = new GmailAccount(accCursor.getString(1),accCursor.getString(3));
            accounts.add(temp);
            Log.d(TAG,"GetGmailAccounts: "+i+"  -  "+temp.getUserID());
            accCursor.moveToNext();
            i++;
        }
        accCursor.close();

        return accounts;
    }

    public static String getGmailAccount(Context context, int index){

        Cursor accCursor = context.getContentResolver().query(IrisContentProvider.ACCOUNT_URI,null,null,null,null);
        String result = "";
        if (accCursor == null) return null;
        accCursor.moveToFirst();

        int i=0;
        while (!accCursor.isAfterLast()) {
            result = accCursor.getString(1);
            Log.d(TAG,"GetGmailAccount at "+index+" : "+accCursor.getString(1));
            if (i==index) break;
            accCursor.moveToNext();
            i++;
        }
        accCursor.close();

        return result;
    }

    public static void setCurrentHistoryID(Context context, String userID, BigInteger histID) {
        if (histID==null) return;
        ContentValues values = new ContentValues();
        values.put(IrisContentProvider.CURR_HIST_ID, histID.toString());
        int result = context.getContentResolver().update(IrisContentProvider.ACCOUNT_URI,values,IrisContentProvider.USER_ID + " = '"+userID+"'",null);
        if (result >=1) Log.i(TAG,"Set "+userID+"'s history ID to "+ histID);
    }

    /**
     * Remove a label from a Gmail Message
     * @param context The context
     * @param ID The ID of the message to remove the label from
     * @param label The label to remove.
     */
    public static void removeLabelFromMessage(final Context context,final String userID, final String ID, final String label) {
        removeLabelFromMessages(context, userID, Collections.singletonList(ID), label);
    }

}
