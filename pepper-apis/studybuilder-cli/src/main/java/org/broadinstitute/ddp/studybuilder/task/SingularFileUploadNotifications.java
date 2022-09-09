package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.MailTemplateDao;
import org.broadinstitute.ddp.db.dao.MailTemplateRepeatableElementDao;
import org.broadinstitute.ddp.db.dto.MailTemplateDto;
import org.broadinstitute.ddp.db.dto.MailTemplateRepeatableElementDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Task to update existing singular study by specifying newly added mail template.
 * If existing template is set for the study, it will be deleted.
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
        final var sqlHelper = handle.attach(SqlHelper.class);
        log.info("TASK:: {}", SingularFileUploadNotifications.class.getSimpleName());

        final var study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        log.info("Study has following mail template #{}", study.getNotificationMailTemplateId());

        sqlHelper.updateStudyMailTemplate(STUDY_GUID, null);
        log.info("Existing {} study was unlinked from the mail template", STUDY_GUID);

        if (study.getNotificationMailTemplateId() != null) {
            sqlHelper.deleteMailTemplateRepeatableElements(study.getNotificationMailTemplateId());
            sqlHelper.deleteMailTemplate(study.getNotificationMailTemplateId());
            log.info("Existing mail template #{} was removed", study.getNotificationMailTemplateId());
        }

        final var mailTemplateId = insertMailTemplate(handle);
        insertMailTemplateRepeatableElement(handle, mailTemplateId);
        log.info("Mail template #{} inserted", mailTemplateId);

        sqlHelper.updateStudyMailTemplate(cfg.getString("study.guid"), mailTemplateId);
        log.info("Study {} was linked to mail template #{}", cfg.getString("study.guid"), mailTemplateId);
    }

    @SneakyThrows
    private long insertMailTemplate(final Handle handle) {
        return handle.attach(MailTemplateDao.class).insert(MailTemplateDto.builder()
                .contentType("text/html")
                .subject("Project Singular: New Participant File(s) Uploaded")
                .body(Files.readString(cfgPath.getParent().resolve(BODY_FILE)))
                .build());
    }

    @SneakyThrows
    private void insertMailTemplateRepeatableElement(final Handle handle, final long mailTemplateId) {
        handle.attach(MailTemplateRepeatableElementDao.class).insert(MailTemplateRepeatableElementDto.builder()
                .mailTemplateId(mailTemplateId)
                .name("FILE_UPLOAD_RECORD")
                .content(Files.readString(cfgPath.getParent().resolve(RECORD_FILE)))
                .build());
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("UPDATE umbrella_study SET notification_mail_template_id=:mailTemplateId WHERE guid=:studyGuid")
        void updateStudyMailTemplate(@Bind("studyGuid") final String studyGuid, @Bind("mailTemplateId") final Long mailTemplateId);

        @SqlUpdate("DELETE FROM mail_template_repeatable_element WHERE mail_template_id = :mailTemplateId")
        void deleteMailTemplateRepeatableElements(@Bind("mailTemplateId") final long mailTemplateId);

        @SqlUpdate("DELETE FROM mail_template WHERE mail_template_id = :mailTemplateId")
        void deleteMailTemplate(@Bind("mailTemplateId") final long mailTemplateId);
    }
}
