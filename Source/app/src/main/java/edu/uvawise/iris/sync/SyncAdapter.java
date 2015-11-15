/*
 * Copyright 2013 The Android Open Source Project
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
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeMessage;

import edu.uvawise.iris.Constants;


/**
 * Gmail Sync Adapter
 *
 * <p>This class is instantiated in {@link SyncService}, which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 *
 * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
class SyncAdapter extends AbstractThreadedSyncAdapter {

    public static final String TAG = SyncAdapter.class.getSimpleName(); //Tag used in logging

    private Context context;    //Store the current context
    private Gmail gmail;        //The Gmail API service



    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.context = context;
    }

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        this.context = context;
    }

    /**
     * Called by the Android system in response to a request to run the sync adapter. The work
     * required to read data from the network, parse it, and store it in the content provider is
     * done here. Extending AbstractThreadedSyncAdapter ensures that all methods within SyncAdapter
     * run on a background thread. For this reason, blocking I/O and other long-running tasks can be
     * run <em>in situ</em>, and you don't have to set up a separate thread for them.
     .
     *
     * <p>This is where we actually perform any work required to perform a sync.
     * {@link android.content.AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
     * so it is safe to peform blocking I/O here.
     *
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    @Override
    public void onPerformSync(final Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        Log.i(TAG, "Beginning synchronization");

        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean(Constants.PREFS_KEY_GMAIL_SYNCING, true)) {
            Log.d(TAG, "Syncing is turned off.");
            return;
        }

        if (account == null) {
            Log.d(TAG, "Passed Account is null");
            return;
        }

        String googleAccount = sharedPreferences.getString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, "");
        if (googleAccount.equals("")) {
            Log.d(TAG, "Saved account name is null");
            return;
        }

        if (!googleAccount.equals(account.name)) {
            Log.d(TAG, "Account name different");
            return;
        }

        try {
            GoogleAccountCredential credential = SyncUtils.getGmailAccountCredential(context, account.name);
            if (credential == null) {
                Log.d(TAG, "Credential Null");
                return;
            }

            if (gmail == null) {
                gmail = SyncUtils.getGmailService(credential);
            }
            Log.d(TAG, "Sync Running");

            final ContentResolver contentResolver = context.getContentResolver();
            Gmail.Users.Messages.List mailList = gmail.users().messages().list(credential.getSelectedAccountName())
                    .setFields("messages(id)")
                    .setQ("in:inbox !is:chat")
                    .setIncludeSpamTrash(false);
            ListMessagesResponse response = mailList.execute();

            ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();
            Message message;
            ContentValues values;
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);
            MimeMessage mimeMessage;

            Boolean lock = false;
            for(Message msgID : response.getMessages()){
                message = gmail.users().messages().get(credential.getSelectedAccountName(),msgID.getId()).setFormat("raw").setFields("historyId,id,internalDate,raw,snippet").execute();
                try {
                    if (!lock) {
                        if (message != null){
                            if (message.getHistoryId() != null) {
                                Log.i(TAG,message.getHistoryId().toString());
                                sharedPreferences.edit().putString(Constants.PREFS_KEY_GMAIL_HISTORY_ID,message.getHistoryId().toString()).apply();
                                lock = true;
                            }
                        }

                    }
                    String address = "Unknown";
                    mimeMessage = new MimeMessage(session, new ByteArrayInputStream(Base64.decodeBase64(message.getRaw())));

                    if (mimeMessage.getFrom()[0] != null){
                        InternetAddress add;
                        add = new InternetAddress(mimeMessage.getFrom()[0].toString());

                        if (add.getPersonal() != null){
                            address = add.getPersonal();
                        } else if (add.getAddress() != null){
                            address = add.getAddress();
                        } else {
                            address = mimeMessage.getFrom()[0].toString();
                        }

                    }
                    Date date = new Date(message.getInternalDate());
                    batch.add(ContentProviderOperation.newInsert(IrisContentProvider.CONTENT_URI)
                            .withValue(IrisContentProvider.ID, message.getId())
                            .withValue(IrisContentProvider.HISTORYID, message.getHistoryId().toString())
                            .withValue(IrisContentProvider.INTERNALDATE, message.getInternalDate())
                            .withValue(IrisContentProvider.DATE, (new SimpleDateFormat("M/d/yy h:mm a", Locale.US).format(date)))
                            .withValue(IrisContentProvider.SNIPPET, message.getSnippet())
                            .withValue(IrisContentProvider.SUBJECT, mimeMessage.getSubject())
                            .withValue(IrisContentProvider.FROM, address)
                            .withValue(IrisContentProvider.BODY, message.getSnippet()).build());
                } catch (MessagingException e) {
                    Date date = new Date(message.getInternalDate());
                    e.printStackTrace();
                    batch.add(ContentProviderOperation.newInsert(IrisContentProvider.CONTENT_URI)
                            .withValue(IrisContentProvider.ID, message.getId())
                            .withValue(IrisContentProvider.HISTORYID, message.getHistoryId().toString())
                            .withValue(IrisContentProvider.INTERNALDATE, message.getInternalDate().toString())
                            .withValue(IrisContentProvider.DATE, (new SimpleDateFormat("M/d/yy h:mm a", Locale.US).format(date)))
                            .withValue(IrisContentProvider.SNIPPET, message.getSnippet())
                            .withValue(IrisContentProvider.SUBJECT, "ERROR GETTING SUBJECT")
                            .withValue(IrisContentProvider.FROM, "ERROR GETTING FROM ADDRESS")
                            .withValue(IrisContentProvider.BODY,"ERROR GETTING BODY").build());
                }
            }
            try {
                contentResolver.applyBatch(Constants.SYNC_AUTH, batch);
                contentResolver.notifyChange(
                        IrisContentProvider.CONTENT_URI, // URI where data was modified
                        null,                           // No local observer
                        false);
            } catch (RemoteException | OperationApplicationException e) {
                Log.e(TAG, "Error updating database: " + e.toString());
                syncResult.databaseError = true;
                return;
            }

        } catch (UserRecoverableAuthException e) {
            SyncUtils.permissionNotification(context, e.getIntent(), account.name);
        } catch (GoogleAuthException e) {
            Log.e(TAG, "GoogleAuthException", e);
        } catch (UserRecoverableAuthIOException e) {
            SyncUtils.permissionNotification(context, e.getIntent(), account.name);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }
        Log.i(TAG, "Synchronization complete");
    }
}
