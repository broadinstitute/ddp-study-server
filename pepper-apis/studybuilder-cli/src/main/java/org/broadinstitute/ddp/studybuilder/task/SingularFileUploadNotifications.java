package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.MailTemplateDao;
import org.broadinstitute.ddp.db.dao.MailTemplateRepeatableElementDao;
import org.broadinstitute.ddp.db.dto.MailTemplateDto;
import org.broadinstitute.ddp.db.dto.MailTemplateRepeatableElementDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Task to update existing singular study by specifying newly added mail template
 */
@Slf4j
public class SingularFileUploadNotifications implements CustomTask {
    private static final String RECORD_FILE = "emails/file-upload-notification/file-record-en.html";
    private static final String BODY_FILE   = "emails/file-upload-notification/en.html";
    private static final String STUDY_GUID  = "singular";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(final Path cfgPath, final Config studyCfg, final Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        final var file = cfgPath.getParent().toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }

        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(final Handle handle) {
        log.info("TASK:: {}", SingularFileUploadNotifications.class.getSimpleName());

        final var mailTemplateId = insertMailTemplate(handle);
        insertMailTemplateRepeatableElement(handle, mailTemplateId);
        log.info("Mail template inserted");

        handle.attach(SqlHelper.class).updateStudyMailTemplate(cfg.getString("study.guid"), mailTemplateId);
        log.info("Study {} mail template was updated", cfg.getString("study.guid"));
    }

    @SneakyThrows
    private long insertMailTemplate(final Handle handle) {
        return handle.attach(MailTemplateDao.class).insert(MailTemplateDto.builder()
                .contentType("text/html")
                .name("Project Singular: File Upload Notification")
                .subject("Project Singular: New Participant File(s) Uploaded")
                .body(Files.readString(new File(BODY_FILE).toPath()))
                .build());
    }

    @SneakyThrows
    private void insertMailTemplateRepeatableElement(final Handle handle, final long mailTemplateId) {
        handle.attach(MailTemplateRepeatableElementDao.class).insert(MailTemplateRepeatableElementDto.builder()
                .mailTemplateId(mailTemplateId)
                .name("FILE_UPLOAD_RECORD")
                .content(Files.readString(new File(RECORD_FILE).toPath()))
                .build());
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("UPDATE umbrella_study SET notification_mail_template_id=:mailTemplateId WHERE guid=:studyGuid")
        void updateStudyMailTemplate(@Bind("studyGuid") final String studyGuid, @Bind("mailTemplateId") final long mailTemplateId);
    }
}
