package org.broadinstitute.ddp.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sendgrid.Attachments;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for SendGrid services, effectively just a wrapper around the API calls.
 */
public class SendGridClient {

    // Note: API endpoint paths should not have leading slash.
    public static final String API_MAIL_SEND = "mail/send";
    public static final String API_TEMPLATES = "templates";

    private static final Logger LOG = LoggerFactory.getLogger(SendGridClient.class);
    private static final Gson gson = new Gson();
    private static final String ATTACHMENT_DISPOSITION = "attachment";
    private static final String ATTACHMENT_PDF_TYPE = "application/pdf";

    private final SendGrid sendGrid;

    // Convenient helper to create new pdf attachment for a mail.
    public static Attachments newPdfAttachment(String name, InputStream content) {
        return new Attachments.Builder(name, content)
                .withDisposition(ATTACHMENT_DISPOSITION)
                .withType(ATTACHMENT_PDF_TYPE)
                .build();
    }

    public SendGridClient(String apiKey) {
        this(new SendGrid(apiKey));
    }

    public SendGridClient(SendGrid sendGrid) {
        this.sendGrid = sendGrid;
    }

    /**
     * Find the currently active version of given template.
     *
     * <p>See https://sendgrid.com/docs/API_Reference/Web_API_v3/Transactional_Templates/templates.html#-GET
     *
     * @param templateId the template id
     * @return result with active version id, id can be null if no active ones
     */
    public ApiResult<String, Void> getTemplateActiveVersionId(String templateId) {
        Request request = new Request();
        request.setMethod(Method.GET);
        request.setEndpoint(API_TEMPLATES + "/" + templateId);
        try {
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode != 200) {
                return ApiResult.err(statusCode, null);
            }

            Template template = gson.fromJson(response.getBody(), Template.class);
            String versionId = null;
            if (CollectionUtils.isNotEmpty(template.versions)) {
                for (var version : template.versions) {
                    if (version.active == 1) {
                        versionId = version.id;
                        break;
                    }
                }
            }

            return ApiResult.ok(statusCode, versionId);
        } catch (IOException | JsonSyntaxException e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Send the given mail. Currently no body response is provided, and error response is simply the string
     * representation.
     *
     * <p>See https://sendgrid.com/docs/API_Reference/Web_API_v3/Mail/index.html
     *
     * @param mail the mail package
     * @return result
     */
    public ApiResult<Void, String> sendMail(Mail mail) {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint(API_MAIL_SEND);
        try {
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 202) {
                return ApiResult.ok(statusCode, null);
            } else {
                return ApiResult.err(statusCode, response.getBody());
            }
        } catch (IOException e) {
            return ApiResult.thrown(e);
        }
    }

    // Currently the model classes below are only used for deserialization internally.
    // If they are more broadly consumed, then they should be refactored.

    static class Template {
        public String id;
        public String name;
        public List<TemplateVersion> versions;

        public Template(String id, String name, List<TemplateVersion> versions) {
            this.id = id;
            this.name = name;
            this.versions = versions;
        }
    }

    static class TemplateVersion {
        public String id;
        public int active;  // 0 if inactive, 1 if active. Only one version can be active at a time.

        public TemplateVersion(String id, int active) {
            this.id = id;
            this.active = active;
        }
    }
}
