package edu.uvawise.iris;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.ActionMenuItemView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import java.util.ArrayList;

import edu.uvawise.iris.service.IrisVoiceService;
import edu.uvawise.iris.sync.GmailAccount;
import edu.uvawise.iris.sync.IrisContentProvider;
import edu.uvawise.iris.sync.SyncUtils;
import edu.uvawise.iris.utils.AndroidUtils;
import edu.uvawise.iris.utils.GmailUtils;
import edu.uvawise.iris.utils.PrefUtils;

/**
 * MainActivity - The main activity for the application providing entry. Shows a list of emails.
 */
public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, ActionBar.OnNavigationListener {

    //Constants that define methods that can be called via launching this Activity via Intent
    public static final String METHOD_TO_CALL = "KEY_METHOD_TO_CALL";
    public static final int LOGOUT = 0;
    public static final int PAUSE_SERVICE = 1;
    public static final int ADDACCOUNT = 2;

    //Google Constants
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int MESSAGES_LOADER_ID = 0;
    static final int ACCOUNT_LOADER_ID = 1;
    private static final String TAG = MainActivity.class.getSimpleName(); //Log tag for this class
    GoogleAccountCredential credential; //Our Google(Gmail) account credential
    Toolbar mToolbar;
    SwipeRefreshLayout mSwipeRefreshLayout; //Swipe to refresh view
    ListView mListView;     //Our email list
    SimpleCursorAdapter mEmailListAdapter; //The adapter to manage data in our email list.
    SimpleCursorAdapter mAccountsAdapter; //The adapter to manage data in our email list.
    int mSelectedAccount = 0;
    String mSelectedAccountName = "";


    /**
     * Called when a new intent is passed to the activity.
     * We use it hear to get a value from the intent and run code based upon the value from the
     * intent. This allows us to logout the user when selected from the Settings activity. Or to
     * pause the background service when the pause button on the notification is pressed.
     *
     * @param intent The new intent that was passed to the activity.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        int value = -1;
        if (intent != null) {
            value = intent.getIntExtra(METHOD_TO_CALL, -1);
        }
        if (-1 != value) {
            switch (value) {
                case ADDACCOUNT:
                    chooseAccount();
                    break;
                case LOGOUT: //Logout current user & clear data
                    //Ask user if they are sure they want to logout
                    final int[] selectedIndex = new int[1];

                    ArrayList<GmailAccount> accounts = GmailUtils.getGmailAccounts(this);
                    if (accounts == null || accounts.isEmpty()) {
                        Toast.makeText(this, "", Toast.LENGTH_LONG).show();
                        return;

                    }
                    final String[] names = new String[accounts.size()];

                    int i = 0;
                    for (GmailAccount acc : accounts) {
                        names[i] = acc.getUserID();
                        i++;
                    }

                    AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
                    // Set the dialog title
                    builder1.setTitle("Pick an account to logout:")
                            .setSingleChoiceItems(names, -1, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    selectedIndex[0] = which;
                                }
                            })
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked OK, so save the mSelectedItems results somewhere
                                    // or return them to the component that opened the dialog
                                    Log.d(TAG, "Selected for logout: " + selectedIndex[0] + "  -  " + names[selectedIndex[0]]);
                                    getContentResolver().delete(IrisContentProvider.MESSAGES_URI, IrisContentProvider.USER_ID + " = ?", new String[]{names[selectedIndex[0]]});
                                    getContentResolver().delete(IrisContentProvider.ACCOUNT_URI, IrisContentProvider.USER_ID + " = ?", new String[]{names[selectedIndex[0]]});
                                    SyncUtils.disableSync(getApplicationContext(), names[selectedIndex[0]]);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {

                                }
                            });
                    builder1.show();
                    break;
                case PAUSE_SERVICE: //Stop service.
                    Intent serviceIntent = new Intent(this, IrisVoiceService.class);
                    stopService(serviceIntent);
                    SyncUtils.updateSyncFrequency(this, SyncUtils.getSyncFrequency(this));
                    ((ActionMenuItemView) findViewById(R.id.action_service)).
                            setIcon(getResources().getDrawable(android.R.drawable.ic_media_play));
                    break;

            }
        }
        super.onNewIntent(intent);
    }


    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); //Load main layout xml

        mToolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
        setSupportActionBar(mToolbar);

        //Find and initialize the swipe-to-refresh view. And set its color scheme.
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.accent);
        mSwipeRefreshLayout.setNestedScrollingEnabled(true);

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
        credential = GmailUtils.getInitialGmailAccountCredential(this)
                .setSelectedAccountName(
                        null);

        //Check to see if the user wants us to keep their screen on while the app is running.
        enforceScreenOnFlag();

        //Setup our Email List
        mListView = (ListView) findViewById(R.id.emailList);
        //Our cursor adapter to load data into the list and handle changes.
        mEmailListAdapter = new SimpleCursorAdapter(this,
                R.layout.list_email_item, null,
                new String[]{IrisContentProvider.SUBJECT,
                        IrisContentProvider.FROM,
                        IrisContentProvider.DATE},
                new int[]{R.id.subjectTextview, R.id.fromTextView, R.id.dateTextView}, 0);
        mListView.setAdapter(mEmailListAdapter); //Set the list to use the adapter above
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL); //Allow multiple lines to be selected.
        mListView.setEmptyView(findViewById(R.id.empty_list_item)); //This view will be shown when the list is empty

        //This is manages our context bar and actions when a list item is selected via long press.
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            /**
             * This method is called when the list selection changes. We use it to update the title
             * of the context bar to show the number of selections.
             * @param mode The current action mode.
             * @param position The position of item that changed its check state.
             * @param id The ID of the item that changed it checked state
             * @param checked If the item is checked or not.
             */
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


            /**
             * Generate a placeholder string (ex: ?,?,?)
             * @param len The number of placeholders
             * @return The generated placeholder string
             */
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


            /**
             * When a button on the context bar is pressed this method is called.
             * @param mode The current action mode.
             * @param item The item that was pressed.
             * @return True if the click was handled.
             */
            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
                // Respond to clicks on the actions in the CAB
                switch (item.getItemId()) {

                    case R.id.action_delete:
                        GmailUtils.deleteMessages(getContext(), mSelectedAccountName, gatherSelections());
                        return true;
                    case R.id.action_archive:
                        GmailUtils.archiveMessages(getContext(), mSelectedAccountName, gatherSelections());
                        return true;
                    default:
                        return false;
                }
            }


            /**
             * Gets all the currently selected items in the list. Then queries the local message
             * database to get all of the corresponding selections message IDs.
             * @return An array list of message ID strings.
             */
            public ArrayList<String> gatherSelections() {
                final ContentResolver contentResolver = getContext().getContentResolver();
                ArrayList<ContentProviderOperation> batch = new ArrayList<>();
                final ArrayList<String> messageIDs = new ArrayList<>();
                String[] checkedIDStrings = new String[mListView.getCheckedItemIds().length];
                for (int i = 0; i < mListView.getCheckedItemIds().length; i++) {
                    Log.d(TAG, "Delete MESSAGE_ID: " + mListView.getCheckedItemIds()[i]);
                    batch.add(ContentProviderOperation.newDelete(IrisContentProvider.MESSAGES_URI)
                            .withSelection(IrisContentProvider.ID + " = ?",
                                    new String[]{mListView.getCheckedItemIds()[i] + ""}).build());
                    checkedIDStrings[i] = "" + mListView.getCheckedItemIds()[i];
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

                    ContentProviderResult[] result = contentResolver.applyBatch(SyncUtils.SYNC_AUTH, batch);
                    Log.d(TAG, "Deleted: " + result.length);
                    contentResolver.notifyChange(
                            IrisContentProvider.MESSAGES_URI, // URI where data was modified
                            null,                           // No local observer
                            false);
                } catch (RemoteException | OperationApplicationException e) {
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


        mAccountsAdapter = new SimpleCursorAdapter(getSupportActionBar().getThemedContext(),
                android.R.layout.simple_dropdown_item_1line, null,
                new String[]{IrisContentProvider.USER_ID},
                new int[]{android.R.id.text1}, 0);


        // Action Bar
        ActionBar actions = getSupportActionBar();
        actions.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actions.setDisplayShowTitleEnabled(false);
        actions.setListNavigationCallbacks(mAccountsAdapter, this);

        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getSupportLoaderManager().initLoader(MESSAGES_LOADER_ID, null, this);
        getSupportLoaderManager().initLoader(ACCOUNT_LOADER_ID, null, this);
    }


    /**
     * Check to see if user wants to keep the screen on while the app is open. If so, add the
     * needed window flag.
     */
    private void enforceScreenOnFlag() {
        if (PrefUtils.getBoolean(this, R.string.pref_keep_screen_on_key, false)) {
            //If the user wants us to, we will set the flag to make it happen
            Log.d(TAG, "Keep Screen On Flag - On");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            //Otherwise, we won't keep the screen on.
            Log.d(TAG, "Keep Screen On Flag - Off");
        }
    }


    /**
     * Called whenever this activity is pushed to the foreground, such as after
     * a call to onCreate().
     */
    @Override
    protected void onResume() {
        super.onResume();
        enforceScreenOnFlag();
        if (isGooglePlayServicesAvailable()) {
            login();
        }
    }


    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
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
                        Log.d(TAG, "Account Picked - " + accountName);
                        credential.setSelectedAccountName(accountName);
                        ContentValues values = new ContentValues();
                        values.put(IrisContentProvider.USER_ID, accountName);
                        values.put(IrisContentProvider.CURR_HIST_ID, "0");
                        getContentResolver().insert(IrisContentProvider.ACCOUNT_URI, values);
                        getSupportLoaderManager().restartLoader(ACCOUNT_LOADER_ID, null, this);
                        SyncUtils.enableSync(this);
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, R.string.error_no_account_chosen, Toast.LENGTH_LONG).show();
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


    /**
     * This is called by the activity to create the menu for the activity's action bar.
     *
     * @param menu The menu that is to be created
     * @return True if the menu was created successfully.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        //Check if the background service is running.
        if (IrisVoiceService.isRunning()) {
            //It is so make the service button show up as a pause button.
            menu.findItem(R.id.action_service).setIcon(android.R.drawable.ic_media_pause);
        } else {
            //Its not so make the service button show up as a play/start button.
            menu.findItem(R.id.action_service).setIcon(android.R.drawable.ic_media_play);
        }
        return true;
    }


    /**
     * Called when an action bar menu item is clicked.
     *
     * @param item The item that was clicked
     * @return True if the click was handled.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings: //Settings Menu Item was clicked
                //Start the settings activity.
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_service: //Service button was clicked

                Intent serviceIntent = new Intent(this, IrisVoiceService.class);
                if (!IrisVoiceService.isRunning()) {
                    //We need to see if we have permissions for the overlay before we potentially
                    //display it.
                    if (!hasDrawOverAppsPermission()) return false;
                    item.setIcon(android.R.drawable.ic_media_pause);
                    SyncUtils.updateSyncFrequency(this, SyncUtils.getServiceSyncFrequency(this));
                    startService(serviceIntent); //Start background service
                } else {
                    //Its already running so we need to stop it with this click.
                    item.setIcon(android.R.drawable.ic_media_play);
                    SyncUtils.updateSyncFrequency(this, SyncUtils.getSyncFrequency(this));
                    stopService(serviceIntent);
                }
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    /**
     * Check to see if the app has permission to get accounts on the device. This is considered a dangerous
     * permission as of API 23 and requires us to ask for permission explicitly at runtime.
     *
     * @return True if we have permission.
     */
    private boolean hasGetAccountsPermission() {
        return AndroidUtils.hasPermission(this, Manifest.permission.GET_ACCOUNTS, this);
    }


    /**
     * Check to see if the app has permission to draw the overlay. This is considered a dangerous
     * permission as of API 23 and requires us to ask for permission explicitly at runtime.
     *
     * @return True if we have permission.
     */
    private boolean hasDrawOverAppsPermission() {
        return AndroidUtils.hasPermission(this, Manifest.permission.SYSTEM_ALERT_WINDOW, this);
    }


    /**
     * Attempt to get a set of data from the Gmail API to display. If the
     * email address isn't known yet, then call chooseAccount() method so the
     * user can pick an account.
     */
    private void login() {
        if (hasGetAccountsPermission()) {
            if (isDeviceOnline()) {
                ArrayList<GmailAccount> accs = GmailUtils.getGmailAccounts(this);
                if (accs == null || accs.isEmpty()) {
                    Log.d(TAG, "No accounts logged in. Pick an account.");
                    chooseAccount();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_no_connection, Toast.LENGTH_LONG).show();
            }
        }
    }


    /**
     * Perform manual sync
     */
    private void forceSync() {
        if (hasGetAccountsPermission()) {
            if (!isDeviceOnline()) {
                Toast.makeText(getApplicationContext(), R.string.error_no_connection, Toast.LENGTH_LONG).show();
            } else {
                String acc = GmailUtils.getGmailAccount(this, mSelectedAccount);
                if (acc == null) {
                    return;
                }
                if (SyncUtils.isSyncEnabled(this, acc)) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    SyncUtils.syncNow(this);
                } else {
                    Toast.makeText(getApplicationContext(), "Sync is disabled. Please enable it via the Android Settings Menu.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }


    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account to authenticate with.
     */
    private void chooseAccount() {
        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }


    /**
     * Checks whether the device currently has a network connection.
     *
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
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS) {
            return false;
        }
        return true;
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
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


    /**
     * This is a getter to retrieve the context of this activity. Useful for inner classes where the
     * activities getContext is not in-scope.
     *
     * @return Current Activity's context
     */
    private Context getContext() {
        return this;
    }


    /**
     * This is called when a new Loader needs to be created. The loader does heavy lifting for
     * checking our dataset in the background. If it has changed it hands off the cursor to our
     * lists adapter which then updates the list to show the new data.
     *
     * @param id   The ID to give the loader. (We only use one, so ID is not really used.)
     * @param args The args to pass to the created loader
     * @return A loader cursor
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created.
        switch (id) {
            case MESSAGES_LOADER_ID:
                Log.d(TAG, "Created message loader");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });

                // Now create and return a CursorLoader that will take care of
                // creating a Cursor for the data being displayed.

                String selection = null;
                String[] selectionArgs = null;
                String userID;
                if (args != null) {
                    userID = args.getString(IrisContentProvider.USER_ID);
                    selection = IrisContentProvider.USER_ID + " = ?";
                    selectionArgs = new String[]{userID};
                }


                return new CursorLoader(this, IrisContentProvider.MESSAGES_URI,
                        new String[]{"_id", IrisContentProvider.SUBJECT, IrisContentProvider.FROM, IrisContentProvider.DATE}, selection, selectionArgs,
                        null);
            case ACCOUNT_LOADER_ID:
                Log.d(TAG, "Created account loader");
                return new CursorLoader(this, IrisContentProvider.ACCOUNT_URI,
                        new String[]{"_id", IrisContentProvider.USER_ID, IrisContentProvider.USER_TOKEN, IrisContentProvider.CURR_HIST_ID}, null, null,
                        null);
            default:
                // An invalid id was passed in
                return null;
        }


    }


    /**
     * Called when a loader is done loading. Here is where we pass the new data to our list adapter.
     *
     * @param loader The loader that finished.
     * @param data   The new data that was found.
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        switch (loader.getId()) {
            case MESSAGES_LOADER_ID:
                mEmailListAdapter.swapCursor(data);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
                break;
            case ACCOUNT_LOADER_ID:
                mAccountsAdapter.swapCursor(data);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
                break;
        }
    }


    /**
     * This is called when the last Cursor provided to onLoadFinished() above is about to be closed.
     *
     * @param loader The loader to reset
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        //We need to make sure we are no longer using the loaders data in our adapter.
        switch (loader.getId()) {
            case MESSAGES_LOADER_ID:
                mEmailListAdapter.swapCursor(null);
                break;
            case ACCOUNT_LOADER_ID:
                mAccountsAdapter.swapCursor(null);
                break;
        }
    }


    /**
     * Load the messages for the currently selected gmail account
     * @param itemPosition The item index that was clicked in the dropdown
     * @param itemId The ID of the item clicked
     * @return false
     */
    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        mSelectedAccount = itemPosition;
        Bundle args = new Bundle();
        String userID = GmailUtils.getGmailAccount(this, itemPosition);
        mSelectedAccountName = userID;
        args.putString(IrisContentProvider.USER_ID, userID);
        getSupportLoaderManager().restartLoader(MESSAGES_LOADER_ID, args, this);
        return false;
    }
}
