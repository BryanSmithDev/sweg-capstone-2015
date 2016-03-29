package edu.uvawise.iris.sync;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.util.HashMap;


/**
 * This is content provider that manages the local copy of Gmail messages.
 */
public class IrisContentProvider extends ContentProvider {

    //Column Names
    public static final String ID = "_id";
    public static final String USER_ID = "usr_id";
    public static final String MESSAGE_ID = "msg_id";
    public static final String SNIPPET = "snippet";
    public static final String HISTORYID = "historyId";
    public static final String INTERNALDATE = "internalDate";
    public static final String DATE = "_date";
    public static final String SUBJECT = "subject";
    public static final String FROM = "_from";
    public static final String BODY = "body";
    public static final String ISREAD = "isRead";
    public static final String CURR_HIST_ID = "currHistID";
    public static final String USER_TOKEN = "userToken";
    //URI Column numbers
    static final int MESSAGES = 0;
    static final int ACCOUNTS = 1;
    static final String PROVIDER_NAME = "edu.uvawise.iris.sync";
    static final String MSG_URL = "content://" + PROVIDER_NAME + "/messages";
    public static final Uri MESSAGES_URI = Uri.parse(MSG_URL);
    static final String ACC_URL = "content://" + PROVIDER_NAME + "/accounts";
    public static final Uri ACCOUNT_URI = Uri.parse(ACC_URL);
    //Database info
    static final UriMatcher uriMatcher;
    static final String DATABASE_NAME = "Gmail";
    static final String MSG_TABLE_NAME = "gmailMessages";
    static final String ACC_TABLE_NAME = "gmailAccounts";
    static final int DATABASE_VERSION = 14;
    static final String CREATE_MSG_DB_TABLE =
            " CREATE TABLE " + MSG_TABLE_NAME + "(" +
                    " " + ID + "           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                    " " + USER_ID + "           VARCHAR(128)," +
                    " " + MESSAGE_ID + "           VARCHAR(32) NOT NULL," +
                    " " + SNIPPET + "      VARCHAR(128)," +
                    " " + HISTORYID + "    VARCHAR(32)  NOT NULL," +
                    " " + INTERNALDATE + " VARCHAR(32)  NOT NULL," +
                    " " + DATE + " VARCHAR(32)," +
                    " " + SUBJECT + "      VARCHAR(256)," +
                    " " + FROM + "         VARCHAR(256)," +
                    " " + BODY + "         VARCHAR(1024)," +
                    " " + ISREAD + "         BOOLEAN);";
    static final String CREATE_ACC_DB_TABLE =
            " CREATE TABLE " + ACC_TABLE_NAME + "(" +
                    " " + ID + "           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                    " " + USER_ID + "           VARCHAR(128) NOT NULL," +
                    " " + USER_TOKEN + "           VARCHAR(128)," +
                    " " + CURR_HIST_ID + "           VARCHAR(32) NOT NULL);";
    private final static String TAG = IrisContentProvider.class.getSimpleName(); //LOG TAG
    static HashMap<String, String> MESSAGES_PROJECTION_MAP;
    static HashMap<String, String> ACCOUNTS_PROJECTION_MAP;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, "messages", MESSAGES);
        uriMatcher.addURI(PROVIDER_NAME, "accounts", ACCOUNTS);
    }

    /**
     * Database specific constant declarations
     */
    private SQLiteDatabase db;


    /**
     * Ran when the content provider is created.
     *
     * @return True if the provider loaded successfully
     */
    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        /**
         * Create a write able database which will trigger its
         * creation if it doesn't already exist.
         */
        db = dbHelper.getWritableDatabase();
        return (db != null);
    }


    /**
     * Insert values into the database
     *
     * @param uri    URI to use
     * @param values Values to insert
     * @return URI with appened ID at the end.
     */
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {

        String tableName = "";
        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                tableName = MSG_TABLE_NAME;
                break;
            case ACCOUNTS:
                tableName = ACC_TABLE_NAME;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /**
         * Add a new record
         */
        long rowID = db.insertWithOnConflict(tableName, "", values, SQLiteDatabase.CONFLICT_REPLACE);
        /**
         * If record is added successfully
         */
        if (rowID != -1) {
            Uri _uri = ContentUris.withAppendedId(MESSAGES_URI, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);
            return _uri;
        }
        throw new SQLException("Failed to add a record into " + uri);

    }


    /**
     * Query the database
     *
     * @param uri           The URI to query. This will be the full URI sent by the client;
     *                      if the client is requesting a specific record, the URI will end in a record number
     *                      that the implementation should parse and add to a WHERE or HAVING clause, specifying
     *                      that _id value.
     * @param projection    The list of columns to put into the cursor. If
     *                      {@code null} all columns are included.
     * @param selection     A selection criteria to apply when filtering rows.
     *                      If {@code null} then all rows are included.
     * @param selectionArgs You may include ?s in selection, which will be replaced by
     *                      the values from selectionArgs, in order that they appear in the selection.
     *                      The values will be bound as Strings.
     * @param sortOrder     How the rows in the cursor should be sorted.
     *                      If {@code null} then the provider is free to define the sort order.
     * @return a Cursor or {@code null}.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();


        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                qb.setTables(MSG_TABLE_NAME);
                qb.setProjectionMap(MESSAGES_PROJECTION_MAP);
                if (sortOrder == null || sortOrder == "") {
                    sortOrder = INTERNALDATE + " DESC";
                }
                break;
            case ACCOUNTS:
                qb.setTables(ACC_TABLE_NAME);
                qb.setProjectionMap(ACCOUNTS_PROJECTION_MAP);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        /**
         * register to watch a content URI for changes
         */
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }


    /**
     * Delete row(s) from the database
     *
     * @param uri       The full URI to query, including a row ID (if a specific record is requested).
     * @param selection An optional restriction to apply to rows when deleting.
     * @return The number of rows affected.
     * @throws SQLException
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                count = db.delete(MSG_TABLE_NAME, selection, selectionArgs);
                break;
            case ACCOUNTS:
                count = db.delete(ACC_TABLE_NAME, selection, selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }


    /**
     * Update rows in the database
     *
     * @param uri       The URI to query. This can potentially have a record ID if this
     *                  is an update request for a specific record.
     * @param values    A set of column_name/value pairs to update in the database.
     *                  This must not be {@code null}.
     * @param selection An optional filter to match rows to update.
     * @return the number of rows affected.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;

        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                count = db.update(MSG_TABLE_NAME, values, selection, selectionArgs);
                break;
            case ACCOUNTS:
                count = db.update(ACC_TABLE_NAME, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }


    /**
     * Get MIME type for data
     *
     * @param uri the URI to query.
     * @return a MIME type string, or {@code null} if there is no type.
     */
    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                return "vnd.android.cursor.dir/vnd.iris.messages";
            case ACCOUNTS:
                return "vnd.android.cursor.dir/vnd.iris.accounts";

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }


    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_MSG_DB_TABLE);
            db.execSQL(CREATE_ACC_DB_TABLE);
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            dropDB(db);
        }


        public void dropDB(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + MSG_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ACC_TABLE_NAME);
            onCreate(db);
        }
    }
}