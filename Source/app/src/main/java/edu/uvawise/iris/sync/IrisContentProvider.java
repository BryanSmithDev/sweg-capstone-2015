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
import android.text.TextUtils;

import java.util.HashMap;


/**
 * This is content provider that manages the local copy of gmail messages.
 */
public class IrisContentProvider extends ContentProvider {

    private final static String TAG = IrisContentProvider.class.getSimpleName();


    static HashMap<String,String> MESSAGES_PROJECTION_MAP;

    //Column Names
    public static final String ID = "_id";
    public static final String SNIPPET = "snippet";
    public static final String HISTORYID = "historyId";
    public static final String INTERNALDATE = "internalDate";
    public static final String DATE = "_date";
    public static final String SUBJECT = "subject";
    public static final String FROM = "_from";
    public static final String BODY = "body";
    public static final String ISREAD = "isRead";

    static final String[] MESSAGE_PROJECTION = new String[] {
            ID,
            SNIPPET,
            HISTORYID,
            INTERNALDATE,
            DATE,
            SUBJECT,
            FROM,
            BODY,
            ISREAD
    };

    //URI Column numbers
    static final int MESSAGES = 1;
    static final int MSG_ID = 2;
    static final int MSG_SNIPPET = 3;
    static final int MSG_HISTORYID = 4;
    static final int MSG_INTERNALDATE = 5;
    static final int MSG_DATE = 6;
    static final int MSG_SUBJECT = 7;
    static final int MSG_FROM = 8;
    static final int MSG_BODY = 9;
    static final int MSG_ISREAD = 10;

