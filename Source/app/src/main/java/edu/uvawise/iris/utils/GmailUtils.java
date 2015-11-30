package edu.uvawise.iris.utils;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.uvawise.iris.sync.SyncUtils;

/**
 * Created by Bryan on 11/29/2015.
 */
public abstract class GmailUtils {

    private static final String[] SCOPES = {GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_MODIFY};
    private static String TAG = GmailUtils.class.getSimpleName();

    /**
     * Gets the Google Account Credential with Gmail scopes
     *
     * @param context     The context to run in.
     * @param accountName The name of the account to retrieve
     * @throws IOException
     * @throws GoogleAuthException
     */
    public static GoogleAccountCredential getGmailAccountCredential(Context context, String accountName) throws IOException, GoogleAuthException {
        return SyncUtils.getGoogleAccountCredential(context, accountName, Arrays.asList(SCOPES));
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

    public static String getGmailAccountName(Context context) {
        String account = null;
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        account = sharedPreferences.getString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, "");
        if (account.equals("")) Log.d(TAG, "Saved account name is null");
        return account;
    }

    public static void archiveMessages(final Context context, final Collection<String> IDs){
        removeLabelFromMessages(context,IDs,"INBOX");
    }

    public static void archiveMessage(final Context context, final String ID){
        archiveMessages(context,Collections.singletonList(ID));
    }

    public static void deleteMessages(final Context context, final Collection<String> IDs){
        if (IDs.size() < 1) return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Delete Thread Running");
                GoogleAccountCredential credential = null;
                try {
                    credential = GmailUtils.getGmailAccountCredential(context, GmailUtils.getGmailAccountName(context));

                    final Gmail gmail = GmailUtils.getGmailService(credential);
                    for (String id : IDs) {
                        Log.d(TAG, "Deleting from server: " + id);
                        gmail.users().messages().trash(GmailUtils.getGmailAccountName(context), id).execute();
                    }
                } catch (IOException | GoogleAuthException e) {
                    MiscUtils.runOnUiThread(context,new Runnable() {
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

    public static void deleteMessage(final Context context, final String ID){
        deleteMessages(context,Collections.singletonList(ID));
    }

    public static void removeLabelFromMessages(final Context context, final Collection<String> IDs, final String label){
        if (IDs.size() < 1) return;
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Label removing Thread Running");
                GoogleAccountCredential credential = null;
                try {
                    credential = getGmailAccountCredential(context, getGmailAccountName(context));

                    final Gmail gmail = GmailUtils.getGmailService(credential);
                    for (String id : IDs) {
                        Log.d(TAG, "Removing label "+label+" from " + id);
                        ModifyMessageRequest request = new ModifyMessageRequest();
                        request.setRemoveLabelIds(Collections.singletonList(label));
                        gmail.users().messages().modify(GmailUtils.getGmailAccountName(context), id, request).execute();
                    }
                } catch (IOException | GoogleAuthException e) {
                    MiscUtils.runOnUiThread(context,new Runnable() {
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

    public static void removeLabelFromMessage(final Context context, final String ID, final String label){
        removeLabelFromMessages(context,Collections.singletonList(ID),label);
    }
}
