package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.util.Map;

import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.Method;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

/**
 * A little util class to make it more convenient to send a SendGrid email message
 */
@Slf4j
public class SendGridMailUtil {
    public static void sendEmailMessage(String fromName, String fromEmailAddress, String toName, String toEmailAddress, String subject,
                                        String sendGridTemplateId, Map<String, String> templateVarNameToValue, String sendGridApiKey) {
        Mail messageToSend = buildEmailMessage(fromName, fromEmailAddress, toName, toEmailAddress, subject, sendGridTemplateId,
                templateVarNameToValue, false);

        submitMailMessage(messageToSend, sendGridApiKey, null);
    }

    public static void sendDynamicEmailMessage(String fromName, String fromEmailAddress, String toName, String toEmailAddress,
                                              String subject, String sendGridTemplateId, Map<String, String> templateVarNameToValue,
                                               String sendGridApiKey, String proxy) {
        Mail messageToSend = buildEmailMessage(fromName, fromEmailAddress, toName, toEmailAddress, subject, sendGridTemplateId,
                templateVarNameToValue, true);

        submitMailMessage(messageToSend, sendGridApiKey, proxy);
    }

    private static Mail buildEmailMessage(String fromName, String fromEmailAddress, String toName, String toEmailAddress, String subject,
                                          String sendGridTemplateId, Map<String, String> templateVarNameToValue,
                                          boolean dynamicSubstitution) {
        Email fromEmail = new Email(fromEmailAddress, fromName);
        Email toEmail = new Email(toEmailAddress, toName);

        Personalization sendGridPersonalization = new Personalization();

        if (dynamicSubstitution) {
            templateVarNameToValue.forEach(sendGridPersonalization::addDynamicTemplateData);

        } else {
            templateVarNameToValue.forEach(sendGridPersonalization::addSubstitution);
        }

        Mail mail = new Mail();
        mail.setFrom(fromEmail);
        sendGridPersonalization.addTo(toEmail);
        mail.addPersonalization(sendGridPersonalization);
        mail.setSubject(subject);
        mail.setTemplateId(sendGridTemplateId);

        return mail;
    }

    private static void submitMailMessage(Mail mailMessage, String sendGridApiKey, String proxy) {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        try {
            request.setBody(mailMessage.build());
        } catch (IOException e) {
            String msg = "There was a problem creating request for SendGrid mail message";
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }

        SendGrid sendGrid = SendGridFactory.createSendGridInstance(sendGridApiKey, proxy);
        Response response;
        try {
            response = sendGrid.api(request);
        } catch (IOException e) {
            String msg = "There was a problem contacting SendGrid to send message";
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
        if (response.getStatusCode() != HttpStatus.SC_ACCEPTED && response.getStatusCode() != HttpStatus.SC_OK) {
            String msg = "SendGrid did not accept our mail message: " + "Response Status Code: " + response.getStatusCode() + "\n"
                    + "Response Body: " + response.getBody();
            log.error(msg);
            throw new RuntimeException(msg);
        } else {
            log.info("Successfully submitted message to SendGrid with subject {}", mailMessage.subject);
        }
    }
}
