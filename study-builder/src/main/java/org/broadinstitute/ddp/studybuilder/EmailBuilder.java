package org.broadinstitute.ddp.studybuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(EmailBuilder.class);
    private static final int PROGRESS_STEP_COUNT = 5;

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

    public void createAll() {
        createForConfigs(List.copyOf(studyCfg.getConfigList("sendgridEmails")));
    }

    public void createForFiles(String[] filepaths) {
        List<Config> found = studyCfg.getConfigList("sendgridEmails")
                .stream()
                .filter(cfg -> {
                    String path = cfgPath.getParent().resolve(cfg.getString("filepath")).toString();
                    for (var filepath : filepaths) {
                        if (path.endsWith(filepath)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
        if (found.size() != filepaths.length) {
            throw new DDPException("Could not find emails for all specified filepaths");
        }
        createForConfigs(found);
    }

    private void createForConfigs(List<Config> emailCfgs) {
        List<EmailInfo> emails = new ArrayList<>();
        for (int i = 0; i < emailCfgs.size(); i++) {
            Config emailCfg = emailCfgs.get(i);
            EmailInfo email = createSendGridEmail(emailCfg);
            if (email != null) {
                emails.add(email);
            }
            if ((i + 1) % PROGRESS_STEP_COUNT == 0) {
                LOG.info("Processed {}/{} emails", i + 1, emailCfgs.size());
            }
        }

        if (!emails.isEmpty()) {
            String table = emails.stream()
                    .map(email -> String.format("name=%s templateId=%s versionId=%s",
                            email.name, email.templateId, email.versionId))
                    .collect(Collectors.joining("\n"));
            LOG.info("Created {} email templates:\n{}", emails.size(), table);
        }
    }

    private EmailInfo createSendGridEmail(Config emailCfg) {
        String name = emailCfg.getString("name");
        String templateId = emailCfg.getString("templateId");
        if (!templateId.isEmpty()) {
            LOG.error("Email {} already has template id set to {}, not creating", name, templateId);
            return null;
        }

        File file = cfgPath.getParent().resolve(emailCfg.getString("filepath")).toFile();
        String html = readRawHtmlContent(file);
        if (emailCfg.getBoolean("render")) {
            html = renderHtmlContent(file.getName(), html);
        }
        String subject = emailCfg.getString("subject");

        var templateResult = sendGrid.createTemplate(name);
        templateResult.rethrowIfThrown(DDPException::new);
        if (templateResult.hasError()) {
            LOG.error("Error while creating email with name {}: {}", name, templateResult.getError());
            return null;
        }

        templateId = templateResult.getBody().getId();
        String versionId = null;

        var versionResult = sendGrid.createTemplateVersion(templateId, name, subject, html, true);
        versionResult.rethrowIfThrown(DDPException::new);
        if (versionResult.hasError()) {
            LOG.error("Error while creating version for email with name {}."
                            + " You might need to manually create the version and run update-emails to load HTML content: {}",
                    name, versionResult.getError());
        } else {
            versionId = versionResult.getBody().getId();
        }

        return new EmailInfo(name, templateId, versionId);
    }

    public void updateAll() {
        updateForConfigs(List.copyOf(studyCfg.getConfigList("sendgridEmails")));
    }

    public void updateForTemplates(String[] templateIds) {
        Set<String> ids = Set.of(templateIds);
        List<Config> found = studyCfg.getConfigList("sendgridEmails")
                .stream()
                .filter(cfg -> ids.contains(cfg.getString("templateId")))
                .collect(Collectors.toList());
        if (found.size() != templateIds.length) {
            throw new DDPException("Could not find emails for all specified template ids");
        }
        updateForConfigs(found);
    }

    private void updateForConfigs(List<Config> emailCfgs) {
        List<EmailInfo> emails = new ArrayList<>();
        for (int i = 0; i < emailCfgs.size(); i++) {
            Config emailCfg = emailCfgs.get(i);
            EmailInfo email = updateSendGridEmail(emailCfg);
            if (email != null) {
                emails.add(email);
            }
            if ((i + 1) % PROGRESS_STEP_COUNT == 0) {
                LOG.info("Processed {}/{} emails", i + 1, emailCfgs.size());
            }
        }

        if (!emails.isEmpty()) {
            String table = emails.stream()
                    .map(email -> String.format("name=%s templateId=%s versionId=%s",
                            email.name, email.templateId, email.versionId))
                    .collect(Collectors.joining("\n"));
            LOG.info("Updated active versions of {} email templates:\n{}", emails.size(), table);
        }
    }

    private EmailInfo updateSendGridEmail(Config emailCfg) {
        File file = cfgPath.getParent().resolve(emailCfg.getString("filepath")).toFile();
        String html = readRawHtmlContent(file);
        if (emailCfg.getBoolean("render")) {
            html = renderHtmlContent(file.getName(), html);
        }
        String subject = emailCfg.getString("subject");
        String name = emailCfg.getString("name");
        String templateId = emailCfg.getString("templateId");

        var versionResult = sendGrid.getTemplateActiveVersionId(templateId);
        versionResult.rethrowIfThrown(DDPException::new);
        if (versionResult.hasError()) {
            LOG.error("Could not find active version for email template id {}: {}",
                    templateId, versionResult.getError());
            return null;
        }
        String versionId = versionResult.getBody();

        // Note: we're not updating version name since we didn't update template name, so we keep them the same.
        var updateResult = sendGrid.updateTemplateVersion(templateId, versionId, null, subject, html);
        updateResult.rethrowIfThrown(DDPException::new);
        if (updateResult.hasError()) {
            LOG.error("Error while updating active version {} for email template id {}: {}",
                    versionId, templateId, versionResult.getError());
            return null;
        }

        return new EmailInfo(name, templateId, versionId);
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
        public String name;
        public String templateId;
        public String versionId;

        public EmailInfo(String name, String templateId, String versionId) {
            this.name = name;
            this.templateId = templateId;
            this.versionId = versionId;
        }
    }
}
