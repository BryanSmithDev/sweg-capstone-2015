package edu.uvawise.iris;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import edu.uvawise.iris.service.IrisVoiceServiceTest;
import edu.uvawise.iris.sync.GmailAccountTest;
import edu.uvawise.iris.utils.GmailUtilsTest;
import edu.uvawise.iris.utils.PrefUtilsTest;

// Runs all unit tests.
@RunWith(Suite.class)
@Suite.SuiteClasses({GmailAccountTest.class,
        IrisVoiceServiceTest.class, PrefUtilsTest.class,GmailUtilsTest.class})
public class ApplicationTest {}