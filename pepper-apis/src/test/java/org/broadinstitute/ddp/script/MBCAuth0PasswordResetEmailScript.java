package org.broadinstitute.ddp.script;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.Auth0Util;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
//Script to trigger pwd reset emails for migrated users during MBC study migration to pepper.
public class MBCAuth0PasswordResetEmailScript extends TxnAwareBaseTest {
    private static final Logger LOG = LoggerFactory.getLogger(MBCAuth0PasswordResetEmailScript.class);
    private static final String STUDY_GUID = "cmi-mbc";
    private static final String FROM_NAME = "MBCproject";
    private static final String FROM_EMAIL = "info@mbcproject.org";
    private static final String MESSAGE_SUBJECT = "Metastatic Breast Cancer Project update: Please choose a password for your account";
    private static final String SENDGRID_CONSENTED_TEMPLATE_ID = "fa576354-73b9-41fe-9cfc-ac370fc70556";
    private static final String SENDGRID_NOT_CONSENTED_TEMPLATE_ID = "cd3e9c60-9636-4685-84fd-c1aa3c7a1177";
    private static final String BASE_WEBPAGE_URL = "https:///mbc.dev.datadonationplatform.org/password-reset-done"; //WATCHOUT.. Its DEV

    private String[] consentedEmails = {};
    private String[] nonConsentedEmails = {};

    private Auth0ManagementClient mgmtClient;

    @Before
    public void setup() {
        TransactionWrapper.useTxn(handle -> {
            mgmtClient = Auth0Util.getManagementClientForStudy(handle, STUDY_GUID);
        });
    }

    //@Ignore
    @Test
    public void sendPasswordResetsForHardcodedUsersNotConsented() throws Exception {
        StudyPasswordResetEmailGenerator emailGenerator = new StudyPasswordResetEmailGenerator();
        List<ProfileWithEmail> recipientProfiles = Collections.emptyList();
        // manually add email addresses here
        //Set<String> ptpEmails = new HashSet<>(Arrays.asList(nonConsentedEmails));
        Set<String> ptpEmails = new HashSet<>();
        LOG.info("Not consented participant email count: {}", ptpEmails.size());

        try {
            recipientProfiles = TransactionWrapper.withTxn(handle -> {
                String auth0Domain = cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
                return emailGenerator.getProfileWithEmailForEmailAddressesBulk(handle, ptpEmails,
                        auth0Domain, mgmtClient.getToken());
            });
        } catch (Exception e) {
            LOG.error("Error sending email", e);
        }

        LOG.info("Not consented participant profile count: {}", recipientProfiles.size());

        try {
            emailGenerator.sendPasswordResetEmails(STUDY_GUID, recipientProfiles, FROM_NAME, FROM_EMAIL, MESSAGE_SUBJECT, BASE_WEBPAGE_URL,
                    SENDGRID_NOT_CONSENTED_TEMPLATE_ID, mgmtClient.getDomain(), mgmtClient.getToken());
        } catch (DDPException e) {
            LOG.error("Exception executing MBCAuth0PasswordResetEmailScript", e);
        }
    }

    //@Ignore
    @Test
    public void sendPasswordResetsForHardcodedUsersConsented() throws Exception {
        StudyPasswordResetEmailGenerator emailGenerator = new StudyPasswordResetEmailGenerator();
        List<ProfileWithEmail> recipientProfiles = Collections.emptyList();
        // manually add email addresses here
        //Set<String> ptpEmails = new HashSet<>(Arrays.asList(consentedEmails));
        Set<String> ptpEmails = new HashSet<>();
        LOG.info("Consented participant email count: {}", ptpEmails.size());

        try {
            recipientProfiles = TransactionWrapper.withTxn(handle -> {
                String auth0Domain = cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
                return emailGenerator.getProfileWithEmailForEmailAddressesBulk(handle, ptpEmails,
                        auth0Domain, mgmtClient.getToken());
            });
        } catch (Exception e) {
            LOG.error("Error sending email for consented user ", e);
        }

        LOG.info("Consented participant profile count: {}", recipientProfiles.size());

        try {
            emailGenerator.sendPasswordResetEmails(STUDY_GUID, recipientProfiles, FROM_NAME, FROM_EMAIL, MESSAGE_SUBJECT, BASE_WEBPAGE_URL,
                    SENDGRID_CONSENTED_TEMPLATE_ID, mgmtClient.getDomain(), mgmtClient.getToken());
        } catch (DDPException e) {
            LOG.error("Exception executing MBCAuth0PasswordResetEmailScript", e);
        }
    }

}
