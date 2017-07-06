package com.tsm.me.tp2_gps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHandler extends SQLiteOpenHelper {
    // Database Version
    private static final int DATABASE_VERSION = 4;

    // Database Name
    private static final String DATABASE_NAME = "DefaultRoutes";

    // Labels table name
    private static final String TABLE_ROUTES = "routes";

    // Labels Table Columns names
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_LAT = "latitude";
    private static final String KEY_LONG = "longitude";
    private static final String KEY_USER = "user";
    private static final String KEY_DATE = "date";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Category table create query
        String CREATE_CATEGORIES_TABLE = "CREATE TABLE " + TABLE_ROUTES + "("
                + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_NAME + " TEXT,"
                + KEY_LAT + " TEXT,"
                + KEY_LONG + " TEXT,"
                + KEY_USER + " INTEGER,"
                + KEY_DATE + " DATETIME)";
        db.execSQL(CREATE_CATEGORIES_TABLE);
    }

    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ROUTES);

        // Create tables again
        onCreate(db);
    }

    /**
     * Inserting new lable into lables table
     * */
    public void insertRoute(String name, String lat, String lng, int user, String date){
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_NAME, name);
        values.put(KEY_LAT, lat);
        values.put(KEY_LONG, lng);
        values.put(KEY_USER, user);
        values.put(KEY_DATE, date);

        System.out.println(values.toString());
        // Inserting Row
        db.insert(TABLE_ROUTES, null, values);
        db.close(); // Closing database connection
    }

    /**
     * Getting all labels
     * returns list of labels
     * */
    public Map<String,String> getAllRoutesInfo(){
        Map<String,String> info = new TreeMap<>();

        // Select All Query
        String selectQuery = "SELECT name,date FROM " + TABLE_ROUTES;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                info.put(cursor.getString(0),cursor.getString(1));
            } while (cursor.moveToNext());
        }

        // closing connection
        cursor.close();
        db.close();

        // returning labels
        return info;
    }
    /**
     * Getting all labels
     * returns list of labels
     * */
    public List<String> getAllRouteNames(){
        List<String> labels = new ArrayList<>();
        labels.add("Select path...");

        // Select All Query
        String selectQuery = "SELECT name FROM " + TABLE_ROUTES;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                labels.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }

        // closing connection
        cursor.close();
        db.close();

        // returning labels
        return labels;
    }

    /**
     * Getting all labels
     * returns list of labels
     * */
    public void updateRow(String name, String lat, String lng, String date){
        SQLiteDatabase db = this.getReadableDatabase();

        ContentValues newValues = new ContentValues();
        newValues.put("latitude", lat);
        newValues.put("longitude", lng);
        newValues.put("date", date);

        String[] args = new String[]{name};
        db.update(TABLE_ROUTES, newValues, "name=?", args);

        db.close();
    }

    public void deleteRow(String name){
        SQLiteDatabase db = this.getReadableDatabase();
        String whereClause = "name=?";
        String[] whereArgs = new String[] {name};
        db.delete(TABLE_ROUTES, whereClause, whereArgs);
    }
}