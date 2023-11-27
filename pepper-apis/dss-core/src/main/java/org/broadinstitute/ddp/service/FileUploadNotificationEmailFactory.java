package org.broadinstitute.ddp.service;

import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.mail.MailTemplateSubstitution;
import org.broadinstitute.ddp.service.mail.MailTemplateService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileUploadNotificationEmailFactory {
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/dd/yyyy");
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");

    public static Mail create(final StudyDto study, final String userGuid, final List<FileUpload> fileUploads) {
        if (study.getNotificationMailTemplateId() == null) {
            throw new DDPException("Can't create e-mail. Study " + study.getGuid() + " doesn't have mail template");
        }

        final var mailTemplate = MailTemplateService.getTemplate(study.getNotificationMailTemplateId());
        mailTemplate.setSubstitutions("FILE_UPLOAD_RECORD", StreamEx.of(fileUploads)
                .map(fileUpload -> toSubstitution(userGuid, fileUpload))
                .toList());

        return new Mail(
                new Email(study.getStudyEmail()),
                mailTemplate.renderSubject(),
                new Email(study.getNotificationEmail()),
                new Content(
                        mailTemplate.getContentType(),
                        mailTemplate.renderBody()));
    }

    private static MailTemplateSubstitution toSubstitution(final String userGuid, final FileUpload fileUpload) {
        return new MailTemplateSubstitution()
                .withValue("USER_GUID", userGuid)
                .withValue("FILE_NAME", fileUpload.getFileName())
                .withValue("FILE_SIZE", fileUpload.getHumanReadableFileSize())
                .withValue("UPLOADED_DATE", dateFormatter.format(Date.from(fileUpload.getCreatedAt())))
                .withValue("UPLOADED_TIME", timeFormatter.format(Date.from(fileUpload.getCreatedAt())));
    }
}
