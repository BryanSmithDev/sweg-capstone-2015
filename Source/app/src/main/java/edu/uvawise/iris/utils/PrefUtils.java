/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uvawise.iris.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Handle the Shared Preferences of the application
 */
public class PrefUtils {

    private static final String TAG = PrefUtils.class.getSimpleName(); //LOG TAG

    //Preference File Name
    public static final String PREFS_NAME = "Settings";

    private PrefUtils() {}

    /**
     * Gets a SharedPreferences object from the application preferences file.
     * @param context the context
     * @return A shared preferences object loaded from the preferences file PREFS_NAME
     */
    public static SharedPreferences getSharedPreferences(Context context){
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets a preference key
     *
     * @param context the context
     * @param keyId the key id
     */
    public static String getKey(Context context, int keyId) {
        return context.getString(keyId);
    }

    /**
     * Gets a boolean preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param defaultValue the default value
     */
    public static boolean getBoolean(Context context, int keyId, boolean defaultValue) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(getKey(context, keyId), defaultValue);
    }

    /**
     * Sets a boolean preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param value the value
     */
    @SuppressLint("CommitPrefEdits")
    public static void setBoolean(Context context, int keyId, boolean value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getKey(context, keyId), value);
        editor.commit();
    }

    /**
     * Gets an integer preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param defaultValue the default value
     */
    public static int getInt(Context context, int keyId, int defaultValue) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(getKey(context, keyId), defaultValue);
    }

    /**
     * Sets an integer preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param value the value
     */
    @SuppressLint("CommitPrefEdits")
    public static void setInt(Context context, int keyId, int value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getKey(context, keyId), value);
        editor.commit();
    }

    /**
     * Gets a float preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param defaultValue the default value
     */
    public static float getFloat(Context context, int keyId, float defaultValue) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getFloat(getKey(context, keyId), defaultValue);
    }

    /**
     * Sets a float preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param value the value
     */
    @SuppressLint("CommitPrefEdits")
    public static void setFloat(Context context, int keyId, float value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(getKey(context, keyId), value);
        editor.commit();
    }

    /**
     * Gets a long preference value.
     *
     * @param context the context
     * @param keyId the key id
     */
    public static long getLong(Context context, int keyId) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getLong(getKey(context, keyId), -1L);
    }

    /**
     * Sets a long preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param value the value
     */
    @SuppressLint("CommitPrefEdits")
    public static void setLong(Context context, int keyId, long value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(getKey(context, keyId), value);
        editor.commit();
    }

    /**
     * Gets a string preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param defaultValue default value
     */
    public static String getString(Context context, int keyId, String defaultValue) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(getKey(context, keyId), defaultValue);
    }

    /**
     * Sets a string preference value.
     *
     * @param context the context
     * @param keyId the key id
     * @param value the value
     */
    @SuppressLint("CommitPrefEdits")
    public static void setString(Context context, int keyId, String value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getKey(context, keyId), value);
        editor.commit();
    }

    /**
     * Clear all saved preferences.
     * @param context the context
     */
    @SuppressLint("CommitPrefEdits")
    public static void clear(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear().commit();
    }
}
