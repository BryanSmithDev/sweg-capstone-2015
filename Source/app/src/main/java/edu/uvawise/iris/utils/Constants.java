package edu.uvawise.iris.utils;

/**
 * Frequently used Global constants are held here.
 */
public abstract class Constants {

    //Sync Authorities.
    public static final String ACCOUNT_TYPE = "com.google";
    public static final String SYNC_AUTH = "edu.uvawise.iris.sync";

    //Preference File Name
    public static final String PREFS_NAME = "Settings";

    //Shared Preference Keys
    public static final String PREFS_KEY_GMAIL_ACCOUNT_NAME = "GoogleAccount";
    public static final String PREFS_KEY_GMAIL_SYNCING = "GmailSyncing";
    public static final String PREFS_KEY_GMAIL_HISTORY_ID = "GmailHistoryID";

    //Preferences Default Values
    public static final boolean PREFS_SCREEN_ON_DEFAULT = false;
    public static final int PREFS_SYNC_FREQ_DEFAULT = 3;
    public static final String PREFS_GMAIL_HISTORY_ID_DEFAULT = null;

    public static final String INTENT_DATA_MESSAGES_ADDED = "IntentPassedData_MessagesAdded";
}
