package edu.uvawise.iris;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ModifyMessageRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import edu.uvawise.iris.service.IrisVoiceService;
import edu.uvawise.iris.sync.IrisContentProvider;
import edu.uvawise.iris.sync.SyncUtils;
import edu.uvawise.iris.utils.Constants;

/**
 * MainActivity - The main activity for the application providing entry. Shows a list of emails.
 */
public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = MainActivity.class.getSimpleName();

    SimpleCursorAdapter mAdapter;

    // If non-null, this is the current filter the user has provided.
    String mCurFilter;

    GoogleAccountCredential credential; //Our Google(Gmail) account credential

    //Google Constants
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;

    SwipeRefreshLayout mSwipeRefreshLayout; //Swipe to refresh view
    ListView mListView;


    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); //Load main layout xml

        //Find and initialize the swipe-to-refresh view. And set its color scheme.
        mSwipeRefreshLayout = (SwipeRefreshLayout)findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.accent);

         /*
         * Sets up a SwipeRefreshLayout.OnRefreshListener that is invoked when the user
         * performs a swipe-to-refresh gesture.
         */
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        // This method performs the actual data-refresh operation.
                        // The method calls setRefreshing(false) when it's finished.
                        forceSync();
                    }
                }
        );


        //Setup an initial Google account.
        SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        credential = SyncUtils.getInitialGmailAccountCredential(this)
                .setSelectedAccountName(settings.getString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, null));

        if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_keep_screen_on_key), Constants.PREFS_SCREEN_ON_DEFAULT)){
            Log.d(TAG,"Keep Screen On Flag - On");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {Log.d(TAG, "Keep Screen On Flag - Off");}


        SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (key.equals(getString(R.string.pref_sync_freq_key))) {
                    Log.d(TAG, "Sync Frequency Changed");
                    if (SyncUtils.isSyncEnabled(getContext())) {
                        Log.d(TAG, "Sync Frequency was enabled. Re-enabling to use new freq");
                        SyncUtils.enableSync(getContext());
                    } else {
                        Log.d(TAG, "Sync wasn't enabled. No need to re-enable.");
                    }
                }

            }
        };
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(prefListener);

        mListView = (ListView)findViewById(R.id.emailList);
        mAdapter = new SimpleCursorAdapter(this,
                R.layout.list_email_item, null,
                new String[] {IrisContentProvider.SUBJECT,IrisContentProvider.FROM,IrisContentProvider.DATE},
                new int[] { R.id.subjectTextview, R.id.fromTextView, R.id.dateTextView }, 0);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(findViewById(R.id.empty_list_item));
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {


            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode mode, int position,
                                                  long id, boolean checked) {
                final int checkedCount = mListView.getCheckedItemCount();
                switch (checkedCount) {
                    case 0:
                        mode.setSubtitle(null);
                        break;
                    case 1:
                        mode.setSubtitle("One item selected");
                        break;
                    default:
                        mode.setSubtitle("" + checkedCount + " items selected");
                        break;
                }

            }

            String makePlaceholders(int len) {
                if (len < 1) {
                    // It will lead to an invalid query anyway ..
                    throw new RuntimeException("No placeholders");
                } else {
                    StringBuilder sb = new StringBuilder(len * 2 - 1);
                    sb.append("?");
                    for (int i = 1; i < len; i++) {
                        sb.append(",?");
                    }
                    return sb.toString();
                }
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
                // Respond to clicks on the actions in the CAB
                switch (item.getItemId()) {

                    case R.id.action_delete:
                        new Thread(new Runnable() { public void run() {
                            Log.d(TAG,"Thread Running");
                                    GoogleAccountCredential credential = null;
                                    try {
                                        credential = SyncUtils.getGmailAccountCredential(getContext(), SyncUtils.getGmailAccountName(getContext()));

                                        final Gmail gmail = SyncUtils.getGmailService(credential);
                                        for(String id : gatherSelections()) {
                                                Log.d(TAG,"Deleting from server: "+id);
                                                gmail.users().messages().trash(SyncUtils.getGmailAccountName(getContext()), id).execute();
                                        }
                                    } catch (IOException | GoogleAuthException e) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getContext(),"Error deleting message(s).",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        e.printStackTrace();
                                    }

                        } }).start();
                        return true;
                    case R.id.action_archive:
                        new Thread(new Runnable() { public void run() {
                            Log.d(TAG,"Thread Running");
                            GoogleAccountCredential credential = null;
                            try {
                                credential = SyncUtils.getGmailAccountCredential(getContext(), SyncUtils.getGmailAccountName(getContext()));

                                final Gmail gmail = SyncUtils.getGmailService(credential);
                                for(String id : gatherSelections()) {
                                    Log.d(TAG,"Archiving "+id);
                                    ModifyMessageRequest request = new ModifyMessageRequest();
                                    request.setRemoveLabelIds(Collections.singletonList("INBOX"));
                                    gmail.users().messages().modify(SyncUtils.getGmailAccountName(getContext()),id,request).execute();
                                }
                            } catch (IOException | GoogleAuthException e) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(),"Error archiving message(s).",Toast.LENGTH_SHORT).show();
                                    }
                                });
                                e.printStackTrace();
                            }

                        } }).start();
                        return true;
                    default:
                        return false;
                }
            }

            public ArrayList<String> gatherSelections(){
                final ContentResolver contentResolver = getContext().getContentResolver();
                ArrayList<ContentProviderOperation> batch = new ArrayList<>();
                final ArrayList<String> messageIDs = new ArrayList<>();
                String[] checkedIDStrings = new String[mListView.getCheckedItemIds().length];
                for(int i=0; i < mListView.getCheckedItemIds().length;i++){
                    Log.d(TAG,"Delete MESSAGE_ID: "+mListView.getCheckedItemIds()[i]);
                    batch.add(ContentProviderOperation.newDelete(IrisContentProvider.MESSAGES_URI).withSelection(IrisContentProvider.ID +" = ?",new String[]{mListView.getCheckedItemIds()[i]+""}).build());
                    checkedIDStrings[i] = ""+mListView.getCheckedItemIds()[i];
                    Log.d(TAG, "ID String: " + checkedIDStrings[i]);
                }
                try {

                    Cursor cur = contentResolver.query(
                            IrisContentProvider.MESSAGES_URI,
                            new String[]{IrisContentProvider.ID, IrisContentProvider.MESSAGE_ID},
                            IrisContentProvider.ID + " IN(" + makePlaceholders(checkedIDStrings.length) + ")",
                            checkedIDStrings,
                            null);

                    if (cur != null) {

                        while (cur.moveToNext()) {
                            Log.d(TAG, "Query: " + cur.getString(1));
                            messageIDs.add(cur.getString(1));
                        }

                        cur.close();
                    } else {
                        Log.d(TAG, "Null cursor");
                    }

                    ContentProviderResult[] result = contentResolver.applyBatch(Constants.SYNC_AUTH,batch);
                    Log.d(TAG,"Deleted: "+result.length);
                    contentResolver.notifyChange(
                            IrisContentProvider.MESSAGES_URI, // URI where data was modified
                            null,                           // No local observer
                            false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (OperationApplicationException e) {
                    e.printStackTrace();
                }
                return messageIDs;
            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
                // Inflate the menu for the CAB
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.context_menu, menu);
                mode.setTitle("Select Messages");
                return true;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
                // Here you can make any necessary updates to the activity when
                // the CAB is removed. By default, selected items are deselected/unchecked.
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
                // Here you can perform updates to the CAB due to
                // an invalidate() request
                return true;
            }
        });

        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getSupportLoaderManager().initLoader(0, null, this);
    }


    /**
     * Called whenever this activity is pushed to the foreground, such as after
     * a call to onCreate().
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (isGooglePlayServicesAvailable()) {
           refreshResults();
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    isGooglePlayServicesAvailable();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                Log.d(TAG, "Picking Account");
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        Log.d(TAG, "Account Picked - "+accountName );
                        credential.setSelectedAccountName(accountName);
                        SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(Constants.PREFS_KEY_GMAIL_ACCOUNT_NAME, accountName);
                        editor.apply();
                        SyncUtils.enableSync(this);
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this,R.string.error_no_account_chosen,Toast.LENGTH_LONG).show();
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode != RESULT_OK) {
                    Log.d(TAG, "Requesting Account Auth");
                    chooseAccount();
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (IrisVoiceService.isRunning()) {
            menu.findItem(R.id.action_service).setIcon(android.R.drawable.ic_media_pause);
        } else {
            menu.findItem(R.id.action_service).setIcon(android.R.drawable.ic_media_play);
        }
        return true;
    }

    /**
     * Handle Action Bar button selections
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_service:
                Intent serviceIntent = new Intent(this, IrisVoiceService.class);
                if (!IrisVoiceService.isRunning()){
                    item.setIcon(android.R.drawable.ic_media_pause);
                    startService(serviceIntent);
                } else {
                    item.setIcon(android.R.drawable.ic_media_play);
                    stopService(serviceIntent);
                }
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    private boolean hasPermission(){
        boolean result = ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_DENIED;
        if (result) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    0);
        } else {
            return true;
        }
        return false;
    }


    /**
     * Attempt to get a set of data from the Gmail API to display. If the
     * email address isn't known yet, then call chooseAccount() method so the
     * user can pick an account.
     */
    private void refreshResults() {
        if (hasPermission()){

            if (credential.getSelectedAccountName() == null) {
                chooseAccount();
            } else {
                if (!isDeviceOnline()) {
                    Toast.makeText(getApplicationContext(), "No network connection available.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Perform manual sync
     */
    private void forceSync(){
        if (hasPermission()) {
            if (credential.getSelectedAccountName() == null) {
                chooseAccount();
            } else {
                if (!isDeviceOnline()) {
                    Toast.makeText(getApplicationContext(), "No network connection available.", Toast.LENGTH_LONG).show();
                } else {
                    if (SyncUtils.isSyncEnabled(this)) {
                        mSwipeRefreshLayout.setRefreshing(true);
                        SyncUtils.syncNow(this);
                    } else {
                        Toast.makeText(getApplicationContext(), "Sync is disabled. Please enable it via the Android Settings Menu.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }


    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account.
     */
    private void chooseAccount() {
        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date. Will
     * launch an error dialog for the user to update Google Play Services if
     * possible.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS ) {
            return false;
        }
        return true;
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                        connectionStatusCode,
                        MainActivity.this,
                        REQUEST_GOOGLE_PLAY_SERVICES);
                dialog.show();
            }
        });
    }


    private Context getContext(){
        return this;
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created.  This
        // sample only has one Loader, so we don't care about the MESSAGE_ID.
        // First, pick the base URI to use depending on whether we are
        // currently filtering.
        Uri baseUri;
        if (mCurFilter != null) {
            baseUri = Uri.withAppendedPath(IrisContentProvider.MESSAGES_URI,
                    Uri.encode(mCurFilter));
        } else {
            baseUri = IrisContentProvider.MESSAGES_URI;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(true);
            }
        });

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(this, baseUri,
                new String[] {"_id",IrisContentProvider.SUBJECT,IrisContentProvider.FROM, IrisContentProvider.DATE}, null, null,
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

}
