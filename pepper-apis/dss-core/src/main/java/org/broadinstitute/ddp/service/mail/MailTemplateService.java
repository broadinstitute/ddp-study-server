package org.broadinstitute.ddp.service.mail;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.MailTemplateDao;
import org.broadinstitute.ddp.db.dao.MailTemplateRepeatableElementDao;
import org.broadinstitute.ddp.db.dto.MailTemplateDto;
import org.broadinstitute.ddp.db.dto.MailTemplateRepeatableElementDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.mail.MailTemplate;
import org.broadinstitute.ddp.model.mail.MailTemplateRepeatableElement;
import org.jdbi.v3.core.Handle;

import java.util.HashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MailTemplateService {
    public static MailTemplate getTemplate(final long id) {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> getTemplate(handle, id));
    }

    private static MailTemplate getTemplate(final Handle handle, final long id) {
        final var mailTemplate = handle.attach(MailTemplateDao.class).findById(id);
        if (mailTemplate.isEmpty()) {
            throw new DDPException("Mail template #" + id + " does not exist");
        }

        return getTemplate(handle, mailTemplate.get());
    }

    private static MailTemplate getTemplate(final Handle handle, final MailTemplateDto mailTemplate) {
        final var repeatableElements = handle.attach(MailTemplateRepeatableElementDao.class).findByMailTemplateId(mailTemplate.getId());

        return new MailTemplate(
                mailTemplate.getContentType(),
                mailTemplate.getSubject(),
                mailTemplate.getBody(),
                StreamEx.of(repeatableElements).map(MailTemplateService::toRepeatableElement).toList());
    }

    private static MailTemplateRepeatableElement toRepeatableElement(final MailTemplateRepeatableElementDto repeatableElement) {
        return new MailTemplateRepeatableElement(repeatableElement.getName(), repeatableElement.getContent(), new HashMap<>());
    }
}
