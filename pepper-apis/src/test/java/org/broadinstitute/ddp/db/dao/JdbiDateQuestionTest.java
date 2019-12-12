package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Year;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiDateQuestionTest extends TxnAwareBaseTest {

    private static final String SID_DATE_TEXT = "DATE_F84700E19899";
    private static final String SID_DATE_SINGLE_TEXT = "DATE_2F192E9E2C00";
    private static final String SID_DATE_PICKLIST = "DATE_647BD81B017D";

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetDateQuestionDtoByQuestionId_notFound() {
        TransactionWrapper.useTxn(handle -> {
            JdbiDateQuestion dao = handle.attach(JdbiDateQuestion.class);
            Optional<DateQuestionDto> dto = dao.findDtoByQuestionId(-1);
            assertNotNull(dto);
            assertFalse(dto.isPresent());
        });
    }

    @Test
    public void testGetDateQuestionDtoByQuestionId() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = insertDateActivity(handle);
            assertDtoData(handle, act, SID_DATE_TEXT, DateRenderMode.TEXT, false);
            assertDtoData(handle, act, SID_DATE_SINGLE_TEXT, DateRenderMode.SINGLE_TEXT, true);
            assertDtoData(handle, act, SID_DATE_PICKLIST, DateRenderMode.PICKLIST, true);
            handle.rollback();
        });
    }

    private void assertDtoData(Handle handle, FormActivityDef activity, String stableId, DateRenderMode expectedMode,
                               boolean expectedDisplay) {
        long questionId = extractQuestion(activity, stableId).getQuestionId();

        JdbiDateQuestion dao = handle.attach(JdbiDateQuestion.class);
        Optional<DateQuestionDto> dto = dao.findDtoByQuestionId(questionId);

        assertTrue(dto.isPresent());
        assertEquals(expectedMode, dto.get().getRenderMode());
        assertEquals(expectedDisplay, dto.get().shouldDisplayCalendar());
    }

    @Test
    public void testGetDatePicklistConfigByQuestionId_notFound() {
        TransactionWrapper.useTxn(handle -> {
            JdbiDateQuestion dao = handle.attach(JdbiDateQuestion.class);
            Optional<DatePicklistDef> config = dao.getDatePicklistDefByQuestionId(-1);
            assertNotNull(config);
            assertFalse(config.isPresent());
        });
    }

    @Test
    public void testGetDatePicklistConfigByQuestionId_noPicklistConfigData() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = insertDateActivity(handle);
            long questionId = extractQuestion(act, SID_DATE_TEXT).getQuestionId();

            JdbiDateQuestion dao = handle.attach(JdbiDateQuestion.class);
            Optional<DatePicklistDef> config = dao.getDatePicklistDefByQuestionId(questionId);

            assertTrue(config.isPresent());
            assertNull(config.get().getUseMonthNames());
            assertNull(config.get().getYearsForward());
            assertNull(config.get().getYearsBack());
            assertNull(config.get().getYearAnchor());
            assertNull(config.get().getFirstSelectedYear());

            handle.rollback();
        });
    }

    @Test
    public void testGetDatePicklistConfigByQuestionId() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = insertDateActivity(handle);
            long questionId = extractQuestion(act, SID_DATE_PICKLIST).getQuestionId();

            JdbiDateQuestion dao = handle.attach(JdbiDateQuestion.class);
            Optional<DatePicklistDef> config = dao.getDatePicklistDefByQuestionId(questionId);

            assertTrue(config.isPresent());
            assertTrue(config.get().getUseMonthNames());
            assertEquals((Integer) 3, config.get().getYearsForward());
            assertEquals((Integer) 80, config.get().getYearsBack());
            assertEquals((Integer) 1988, config.get().getFirstSelectedYear());
            assertNull(config.get().getYearAnchor());

            assertEquals(Year.now().getValue() - 80, config.get().getStartYear());
            assertEquals(Year.now().getValue() + 3, config.get().getEndYear());

            handle.rollback();
        });
    }

    private DateQuestionDef extractQuestion(FormActivityDef activity, String stableId) {
        return activity.getSections().get(0).getBlocks().stream()
                .map(block -> (DateQuestionDef) ((QuestionBlockDef) block).getQuestion())
                .filter(question -> question.getStableId().equals(stableId))
                .findFirst().get();
    }

    private FormActivityDef insertDateActivity(Handle handle) {
        DateQuestionDef d1 = DateQuestionDef.builder().setStableId(SID_DATE_TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "d1"))
                .setRenderMode(DateRenderMode.TEXT).addFields(DateFieldType.YEAR)
                .build();
        DateQuestionDef d2 = DateQuestionDef.builder().setStableId(SID_DATE_SINGLE_TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "d2"))
                .setRenderMode(DateRenderMode.SINGLE_TEXT).addFields(DateFieldType.YEAR)
                .setDisplayCalendar(true)
                .build();
        DateQuestionDef d3 = DateQuestionDef.builder().setStableId(SID_DATE_PICKLIST)
                .setPrompt(new Template(TemplateType.TEXT, null, "d3"))
                .setRenderMode(DateRenderMode.PICKLIST).addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .setPicklistDef(new DatePicklistDef(true, 3, 80, null, 1988))
                .setDisplayCalendar(true)
                .build();
        FormActivityDef form = FormActivityDef.generalFormBuilder("act", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "date test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(d1, d2, d3)))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add date activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
