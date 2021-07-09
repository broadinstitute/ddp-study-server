package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import com.sendgrid.Client;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.Method;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.HttpClients;
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
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }

        SendGrid sendGrid = createSendGridInstance(sendGridApiKey, proxy);
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

    private static SendGrid createSendGridInstance(String sendGridApiKey, String proxy) {
        var httpClientBuilder = HttpClients.custom();
        if (proxy != null && !proxy.isBlank()) {
            URL proxyUrl;
            try {
                proxyUrl = new URL(proxy);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("proxy needs to be a valid url");
            }
            httpClientBuilder.setProxy(new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol()));
            httpClientBuilder.setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE);
            LOG.info("Using SendGrid proxy: {}", proxy);
        }
        var client = new Client(httpClientBuilder.build());
        return new SendGrid(sendGridApiKey, client);
    }
}
