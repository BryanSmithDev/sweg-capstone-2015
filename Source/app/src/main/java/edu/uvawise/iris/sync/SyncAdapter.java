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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;

import java.io.IOException;


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

            Gmail.Users.Messages.List mailList = gmail.users().messages().list(credential.getSelectedAccountName()).setQ("in:inbox !is:chat")
                            .setIncludeSpamTrash(false);

            ListMessagesResponse response = mailList.execute();

            Log.d(TAG,response.getResultSizeEstimate()+"");

            // Get a handler that can be used to post to the main thread
            Handler mainHandler = new Handler(getContext().getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(),"Sync Ran - "+account.name,Toast.LENGTH_SHORT).show();
                }
            };
            mainHandler.post(myRunnable);

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
