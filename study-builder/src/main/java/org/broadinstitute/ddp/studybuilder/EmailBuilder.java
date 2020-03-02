package org.broadinstitute.ddp.studybuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(EmailBuilder.class);
    private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private SendGridClient sendGrid;
    private VelocityEngine velocity;

    public EmailBuilder(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        this.sendGrid = new SendGridClient(studyCfg.getString("sendgrid.apiKey"));
        this.velocity = new VelocityEngine();
        initVelocityEngine();
    }

    private void initVelocityEngine() {
        // If templates reference a non-existing variable, then throw exception.
        velocity.addProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT, true);
        velocity.init();
    }

    private List<Config> searchForConfigs(Set<String> keys) {
        Set<String> remaining = new HashSet<>(keys);
        List<Config> found = studyCfg.getConfigList("sendgridEmails")
                .stream()
                .filter(cfg -> remaining.remove(cfg.getString("key")))
                .collect(Collectors.toList());
        if (!remaining.isEmpty()) {
            throw new DDPException("Could not find emails for keys: " + remaining.toString());
        }
        return found;
    }

    public void createAll() {
        createForConfigs(List.copyOf(studyCfg.getConfigList("sendgridEmails")));
    }

    public void createForEmailKeys(Set<String> keys) {
        createForConfigs(searchForConfigs(keys));
    }

    private void createForConfigs(List<Config> emailCfgs) {
        List<EmailInfo> emails = new ArrayList<>();
        for (var emailCfg : emailCfgs) {
            EmailInfo email = createSendGridEmail(emailCfg);
            if (email != null) {
                emails.add(email);
                LOG.info("Created email {} with templateId={} versionId={}", email.key, email.templateId, email.versionId);
            }
        }
        if (!emails.isEmpty()) {
            Map<String, String> mappings = emails.stream().collect(Collectors.toMap(e -> e.key, e -> e.templateId));
            LOG.info("Created {} email templates:\n{}", emails.size(), prettyGson.toJson(mappings));
        }
    }

    private EmailInfo createSendGridEmail(Config emailCfg) {
        String key = emailCfg.getString("key");
        String templateId = ConfigUtil.getStrIfPresent(varsCfg, "emails." + key);
        if (templateId != null && !templateId.isEmpty()) {
            LOG.error("Email {} already has template id set to {}, not creating", key, templateId);
            return null;
        }

        File file = cfgPath.getParent().resolve(emailCfg.getString("filepath")).toFile();
        String html = readRawHtmlContent(file);
        if (emailCfg.getBoolean("render")) {
            html = renderHtmlContent(file.getName(), html);
        }
        String subject = emailCfg.getString("subject");
        String name = emailCfg.getString("name");

        var templateResult = sendGrid.createTemplate(name);
        templateResult.rethrowIfThrown(e -> new DDPException("Error while creating email " + key, e));
        if (templateResult.hasError()) {
            LOG.error("Error while creating email {}: {}", key, templateResult.getError());
            return null;
        }

        templateId = templateResult.getBody().getId();
        String versionId = null;

        var versionResult = sendGrid.createTemplateVersion(templateId, name, subject, html, true);
        versionResult.rethrowIfThrown(e -> new DDPException("Error while creating version for email " + key, e));
        if (versionResult.hasError()) {
            LOG.error("Error while creating version for email {}. You might need to manually create"
                            + " the version and run update-emails to load HTML content: {}",
                    key, versionResult.getError());
        } else {
            versionId = versionResult.getBody().getId();
        }

        return new EmailInfo(key, templateId, versionId);
    }

    public void updateAll() {
        updateForConfigs(List.copyOf(studyCfg.getConfigList("sendgridEmails")));
    }

    public void updateForEmailKeys(Set<String> keys) {
        updateForConfigs(searchForConfigs(keys));
    }

    private void updateForConfigs(List<Config> emailCfgs) {
        List<EmailInfo> emails = new ArrayList<>();
        for (var emailCfg : emailCfgs) {
            EmailInfo email = updateSendGridEmail(emailCfg);
            if (email != null) {
                emails.add(email);
                LOG.info("Updated email {} with templateId={} versionId={}", email.key, email.templateId, email.versionId);
            }
        }
        if (!emails.isEmpty()) {
            LOG.info("Updated active versions of {} email templates", emails.size());
        }
    }

    private EmailInfo updateSendGridEmail(Config emailCfg) {
        String key = emailCfg.getString("key");
        String templateId = ConfigUtil.getStrIfPresent(varsCfg, "emails." + key);
        if (templateId == null) {
            throw new DDPException("Could not find email template id for email " + key);
        }

        File file = cfgPath.getParent().resolve(emailCfg.getString("filepath")).toFile();
        String html = readRawHtmlContent(file);
        if (emailCfg.getBoolean("render")) {
            html = renderHtmlContent(file.getName(), html);
        }
        String subject = emailCfg.getString("subject");

        var versionResult = sendGrid.getTemplateActiveVersionId(templateId);
        versionResult.rethrowIfThrown(e -> new DDPException("Error fetching active version for email " + key, e));
        if (versionResult.hasError()) {
            LOG.error("Could not find active version for email {} with template id {}: {}",
                    key, templateId, versionResult.getError());
            return null;
        }
        String versionId = versionResult.getBody();

        // Note: we're not updating version name since we didn't update template name, so we keep them the same.
        var updateResult = sendGrid.updateTemplateVersion(templateId, versionId, null, subject, html);
        updateResult.rethrowIfThrown(e -> new DDPException("Error while updating email " + key, e));
        if (updateResult.hasError()) {
            LOG.error("Error while updating active version {} for email {} with template id {}: {}",
                    versionId, key, templateId, versionResult.getError());
            return null;
        }

        return new EmailInfo(key, templateId, versionId);
    }

    private String readRawHtmlContent(File file) {
        if (!file.exists()) {
            throw new DDPException("File " + file + " does not exist");
        }
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new DDPException(e);
        }
    }

    private String renderHtmlContent(String name, String rawHtml) {
        var ctx = new VelocityContext(varsCfg.root().unwrapped());
        StringWriter writer = new StringWriter();
        velocity.evaluate(ctx, writer, name, rawHtml);
        return writer.toString();
    }

    private static class EmailInfo {
        public String key;
        public String templateId;
        public String versionId;

        public EmailInfo(String key, String templateId, String versionId) {
            this.key = key;
            this.templateId = templateId;
            this.versionId = versionId;
        }
    }
}
