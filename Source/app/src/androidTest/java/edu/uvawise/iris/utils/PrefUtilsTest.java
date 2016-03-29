package edu.uvawise.iris.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.uvawise.iris.MainActivity;
import edu.uvawise.iris.R;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class PrefUtilsTest  extends AndroidTestCase {

    @Rule
    public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void testGetPrefs() {
        Context context = mActivityRule.getActivity().getBaseContext();
        assertThat(PrefUtils.getSharedPreferences(context), isA(SharedPreferences.class));
    }

    @Test
    public void testGetKey() {
        Context context = mActivityRule.getActivity().getBaseContext();
        assertThat(PrefUtils.getKey(context,R.string.pref_key_gmail_account_name), is(any(String.class)));
    }

    @Test
    public void testString() {
        Context context = mActivityRule.getActivity().getBaseContext();
        PrefUtils.setString(context, R.string.pref_key_gmail_account_name,"TestString");
        assertThat(PrefUtils.getString(context,R.string.pref_key_gmail_account_name,null),is("TestString"));
    }

    @Test
    public void testBoolean() {
        Context context = mActivityRule.getActivity().getBaseContext();
        PrefUtils.setBoolean(context, R.string.pref_key_gmail_account_name,true);
        assertThat(PrefUtils.getBoolean(context,R.string.pref_key_gmail_account_name,false),is(true));
    }

    @Test
    public void testFloat() {
        Context context = mActivityRule.getActivity().getBaseContext();
        PrefUtils.setFloat(context, R.string.pref_key_gmail_account_name,2F);
        assertThat(PrefUtils.getFloat(context,R.string.pref_key_gmail_account_name,0F),is(2F));
    }

    @Test
    public void testLong() {
        Context context = mActivityRule.getActivity().getBaseContext();
        PrefUtils.setLong(context, R.string.pref_key_gmail_account_name,123L);
        assertThat(PrefUtils.getLong(context,R.string.pref_key_gmail_account_name),is(123L));
    }



}
