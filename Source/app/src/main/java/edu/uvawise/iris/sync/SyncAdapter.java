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
import android.content.Context;
import android.content.Intent;
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
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryLabelAdded;
import com.google.api.services.gmail.model.HistoryLabelRemoved;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.HistoryMessageDeleted;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import edu.uvawise.iris.service.IrisVoiceService;
import edu.uvawise.iris.utils.Constants;


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

    private List<String> newMessages = new ArrayList<>();

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

            try {
                if (canPartialSync(credential, sharedPreferences)) {
                    performPartialSync(credential, sharedPreferences);
                } else {
                    performFullSync(credential, sharedPreferences);
                }
            } catch (MessagingException e) {
                Log.e(TAG, "Error Syncing");
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Error Syncing");
            }


        } catch (UserRecoverableAuthException e) {
            SyncUtils.permissionNotification(context, e.getIntent(), account.name);
        } catch (GoogleAuthException e) {
            Log.e(TAG, "GoogleAuthException", e);
        } catch (UserRecoverableAuthIOException e) {
            SyncUtils.permissionNotification(context, e.getIntent(), account.name);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            syncResult.databaseError = true;
        }
        Log.i(TAG, "Synchronization complete");
    }

    private void performFullSync(GoogleAccountCredential credential, SharedPreferences sharedPreferences) throws MessagingException, IOException{

        final ContentResolver contentResolver = context.getContentResolver();

        contentResolver.delete(IrisContentProvider.MESSAGES_URI, null, null);

        Gmail.Users.Messages.List mailList = gmail.users().messages().list(credential.getSelectedAccountName())
                .setFields("messages(id,historyId)")
                .setQ("in:inbox !is:chat")
                .setIncludeSpamTrash(false);
        ListMessagesResponse response = mailList.execute();


        ArrayList<ContentProviderOperation> batch = new ArrayList<>();

        if (response.getMessages() == null) return;
        BigInteger newHistID = null;
        BigInteger temp = null;
        for(Message msgID : response.getMessages()){
            temp = addMessage(credential,sharedPreferences,msgID.getId(),batch);
            if (newHistID == null) newHistID = temp;
        }

        try {
            contentResolver.applyBatch(Constants.SYNC_AUTH, batch);
            contentResolver.notifyChange(
                    IrisContentProvider.MESSAGES_URI, // URI where data was modified
                    null,                           // No local observer
                    false);
            Log.i(TAG, "Setting HistoryID: " + newHistID.toString());
            sharedPreferences.edit().putString(Constants.PREFS_KEY_GMAIL_HISTORY_ID, newHistID.toString()).apply();
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
        }
    }

    private BigInteger getHistoryID(){
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String histString = sharedPreferences.getString(Constants.PREFS_KEY_GMAIL_HISTORY_ID, Constants.PREFS_GMAIL_HISTORY_ID_DEFAULT);
        BigInteger histID = null;
        if (histString != null) {
            histID = new BigInteger(histString);
        }
        return histID;
    }


    private ListHistoryResponse getHistoryResponse(GoogleAccountCredential credential, BigInteger histID) throws IOException{
        return getHistoryResponse( credential,  histID, null);
    }


    private ListHistoryResponse getHistoryResponse(GoogleAccountCredential credential, BigInteger histID, String pageToken) throws IOException{
        Gmail.Users.History.List list;
        try {
            if (histID != null) {
                list = gmail.users().history().list(credential.getSelectedAccountName())
                        .setStartHistoryId(histID)
                        .setLabelId("INBOX")
                        .setFields("history(labelsAdded,labelsRemoved,messagesAdded,messagesDeleted),historyId,nextPageToken");
                if (pageToken != null) {
                    return list.setPageToken(pageToken).execute();
                } else {
                    return list.execute();
                }
            }
            return null;
        } catch (HttpResponseException e){
            if (e.getStatusCode() == 404){ Log.e(TAG,"ERROR 404-History ID was invalid or expired.");}
            e.printStackTrace();
            return null;
        }
    }

    private boolean canPartialSync(GoogleAccountCredential credential,SharedPreferences sharedPreferences) throws IOException{
        BigInteger histID = getHistoryID();
        if (histID == null) return false;
        try {
            ListHistoryResponse response = getHistoryResponse(credential,histID);
            if (response != null) {
                if (response.getNextPageToken() != null) {
                    //sharedPreferences.edit().putString(Constants.PREFS_KEY_GMAIL_HISTORY_ID, response.getHistoryId().toString()).apply();
                }
                return true;
            }
        } catch (HttpResponseException e){
            return false;
        }
        return false;
    }

    private void performPartialSync(GoogleAccountCredential credential, SharedPreferences sharedPreferences) throws MessagingException, IOException{
        Log.i(TAG,"Performing Partial Sync");
        final ContentResolver contentResolver = context.getContentResolver();
        BigInteger histID = getHistoryID();

        if (histID == null){
            Log.e(TAG,"No history ID available. (Maybe invalid or haven't done a full sync)");
            return;
        }
        Log.i(TAG,"Using history ID: "+histID);
        ListHistoryResponse response = getHistoryResponse(credential,histID);
        if (response == null){
            Log.e(TAG,"No history response.");
            return;
        }

        BigInteger newHistID = getHistoryID();
        ArrayList<ContentProviderOperation> batch = new ArrayList<>();
        if (response.getHistory() != null){
            if ( !response.getHistory().isEmpty()) {


                ArrayList<Message> addedMessages = new ArrayList<>();
                ArrayList<Message> deletedMessages = new ArrayList<>();

                List<History> histories = new ArrayList<History>();
                while (response.getHistory() != null) {
                    histories.addAll(response.getHistory());
                    newHistID = response.getHistoryId();
                    if (response.getNextPageToken() != null) {
                        String pageToken = response.getNextPageToken();
                        response = getHistoryResponse(credential, histID, pageToken);
                    } else {
                        break;
                    }
                }

                for (History hist : histories) {
                    if (hist.getLabelsAdded() != null) {
                        for (HistoryLabelAdded msgA : hist.getLabelsAdded()) {
                            addedMessages.add(msgA.getMessage());
                        }
                    }
                    if (hist.getLabelsRemoved() != null) {
                        for (HistoryLabelRemoved msgD : hist.getLabelsRemoved()) {
                            deletedMessages.add(msgD.getMessage());
                        }
                    }
                    if (hist.getMessagesAdded() != null) {
                        for (HistoryMessageAdded msgA : hist.getMessagesAdded()) {
                            addedMessages.add(msgA.getMessage());
                        }
                    }
                    if (hist.getMessagesDeleted() != null) {
                        for (HistoryMessageDeleted msgD : hist.getMessagesDeleted()) {
                            deletedMessages.add(msgD.getMessage());
                        }
                    }
                }

                for (Message msgID : addedMessages) {
                    addMessage(credential, sharedPreferences, msgID.getId(), batch);
                }

                for (Message msgID : deletedMessages) {
                    deleteMessage(contentResolver, msgID.getId(), batch);
                }

                if (IrisVoiceService.isRunning() && newMessages != null && !newMessages.isEmpty()) {
                    Log.d(TAG,"Putting new messages in the intent.");
                    Intent serviceIntent = new Intent(context, IrisVoiceService.class);
                    String[] stockArr = new String[newMessages.size()];
                    stockArr = newMessages.toArray(stockArr);
                    serviceIntent.putExtra(Constants.INTENT_DATA_MESSAGES_ADDED,stockArr);
                    context.startService(serviceIntent);
                    newMessages.clear();
                }
            }
        }

        try {

            contentResolver.applyBatch(Constants.SYNC_AUTH, batch);
            contentResolver.notifyChange(
                    IrisContentProvider.MESSAGES_URI, // URI where data was modified
                    null,                           // No local observer
                    false);

            Log.i(TAG, "Setting HistoryID: " + newHistID.toString());
            sharedPreferences.edit().putString(Constants.PREFS_KEY_GMAIL_HISTORY_ID, newHistID.toString()).apply();
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
        }

    }


    private BigInteger addMessage(GoogleAccountCredential credential, SharedPreferences sharedPreferences,String msgID,ArrayList<ContentProviderOperation> batch) throws IOException, MessagingException{
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage mimeMessage;
        Message message;

        try {
            message = gmail.users().messages().get(credential.getSelectedAccountName(), msgID).setFormat("raw").setFields("historyId,id,internalDate,labelIds,raw,snippet").execute();
        } catch (GoogleJsonResponseException e) {
            Log.d(TAG,"Message no longer exists: "+msgID);
            return null;
        }
        if (message == null) return null;

        for (String labelID : message.getLabelIds()){
            if (labelID.equals("CHAT")) return null;
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
        batch.add(ContentProviderOperation.newInsert(IrisContentProvider.MESSAGES_URI)
                .withValue(IrisContentProvider.ID, message.getId())
                .withValue(IrisContentProvider.HISTORYID, message.getHistoryId().toString())
                .withValue(IrisContentProvider.INTERNALDATE, message.getInternalDate())
                .withValue(IrisContentProvider.DATE, (new SimpleDateFormat("M/d/yy h:mm a", Locale.US).format(date)))
                .withValue(IrisContentProvider.SNIPPET, message.getSnippet())
                .withValue(IrisContentProvider.SUBJECT, mimeMessage.getSubject())
                .withValue(IrisContentProvider.FROM, address)
                .withValue(IrisContentProvider.BODY, message.getSnippet()).build());

        newMessages.add(message.getFactory().toPrettyString(message));

        return message.getHistoryId();

    }

    private boolean deleteMessage(ContentResolver contentResolver, String msgID, ArrayList<ContentProviderOperation> batch) {
        batch.add(ContentProviderOperation.newDelete(IrisContentProvider.MESSAGES_URI).withSelection(IrisContentProvider.ID + "=?", new String[]{msgID}).build());
        return true;
    }



}
