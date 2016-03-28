package edu.uvawise.iris.utils;

import android.content.Context;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.gmail.Gmail;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.isEmptyString;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;

import edu.uvawise.iris.MainActivity;

/**
 * Created by bws7w on 3/28/2016.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class GmailUtilsTest extends AndroidTestCase {

    @Rule
    public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void testGmailAccountCred(){
        Context context = mActivityRule.getActivity().getApplicationContext();
        GoogleAccountCredential cred;

        try {
            cred = GmailUtils.getGmailAccountCredential(context,"uvawisemcstablet1@gmail.com");
            assertThat(cred,isA(GoogleAccountCredential.class));
            assertThat(cred.getSelectedAccountName(),is("uvawisemcstablet1@gmail.com"));
        } catch (IOException | GoogleAuthException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testInitialGmailAccountCred(){
        Context context = mActivityRule.getActivity().getApplicationContext();
        GoogleAccountCredential cred;

        cred = GmailUtils.getInitialGmailAccountCredential(context);
        assertThat(cred,isA(GoogleAccountCredential.class));
    }

    @Test
    public void testGetToken(){
        Context context = mActivityRule.getActivity().getApplicationContext();
        String token;

        try {
            token = GmailUtils.getToken(context,"uvawisemcstablet1@gmail.com",null);
            assertThat(token,isA(String.class));
        } catch (IOException | GoogleAuthException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGetGmailService(){
        Context context = mActivityRule.getActivity().getApplicationContext();
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, Arrays.asList(GmailUtils.SCOPES));
        credential.setSelectedAccountName("uvawisemcstablet1@gmail.com");
        try {
            credential.getToken();
        } catch (IOException | GoogleAuthException e) {
            fail(e.getMessage());
        }
        Gmail service = GmailUtils.getGmailService(credential);
        assertThat(service,isA(Gmail.class));

    }
}
