package org.broadinstitute.ddp.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
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

    // Note: API endpoint paths should not have leading slash,
    // and path parameters uses colon syntax like SparkJava.
    public static final String API_MAIL_SEND = "mail/send";
    public static final String API_TEMPLATES = "templates";
    public static final String API_TEMPLATES_VERSIONS = "templates/:templateId/versions";
    public static final String PARAM_TEMPLATE_ID = ":templateId";

    private static final Logger LOG = LoggerFactory.getLogger(SendGridClient.class);
    private static final Gson gson = new Gson();
    private static final String ATTACHMENT_DISPOSITION = "attachment";
    private static final String ATTACHMENT_PDF_TYPE = "application/pdf";
    private static final String GEN_LEGACY = "legacy";
    private static final String GEN_DYNAMIC = "dynamic";
    private static final String EDITOR_CODE = "code";
    private static final String EDITOR_DESIGN = "design";

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
     * Create a new legacy template. Currently the error response is simply the string representation.
     *
     * @param name the name for the template
     * @return result with created template, or error details
     */
    public ApiResult<Template, String> createTemplate(String name) {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("generation", GEN_LEGACY);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint(API_TEMPLATES);
        request.setBody(gson.toJson(payload));

        try {
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode == 201) {
                Template template = gson.fromJson(response.getBody(), Template.class);
                return ApiResult.ok(statusCode, template);
            } else {
                return ApiResult.err(statusCode, response.getBody());
            }
        } catch (IOException | JsonSyntaxException e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Create a new version for the given template with the code editor experience. Currently the error response is
     * simply the string representation.
     *
     * @param templateId the template id
     * @param name       the name for the version
     * @param subject    the email subject
     * @param html       the email html content
     * @param isActive   is version active or not
     * @return result with created version, or error details
     */
    public ApiResult<TemplateVersion, String> createTemplateVersion(
            String templateId, String name, String subject, String html, boolean isActive) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("subject", subject);
        payload.put("html_content", html);
        payload.put("active", isActive ? 1 : 0);
        payload.put("editor", EDITOR_CODE);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint(API_TEMPLATES_VERSIONS.replace(PARAM_TEMPLATE_ID, templateId));
        request.setBody(gson.toJson(payload));

        try {
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode == 201) {
                TemplateVersion version = gson.fromJson(response.getBody(), TemplateVersion.class);
                return ApiResult.ok(statusCode, version);
            } else {
                return ApiResult.err(statusCode, response.getBody());
            }
        } catch (IOException | JsonSyntaxException e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Edit a template version. If arguments are `null`, then they will not be included in the PATCH payload. Currently
     * the error response is simply the string representation.
     *
     * @param templateId the template id
     * @param versionId  the version id
     * @param name       the updated version name
     * @param subject    the updated email subject
     * @param html       the updated email html content
     * @return result with updated version, or error details
     */
    public ApiResult<TemplateVersion, String> updateTemplateVersion(
            String templateId, String versionId, String name, String subject, String html) {
        Map<String, Object> payload = new HashMap<>();
        if (name != null) {
            payload.put("name", name);
        }
        if (subject != null) {
            payload.put("subject", subject);
        }
        if (html != null) {
            payload.put("html_content", html);
        }

        String path = API_TEMPLATES_VERSIONS.replace(PARAM_TEMPLATE_ID, templateId) + "/" + versionId;

        Request request = new Request();
        request.setMethod(Method.PATCH);
        request.setEndpoint(path);
        request.setBody(gson.toJson(payload));

        try {
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                TemplateVersion version = gson.fromJson(response.getBody(), TemplateVersion.class);
                return ApiResult.ok(statusCode, version);
            } else {
                return ApiResult.err(statusCode, response.getBody());
            }
        } catch (IOException | JsonSyntaxException e) {
            return ApiResult.thrown(e);
        }
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
            if (CollectionUtils.isNotEmpty(template.getVersions())) {
                for (var version : template.getVersions()) {
                    if (version.isActive()) {
                        versionId = version.getId();
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

    public static class Template {
        private String id;
        private String name;
        private String generation;   // either `legacy` or `dynamic`
        @SerializedName("updated_at")
        private String updatedAt;
        private List<TemplateVersion> versions;

        public Template(String id, String name, List<TemplateVersion> versions) {
            this.id = id;
            this.name = name;
            this.versions = versions;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getGeneration() {
            return generation;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public List<TemplateVersion> getVersions() {
            return versions;
        }
    }

    public static class TemplateVersion {
        private String id;
        @SerializedName("template_id")
        private String templateId;
        private String name;
        private String subject;
        @SerializedName("html_content")
        private String htmlContent;
        @SerializedName("updated_at")
        private String updatedAt;
        private String editor;   // either `code` or `design`
        private int active;      // 0 if inactive, 1 if active. Only one version can be active at a time.

        public TemplateVersion(String id, int active) {
            this.id = id;
            this.active = active;
        }

        public String getId() {
            return id;
        }

        public String getTemplateId() {
            return templateId;
        }

        public String getName() {
            return name;
        }

        public String getSubject() {
            return subject;
        }

        public String getHtmlContent() {
            return htmlContent;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public String getEditor() {
            return editor;
        }

        public boolean isActive() {
            return active == 1;
        }
    }
}
