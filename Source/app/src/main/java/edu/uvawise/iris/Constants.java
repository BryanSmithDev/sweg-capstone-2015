package edu.uvawise.iris;

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

    //Preferences Default Values
    public static final boolean PREFS_KEY_SCREEN_ON_DEFAULT = false;
    public static final int PREFS_SYNC_FREQ_DEFAULT = 3;
}
