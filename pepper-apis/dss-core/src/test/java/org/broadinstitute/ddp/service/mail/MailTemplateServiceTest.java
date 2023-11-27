package org.broadinstitute.ddp.service.mail;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.MailTemplateDao;
import org.broadinstitute.ddp.db.dao.MailTemplateRepeatableElementDao;
import org.broadinstitute.ddp.db.dto.MailTemplateDto;
import org.broadinstitute.ddp.db.dto.MailTemplateRepeatableElementDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.mail.MailTemplateSubstitution;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MailTemplateServiceTest extends TxnAwareBaseTest {
    private static Long TEMPLATE_ID;

    @BeforeClass
    public static void beforeClass() {
        TransactionWrapper.useTxn(MailTemplateServiceTest::init);
    }

    private static void init(final Handle handle) {
        TEMPLATE_ID = handle.attach(MailTemplateDao.class).insert(MailTemplateDto.builder()
                .subject("This is the subject")
                .body("Dear, ${USER} This is the body!")
                .contentType("text/html")
                .build());

        handle.attach(MailTemplateRepeatableElementDao.class).insert(MailTemplateRepeatableElementDto.builder()
                .mailTemplateId(TEMPLATE_ID)
                .name("USER")
                .content("<b>${TITLE} ${NAME}</b>")
                .build());
    }

    @Test(expected = DDPException.class)
    public void testGetNotExistingTemplate() {
        MailTemplateService.getTemplate(-10L);
    }

    @Test
    public void testGetRawTemplate() {
        final var template = MailTemplateService.getTemplate(TEMPLATE_ID);

        assertNotNull("The template must exist", template);
        assertNotNull("Content type must be set", template.getContentType());
        assertNotNull("Subject must be set", template.getSubject());
        assertNotNull("Body must be set", template.getBody());

        assertNotNull("Subject rendering must work", template.renderSubject());
        assertNotNull("Body rendering must work", template.renderBody());

        assertTrue("Rendered body must contain template", template.renderBody().contains("${USER}"));
    }

    @Test
    public void testTemplateWithSubstitution() {
        final var template = MailTemplateService.getTemplate(TEMPLATE_ID);

        assertNotNull("The template must exist", template);
        assertNotNull("Body rendering must work", template.renderBody());

        template.setSubstitutions("USER",
                new MailTemplateSubstitution()
                        .withValue("TITLE", "M.Sc.")
                        .withValue("NAME", "Dmitrii"),
                new MailTemplateSubstitution()
                        .withValue("TITLE", "B.Sc.")
                        .withValue("NAME", "Alex"));

        assertFalse("Rendered body must not contain template", template.renderBody().contains("${USER}"));
        assertTrue("Rendered body must contain Dmitrii", template.renderBody().contains("M.Sc. Dmitrii"));
        assertTrue("Rendered body must contain Alex", template.renderBody().contains("B.Sc. Alex"));
    }
}
