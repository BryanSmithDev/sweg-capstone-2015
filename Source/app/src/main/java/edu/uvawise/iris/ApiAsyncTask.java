package edu.uvawise.iris;

import android.os.AsyncTask;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.model.Thread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An asynchronous task that handles the Gmail API call.
 * Placing the API calls in their own task ensures the UI stays responsive.
 */
public class ApiAsyncTask extends AsyncTask<Void, Void, Void> {
    private MainActivity mActivity;

    /**
     * Constructor.
     * @param activity MainActivity that spawned this task.
     */
    ApiAsyncTask(MainActivity activity) {
        this.mActivity = activity;
    }

    /**
     * Background task to call Gmail API.
     * @param params no parameters needed for this task.
     */
    @Override
    protected Void doInBackground(Void... params) {
        try {
            mActivity.updateResultsText(getDataFromApi());

        } catch (final GooglePlayServicesAvailabilityIOException availabilityException) {
            mActivity.showGooglePlayServicesAvailabilityErrorDialog(
                    availabilityException.getConnectionStatusCode());

        } catch (UserRecoverableAuthIOException userRecoverableException) {
            mActivity.startActivityForResult(
                    userRecoverableException.getIntent(),
                    MainActivity.REQUEST_AUTHORIZATION);

        } catch (Exception e) {
            Log.e("ERROR", "The following error occurred:\n" +
                    e.getMessage());
        }
        return null;
    }

    /**
     * Fetch a list of Gmail labels attached to the specified account.
     * @return List of Strings labels.
     * @throws IOException
     */
    private List<String> getDataFromApi() throws IOException {
        // Get the labels in the user's account.
        String user = "me";
        List<String> result = new ArrayList<>();
        List<String> result2 = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        Gmail.Users.Messages.List mailList =
                mActivity.mService.users().messages().list(user).setQ("in:inbox !is:chat")
                        .setIncludeSpamTrash(false);

        ListMessagesResponse response = mailList.execute();


        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                response = mailList.setPageToken(response.getNextPageToken()).execute();
            } else {
                break;
            }
        }

        for (Message message : messages) {
            result.add(message.getId());
        }

        Message msg;
        int i= 0;
        for (String id : result) {
            msg = mActivity.mService.users().messages().get("me",id).setFormat("full").execute();
            messages.set(i,msg);
            result2.add(msg.getPayload().getHeaders().get(5).getValue());
            i++;
        }
        return result2;
    }

}