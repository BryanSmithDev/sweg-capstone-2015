package edu.uvawise.iris.sync;


import android.util.Log;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


/**
 * Unit tests for the Gmail Account class
 */
public class GmailAccountTest {

    private static String userID = "testemail@google.com";
    private static String currHistoryID = "123456789";

    GmailAccount mTestAccount;


    /**
     * Test the userID being set using the constructor
     */
    @Test
    public void testConstructorUserID() {
        Log.d(GmailAccountTest.class.getSimpleName(), "Starting Constructor Test - UserID Test");
        GmailAccount testAccount = new GmailAccount(userID, "");
        assertThat(testAccount.getUserID(), is(userID));
    }


    /**
     * Test the history ID being set using the constructor
     */
    @Test
    public void testConstructorHistoryID() {
        GmailAccount testAccount = new GmailAccount("", currHistoryID);
        assertThat(testAccount.getCurrHistoryID(), is(currHistoryID));
    }


    /**
     * Test the userID being set using the setter/getter
     */
    @Test
    public void testSetterUserID() {
        GmailAccount testAccount = new GmailAccount("", "");
        testAccount.setUserID(userID);
        assertThat(testAccount.getUserID(), is(userID));
    }


    /**
     * Test the history ID being set using the setter/getter
     */
    @Test
    public void testSetterHistoryID() {
        GmailAccount testAccount = new GmailAccount("", "");
        testAccount.setCurrHistoryID(currHistoryID);
        assertThat(testAccount.getCurrHistoryID(), is(currHistoryID));
    }

}




