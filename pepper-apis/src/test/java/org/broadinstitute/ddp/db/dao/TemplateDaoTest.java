package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TemplateDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testInsertTemplate() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao dao = handle.attach(TemplateDao.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);
            JdbiVariableSubstitution jdbiSub = handle.attach(JdbiVariableSubstitution.class);

            Template tmpl = new Template(TemplateType.HTML, null, "<p>$var</p>");
            tmpl.addVariable(new TemplateVariable("var", Arrays.asList(
                    new Translation("en", "variable english"),
                    new Translation("ru", "variable russian"))));

            long millis = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(testData.getUserId(), millis, null, "test");
            dao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());
            assertNotNull(tmpl.getTemplateCode());

            assertEquals((Long) revId, jdbiTmpl.getRevisionIdIfActive(tmpl.getTemplateId()).get());
            assertEquals(2, jdbiSub.getActiveRevisionIdsByTemplateId(tmpl.getTemplateId()).size());


            //load template
            Template loadedTmpl = dao.loadTemplateById(tmpl.getTemplateId());
            assertNotNull(loadedTmpl.getTemplateId());
            assertNotNull(loadedTmpl.getTemplateCode());
            assertEquals(tmpl.getTemplateCode(), loadedTmpl.getTemplateCode());
            assertEquals(tmpl.getTemplateText(), loadedTmpl.getTemplateText());
            assertEquals("variable russian", HtmlConverter.getPlainText(loadedTmpl.render("ru")));
            assertEquals("variable english", HtmlConverter.getPlainText(loadedTmpl.render("en")));

            handle.rollback();
        });
    }

    @Test
    public void testInsertTemplate_alreadySet() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Template id");
        TransactionWrapper.useTxn(handle -> {
            TemplateDao dao = handle.attach(TemplateDao.class);
            Template tmpl = new Template(TemplateType.TEXT, null, "tmpl");
            tmpl.setTemplateId(1L);
            dao.insertTemplate(tmpl, 1L);
        });
    }

    @Test
    public void testDisableTemplate() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao dao = handle.attach(TemplateDao.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);
            JdbiVariableSubstitution jdbiSub = handle.attach(JdbiVariableSubstitution.class);

            Template tmpl = new Template(TemplateType.HTML, null, "<p>$var</p>");
            tmpl.addVariable(new TemplateVariable("var", Arrays.asList(
                    new Translation("en", "variable english"),
                    new Translation("ru", "variable russian"))));

            long millis = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(testData.getUserId(), millis, null, "test");
            dao.insertTemplate(tmpl, revId);

            RevisionMetadata meta = new RevisionMetadata(millis + 2000L, testData.getUserId(), "test");
            dao.disableTemplate(tmpl.getTemplateId(), meta);
            assertFalse(jdbiTmpl.getRevisionIdIfActive(tmpl.getTemplateId()).isPresent());
            assertEquals(0, jdbiSub.getActiveRevisionIdsByTemplateId(tmpl.getTemplateId()).size());

            handle.rollback();
        });
    }

    @Test
    public void testDisableTemplate_noSubstitutions() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao dao = handle.attach(TemplateDao.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);
            JdbiVariableSubstitution jdbiSub = handle.attach(JdbiVariableSubstitution.class);

            Template tmpl = new Template(TemplateType.TEXT, null, "tmpl");

            long millis = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(testData.getUserId(), millis, null, "test");
            dao.insertTemplate(tmpl, revId);

            RevisionMetadata meta = new RevisionMetadata(millis + 2000L, testData.getUserId(), "test");
            dao.disableTemplate(tmpl.getTemplateId(), meta);
            assertFalse(jdbiTmpl.getRevisionIdIfActive(tmpl.getTemplateId()).isPresent());
            assertEquals(0, jdbiSub.getActiveRevisionIdsByTemplateId(tmpl.getTemplateId()).size());

            handle.rollback();
        });
    }

    @Test
    public void testDisableTemplate_notCurrentlyActive() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("active template");
        TransactionWrapper.useTxn(handle -> {
            TemplateDao dao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "<p>$var</p>");
            tmpl.addVariable(new TemplateVariable("var", Arrays.asList(
                    new Translation("en", "variable english"),
                    new Translation("ru", "variable russian"))));

            long millis = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(testData.getUserId(), millis, null, "test");
            dao.insertTemplate(tmpl, revId);

            // Terminate template
            RevisionMetadata meta = new RevisionMetadata(millis + 2000L, testData.getUserId(), "test");
            dao.disableTemplate(tmpl.getTemplateId(), meta);

            // Terminate it again
            dao.disableTemplate(tmpl.getTemplateId(), meta);

            handle.rollback();
        });
    }
}
