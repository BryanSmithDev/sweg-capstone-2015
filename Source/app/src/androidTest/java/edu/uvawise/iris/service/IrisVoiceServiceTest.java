package edu.uvawise.iris.service;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.test.AndroidTestCase;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by bws7w on 3/28/2016.
 */
public class IrisVoiceServiceTest extends AndroidTestCase {
    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Test
    public void testIrisVoiceService() throws TimeoutException {
        // Create the service Intent.
        Intent serviceIntent =
                new Intent(InstrumentationRegistry.getTargetContext(), IrisVoiceService.class);

        // Data can be passed to the service via the Intent.
        serviceIntent.putExtra(IrisVoiceService.INTENT_DATA_MESSAGE_ACCOUNTS, new String[]{"testemail1@google.com","testemail2@google.com"});
        serviceIntent.putExtra(IrisVoiceService.INTENT_DATA_MESSAGES_ADDED, new String[]{"12345","6789"});

        // Bind the service and grab a reference to the binder.
        mServiceRule.startService(serviceIntent);


        // Verify that the service is working correctly.
        assertThat(IrisVoiceService.isRunning(), is(true));
    }
}