    static final String PROVIDER_NAME = "edu.uvawise.iris.sync";
    static final String URL = "content://" + PROVIDER_NAME + "/messages";
    public static final Uri MESSAGES_URI = Uri.parse(URL);
    public static final Uri MESSAGE_ID_URI = Uri.parse(URL+"/"+ID+"/"+MSG_ID);
    public static final Uri MESSAGE_SNIPPET_URI = Uri.parse(URL+"/"+SNIPPET+"/"+MSG_SNIPPET);
    public static final Uri MESSAGE_HISTORYID_URI = Uri.parse(URL+"/"+HISTORYID+"/"+MSG_HISTORYID);
    public static final Uri MESSAGE_INTERNALDATE_URI = Uri.parse(URL+"/"+INTERNALDATE+"/"+MSG_INTERNALDATE);
    public static final Uri MESSAGE_DATE_URI = Uri.parse(URL+"/"+DATE+"/"+MSG_DATE);
    public static final Uri MESSAGE_SUBJECT_URI = Uri.parse(URL+"/"+SUBJECT+"/"+MSG_SUBJECT);
    public static final Uri MESSAGE_FROM_URI = Uri.parse(URL+"/"+FROM+"/"+MSG_FROM);
    public static final Uri MESSAGE_BODY_URI = Uri.parse(URL+"/"+BODY+"/"+MSG_BODY);
    public static final Uri MESSAGE_ISREAD_URI = Uri.parse(URL+"/"+ISREAD+"/"+MSG_ISREAD);


    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, "messages", MESSAGES);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+ID+"/#", MSG_ID);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+SNIPPET+"/#", MSG_SNIPPET);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+HISTORYID+"/#", MSG_HISTORYID);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+INTERNALDATE+"/#", MSG_INTERNALDATE);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+DATE+"/#", MSG_DATE);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+SUBJECT+"/#", MSG_SUBJECT);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+FROM+"/#", MSG_FROM);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+BODY+"/#", MSG_BODY);
        uriMatcher.addURI(PROVIDER_NAME, "messages/"+ISREAD+"/#", MSG_ISREAD);
    }

    /**
     * Database specific constant declarations
     */
    private SQLiteDatabase db;
    static final String DATABASE_NAME = "Gmail";
    static final String TABLE_NAME = "gmailMessages";
    static final int DATABASE_VERSION = 7;
    static final String CREATE_DB_TABLE =
            " CREATE TABLE " + TABLE_NAME + "("+
            " "+ID+"           VARCHAR(32) NOT NULL PRIMARY KEY,"+
            " "+SNIPPET+"      VARCHAR(128),"+
            " "+HISTORYID+"    VARCHAR(32)  NOT NULL,"+
            " "+INTERNALDATE+" VARCHAR(32)  NOT NULL,"+
            " "+DATE+" VARCHAR(32),"+
            " "+SUBJECT+"      VARCHAR(256)," +
            " "+FROM+"         VARCHAR(256)," +
            " "+BODY+"         VARCHAR(1024)," +
            " "+ISREAD+"         BOOLEAN);";

    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL(CREATE_DB_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            dropDB(db);
        }

        public void dropDB(SQLiteDatabase db){
            db.execSQL("DROP TABLE IF EXISTS " +  TABLE_NAME);
            onCreate(db);
        }
    }

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

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {

        /**
         * Add a new record
         */
        long rowID = db.insertWithOnConflict(TABLE_NAME, "", values, SQLiteDatabase.CONFLICT_REPLACE);


        /**
         * If record is added successfully
         */
        if (rowID != -1)
        {
            Uri _uri = ContentUris.withAppendedId(MESSAGES_URI, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);
            return _uri;
        }
        throw new SQLException("Failed to add a record into " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);

        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                qb.setProjectionMap(MESSAGES_PROJECTION_MAP);
                break;

            case MSG_ID:
                qb.appendWhere(ID + "=" + uri.getPathSegments().get(1));
                break;

            case MSG_HISTORYID:
                qb.appendWhere( HISTORYID + "=" + uri.getPathSegments().get(1));
                break;

            case MSG_INTERNALDATE:
                qb.appendWhere( INTERNALDATE + "=" + uri.getPathSegments().get(1));
                break;

            case MSG_DATE:
                qb.appendWhere( DATE + "=" + uri.getPathSegments().get(1));
                break;

            case MSG_FROM:
                qb.appendWhere( FROM + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (sortOrder == null || sortOrder == ""){
            /**
             * By default sort on date
             */
            sortOrder = INTERNALDATE+" DESC";
        }
        Cursor c = qb.query(db,	projection,	selection, selectionArgs,null, null, sortOrder);

        /**
         * register to watch a content URI for changes
         */
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        switch (uriMatcher.match(uri)){
            case MESSAGES:
                count = db.delete(TABLE_NAME, selection, selectionArgs);
                break;

            case MSG_ID:
                String id = uri.getPathSegments().get(1);
                count = db.delete( TABLE_NAME, ID +  " = " + id +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            case MSG_HISTORYID:
                String hID = uri.getPathSegments().get(1);
                count = db.delete( TABLE_NAME, HISTORYID +  " = " + hID +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            case MSG_INTERNALDATE:
                String intDate = uri.getPathSegments().get(1);
                count = db.delete( TABLE_NAME, INTERNALDATE +  " = " + intDate +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            case MSG_DATE:
                String date = uri.getPathSegments().get(1);
                count = db.delete( TABLE_NAME, DATE +  " = " + date +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            case MSG_FROM:
                String from = uri.getPathSegments().get(1);
                count = db.delete( TABLE_NAME, FROM +  " = " + from +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;

        switch (uriMatcher.match(uri)){
            case MESSAGES:
                count = db.update(TABLE_NAME, values, selection, selectionArgs);
                break;

            case MSG_ID:
                count = db.update(TABLE_NAME, values, ID + " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;

            case MSG_HISTORYID:
                count = db.update(TABLE_NAME, values, HISTORYID + " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;

            case MSG_INTERNALDATE:
                count = db.update(TABLE_NAME, values, INTERNALDATE + " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;

            case MSG_DATE:
                count = db.update(TABLE_NAME, values, DATE + " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;

            case MSG_FROM:
                count = db.update(TABLE_NAME, values, FROM + " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri );
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)){
            /**
             * Get all message records
             */
            case MESSAGES:
                return "vnd.android.cursor.dir/vnd.iris.messages";

            /**
             * Get a particular message
             */
            case MSG_ID:
            case MSG_HISTORYID:
            case MSG_INTERNALDATE:
            case MSG_DATE:
            case MSG_FROM:
                return "vnd.android.cursor.item/vnd.iris.messages";

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }
}