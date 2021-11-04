package org.broadinstitute.lddp.email;

import static org.broadinstitute.ddp.client.SendGridClient.PATH_MAIL_SEND;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.datstat.SurveyConfig;
import org.broadinstitute.lddp.datstat.SurveyInstance;
import org.broadinstitute.lddp.datstat.SurveyService;
import org.broadinstitute.lddp.exception.SendGridException;
import org.broadinstitute.lddp.file.BasicProcessor;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Used for integrating with SendGrid...
 */
public class SendGridClient implements EmailClient
{
    private static final Logger logger = LoggerFactory.getLogger(SendGridClient.class);

    public static final String ERROR_INVALID_SURVEY_LINK_NUMBER = "Recipients must all have the same number of survey links.";

    public static final String ERROR_INVALID_SURVEY_LINK_KEYS = "Recipients must all have the same type of survey links.";

    private static final String LOG_PREFIX = "SendGrid";
    private static final boolean FLATTEN_ON = true; //always flatten pdfs
    public static final String CURRENT_LOG_VERSION = "1.0";

    private String sendGridKey;
    private JsonObject settings = null;
    private String portalUrl;
    private EDCClient edc;
    private String environment;

    public void configure(String sendGridKey, JsonObject settings, @NonNull String portalUrl, EDCClient edc,
                          @NonNull String environment)
    {
        this.sendGridKey = sendGridKey;
        this.settings = settings;
        this.portalUrl = portalUrl;
        this.edc = edc;
        this.environment = environment;
    }

    public void sendSingleNonTemplate(@NonNull String emailAddress, @NonNull String subject, @NonNull String message, @NonNull String messageType) {
        try {
            SendGrid sendGrid = new SendGrid(sendGridKey);

            Email fromEmail = new Email(settings.get("sendGridFrom").getAsString(), settings.get("sendGridFromName").getAsString());
            Email toEmail = new Email(emailAddress);

            Mail mail = new Mail();
            mail.setFrom(fromEmail);
            Personalization personalization = new Personalization();
            personalization.addTo(toEmail);
            mail.addPersonalization(personalization);
            mail.setSubject(subject);

            Content content = new Content();
            content.setType("text/html");
            content.setValue(message);
            mail.addContent(content);
            send(sendGrid, mail, messageType);
        }
        catch (Exception ex)
        {
            throw new SendGridException("An error occurred trying to send a " + messageType + " non-template email.", ex);
        }
    }

    /**
     * Used to send emails using SendGrid templates.
     */
    public String sendSingleEmail(@NonNull String sendGridTemplate, @NonNull Recipient recipient, String customAttachmentClassNames) {
        ArrayList<Recipient> recipientList = new ArrayList<>();
        recipientList.add(recipient);
        return sendEmail(sendGridTemplate, recipientList, customAttachmentClassNames);
    }

    /**
     * Used to send emails using SendGrid templates.
     * Be very careful when setting allowBatch to true. Json being send for all recipients must be the same in that case.
     * Probably best/safest to use that for marketing campaigns that are sent immediately and would require lots of calls to
     * SendGrid otherwise.
     */
    private String sendEmail(@NonNull String sendGridTemplate, @NonNull Collection<Recipient> recipientList, String customAttachmentClassNames)
    {
        String json;

        try
        {
            if (recipientList.size() == 0) {
                throw new IllegalArgumentException("RecipientList cannot be empty.");
            }

            SendGrid sendGrid = new SendGrid(sendGridKey);


            json = sendRequestUsingTemplate((Recipient)(recipientList.toArray())[0], sendGridTemplate, sendGrid, customAttachmentClassNames);

        }
        catch (Exception ex)
        {
            throw new SendGridException("An error occurred trying to send emails.", ex);
        }

        return json;
    }



