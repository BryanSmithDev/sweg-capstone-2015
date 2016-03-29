package edu.uvawise.iris.sync;


import android.util.Log;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;



/**
 * Created by bws7w on 3/28/2016.
*/
public class GmailAccountTest {

    private static String userID = "testemail@google.com";
    private static String currHistoryID = "123456789";

    GmailAccount mTestAccount;

    @Test
    public void testConstructorUserID() {
        Log.d(GmailAccountTest.class.getSimpleName(),"Starting Constructor Test - UserID Test");
        GmailAccount testAccount = new GmailAccount(userID,"");
        assertThat(testAccount.getUserID(), is(userID));
    }

    @Test
    public void testConstructorHistoryID() {
        GmailAccount testAccount = new GmailAccount("",currHistoryID);
        assertThat(testAccount.getCurrHistoryID(), is(currHistoryID));
    }

    @Test
    public void testSetterUserID() {
        GmailAccount testAccount = new GmailAccount("","");
        testAccount.setUserID(userID);
        assertThat(testAccount.getUserID(), is(userID));
    }

    @Test
    public void testSetterHistoryID() {
        GmailAccount testAccount = new GmailAccount("","");
        testAccount.setCurrHistoryID(currHistoryID);
        assertThat(testAccount.getCurrHistoryID(), is(currHistoryID));
    }

}




