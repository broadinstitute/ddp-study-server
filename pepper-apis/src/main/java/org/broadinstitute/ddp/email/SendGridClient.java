package org.broadinstitute.ddp.email;

import com.google.gson.JsonObject;
import com.sendgrid.SendGrid;
import com.sendgrid.smtpapi.SMTPAPI;
import org.broadinstitute.ddp.datstat.DatStatUtil;
import org.broadinstitute.ddp.datstat.SurveyConfig;
import org.broadinstitute.ddp.datstat.SurveyInstance;
import org.broadinstitute.ddp.datstat.SurveyService;
import org.broadinstitute.ddp.exception.SendGridException;
import org.broadinstitute.ddp.file.BasicProcessor;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            SendGrid.Email email = new SendGrid.Email();
            email.setFromName(settings.get("sendGridFromName").getAsString());
            email.setFrom(settings.get("sendGridFrom").getAsString());
            email.setTo(new String[]{emailAddress});
            email.setSubject(subject);
            email.setHtml(message);
            send(sendGrid, email, messageType);
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
        return sendEmail(sendGridTemplate, recipientList, customAttachmentClassNames, false);
    }

    public String sendBatchEmail(@NonNull String sendGridTemplate, @NonNull Collection<Recipient> recipientList) {
        return sendEmail(sendGridTemplate, recipientList, null, true);
    }

    /**
     * Used to send emails using SendGrid templates.
     * Be very careful when setting allowBatch to true. Json being send for all recipients must be the same in that case.
     * Probably best/safest to use that for marketing campaigns that are sent immediately and would require lots of calls to
     * SendGrid otherwise.
     */
    private String sendEmail(@NonNull String sendGridTemplate, @NonNull Collection<Recipient> recipientList, String customAttachmentClassNames, boolean allowBatch)
    {
        String json;

        try
        {
            if (recipientList.size() == 0) {
                throw new IllegalArgumentException("RecipientList cannot be empty.");
            }

            SendGrid sendGrid = new SendGrid(sendGridKey);

            if (allowBatch) {
                json = sendBatchRequestUsingTemplate(recipientList, sendGridTemplate, sendGrid);
            }
            else {
                json = sendRequestUsingTemplate((Recipient)(recipientList.toArray())[0], sendGridTemplate, sendGrid, customAttachmentClassNames);
            }
        }
        catch (Exception ex)
        {
            throw new SendGridException("An error occurred trying to send emails.", ex);
        }

        return json;
    }

    /**
     * Sends a single request to SendGrid for one or more recipients.
     */
    private String sendBatchRequestUsingTemplate(@NonNull Collection<Recipient> recipientList, @NonNull String sendGridTemplate, @NonNull SendGrid sendGrid) throws Exception
    {
        logger.info(LOG_PREFIX + " - About to send a single request for " + recipientList.size() + " email(s) for template " + sendGridTemplate + "...");

        SendGrid.Email email = configureTemplateEmail(sendGridTemplate);

        SMTPAPI header = email.getSMTPAPI();

        Collection<String> surveyLinkKeys = null;

        //loop through all the recipients to build our request -- first make sure we have right number/type of survey links otherwise bad json DON'T SEND
        for (Recipient recipient : recipientList)
        {
            if (surveyLinkKeys == null)
            {
                surveyLinkKeys = recipient.getSurveyLinks().keySet();
            }
            else if (surveyLinkKeys.size() != recipient.getSurveyLinks().keySet().size())
            {
                throw new SendGridException(ERROR_INVALID_SURVEY_LINK_NUMBER);
            }
            else if (!surveyLinkKeys.containsAll(recipient.getSurveyLinks().keySet()))
            {
                throw new SendGridException(ERROR_INVALID_SURVEY_LINK_KEYS);
            }
            addToHeader(header, recipient, sendGridTemplate);
        }

        //send request to SendGrid for all recipients
        send(sendGrid, email, sendGridTemplate);

        return header.jsonString();
    }

    /**
     * Sends a request per recipient to SendGrid.
     */
    private String sendRequestUsingTemplate(@NonNull Recipient recipient,
                                             @NonNull String sendGridTemplate, @NonNull SendGrid sendGrid,
                                             String customAttachmentClassNames) throws Exception
    {
        logger.info(LOG_PREFIX + " - About to send 1 email request for " + sendGridTemplate + "...");

        SendGrid.Email email;

        //use this to keep track of email JSON we are creating... useful when testing
        StringBuilder builder = new StringBuilder();

            email = configureTemplateEmail(sendGridTemplate);

            SMTPAPI header = email.getSMTPAPI();
            addToHeader(header, recipient, sendGridTemplate);

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

                    try (InputStream attachment = fileProcessor.generateStream(surveyInstance))
                    {
                        email.addAttachment(fileProcessor.getFileName(), attachment);
                    }
                }
            }

            //send request to SendGrid for single recipient
            send(sendGrid, email, sendGridTemplate);

            builder.append(header.jsonString());

        return builder.toString();
    }

    private void addToHeader(@NonNull SMTPAPI header, @NonNull Recipient recipient, @NonNull String template)
    {
        header.addTo(recipient.emailClientToAddress());
        header.addSubstitution(":firstName", recipient.getFirstName());
        header.addSubstitution(":lastName", recipient.getLastName());
        header.addSubstitution(":url", recipient.getUrl());
        header.addSubstitution(":pdfUrl", recipient.getPdfUrl());
        header.addSubstitution(":portalUrl", portalUrl);
        header.addSubstitution(":shortId", Integer.toString(recipient.getShortId()));

        //these can be used to add all survey links to an email, for example
        //it is important to check the number/type of these links ahead of time since under batch send circumstances you will create bad json
        for(Map.Entry entry: recipient.getSurveyLinks().entrySet())
        {
            header.addSubstitution(entry.getKey().toString(), entry.getValue().toString());
        }

        header.addUniqueArg("ddp_env_type", this.environment);
        header.addUniqueArg("ddp_email_template", template);
        header.addUniqueArg("ddp_log_ver", CURRENT_LOG_VERSION);
    }

    private SendGrid.Email configureTemplateEmail(@NonNull String template)
    {
        SendGrid.Email email = new SendGrid.Email();
        email.setFromName(settings.get("sendGridFromName").getAsString());
        email.setFrom(settings.get("sendGridFrom").getAsString());

        //emailing will fail unless we set these two properties to a non-empty value!!!
        email.setHtml(" ");
        email.setSubject(" ");
        email.setTemplateId(template);

        return email;
    }

    private void send(@NonNull SendGrid sendGrid, @NonNull SendGrid.Email email, @NonNull String sendGridTemplate) throws Exception
    {
        //check settings to see if we should really send out emails...
        if (settings.get("TESTING_DONOTSEND") == null)
        {
            SendGrid.Response response = sendGrid.send(email);

            if (response.getStatus())
            {
                logger.info(LOG_PREFIX + " - Email information sent to SendGrid for template " + sendGridTemplate + ".");
            }
            else
            {
                throw new SendGridException("Response = error; code = " + response.getCode() + "; message = " + response.getMessage());
            }
        }
        else
        {
            logger.warn(LOG_PREFIX + " - Email(s) not sent. See settings for details.");
        }
    }
}
