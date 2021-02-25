package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.auth0.exception.APIException;
import com.auth0.json.mgmt.users.User;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailBlaster {

    private static final Logger LOG = LoggerFactory.getLogger(EmailBlaster.class);
    //private static final String STUDY_GUID = "47D94XZTP3";
    private static final String FROM_NAME = "TestBoston";
    private static final String FROM_EMAIL = "info@testboston.org";
    private static final String MESSAGE_SUBJECT = "Important Changes to TestBoston";
    private static final String SENDGRID_TEMPLATE_ID = "12c5e2d4-e541-4e0b-8ffc-fb0a7d8cbb11";


    public static void main(String[] args) {
        new EmailBlaster().sendEmail();
    }

    public void sendEmail() {
        List<String> recipients = new ArrayList<>();
        recipients.add("andrew@broadinstitute.org");
        // manually add email addresses here

        Config cfg = ConfigFactory.load();
        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1,
                        cfg.getString(ConfigFile.DB_URL)));

        final Set<String> auth0UserIds = new TreeSet<>();
        TransactionWrapper.useTxn(handle -> {
            List<UserDto> enrolledParticipants = handle.attach(JdbiUser.class).findAllEnrolledParticipants("testboston");

            Auth0ManagementClient mgmtClient = Auth0Util.getManagementClientForDomain(handle, "https://testboston.us.auth0.com/");
            LOG.info("Found {} participants", enrolledParticipants.size());


            for (UserDto enrolledParticipant : enrolledParticipants) {

                enrolledParticipant.getAuth0UserId();

                if (enrolledParticipant != null && StringUtils.isNotBlank(enrolledParticipant.getAuth0UserId())) {
                    auth0UserIds.add(enrolledParticipant.getAuth0UserId());
                }
            }

            Auth0Util auth0Util = new Auth0Util("https://testboston.us.auth0.com/");
            Map<String, String> userEmailsById = auth0Util.getAuth0UsersByAuth0UserIds(auth0UserIds, mgmtClient.getToken());

            LOG.info("Found {} emails", userEmailsById.size());

            for (String emailAddress : userEmailsById.keySet()) {
                LOG.info("Found {}", emailAddress);
            }

            recipients.addAll(userEmailsById.keySet());


        });


        String apiKey = cfg.getString(ConfigFile.SENDGRID_API_KEY);
        try {
            for (String recipient : recipients) {
                LOG.info("Sending to " + recipient);
                SendGridMailUtil.sendEmailMessage(FROM_NAME, FROM_EMAIL, null, recipient, MESSAGE_SUBJECT, SENDGRID_TEMPLATE_ID,
                        Collections.emptyMap(), apiKey);
                LOG.info("Sent to " + recipient);
            }

        } catch (DDPException e) {
            LOG.error("Exception executing AngioAuth0PasswordResetEmailScript", e);
        }
        System.exit(0);
    }
}
