package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.util.Map;

import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Personalization;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A little util class to make it more convenient to send a SendGrid email message
 */
public class SendGridMailUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SendGridMailUtil.class);

    public static void sendEmailMessage(String fromName, String fromEmailAddress, String toName, String toEmailAddress, String subject,
                                        String sendGridTemplateId, Map<String, String> templateVarNameToValue, String sendGridApiKey) {
        Mail messageToSend = buildEmailMessage(fromName, fromEmailAddress, toName, toEmailAddress, subject, sendGridTemplateId,
                templateVarNameToValue);

        submitMailMessage(messageToSend, sendGridApiKey);
    }

    private static Mail buildEmailMessage(String fromName, String fromEmailAddress, String toName, String toEmailAddress, String subject,
                                          String sendGridTemplateId, Map<String, String> templateVarNameToValue) {
        Email fromEmail = new Email(fromEmailAddress, fromName);
        Email toEmail = new Email(toEmailAddress, toName);

        Personalization sendGridPersonalization = new Personalization();
        templateVarNameToValue.forEach(sendGridPersonalization::addSubstitution);


        Mail mail = new Mail();
        mail.setFrom(fromEmail);
        sendGridPersonalization.addTo(toEmail);
        mail.addPersonalization(sendGridPersonalization);
        mail.setSubject(subject);
        mail.setTemplateId(sendGridTemplateId);

        return mail;
    }

    private static void submitMailMessage(Mail mailMessage, String sendGridApiKey) {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        try {
            request.setBody(mailMessage.build());
        } catch (IOException e) {
            String msg = "There was a problem creating request for SendGrid mail message";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }

        SendGrid sendGrid = new SendGrid(sendGridApiKey);
        Response response;
        try {
            response = sendGrid.api(request);
        } catch (IOException e) {
            String msg = "There was a problem contacting SendGrid to send message";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }
        if (response.getStatusCode() != HttpStatus.SC_ACCEPTED && response.getStatusCode() != HttpStatus.SC_OK) {
            String msg = "SendGrid did not accept our mail message: " + "Response Status Code: " + response.getStatusCode() + "\n"
                    + "Response Body: " + response.getBody();
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
    }
}
