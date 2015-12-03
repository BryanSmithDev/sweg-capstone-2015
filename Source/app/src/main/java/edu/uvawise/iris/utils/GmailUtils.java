package edu.uvawise.iris.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.uvawise.iris.R;

/**
 * Collection of Gmail Helper Methods. Simplifies reuse of GMail API Tasks.
 */
public abstract class GmailUtils {

    private static String TAG = GmailUtils.class.getSimpleName(); //LOG TAG

    //Our GMAIL API Permission Scopes.
    private static final String[] SCOPES = {GmailScopes.MAIL_GOOGLE_COM,
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
        if (!accountName.equals("")) credential.setSelectedAccountName(accountName);
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
     * Get the currently logged in Google Account Name.
     * @param context the context
     * @return The name of the Google account logged in. Or an empty string if none.
     */
    public static String getGmailAccountName(Context context) {
        return getGmailAccountName(context,"");
    }

    /**
     * Get the currently logged in Google Account Name.
     * @param context the context
     * @return The name of the Google account logged in. Or the defaultValue specified if none.
     */
    public static String getGmailAccountName(Context context, String defaultValue) {
        String account = PrefUtils.getString(context,R.string.pref_key_gmail_account_name,defaultValue);
        if (account == null || account.equals("")) Log.w(TAG, "Saved account name is null");
        return account;
    }

    /**
     * Sets the currently logged in Google Account Name.
     * @param context the context
     */
    public static void setGmailAccountName(Context context, String name){
        PrefUtils.setString(context,R.string.pref_key_gmail_account_name,name);
    }

    /**
     * Get the current saved history ID
     * @param context the context
     * @return BigInteger history ID or null if none.
     */
    public static BigInteger getCurrentHistoryID(Context context) {
        String histString = PrefUtils.getString(context,R.string.pref_key_gmail_history_id,null);
        BigInteger histID = null;
        if (histString != null) {
            histID = new BigInteger(histString);
        }
        return histID;
    }

    /**
     * Sets the current history ID into the shared preference
     * @param context the context
     * @param histID The BigInteger history ID to save
     */
    public static void setCurrentHistoryID(Context context, BigInteger histID) {
        setCurrentHistoryID(context, histID.toString());
    }

    /**
     * Sets the current history ID into the shared preference
     * @param context the context
     * @param histID The String history ID to save
     */
    public static void setCurrentHistoryID(Context context, String histID) {
        PrefUtils.setString(context, R.string.pref_key_gmail_history_id ,histID);
        Log.i(TAG, "Setting HistoryID: " + histID);
    }

    /**
     * Archives a group of Gmail messages
     * @param context the context
     * @param IDs The list of IDs to archive
     */
    public static void archiveMessages(final Context context, final Collection<String> IDs) {
        removeLabelFromMessages(context, IDs, "INBOX");
    }

    /**
     * Archives a Gmail message
     * @param context the context
     * @param ID The ID of the message to archive
     */
    public static void archiveMessage(final Context context, final String ID) {
        archiveMessages(context, Collections.singletonList(ID));
    }

    /**
     * Deletes a group of Gmail Messages
     * @param context the context
     * @param IDs The list of IDs to delete
     */
    public static void deleteMessages(final Context context, final Collection<String> IDs) {
        if (IDs.size() < 1) return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Delete Thread Running");
                GoogleAccountCredential credential = null;
                BigInteger histID;
                try {
                    credential = GmailUtils.getGmailAccountCredential(context, GmailUtils.getGmailAccountName(context));

                    final Gmail gmail = GmailUtils.getGmailService(credential);
                    for (String id : IDs) {
                        Log.d(TAG, "Deleting from server: " + id);
                        gmail.users().messages().trash(GmailUtils.getGmailAccountName(context), id).execute();
                    }
                    histID = gmail.users().getProfile(GmailUtils.getGmailAccountName(context)).execute().getHistoryId();
                    if (histID != null) setCurrentHistoryID(context, histID);
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
    public static void deleteMessage(final Context context, final String ID) {
        deleteMessages(context, Collections.singletonList(ID));
    }

    /**
     * Remove a label from a group of messages
     * @param context The context
     * @param IDs The list of IDs to remove the label from
     * @param label The label to remove
     */
    public static void removeLabelFromMessages(final Context context, final Collection<String> IDs, final String label) {
        if (IDs.size() < 1) return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Label removing Thread Running");
                GoogleAccountCredential credential = null;
                BigInteger histID;
                try {
                    credential = getGmailAccountCredential(context, getGmailAccountName(context));

                    final Gmail gmail = GmailUtils.getGmailService(credential);
                    for (String id : IDs) {
                        Log.d(TAG, "Removing label " + label + " from " + id);
                        ModifyMessageRequest request = new ModifyMessageRequest();
                        request.setRemoveLabelIds(Collections.singletonList(label));
                        gmail.users().messages().modify(GmailUtils.getGmailAccountName(context), id, request).execute();
                    }
                    histID = gmail.users().getProfile(GmailUtils.getGmailAccountName(context)).execute().getHistoryId();
                    if (histID != null) setCurrentHistoryID(context, histID);
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

    /**
     * Remove a label from a Gmail Message
     * @param context The context
     * @param ID The ID of the message to remove the label from
     * @param label The label to remove.
     */
    public static void removeLabelFromMessage(final Context context, final String ID, final String label) {
        removeLabelFromMessages(context, Collections.singletonList(ID), label);
    }

}
