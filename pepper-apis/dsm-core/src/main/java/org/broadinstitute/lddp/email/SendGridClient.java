package org.broadinstitute.lddp.email;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.google.gson.JsonObject;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.NonNull;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsm.exception.NotificationSentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SendGridClient is a legacy wrapper for using SendGrid, carried over from
 * the legacy DDP repo, and used exclusively by DSM.
 */
public class SendGridClient {

    private static final Logger logger = LoggerFactory.getLogger(SendGridClient.class);

    private static final String LOG_PREFIX = "SendGrid";
    private final SendGrid sendGrid;

    public SendGridClient(SendGrid sendGrid) {
        this.sendGrid = sendGrid;
    }

    /**
     * Used to send emails using SendGrid templates.
     */
    public void sendSingleEmail(@NonNull String sendGridTemplate, @NonNull Recipient recipient, JsonObject settings) {
        ArrayList<Recipient> recipientList = new ArrayList<>();
        recipientList.add(recipient);
        sendEmail(sendGridTemplate, recipientList, settings);
    }

    /**
     * Used to send emails using SendGrid templates.
     * Be very careful when setting allowBatch to true. Json being send for all recipients must be the same in that case.
     * Probably best/safest to use that for marketing campaigns that are sent immediately and would require lots of calls to
     * SendGrid otherwise.
     */
    private void sendEmail(@NonNull String sendGridTemplate, @NonNull Collection<Recipient> recipientList,
                           JsonObject settings) {
        try {
            if (recipientList.size() == 0) {
                throw new IllegalArgumentException("RecipientList cannot be empty.");
            }
            sendRequestUsingTemplate((Recipient) (recipientList.toArray())[0], sendGridTemplate, sendGrid, settings);
        } catch (Exception ex) {
            throw new NotificationSentException("An error occurred trying to send emails. " + ex.getMessage());
        }
    }


    /**
     * Sends a request per recipient to SendGrid.
     */
    private void sendRequestUsingTemplate(@NonNull Recipient recipient, @NonNull String sendGridTemplate,
                                          @NonNull SendGrid sendGrid, JsonObject settings)
            throws Exception {
        logger.info(LOG_PREFIX + " - About to send 1 email request for " + sendGridTemplate + "...");
        Mail mail = configureTemplateEmail(sendGridTemplate, recipient, settings);
        send(sendGrid, mail);
    }

    private Personalization addToHeader(@NonNull Recipient recipient) {
        Email toEmail = new Email(recipient.getEmail());

        Personalization sendGridPersonalization = new Personalization();
        sendGridPersonalization.addTo(toEmail);

        sendGridPersonalization.addSubstitution(":firstName", recipient.getFirstName());
        sendGridPersonalization.addSubstitution(":lastName", recipient.getLastName());
        sendGridPersonalization.addSubstitution(":url", recipient.getPermalink());
        sendGridPersonalization.addSubstitution(":shortId", Integer.toString(recipient.getShortId()));

        for (Map.Entry entry : recipient.getPersonalization().entrySet()) {
            sendGridPersonalization.addSubstitution(entry.getKey().toString(), entry.getValue().toString());
        }
        return sendGridPersonalization;
    }

    private Mail configureTemplateEmail(@NonNull String template, Recipient recipient, JsonObject settings) {
        Personalization sendGridPersonalization = addToHeader(recipient);

        Email fromEmail = new Email(settings.get("sendGridFrom").getAsString(), settings.get("sendGridFromName").getAsString());

        Mail mail = new Mail();
        mail.setFrom(fromEmail);
        mail.setSubject("");
        mail.setTemplateId(template);
        mail.addPersonalization(sendGridPersonalization);

        return mail;
    }

    private void send(@NonNull SendGrid sendGrid, @NonNull Mail mail) throws Exception {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        try {
            request.setBody(mail.build());
        } catch (IOException e) {
            String msg = "There was a problem creating request for SendGrid mail message";
            throw new RuntimeException(msg, e);
        }

        Response response = sendGrid.api(request);

        if (response.getStatusCode() != HttpStatus.SC_ACCEPTED && response.getStatusCode() != HttpStatus.SC_OK) {
            String msg = "SendGrid did not accept our mail message: " + "Response Status Code: " + response.getStatusCode() + "\n"
                    + "Response Body: " + response.getBody();
            throw new RuntimeException(msg);
        } else {
            logger.info("Successfully submitted message to SendGrid with subject {}", mail.subject);
        }
    }
}
