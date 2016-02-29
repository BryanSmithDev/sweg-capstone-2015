package edu.uvawise.iris.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;

import android.os.Bundle;


import com.google.api.services.gmail.Gmail;

import java.util.ArrayList;

import edu.uvawise.iris.R;
import edu.uvawise.iris.utils.GmailUtils;
import edu.uvawise.iris.utils.PrefUtils;


/**
 * Static helper methods for working with the sync framework.
 */
public class SyncUtils {

    private static final String TAG = SyncUtils.class.getSimpleName();
    private static final int SYNC_FREQUENCY_DEFAULT = 3;

    //Sync Authorities.
    public static final String ACCOUNT_TYPE = "com.google";
    public static final String SYNC_AUTH = "edu.uvawise.iris.sync";


    /**
     * Syncs now for the current account.
     *
     * @param context The context to run in.
     */
    public static void syncNow(Context context) {
        Account[] accounts = AccountManager.get(context).getAccountsByType(SyncUtils.ACCOUNT_TYPE);
        ArrayList<GmailAccount> googleAccounts = GmailUtils.getGmailAccounts(context);
        Bundle b = new Bundle();
        // Disable sync backoff and ignore sync preferences. In other words...perform sync NOW!
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

        for (Account account : accounts) {
            for (GmailAccount gAccount : googleAccounts) {
                if (account.name.equals(gAccount.getUserID()))
                ContentResolver.requestSync(account, SyncUtils.SYNC_AUTH, b);
            }
        }
    }

    /**
     * Disables syncing.
     *
     * @param context The context to run in.
     */
    public static void disableSync(Context context) {
        GmailUtils.setIsSyncing(context,false);
        disableSyncForAll(context);

    }

    /**
     * Disables syncing for all accounts.
     *
     * @param context The context to run in.
     */
    private static void disableSyncForAll(Context context) {
        Account[] accounts = AccountManager.get(context).getAccountsByType(SyncUtils.ACCOUNT_TYPE);
        for (Account account : accounts) {
            ContentResolver.cancelSync(account, SyncUtils.SYNC_AUTH);
            ContentResolver.setIsSyncable(account, SyncUtils.SYNC_AUTH, 0);
            ContentResolver.setSyncAutomatically(account, SyncUtils.SYNC_AUTH, false);
        }
    }

    /**
     * Returns true if currently syncing
     *
     * @param context The context to run in.
     */
    public static boolean isSyncActive(Context context) {
        Account[] accounts = AccountManager.get(context).getAccountsByType(SyncUtils.ACCOUNT_TYPE);
        for (Account account : accounts) {
            if (ContentResolver.isSyncActive(account, SyncUtils.SYNC_AUTH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if sync is enabled.
     *
     * @param context The context to run in.
     */
    public static boolean isSyncEnabled(Context context) {
        Account[] am = AccountManager.get(context).getAccountsByType(SyncUtils.ACCOUNT_TYPE);
        if (!GmailUtils.isSyncing(context)) return false;
        for (Account account : am) {
           // if (GmailUtils.getGmailAccountName(context).equals(account.name)) {
                boolean isYourAccountSyncEnabled = ContentResolver.getSyncAutomatically(account, SyncUtils.SYNC_AUTH);
                boolean isMasterSyncEnabled = ContentResolver.getMasterSyncAutomatically();
                if (isMasterSyncEnabled && isYourAccountSyncEnabled) return true;
           // }
        }
        return false;
    }

    /**
     * Enables syncing.
     *
     * @param context The context to run in.
     */
    public static void enableSync(Context context) {
        GmailUtils.setIsSyncing(context,true);
        disableSyncForAll(context);
        ContentResolver.setMasterSyncAutomatically(true);

        // Enable sync for account
       // String googleAccount = GmailUtils.getGmailAccountName(context);
       ArrayList<GmailAccount> accounts = GmailUtils.getGmailAccounts(context);

        for (GmailAccount gmailAccount : accounts) {
            enableSyncForAccount(context, new Account(gmailAccount.getUserID(), SyncUtils.ACCOUNT_TYPE));
        }
    }

    /**
     * Enables syncing for an account.
     *
     * @param account The account to enable syncing for.
     */
    public static void enableSyncForAccount(Context context, Account account) {
        int min = getSyncFrequency(context);
        // Inform the system that this account supports sync
        ContentResolver.setIsSyncable(account, SyncUtils.SYNC_AUTH, 1);
        // Inform the system that this account is eligible for auto sync when the network is up
        ContentResolver.setSyncAutomatically(account, SyncUtils.SYNC_AUTH, true);
        // Recommend a schedule for automatic synchronization. The system may modify this based
        // on other scheduled syncs and network utilization.
        ContentResolver.addPeriodicSync(
                account, SyncUtils.SYNC_AUTH, new Bundle(), 60 * min);
    }

    /**
     * Gets the currently saved sync frequency from preferences
     * @param context The context to use to retrieve the preferences
     * @return Integer value saved for the sync frequency, or the default value.
     */
    private static int getSyncFrequency(Context context){
        return Integer.parseInt(PrefUtils.getString(context,R.string.pref_sync_freq_key,SYNC_FREQUENCY_DEFAULT+""));
    }

}
