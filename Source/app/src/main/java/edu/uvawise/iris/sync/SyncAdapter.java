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
import edu.uvawise.iris.utils.GmailUtils;


/**
 * Gmail Sync Adapter
 * <p/>
 * <p>This class is instantiated in {@link SyncService}, which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 * <p/>
 * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
class SyncAdapter extends AbstractThreadedSyncAdapter {

    public static final String TAG = SyncAdapter.class.getSimpleName(); //Tag used in logging

    private Context context;    //Store the current context
    private Gmail gmail;        //The Gmail API service

    private List<String> newMessages = new ArrayList<>();
    private List<String> newMessageAccounts = new ArrayList<>();


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
     * .
     * <p/>
     * <p>This is where we actually perform any work required to perform a sync.
     * {@link android.content.AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
     * so it is safe to peform blocking I/O here.
     * <p/>
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    @Override
    public void onPerformSync(final Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        Log.i(TAG, ".");
        Log.i(TAG, "-------------------------------------------------------");
        Log.i(TAG, "Beginning synchronization for: " + account.name);
        Log.i(TAG, "-------------------------------------------------------");


        ArrayList<GmailAccount> accounts = GmailUtils.getGmailAccounts(context);

        if (accounts == null || accounts.isEmpty()) {
            Log.e(TAG, "Can't sync. No accounts.");
            return;
        }

        boolean loggedIn = false;
        for (GmailAccount gmailAccount : accounts) {
            if (gmailAccount.getUserID().equals(account.name)) {
                loggedIn = true;
                break;
            }
        }


        GoogleAccountCredential credential;
        if (loggedIn) {
            Log.d(TAG, "" + account.name);
            try {
                credential = GmailUtils.getGmailAccountCredential(context, account.name);
                if (credential == null) {
                    Log.d(TAG, "Credential Null");
                    return;
                }

                gmail = GmailUtils.getGmailService(credential);

                Log.d(TAG, "Sync Running for:" + credential.getSelectedAccountName());
                Log.d(TAG, "Cred Name:" + credential.getSelectedAccountName());

                try {
                    if (canPartialSync(credential)) {
                        performPartialSync(credential);
                    } else {
                        performFullSync(credential);
                    }
                } catch (MessagingException | IOException e) {
                    Log.e(TAG, "Error Syncing");
                }


            } catch (UserRecoverableAuthException e) {
                GmailUtils.permissionNotification(context, e.getIntent(), account.name);
            } catch (GoogleAuthException e) {
                Log.e(TAG, "GoogleAuthException", e);
            } catch (UserRecoverableAuthIOException e) {
                GmailUtils.permissionNotification(context, e.getIntent(), account.name);
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
                syncResult.databaseError = true;
            } finally {
                credential = null;
            }
            Log.i(TAG, "Synchronization complete for:" + account.name);
            Log.i(TAG, "-------------------------------------------------------");
            Log.i(TAG, ".");
        }

    }


    /**
     * We have never synced or can't use the current history ID. We need to do a full sync with
     * the server.
     *
     * @param credential Google crediential to use to sync with.
     * @throws MessagingException
     * @throws IOException
     */
    private void performFullSync(GoogleAccountCredential credential) throws MessagingException, IOException {
        Log.i(TAG, "Performing Full Sync");
        final ContentResolver contentResolver = context.getContentResolver();
        String userID = credential.getSelectedAccountName();

        contentResolver.delete(IrisContentProvider.MESSAGES_URI, IrisContentProvider.USER_ID + " = ?", new String[]{userID});

        Gmail.Users.Messages.List mailList = gmail.users().messages().list("me")
                .setFields("messages(id,historyId)")
                .setQ("in:inbox !is:chat")
                .setIncludeSpamTrash(false);
        ListMessagesResponse response = mailList.execute();


        ArrayList<ContentProviderOperation> batch = new ArrayList<>();

        if (response.getMessages() == null) return;
        BigInteger newHistID = null;
        BigInteger temp = null;
        for (Message msgID : response.getMessages()) {
            temp = addMessage(credential, msgID.getId(), batch);
            if (newHistID == null) newHistID = temp;
        }

        try {
            contentResolver.applyBatch(SyncUtils.SYNC_AUTH, batch);
            contentResolver.notifyChange(
                    IrisContentProvider.MESSAGES_URI, // URI where data was modified
                    null,                           // No local observer
                    false);
            GmailUtils.setCurrentHistoryID(context, userID, newHistID);
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
        }
    }


    /**
     * Get the history list of the specified history ID
     *
     * @param credential The Google Credential to sync with
     * @param histID     The history ID to get the list of.
     * @return the history list response object.
     * @throws IOException
     */
    private ListHistoryResponse getHistoryResponse(GoogleAccountCredential credential, BigInteger histID) throws IOException {
        return getHistoryResponse(credential, histID, null);
    }


    /**
     * Get the history list of the specified history ID
     *
     * @param credential The Google Credential to sync with
     * @param histID     The history ID to get the list of.
     * @return the history list response object.
     * @throws IOException
     */
    private ListHistoryResponse getHistoryResponse(GoogleAccountCredential credential, BigInteger histID, String pageToken) throws IOException {
        Gmail.Users.History.List list;
        try {
            if (histID != null) {
                list = gmail.users().history().list("me")
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
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 404) {
                Log.e(TAG, "ERROR 404-History ID was invalid or expired.");
            }
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Determine if we can do a partial sync or not.
     *
     * @param credential The Google Credential to use to sync.
     * @return True if we can use partial sync. False otherwise.
     * @throws IOException
     */
    private boolean canPartialSync(GoogleAccountCredential credential) throws IOException {
        BigInteger histID = GmailUtils.getCurrentHistoryID(context, credential.getSelectedAccountName());
        if (histID == null) return false;
        try {
            ListHistoryResponse response = getHistoryResponse(credential, histID);
            if (response != null) {
                return true;
            }
        } catch (HttpResponseException e) {
            return false;
        }
        return false;
    }


    /**
     * Perform a partial sync with the Gmail server.
     *
     * @param credential The Google Credential to sync with.
     * @throws MessagingException
     * @throws IOException
     */
    private void performPartialSync(GoogleAccountCredential credential) throws MessagingException, IOException {
        Log.i(TAG, "Performing Partial Sync");
        final ContentResolver contentResolver = context.getContentResolver();
        String userID = credential.getSelectedAccountName();
        BigInteger histID = GmailUtils.getCurrentHistoryID(context, userID);

        if (histID == null) {
            Log.e(TAG, "No history ID available. (Maybe invalid or haven't done a full sync)");
            return;
        }
        Log.i(TAG, "Using history ID: " + histID);
        ListHistoryResponse response = getHistoryResponse(credential, histID);
        if (response == null) {
            Log.e(TAG, "No history response.");
            return;
        }

        BigInteger newHistID = GmailUtils.getCurrentHistoryID(context, userID);
        ArrayList<ContentProviderOperation> batch = new ArrayList<>();
        if (response.getHistory() != null) {
            if (!response.getHistory().isEmpty()) {


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
                    try {
                        addMessage(credential, msgID.getId(), batch);
                    } catch(IOException | MessagingException | NullPointerException e){
                        Log.e(TAG,"Error adding message. "+e.getMessage());
                    }
                }

                for (Message msgID : deletedMessages) {
                    try{
                        deleteMessage(msgID.getId(), batch);
                    } catch(NullPointerException e){
                        Log.e(TAG,"Error deleting message. "+e.getMessage());
                    }
                }

                GmailUtils.setCurrentHistoryID(context, userID, newHistID);
                if (IrisVoiceService.isRunning() && newMessages != null && !newMessages.isEmpty()) {
                    Log.d(TAG, "Putting new messages in the intent.");
                    Intent serviceIntent = new Intent(context, IrisVoiceService.class);
                    String[] stockArr = new String[newMessages.size()];
                    String[] stockArr2 = new String[newMessageAccounts.size()];
                    stockArr = newMessages.toArray(stockArr);
                    serviceIntent.putExtra(IrisVoiceService.INTENT_DATA_MESSAGES_ADDED, newMessages.toArray(stockArr));
                    stockArr2 = newMessageAccounts.toArray(stockArr2);
                    serviceIntent.putExtra(IrisVoiceService.INTENT_DATA_MESSAGE_ACCOUNTS, newMessageAccounts.toArray(stockArr2));
                    context.startService(serviceIntent);
                    newMessages.clear();
                    newMessageAccounts.clear();
                }
            }
        }

        try {

            contentResolver.applyBatch(SyncUtils.SYNC_AUTH, batch);
            contentResolver.notifyChange(
                    IrisContentProvider.MESSAGES_URI, // URI where data was modified
                    null,                           // No local observer
                    false);


            GmailUtils.setCurrentHistoryID(context, userID, newHistID);
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Error updating database: " + e.toString());
        }

    }


    /**
     * Get the message with the specified ID from the Gmail server and add it to the database
     * batch operations.
     *
     * @param credential The Google Credential to sync with
     * @param msgID      The message ID to add.
     * @param batch      The array that is holding the database batch operations.
     * @return Returns the history ID of the message added.
     * @throws IOException
     * @throws MessagingException
     */
    private BigInteger addMessage(GoogleAccountCredential credential, String msgID, ArrayList<ContentProviderOperation> batch) throws IOException, MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage mimeMessage;
        Message message;

        try {
            message = gmail.users().messages().get("me", msgID).setFormat("raw").setFields("historyId,id,internalDate,labelIds,raw,snippet").execute();
        } catch (GoogleJsonResponseException e) {
            Log.d(TAG, "Message no longer exists: " + msgID + " - " + e.getMessage());
            return null;
        }
        if (message == null) return null;

        for (String labelID : message.getLabelIds()) {
            if (labelID.equals("CHAT")) return null;
        }

        String address = "Unknown";
        mimeMessage = new MimeMessage(session, new ByteArrayInputStream(Base64.decodeBase64(message.getRaw())));

        if (mimeMessage.getFrom()[0] != null) {
            InternetAddress add;
            add = new InternetAddress(mimeMessage.getFrom()[0].toString());

            if (add.getPersonal() != null) {
                address = add.getPersonal();
            } else if (add.getAddress() != null) {
                address = add.getAddress();
            } else {
                address = mimeMessage.getFrom()[0].toString();
            }

        }

        Date date = new Date(message.getInternalDate());
        batch.add(ContentProviderOperation.newInsert(IrisContentProvider.MESSAGES_URI)
                .withValue(IrisContentProvider.MESSAGE_ID, message.getId())
                .withValue(IrisContentProvider.USER_ID, credential.getSelectedAccountName())
                .withValue(IrisContentProvider.HISTORYID, message.getHistoryId().toString())
                .withValue(IrisContentProvider.INTERNALDATE, message.getInternalDate())
                .withValue(IrisContentProvider.DATE, (new SimpleDateFormat("M/d/yy h:mm a", Locale.US).format(date)))
                .withValue(IrisContentProvider.SNIPPET, message.getSnippet())
                .withValue(IrisContentProvider.SUBJECT, mimeMessage.getSubject())
                .withValue(IrisContentProvider.FROM, address)
                .withValue(IrisContentProvider.BODY, message.getSnippet()).build());

        newMessages.add(message.getId());
        newMessageAccounts.add(credential.getSelectedAccountName());

        return message.getHistoryId();

    }

    //TODO: Check if need filter delete based on account ID


    /**
     * Add a delete message operation to the specified database batch.
     *
     * @param msgID The ID of the message to delete.
     * @param batch The array that is holding the database batch operations.
     * @return True
     */
    private boolean deleteMessage(String msgID, ArrayList<ContentProviderOperation> batch) {
        batch.add(ContentProviderOperation.newDelete(IrisContentProvider.MESSAGES_URI).withSelection(IrisContentProvider.MESSAGE_ID + "=?", new String[]{msgID}).build());
        return true;
    }


}
