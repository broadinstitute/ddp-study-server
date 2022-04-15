package org.broadinstitute.ddp.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.junit.Before;
import org.junit.Ignore;

@Slf4j
@Ignore
//Script to trigger pwd reset emails for migrated users during MBC study migration to pepper.
public class MBCAuth0PasswordResetEmailScript extends TxnAwareBaseTest {
    private static final String STUDY_GUID = "cmi-mbc";
    private static final String FROM_NAME = "MBCproject";
    private static final String FROM_EMAIL = "info@mbcproject.org";
    private static final String MESSAGE_SUBJECT = "Metastatic Breast Cancer Project update: Please choose a password for your account";
    private static final String SENDGRID_CONSENTED_TEMPLATE_ID = "fa576354-73b9-41fe-9cfc-ac370fc70556";
    private static final String SENDGRID_NOT_CONSENTED_TEMPLATE_ID = "cd3e9c60-9636-4685-84fd-c1aa3c7a1177";
    private static final String BASE_WEBPAGE_URL = "https:///mbc.dev.datadonationplatform.org/password-reset-done";
    //WATCHOUT.. Its DEV sendgrid template ids and Dev url

    private static final Integer TODAYS_LIMIT = 150;

    private String[] consentedEmails = {};
    private String[] nonConsentedEmails = {};
    private String[] consentedEmailsSent = {};
    private String[] nonConsentedEmailsSent = {};

    private Auth0ManagementClient mgmtClient;

    @Before
    public void setup() {
        TransactionWrapper.useTxn(handle -> {
            mgmtClient = Auth0ManagementClient.forStudy(handle, STUDY_GUID);
        });
    }

    @Ignore
    //@Test
    public void sendPasswordResetsForHardcodedUsersNotConsented() {
        StudyPasswordResetEmailGenerator emailGenerator = new StudyPasswordResetEmailGenerator();
        List<ProfileWithEmail> recipientProfiles = Collections.emptyList();
        //add emails
        List<String> ncEmailListSent = new ArrayList<>(Arrays.asList(nonConsentedEmailsSent));
        List<String> ncEmailList = new ArrayList<>(Arrays.asList(nonConsentedEmails));
        Collections.sort(ncEmailList);
        ncEmailList.replaceAll(String::toLowerCase);
        ncEmailListSent.replaceAll(String::toLowerCase);

        //remove emails already sent
        ncEmailList.removeAll(ncEmailListSent);

        log.info("NON consented email ARRAY count: {} non consented email List count: {} already sent count: {}",
                nonConsentedEmails.length, ncEmailList.size(), ncEmailListSent.size());

        //pick todays emails
        Set<String> ptpEmails = new HashSet<>();
        for (int counter = 0; counter <= TODAYS_LIMIT; counter++) {
            ptpEmails.add(ncEmailList.get(counter));
        }

        log.info("TODAYS {} non-consented emails:: {}", ptpEmails.size(), ptpEmails);

        try {
            recipientProfiles = TransactionWrapper.withTxn(handle -> {
                String auth0Domain = cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
                return emailGenerator.getProfileWithEmailForEmailAddressesBulk(handle, ptpEmails,
                        auth0Domain, mgmtClient.getToken());
            });
        } catch (Exception e) {
            log.error("Error sending email", e);
        }

        log.info("Not consented participant profile count: {}", recipientProfiles.size());

        try {
            emailGenerator.sendPasswordResetEmails(STUDY_GUID, recipientProfiles, FROM_NAME, FROM_EMAIL, MESSAGE_SUBJECT, BASE_WEBPAGE_URL,
                    SENDGRID_NOT_CONSENTED_TEMPLATE_ID, mgmtClient);
        } catch (DDPException e) {
            log.error("Exception executing MBCAuth0PasswordResetEmailScript", e);
        }

        //add emails sent today to sentEmails and update the nonConsentedEmailsSent array manually;
        ncEmailListSent.addAll(ptpEmails);
        log.info("ncEmailListSent = {}", ncEmailListSent);
    }

    @Ignore
    //@Test
    public void sendPasswordResetsForHardcodedUsersConsented() throws Exception {
        StudyPasswordResetEmailGenerator emailGenerator = new StudyPasswordResetEmailGenerator();
        List<ProfileWithEmail> recipientProfiles = Collections.emptyList();

        //add emails
        List<String> consentedEmailListSent = new ArrayList<>(Arrays.asList(consentedEmailsSent));
        List<String> consentedEmailList = new ArrayList<>(Arrays.asList(consentedEmails));
        Collections.sort(consentedEmailList);
        consentedEmailList.replaceAll(String::toLowerCase);
        consentedEmailListSent.replaceAll(String::toLowerCase);

        //remove emails already sent
        consentedEmailList.removeAll(consentedEmailListSent);
        log.info("Consented email ARRAY count: {} consented email list count: {} ", consentedEmails.length, consentedEmailList.size());

        //pick todays emails to send
        Set<String> ptpEmails = new HashSet<>();
        for (int counter = 0; counter <= TODAYS_LIMIT; counter++) {
            ptpEmails.add(consentedEmailList.get(counter));
        }

        log.info("TODAYS consented users {} emails:: {}", ptpEmails.size(), ptpEmails);

        try {
            recipientProfiles = TransactionWrapper.withTxn(handle -> {
                String auth0Domain = cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
                return emailGenerator.getProfileWithEmailForEmailAddressesBulk(handle, ptpEmails,
                        auth0Domain, mgmtClient.getToken());
            });
        } catch (Exception e) {
            log.error("Error sending email for consented user ", e);
        }

        log.info("Consented participant profile count: {}", recipientProfiles.size());

        try {
            emailGenerator.sendPasswordResetEmails(STUDY_GUID, recipientProfiles, FROM_NAME, FROM_EMAIL, MESSAGE_SUBJECT, BASE_WEBPAGE_URL,
                    SENDGRID_CONSENTED_TEMPLATE_ID, mgmtClient);
        } catch (DDPException e) {
            log.error("Exception executing MBCAuth0PasswordResetEmailScript", e);
        }

        //add emails sent today to sentEmails and update the consentedEmailsSent array manually;
        consentedEmailListSent.addAll(ptpEmails);
        log.info("consentedEmailListSent = {}", consentedEmailListSent);
    }

}
