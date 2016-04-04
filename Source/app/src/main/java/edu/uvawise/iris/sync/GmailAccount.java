package edu.uvawise.iris.sync;

/**
 * Gmail Account object that will store information about a Gmail Account for easy transport.
 */
public class GmailAccount {
    private String userID;
    private String currHistoryID;


    public GmailAccount(String ID, String histID) {
        setUserID(ID);
        setCurrHistoryID(histID);
    }


    public String getUserID() {
        return userID;
    }


    public void setUserID(String userID) {
        this.userID = userID;
    }


    public String getCurrHistoryID() {
        return currHistoryID;
    }


    public void setCurrHistoryID(String currHistoryID) {
        this.currHistoryID = currHistoryID;
    }


}