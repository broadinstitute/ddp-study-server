package org.broadinstitute.ddp.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
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
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "<em>$what_age</em>");
            tmpl.addVariable(new TemplateVariable("what_age", Arrays.asList(
                    new Translation("en", "How old are you?"),
                    new Translation("ru", "Сколько вам лет?"))));
            long timestamp = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(userId, timestamp, null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "<em>How old are you?</em>";
            String actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId, timestamp);
            assertEquals(expected, actual);

            langId = LanguageStore.get("ru").getId();
            expected = "<em>Сколько вам лет?</em>";
            actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId, timestamp);
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testRenderPepper506CancerGrade() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "$BRAIN_CANCER_GRADE_grade-high");
            tmpl.addVariable(new TemplateVariable("BRAIN_CANCER_GRADE_grade-high", Arrays.asList(
                    new Translation("en", "High"))));
            long timestamp = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(userId, timestamp, null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "High";
            String actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId, timestamp);
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testRenderPepper506CancerGradeUnderscore() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "$BRAIN_CANCER_GRADE_grade_high");
            tmpl.addVariable(new TemplateVariable("BRAIN_CANCER_GRADE_grade_high", Arrays.asList(
                    new Translation("en", "High"))));
            long timestamp = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(userId, timestamp, null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "High";
            String actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId, timestamp);
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testRenderPepper506CancerGradeLowerCase() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "$brain_cancer_grade_grade-high");
            tmpl.addVariable(new TemplateVariable("brain_cancer_grade_grade-high", Arrays.asList(
                    new Translation("en", "High"))));
            long timestamp = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(userId, timestamp, null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "High";
            String actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId, timestamp);
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testRenderPepper506Cancer() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            Template tmpl = new Template(TemplateType.HTML, null, "$prompt_CHILD_DIAGNOSIS_DATE");
            tmpl.addVariable(new TemplateVariable("prompt_CHILD_DIAGNOSIS_DATE", Arrays.asList(
                    new Translation("en", "When was your child first diagnosed with brain cancer? "
                            + "Please include \\\"month\\\" if known"))));
            long timestamp = Instant.now().toEpochMilli();
            long revId = jdbiRev.insert(userId, timestamp, null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "When was your child first diagnosed with brain cancer? Please include \\\"month\\\" if known";
            String actual = renderer.renderContent(handle, tmpl.getTemplateId(), langId, timestamp);
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testDoubleRenderPass() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            RenderValueProvider valueProvider = new RenderValueProvider.Builder()
                    .setParticipantFirstName("John")
                    .setFirstCompletedDate(LocalDate.of(2000, 1, 1))
                    .setDate(LocalDate.of(1990, 1, 1))
                    .build();

            Map<String, Object> context = new HashMap<>();
            context.put(I18nTemplateConstants.DDP, valueProvider);

            Template tmpl = new Template(TemplateType.HTML, null, "<em>$question_name</em>");
            tmpl.addVariable(new TemplateVariable("question_name", Collections.singletonList(
                    new Translation("en",
                            "Your name is $ddp.participantFirstName()? "
                                    + "This activity was first completed on "
                                    + "$ddp.firstCompletedDate(\"MM / dd / yyyy\", $ddp.date(\"MM / dd / yyyy\"))"))));
            long revId = jdbiRev.insert(userId, Instant.now().toEpochMilli(), null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "<em>Your name is John? This activity was first completed on 01 / 01 / 2000</em>";
            Map<Long, String> actual = renderer.bulkRender(handle, Collections.singleton(tmpl.getTemplateId()),
                    langId, context, Instant.now().toEpochMilli());
            assertEquals(expected, actual.get(tmpl.getTemplateId()));

            handle.rollback();
        });
    }

    @Test
    public void testDoubleRenderPassWithDefaultValue() {
        TransactionWrapper.useTxn(handle -> {
            TemplateDao tmplDao = handle.attach(TemplateDao.class);
            JdbiRevision jdbiRev = handle.attach(JdbiRevision.class);

            RenderValueProvider valueProvider = new RenderValueProvider.Builder()
                    .setParticipantFirstName("John")
                    .setDate(LocalDate.of(1990, 1, 1))
                    .build();

            Map<String, Object> context = new HashMap<>();
            context.put(I18nTemplateConstants.DDP, valueProvider);

            Template tmpl = new Template(TemplateType.HTML, null, "<em>$question_name</em>");
            tmpl.addVariable(new TemplateVariable("question_name", Collections.singletonList(
                    new Translation("en",
                            "Your name is $ddp.participantFirstName()? "
                                    + "This activity was first completed on "
                                    + "$ddp.firstCompletedDate(\"MM / dd / yyyy\", $ddp.date(\"MM / dd / yyyy\"))"))));
            long revId = jdbiRev.insert(userId, Instant.now().toEpochMilli(), null, "add test template");
            tmplDao.insertTemplate(tmpl, revId);
            assertNotNull(tmpl.getTemplateId());

            long langId = LanguageStore.getDefault().getId();
            String expected = "<em>Your name is John? This activity was first completed on 01 / 01 / 1990</em>";
            Map<Long, String> actual = renderer.bulkRender(handle, Collections.singleton(tmpl.getTemplateId()),
                    langId, context, Instant.now().toEpochMilli());
            assertEquals(expected, actual.get(tmpl.getTemplateId()));

            handle.rollback();
        });
    }

    @Test
    public void testRenderContentFailureDueToNullTemplateId() {
        thrown.expect(IllegalArgumentException.class);
        TransactionWrapper.useTxn(handle -> renderer.renderContent(handle, null, 1L, Instant.now().toEpochMilli()));
    }

    @Test
    public void testRenderContentFailureDueToNullLanguageCode() {
        thrown.expect(IllegalArgumentException.class);
        TransactionWrapper.useTxn(handle -> renderer.renderContent(handle, 1L, null, Instant.now().toEpochMilli()));
    }

    @Test
    public void testRenderContentFailureDueMussingTemplateText() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("id 123123123");
        TransactionWrapper.useTxn(handle -> renderer.renderContent(handle, 123123123L, 1L, Instant.now().toEpochMilli()));
    }
}
