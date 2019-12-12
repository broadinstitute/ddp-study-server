package org.broadinstitute.ddp.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class I18nContentRendererTest extends TxnAwareBaseTest {

    private static I18nContentRenderer renderer;
    private static long userId;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        renderer = new I18nContentRenderer();
        userId = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle).getUserId());
    }

    @Test
    public void testRenderContentSuccess() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiLanguageCode jdbiLang = handle.attach(JdbiLanguageCode.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "<em>$what_age</em>");
            tmpl.addVariable(new TemplateVariable("what_age", Arrays.asList(
                    new Translation("en", "How old are you?"),
                    new Translation("ru", "Сколько вам лет?"))));
            long revId = jdbiRev.insert(userId, Instant.now().toEpochMilli(), null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = jdbiLang.getLanguageCodeId("en");
            String expected = "<em>How old are you?</em>";
            String actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId);
            assertEquals(expected, actual);

            langId = jdbiLang.getLanguageCodeId("ru");
            expected = "<em>Сколько вам лет?</em>";
            actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId);
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testRenderContentFailureDueToNullTemplateId() {
        thrown.expect(IllegalArgumentException.class);
        TransactionWrapper.useTxn(handle -> renderer.renderContent(handle, null, 1L));
    }

    @Test
    public void testRenderContentFailureDueToNullLanguageCode() {
        thrown.expect(IllegalArgumentException.class);
        TransactionWrapper.useTxn(handle -> renderer.renderContent(handle, 1L, null));
    }

    @Test
    public void testRenderContentFailureDueMussingTemplateText() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("id 123123123");
        TransactionWrapper.useTxn(handle -> renderer.renderContent(handle, 123123123L, 1L));
    }
}