    /**
     * Sends a request per recipient to SendGrid.
     */
    private String sendRequestUsingTemplate(@NonNull Recipient recipient,
                                             @NonNull String sendGridTemplate, @NonNull SendGrid sendGrid,
                                             String customAttachmentClassNames) throws Exception
    {
        logger.info(LOG_PREFIX + " - About to send 1 email request for " + sendGridTemplate + "...");

        Mail email;

        //use this to keep track of email JSON we are creating... useful when testing
        StringBuilder builder = new StringBuilder();

            email = configureTemplateEmail(sendGridTemplate);
            Personalization personalization = addToHeader(recipient, sendGridTemplate);
            email.addPersonalization(personalization);

            if ((settings.get("TESTING_DONOTATTACH") == null)&&(customAttachmentClassNames != null))
            {
                if (edc == null) throw new SendGridException("EDCClient is required to send emails with attachments.");

                String[] classNames = customAttachmentClassNames.split("\\|");
                BasicProcessor fileProcessor;
                SurveyInstance surveyInstance;

                SurveyConfig config = ((DatStatUtil) edc).getSurveyConfigMap().get(recipient.getCompletedSurvey());
                if (config != null)
                {
                    SurveyService service = new SurveyService();
                    surveyInstance = service.fetchSurveyInstance((DatStatUtil) edc,
                            config.getSurveyClass(), recipient.getCompletedSurveySessionId());

                } else
                {
                    throw new SendGridException("An error occurred trying to configure an email with an attachment for " + recipient.getEmail() + ".");
                }

                for (String className : classNames)
                {
                    fileProcessor = (BasicProcessor)Class.forName(className).getConstructor(boolean.class).newInstance(FLATTEN_ON);

                    try (InputStream attachmentStream = fileProcessor.generateStream(surveyInstance))
                    {

                        Attachments attachments = new Attachments();

                        String b64Encoded = Base64.encodeBase64String(IOUtils.toByteArray(attachmentStream));
                        attachments.setContent(b64Encoded);
                        attachments.setFilename(fileProcessor.getFileName());
                        attachments.setDisposition("attachment");
                        email.addAttachments(attachments);
                    }
                }
            }

            //send request to SendGrid for single recipient
            send(sendGrid, email, sendGridTemplate);
            builder.append(personalization.toString());

        return builder.toString();
    }

    private Personalization addToHeader(@NonNull Recipient recipient, @NonNull String template)
    {

        Personalization personalization = new Personalization();
        personalization.addTo(new Email(recipient.emailClientToAddress()));
        personalization.addSubstitution(":firstName", recipient.getFirstName());
        personalization.addSubstitution(":lastName", recipient.getLastName());
        personalization.addSubstitution(":url", recipient.getUrl());
        personalization.addSubstitution(":pdfUrl", recipient.getPdfUrl());
        personalization.addSubstitution(":portalUrl", portalUrl);
        personalization.addSubstitution(":shortId", Integer.toString(recipient.getShortId()));

        //these can be used to add all survey links to an email, for example
        //it is important to check the number/type of these links ahead of time since under batch send circumstances you will create bad json
        for(Map.Entry entry: recipient.getSurveyLinks().entrySet())
        {
            personalization.addSubstitution(entry.getKey().toString(), entry.getValue().toString());
        }

        personalization.addCustomArg("ddp_env_type", this.environment);
        personalization.addCustomArg("ddp_email_template", template);
        personalization.addCustomArg("ddp_log_ver", CURRENT_LOG_VERSION);

        return personalization;
    }

    private Mail configureTemplateEmail(@NonNull String template)
    {
        Mail email = new Mail();
        String senderEmail = settings.get("sendGridFromName").getAsString();
        String fromName = settings.get("sendGridFromName").getAsString();
        email.setFrom(new Email(senderEmail, fromName));

        email.setSubject(" ");
        email.setTemplateId(template);

        return email;
    }

    private void send(@NonNull SendGrid sendGrid, @NonNull Mail email, @NonNull String sendGridTemplate) throws Exception
    {
        //check settings to see if we should really send out emails...
        if (settings.get("TESTING_DONOTSEND") == null) {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint(PATH_MAIL_SEND);
            Response response = null;
            try {
                request.setBody(email.build());
                response = sendGrid.api(request);
            } catch (IOException | JsonSyntaxException e) {
                throw new SendGridException("Could not send email ", e);
            }
            if (response.getStatusCode() != 200 && response.getStatusCode() != 202) {
                logger.info(LOG_PREFIX + " - Email information sent to SendGrid for template " + sendGridTemplate + ".");
            } else {
                throw new SendGridException("Response = error; code = " + response.getStatusCode() + "; message = " + response.getBody());
            }
        }
        else {
            logger.warn(LOG_PREFIX + " - Email(s) not sent. See settings for details.");
        }
    }
}
