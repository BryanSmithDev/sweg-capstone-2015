/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uvawise.iris.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.uvawise.iris.R;
import edu.uvawise.iris.utils.Constants;


/**
 * Static helper methods for working with the sync framework.
 */
public class SyncUtils {

    private static final String TAG = SyncUtils.class.getSimpleName();
    private static final long SYNC_FREQUENCY = 60;

    private static final String[] SCOPES = {GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_MODIFY};

    /**
     * Syncs now for the current account.
     *
     * @param context The context to run in.
     */
    public static void syncNow(Context context) {
        Account[] accounts = AccountManager.get(context).getAccountsByType(Constants.ACCOUNT_TYPE);
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String googleAccount = sharedPreferences.getString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, "");
        Bundle b = new Bundle();
        // Disable sync backoff and ignore sync preferences. In other words...perform sync NOW!
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        for (Account account : accounts) {
            if (account.name.equals(googleAccount)) {
                ContentResolver.cancelSync(account, Constants.SYNC_AUTH);
                ContentResolver.requestSync(account, Constants.SYNC_AUTH, b);
                break;
            }
        }
    }

    /**
     * Disables syncing.
     *
     * @param context The context to run in.
     */
    public static void disableSync(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Constants.PREFS_KEY_GMAIL_SYNCING, false);
        editor.apply();

        disableSyncForAll(context);

    }

    /**
     * Disables syncing for all accounts.
     *
     * @param context The context to run in.
     */
    private static void disableSyncForAll(Context context) {
        Account[] accounts = AccountManager.get(context).getAccountsByType(Constants.ACCOUNT_TYPE);
        for (Account account : accounts) {
            ContentResolver.cancelSync(account, Constants.SYNC_AUTH);
            ContentResolver.setIsSyncable(account, Constants.SYNC_AUTH, 0);
            ContentResolver.setSyncAutomatically(account, Constants.SYNC_AUTH, false);
        }
    }

    /**
     * Returns true if currently syncing
     *
     * @param context The context to run in.
     */
    public static boolean isSyncActive(Context context) {
        Account[] accounts = AccountManager.get(context).getAccountsByType(Constants.ACCOUNT_TYPE);
        for (Account account : accounts) {
            Log.d(TAG, "Is Sync Active? - " + account.name);
            if (ContentResolver.isSyncActive(account, Constants.SYNC_AUTH)) {
                Log.d(TAG, "Is Sync Active? - " + account.name + "Yes");
                return true;
            }
        }
        return false;
    }

    /**
     * Check if sync is enabled.
     * @param context  The context to run in.
     */
    public static boolean isSyncEnabled(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        Account[] am = AccountManager.get(context).getAccountsByType(Constants.ACCOUNT_TYPE);
        if (!sharedPreferences.getBoolean(Constants.PREFS_KEY_GMAIL_SYNCING,false)) return false;
        for (Account account : am) {
            if (sharedPreferences.getString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, "").equals(account.name)) {
                boolean isYourAccountSyncEnabled = ContentResolver.getSyncAutomatically(account,Constants.SYNC_AUTH);
                boolean isMasterSyncEnabled = ContentResolver.getMasterSyncAutomatically();
                if (isMasterSyncEnabled && isYourAccountSyncEnabled) return true;
            }
        }
        return false;
    }

    /**
     * Enables syncing.
     *
     * @param context The context to run in.
     */
    public static void enableSync(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Constants.PREFS_KEY_GMAIL_SYNCING, true);
        editor.apply();

        disableSyncForAll(context);

        ContentResolver.setMasterSyncAutomatically(true);

        // Enable sync for account
        String googleAccount = sharedPreferences.getString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, "");

        enableSyncForAccount(context, new Account(googleAccount, Constants.ACCOUNT_TYPE));
    }

    /**
     * Enables syncing for an account.
     *
     * @param account The account to enable syncing for.
     */
    private static void enableSyncForAccount(Context context, Account account) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int min = Integer.parseInt(prefs.getString(context.getString(R.string.pref_sync_freq_key), "3"));
        // Inform the system that this account supports sync
        ContentResolver.setIsSyncable(account, Constants.SYNC_AUTH, 1);
        // Inform the system that this account is eligible for auto sync when the network is up
        ContentResolver.setSyncAutomatically(account, Constants.SYNC_AUTH, true);
        // Recommend a schedule for automatic synchronization. The system may modify this based
        // on other scheduled syncs and network utilization.
        ContentResolver.addPeriodicSync(
                account, Constants.SYNC_AUTH, new Bundle(), SYNC_FREQUENCY*min);
    }


    /**
     *  Show a notification stating that Iris needs permission for the selected account. Clicking
     *  the notification will prompt them to give access via Google's API
     * @param context The context to run in.
     * @param e The permission intent
     * @param account The account name that needs permission.
     */
    public static void permissionNotification(Context context, Intent e, String account){
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
     * @param context The context to run in.
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
     * @param context The context to run in.
     * @param accountName The name of the account to retrieve.
     * @param scope The scopes needed from the account.
     */
    public static GoogleAccountCredential getGoogleAccountCredential(
            Context context, String accountName, List<String> scope) throws IOException, GoogleAuthException {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scope);
        if (!accountName.equals("")) credential.setSelectedAccountName(accountName);
        credential.getToken();
        return credential;
    }

    /**
     *  Gets the Google Account Credential with Gmail scopes
     * @param context The context to run in.
     * @param accountName The name of the account to retrieve
     * @throws IOException
     * @throws GoogleAuthException
     */
    public static GoogleAccountCredential getGmailAccountCredential(Context context,String accountName) throws IOException, GoogleAuthException {
        return getGoogleAccountCredential(context, accountName, Arrays.asList(SCOPES));
    }

    /**
     * Get an initial, blank Gmail credential
     * @param context  The context to run in.
     * @return
     */
    public static GoogleAccountCredential getInitialGmailAccountCredential(Context context){
        return GoogleAccountCredential.usingOAuth2(context, Arrays.asList(SCOPES));
    }

    /**
     * Gets the OAuth2 token for the specified account.
     *
     * @param context  The context to run in.
     * @param accountName The account name to get the token for.
     * @param scope The scopes needed for the account.
     */
    public static String getToken(Context context, String accountName, List<String> scope)
            throws IOException, GoogleAuthException {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scope);
        credential.setSelectedAccountName(accountName);
        return credential.getToken();
    }

    /**
     * Gets the Gmail API Service
     * @param credential The account credential that will give permission to the API
     * @return The Gmail Service.
     */
    public static Gmail getGmailService(GoogleAccountCredential credential){
       return new com.google.api.services.gmail.Gmail.Builder(
               AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential).setApplicationName("Iris").build();
    }


}
