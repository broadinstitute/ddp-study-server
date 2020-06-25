package org.broadinstitute.ddp.db.dao;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.db.FormInstanceDao;
import org.broadinstitute.ddp.db.SectionBlockDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RegexRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.DateRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.LengthRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RegexRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class FormActivityDaoTest extends TxnAwareBaseTest {

    private static final String DUMMY_QSID = "QUESTION_SID";

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static ActivityInstanceService service;

    @BeforeClass
    public static void setup() {
        org.broadinstitute.ddp.db.ActivityInstanceDao actInstDao = new org.broadinstitute.ddp.db.ActivityInstanceDao(
                FormInstanceDao.fromDaoAndConfig(new SectionBlockDao(), sqlConfig));
        service = new ActivityInstanceService(actInstDao, new TreeWalkInterpreter());
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testInsertActivity_singleTemplateBlock() {
        TemplateVariable var = new TemplateVariable("single_var", Collections.singletonList(new Translation("en", "test content")));
        Template tmpl = new Template(TemplateType.HTML, "tmpl", "<p>$single_var</p>");
        tmpl.addVariable(var);
        FormActivityDef form = buildSingleBlockForm(testData.getStudyGuid(), "Template Activity", new ContentBlockDef(tmpl));

        TransactionWrapper.useTxn(handle -> {
            FormInstance inst = runInsertAndFetchInstance(handle, form, testData.getUserGuid());

            assertEquals("Template Activity", inst.getTitle());
            assertEquals(FormType.GENERAL, inst.getFormType());

            FormBlock block = inst.getBodySections().get(0).getBlocks().get(0);
            assertEquals(BlockType.CONTENT, block.getBlockType());

            ContentBlock contentBlock = (ContentBlock) block;
            assertEquals("<p>test content</p>", contentBlock.getBody());

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_booleanQuestion() {
        Template prompt = new Template(TemplateType.TEXT, "prompt", "bool question");

        Template header = new Template(TemplateType.TEXT, "header", "info header");
        Template footer = new Template(TemplateType.TEXT, "footer", "info footer");

        Template yesOpt = new Template(TemplateType.TEXT, "yes", "true");
        Template noOpt = new Template(TemplateType.TEXT, "no", "false");

        Template reqHint = new Template(TemplateType.TEXT, "val", "required hint");
        List<RuleDef> rules = Collections.singletonList(new RequiredRuleDef(reqHint));

        BoolQuestionDef boolQuestion = new BoolQuestionDef(DUMMY_QSID,
                                                            false,
                                                            prompt,
                                                            header,
                                                            footer,
                                                            rules,
                                                            yesOpt,
                                                            noOpt,
                                                            true);
        FormActivityDef form = buildSingleBlockForm(testData.getStudyGuid(), "Boolean Activity", new QuestionBlockDef(boolQuestion));

        TransactionWrapper.useTxn(handle -> {
            FormInstance inst = runInsertAndFetchInstance(handle, form, testData.getUserGuid());

            assertEquals("Boolean Activity", inst.getTitle());
            BoolQuestion question = unwrapSingleBlockQuestion(inst, BoolQuestion.class);

            assertEquals(DUMMY_QSID, question.getStableId());
            assertTrue(HtmlConverter.hasSameValue("bool question", question.getPrompt()));
            assertEquals("bool question", question.getTextPrompt());
            assertEquals("true", question.getTrueContent());
            assertEquals("false", question.getFalseContent());

            assertEquals(1, question.getValidations().size());
            Rule<BoolAnswer> rule = question.getValidations().get(0);
            assertEquals(RuleType.REQUIRED, rule.getRuleType());
            assertEquals("required hint", rule.getCorrectionHint());
            assertEquals(rule.getMessage(), rule.getCorrectionHint());

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_textQuestion() {
        Template prompt = new Template(TemplateType.TEXT, "prompt", "text question");

        Template header = new Template(TemplateType.TEXT, "header", "info header");
        Template footer = new Template(TemplateType.TEXT, "footer", "info footer");

        Template lengthHint = new Template(TemplateType.TEXT, "lenVal", "length hint");
        Template regexHint = new Template(TemplateType.TEXT, "rgxVal", "regex hint");

        List<RuleDef> rules = Arrays.asList(
                new RequiredRuleDef(null),
                new LengthRuleDef(lengthHint, 10, 20),
                new RegexRuleDef(regexHint, "abc"));

        TextQuestionDef textQuestion = new TextQuestionDef(DUMMY_QSID,
                                                            false,
                                                            prompt,
                                                            header,
                                                            footer,
                                                            null,
                                                            rules,
                                                            TextInputType.TEXT,
                                                            true);
        
        FormActivityDef form = buildSingleBlockForm(testData.getStudyGuid(), "Text Activity", new QuestionBlockDef(textQuestion));

        TransactionWrapper.useTxn(handle -> {
            FormInstance inst = runInsertAndFetchInstance(handle, form, testData.getUserGuid());

            assertEquals("Text Activity", inst.getTitle());
            TextQuestion question = unwrapSingleBlockQuestion(inst, TextQuestion.class);

            assertEquals(DUMMY_QSID, question.getStableId());
            assertTrue(HtmlConverter.hasSameValue("text question", question.getPrompt()));
            assertEquals("text question", question.getTextPrompt());
            assertEquals(TextInputType.TEXT, question.getInputType());

            assertEquals(rules.size(), question.getValidations().size());
            for (Rule<TextAnswer> rule : question.getValidations()) {
                switch (rule.getRuleType()) {
                    case REQUIRED:
                        assertNotNull(rule.getDefaultMessage());
                        assertNull(rule.getCorrectionHint());
                        assertEquals(rule.getMessage(), rule.getDefaultMessage());
                        break;
                    case LENGTH:
                        assertEquals("length hint", rule.getCorrectionHint());
                        assertEquals((Integer) 10, ((LengthRule) rule).getMin());
                        assertEquals((Integer) 20, ((LengthRule) rule).getMax());
                        break;
                    case REGEX:
                        assertEquals("regex hint", rule.getCorrectionHint());
                        assertEquals("abc", ((RegexRule) rule).getPattern());
                        break;
                    default:
                        fail("unrecognized rule " + rule.getRuleType());
                }
            }

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_dateQuestion() {
        runInsertDateQuestionTest(false);
        runInsertDateQuestionTest(true);
    }

    @Test
    public void testInsertActivity_picklistQuestion() {
        runInsertPicklistQuestionTest(false);
        runInsertPicklistQuestionTest(true);
    }

    @Test
    public void testInsertActivity_version() {
        FormActivityDef form = FormActivityDef.formBuilder(FormType.GENERAL, "DUMMY_ACTIVITY", "v1.1.23", testData.getStudyGuid())
                .addName(new Translation("en", "dummy activity"))
                .build();
        TransactionWrapper.useTxn(handle -> {
            long millis = Instant.now().toEpochMilli();
            long revId = handle.attach(JdbiRevision.class).insert(testData.getUserId(), millis, null, "testing");

            handle.attach(FormActivityDao.class).insertActivity(form, revId);
            assertNotNull(form.getActivityId());

            List<ActivityVersionDto> versions = handle.attach(JdbiActivityVersion.class)
                    .findAllVersionsInAscendingOrder(form.getActivityId());
            assertNotNull(versions);
            assertEquals(1, versions.size());
            assertEquals(form.getActivityId(), (Long) versions.get(0).getActivityId());
            assertEquals("v1.1.23", versions.get(0).getVersionTag());
            assertEquals(revId, versions.get(0).getRevId());

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_blockShownExpression() {
        Template tmpl = new Template(TemplateType.TEXT, "tmpl", "test block expr");
        ContentBlockDef block = new ContentBlockDef(tmpl);
        block.setShownExpr("true && false");

        FormActivityDef form = buildSingleBlockForm(testData.getStudyGuid(), "dummy activity", block);

        TransactionWrapper.useTxn(handle -> {
            FormInstance inst = runInsertAndFetchInstance(handle, form, testData.getUserGuid());
            ContentBlock actual = (ContentBlock) inst.getBodySections().get(0).getBlocks().get(0);

            assertNotNull(actual.getShownExpr());
            assertFalse(actual.isShown());

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_canUseSameQuestionStableIdInDifferentStudies() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDao dao = handle.attach(FormActivityDao.class);

            String stableId = "SOME_STABLE_ID";
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), testData.getUserId(), "test");


            StudyDto study1 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            TextQuestionDef q1 = TextQuestionDef.builder(TextInputType.TEXT, stableId,
                    new Template(TemplateType.TEXT, null, "q1 prompt")).build();
            FormActivityDef form1 = buildSingleBlockForm(study1.getGuid(), "act1", new QuestionBlockDef(q1));
            dao.insertActivity(form1, revId);
            assertNotNull(form1.getActivityId());

            revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), testData.getUserId(), "test");
            StudyDto study2 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            TextQuestionDef q2 = TextQuestionDef.builder(TextInputType.TEXT, stableId,
                    new Template(TemplateType.TEXT, null, "q2 prompt")).build();
            FormActivityDef form2 = buildSingleBlockForm(study2.getGuid(), "act2", new QuestionBlockDef(q2));
            dao.insertActivity(form2, revId);
            assertNotNull(form1.getActivityId());

            handle.rollback();
        });
    }

    private FormActivityDef buildSingleBlockForm(String studyGuid, String title, FormBlockDef block) {
        return FormActivityDef.formBuilder(FormType.GENERAL, "ACTIVITY" + System.currentTimeMillis(), "v1", studyGuid)
                .setMaxInstancesPerUser(1)
                .setDisplayOrder(1)
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("en", title))
                .addSection(new FormSectionDef(null, Collections.singletonList(block)))
                .build();
    }

    private <T extends Question> T unwrapSingleBlockQuestion(FormInstance inst, Class<T> clazz) {
        assertEquals(FormType.GENERAL, inst.getFormType());
        FormBlock block = inst.getBodySections().get(0).getBlocks().get(0);
        assertEquals(BlockType.QUESTION, block.getBlockType());
        Question questionBlock = ((QuestionBlock) block).getQuestion();
        return clazz.cast(questionBlock);
    }

    private FormInstance runInsertAndFetchInstance(Handle handle, FormActivityDef form, String userGuid) {
        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "testing");

        handle.attach(FormActivityDao.class).insertActivity(form, revId);
        assertNotNull(form.getActivityId());

        String instanceGuid = handle.attach(ActivityInstanceDao.class)
                .insertInstance(form.getActivityId(), userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
        Optional<FormInstance> inst = service.getTranslatedForm(handle, userGuid, instanceGuid, "en", ContentStyle.STANDARD);
        assertTrue(inst.isPresent());

        return inst.get();
    }

    private void    runInsertPicklistQuestionTest(boolean allowDetails) {
        TransactionWrapper.useTxn(handle -> {
            Template prompt = new Template(TemplateType.TEXT, "prompt", "picklist question");
            Template label = new Template(TemplateType.TEXT, "label", "picklist label");

            Template otherOptionLabel = new Template(TemplateType.TEXT, null, "other option label");
            Template otherOptionDetail = new Template(TemplateType.TEXT, null, "detail field label");
            PicklistOptionDef otherOption;
            if (allowDetails) {
                otherOption = new PicklistOptionDef("PO_OTHER", otherOptionLabel, otherOptionDetail);
            } else {
                otherOption = new PicklistOptionDef("PO_OTHER", otherOptionLabel);
            }

            List<PicklistOptionDef> options = Arrays.asList(
                    new PicklistOptionDef("PO1", new Template(TemplateType.TEXT, "po1", "option 1")),
                    new PicklistOptionDef("PO2", new Template(TemplateType.TEXT, "po2", "option 2")),
                    otherOption);

            List<RuleDef> rules = Collections.singletonList(new RequiredRuleDef(null));

            PicklistQuestionDef picklistQuestion = PicklistQuestionDef
                    .buildMultiSelect(PicklistRenderMode.DROPDOWN, DUMMY_QSID, prompt)
                    .setLabel(label)
                    .addOptions(options)
                    .addValidations(rules)
                    .build();

            FormActivityDef form = buildSingleBlockForm(testData.getStudyGuid(), "Picklist Activity",
                    new QuestionBlockDef(picklistQuestion));
            FormInstance inst = runInsertAndFetchInstance(handle, form, testData.getUserGuid());

            assertEquals("Picklist Activity", inst.getTitle());
            PicklistQuestion question = unwrapSingleBlockQuestion(inst, PicklistQuestion.class);

            assertEquals(DUMMY_QSID, question.getStableId());
            assertTrue(HtmlConverter.hasSameValue("picklist question", question.getPrompt()));
            assertEquals("picklist question", question.getTextPrompt());
            assertEquals("picklist label", question.getPicklistLabel());
            assertEquals(PicklistSelectMode.MULTIPLE, question.getSelectMode());
            assertEquals(PicklistRenderMode.DROPDOWN, question.getRenderMode());

            assertEquals(options.size(), question.getPicklistOptions().size());
            for (PicklistOption option : question.getPicklistOptions()) {
                if ("PO1".equals(option.getStableId())) {
                    assertEquals("option 1", option.getOptionLabel());
                    assertFalse(option.isDetailsAllowed());
                } else if ("PO2".equals(option.getStableId())) {
                    assertEquals("option 2", option.getOptionLabel());
                    assertFalse(option.isDetailsAllowed());
                } else if ("PO_OTHER".equals(option.getStableId())) {
                    assertEquals("other option label", option.getOptionLabel());
                    if (allowDetails) {
                        assertTrue(option.isDetailsAllowed());
                        assertEquals("detail field label", option.getDetailLabel());
                    } else {
                        assertFalse(option.isDetailsAllowed());
                    }
                } else {
                    fail("unrecognized picklist option " + option.getStableId());
                }
            }

            assertEquals(rules.size(), question.getValidations().size());
            for (Rule<PicklistAnswer> rule : question.getValidations()) {
                switch (rule.getRuleType()) {
                    case REQUIRED:
                        assertNull(rule.getCorrectionHint());
                        break;
                    default:
                        fail("unrecognized rule " + rule.getRuleType());
                }
            }

            handle.rollback();
        });
    }

    private void runInsertDateQuestionTest(boolean isPicklist) {
        TransactionWrapper.useTxn(handle -> {
            Template prompt = new Template(TemplateType.TEXT, "prompt", "date question");
            Template header = new Template(TemplateType.TEXT, "header", "info header");
            Template footer = new Template(TemplateType.TEXT, "footer", "info footer");
            Template fieldReqHint = new Template(TemplateType.TEXT, "frVal", "field required hint");
            Template dateRangeHint = new Template(TemplateType.TEXT, "drVal", "date range hint");

            List<RuleDef> rules = Arrays.asList(
                    new RequiredRuleDef(null),
                    new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, fieldReqHint),
                    new DateRangeRuleDef(dateRangeHint, null, LocalDate.of(2018, 3, 14), false));

            List<DateFieldType> fields = Arrays.asList(DateFieldType.YEAR, DateFieldType.MONTH);

            DateRenderMode mode = DateRenderMode.TEXT;
            DatePicklistDef picklistDef = null;
            if (isPicklist) {
                mode = DateRenderMode.PICKLIST;
                picklistDef = new DatePicklistDef(true, 35, 125, 2012, 1988, true);
            }

            DateQuestionDef dateQuestion = new DateQuestionDef(DUMMY_QSID,
                                                                true,
                                                                prompt,
                                                                header,
                                                                footer,
                                                                rules,
                                                                mode,
                                                                true,
                                                                fields,
                                                                picklistDef,
                                                                true);

            FormActivityDef form = buildSingleBlockForm(testData.getStudyGuid(), "Date Activity", new QuestionBlockDef(dateQuestion));
            FormInstance inst = runInsertAndFetchInstance(handle, form, testData.getUserGuid());

            assertEquals("Date Activity", inst.getTitle());
            DateQuestion question = unwrapSingleBlockQuestion(inst, DateQuestion.class);

            assertEquals(DUMMY_QSID, question.getStableId());
            assertTrue(HtmlConverter.hasSameValue("date question", question.getPrompt()));
            assertEquals("date question", question.getTextPrompt());
            assertEquals(fields, question.getFields());
            assertTrue(question.getDisplayCalendar());

            if (isPicklist) {
                assertEquals(DateRenderMode.PICKLIST, question.getRenderMode());
            } else {
                assertEquals(DateRenderMode.TEXT, question.getRenderMode());
            }

            if (isPicklist) {
                DatePicklistQuestion datePicklist = (DatePicklistQuestion) question;
                assertTrue(datePicklist.getUseMonthNames());
                assertEquals(2012 - 125, datePicklist.getStartYear().intValue());
                assertEquals(2012 + 35, datePicklist.getEndYear().intValue());
                assertEquals(1988, datePicklist.getFirstSelectedYear().intValue());
            }

            assertEquals(rules.size(), question.getValidations().size());
            for (Rule<DateAnswer> rule : question.getValidations()) {
                switch (rule.getRuleType()) {
                    case REQUIRED:
                        assertNull(rule.getCorrectionHint());
                        break;
                    case YEAR_REQUIRED:
                        assertEquals("field required hint", rule.getCorrectionHint());
                        break;
                    case DATE_RANGE:
                        DateRangeRule rangeRule = (DateRangeRule) rule;
                        assertEquals("date range hint", rangeRule.getCorrectionHint());
                        assertNull(rangeRule.getStartDate());
                        assertEquals(LocalDate.of(2018, 3, 14), rangeRule.getEndDate());
                        break;
                    default:
                        fail("unrecognized rule " + rule.getRuleType());
                }
            }

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_formWithAdditionalSections() {
        TransactionWrapper.useTxn(handle -> {
            ContentBlockDef introBlock = new ContentBlockDef(new Template(TemplateType.TEXT, null, "intro template"));
            ContentBlockDef bodyBlock = new ContentBlockDef(new Template(TemplateType.TEXT, null, "body template"));
            ContentBlockDef closingBlock = new ContentBlockDef(new Template(TemplateType.TEXT, null, "closing template"));

            String actCode = "ACTIVITY" + Instant.now().toEpochMilli();
            FormActivityDef form = FormActivityDef.generalFormBuilder(actCode, "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity with all the sections"))
                    .addSection(new FormSectionDef(null, Collections.singletonList(bodyBlock)))
                    .setIntroduction(new FormSectionDef(null, Collections.singletonList(introBlock)))
                    .setClosing(new FormSectionDef(null, Collections.singletonList(closingBlock)))
                    .setListStyleHint(ListStyleHint.NUMBER)
                    .build();
            FormInstance instance = runInsertAndFetchInstance(handle, form, testData.getUserGuid());

            assertEquals(ListStyleHint.NUMBER, instance.getListStyleHint());

            assertNotNull(instance.getIntroduction());
            assertEquals(1, instance.getIntroduction().getBlocks().size());
            assertEquals(introBlock.getBlockId(), instance.getIntroduction().getBlocks().get(0).getBlockId());

            assertNotNull(instance.getClosing());
            assertEquals(1, instance.getClosing().getBlocks().size());
            assertEquals(closingBlock.getBlockId(), instance.getClosing().getBlocks().get(0).getBlockId());

            assertEquals(1, instance.getBodySections().size());
            assertEquals(1, instance.getBodySections().get(0).getBlocks().size());
            assertEquals(bodyBlock.getBlockId(), instance.getBodySections().get(0).getBlocks().get(0).getBlockId());

            handle.rollback();
        });
    }

    @Test
    public void testInsertActivity_allowOndemandTrigger() {
        TransactionWrapper.useTxn(handle -> {
            String actCode = "ACTIVITY" + Instant.now().toEpochMilli();
            FormActivityDef form = FormActivityDef.generalFormBuilder(actCode, "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity that allows on-demand triggering"))
                    .setAllowOndemandTrigger(true)
                    .build();

            long millis = Instant.now().toEpochMilli();
            long revId = handle.attach(JdbiRevision.class).insert(testData.getUserId(), millis, null, "testing");

            handle.attach(FormActivityDao.class).insertActivity(form, revId);
            assertNotNull(form.getActivityId());

            ActivityDto actDto = handle.attach(JdbiActivity.class).queryActivityById(form.getActivityId());
            assertNotNull(actDto);
            assertTrue(actDto.isOndemandTriggerAllowed());

            handle.rollback();
        });
    }

    @Test
    public void testFindDefByDtoAndVersion() {
        TransactionWrapper.useTxn(handle -> {
            FormSectionDef intro = new FormSectionDef(null, Arrays.asList(new ContentBlockDef(Template.text("intro"))));
            FormSectionDef closing = new FormSectionDef(null, Arrays.asList(new ContentBlockDef(Template.text("closing"))));

            FormSectionDef questionSection = new FormSectionDef(null, new ArrayList<>());

            AgreementQuestionDef agreementDef = new AgreementQuestionDef("AGREEMENT",
                                                                        true,
                                                                        Template.text("agreement prompt"),
                                                                        null,
                                                                        Template.text("info header"),
                                                                        Template.text("info footer"),
                                                                        singletonList(new RequiredRuleDef(null)),
                                                                        true);
            questionSection.getBlocks().add(new QuestionBlockDef(agreementDef));

            BoolQuestionDef boolDef = BoolQuestionDef.builder()
                                                    .setStableId("BOOLEAN")
                                                    .setPrompt(Template.text("bool prompt"))
                                                    .setTrueTemplate(Template.text("bool yes"))
                                                    .setFalseTemplate(Template.text("bool no"))
                                                    .setAdditionalInfoHeader(Template.text("info header"))
                                                    .setAdditionalInfoFooter(Template.text("info footer"))
                                                    .setRestricted(true)
                                                    .setDeprecated(true)
                                                    .setHideNumber(true)
                                                    .build();
            questionSection.getBlocks().add(new QuestionBlockDef(boolDef));

            questionSection.getBlocks().add(new QuestionBlockDef(TextQuestionDef
                    .builder(TextInputType.ESSAY, "TEXT", Template.text("text prompt"))
                    .setPlaceholderTemplate(Template.text("placeholder"))
                    .setRestricted(true)
                    .setDeprecated(true)
                    .setHideNumber(true)
                    .build()));

            questionSection.getBlocks().add(new QuestionBlockDef(DateQuestionDef
                    .builder(DateRenderMode.PICKLIST, "DATE", Template.text("date prompt"))
                    .setPicklistDef(new DatePicklistDef(true, 10, 100, 2018, 1988, true))
                    .setDisplayCalendar(true)
                    .setRestricted(true)
                    .setDeprecated(true)
                    .setHideNumber(true)
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .addValidation(new RequiredRuleDef(Template.text("date required")))
                    .build()));

            questionSection.getBlocks().add(new QuestionBlockDef(PicklistQuestionDef
                    .builder(PicklistSelectMode.MULTIPLE, PicklistRenderMode.LIST, "PICKLIST", Template.text("picklist prompt"))
                    .addOption(PicklistOptionDef.newExclusive("OP1", Template.text("exclusive"), Template.text("with details")))
                    .addOption(PicklistOptionDef.newExclusive("OP2", Template.text("exclusive no details")))
                    .addOption(new PicklistOptionDef("OP3", Template.text("option"), Template.text("with details")))
                    .addOption(new PicklistOptionDef("OP4", Template.text("option no details")))
                    .setRestricted(true)
                    .setDeprecated(true)
                    .setHideNumber(true)
                    .build()));

            questionSection.getBlocks().add(new QuestionBlockDef(CompositeQuestionDef.builder()
                    .setStableId("COMPOSITE")
                    .setPrompt(Template.text("composite prompt"))
                    .setAllowMultiple(true)
                    .setAddButtonTemplate(Template.text("button"))
                    .setAdditionalItemTemplate(Template.text("additional"))
                    .setRestricted(true)
                    .setDeprecated(true)
                    .setHideNumber(true)
                    .addChildrenQuestions(
                            TextQuestionDef.builder(TextInputType.TEXT, "COMPOSITE_TEXT", Template.text("composite text"))
                                    .build(),
                            DateQuestionDef
                                    .builder(DateRenderMode.TEXT, "COMPOSITE_DATE", Template.text("composite date"))
                                    .addFields(DateFieldType.YEAR)
                                    .build())
                    .build()));

            FormSectionDef componentSection = new FormSectionDef(null, new ArrayList<>());

            componentSection.getBlocks().add(new MailingAddressComponentDef(null, null));

            componentSection.getBlocks().add(new PhysicianComponentDef(true,
                    Template.text("button"), Template.text("title"), Template.text("subtitle"),
                    InstitutionType.PHYSICIAN, true, false));

            componentSection.getBlocks().add(new InstitutionComponentDef(true,
                    Template.text("button"), Template.text("title"), Template.text("subtitle"),
                    InstitutionType.INITIAL_BIOPSY, true, false));

            componentSection.getBlocks().add(new InstitutionComponentDef(true,
                    Template.text("button"), Template.text("title"), Template.text("subtitle"),
                    InstitutionType.INSTITUTION, true, false));

            FormSectionDef conditionalSection = new FormSectionDef(null, new ArrayList<>());

            ConditionalBlockDef condBlockDef = new ConditionalBlockDef(PicklistQuestionDef
                    .buildSingleSelect(PicklistRenderMode.LIST, "CONDITIONAL_CONTROL", Template.text("conditional control prompt"))
                    .addOption(new PicklistOptionDef("OP1", Template.text("yes")))
                    .build());
            condBlockDef.addNestedBlock(new ContentBlockDef(Template.text("nested")));

            conditionalSection.getBlocks().add(condBlockDef);

            FormSectionDef groupSection = new FormSectionDef(null, new ArrayList<>());

            GroupBlockDef groupBlockDef = new GroupBlockDef(ListStyleHint.BULLET, Template.text("group"));
            groupBlockDef.addNestedBlock(new QuestionBlockDef(BoolQuestionDef
                    .builder("GROUP_BOOL", Template.text("group bool"), Template.text("group bool yes"), Template.text("group bool no"))
                    .build()));

            groupSection.getBlocks().add(groupBlockDef);

            List<FormSectionDef> body = Arrays.asList(questionSection, componentSection, conditionalSection, groupSection);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .setListStyleHint(ListStyleHint.NUMBER)
                    .setDisplayOrder(25)
                    .setEditTimeoutSec(100L)
                    .setMaxInstancesPerUser(5)
                    .setWriteOnce(true)
                    .setIntroduction(intro)
                    .addSections(body)
                    .setClosing(closing)
                    .build();

            long timestamp = Instant.now().toEpochMilli();
            long revId = handle.attach(JdbiRevision.class).insert(testData.getUserId(), timestamp, null, "testing");

            FormActivityDao dao = handle.attach(FormActivityDao.class);
            dao.insertActivity(form, revId);
            assertNotNull(form.getActivityId());
            assertNotNull(form.getVersionId());

            ActivityDto activityDto = handle.attach(JdbiActivity.class).queryActivityById(form.getActivityId());
            assertNotNull(activityDto);

            ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class).findById(form.getVersionId()).orElse(null);
            assertNotNull(versionDto);

            FormActivityDef actual = dao.findDefByDtoAndVersion(activityDto, versionDto);
            assertEquals(form.getFormType(), actual.getFormType());
            assertEquals(form.getActivityCode(), actual.getActivityCode());
            assertEquals(form.getVersionTag(), actual.getVersionTag());
            assertEquals(form.getStudyGuid(), actual.getStudyGuid());

            assertEquals(form.getListStyleHint(), actual.getListStyleHint());
            assertEquals(form.getMaxInstancesPerUser(), actual.getMaxInstancesPerUser());
            assertEquals(form.getDisplayOrder(), actual.getDisplayOrder());
            assertEquals(form.isWriteOnce(), actual.isWriteOnce());
            assertEquals(form.getEditTimeoutSec(), actual.getEditTimeoutSec());
            assertEquals(form.isOndemandTriggerAllowed(), actual.isOndemandTriggerAllowed());

            assertEquals(1, actual.getIntroduction().getBlocks().size());
            assertEquals(BlockType.CONTENT, actual.getIntroduction().getBlocks().get(0).getBlockType());

            assertEquals(1, actual.getClosing().getBlocks().size());
            assertEquals(BlockType.CONTENT, actual.getClosing().getBlocks().get(0).getBlockType());

            assertEquals(4, actual.getSections().size());

            FormSectionDef section = actual.getSections().get(0);
            assertEquals(6, section.getBlocks().size());
            assertEquals(QuestionType.AGREEMENT, ((QuestionBlockDef) section.getBlocks().get(0)).getQuestion().getQuestionType());
            assertEquals(QuestionType.BOOLEAN, ((QuestionBlockDef) section.getBlocks().get(1)).getQuestion().getQuestionType());
            assertEquals(QuestionType.TEXT, ((QuestionBlockDef) section.getBlocks().get(2)).getQuestion().getQuestionType());

            QuestionDef question = ((QuestionBlockDef) section.getBlocks().get(3)).getQuestion();
            assertEquals(QuestionType.DATE, question.getQuestionType());
            assertTrue(question.isRestricted());
            assertTrue(question.isDeprecated());
            assertTrue(question.shouldHideNumber());
            assertEquals(RuleType.REQUIRED, question.getValidations().get(0).getRuleType());

            DateQuestionDef dateQuestion = (DateQuestionDef) question;
            assertNotNull(dateQuestion.getPicklistDef());
            assertTrue(dateQuestion.isDisplayCalendar());
            assertEquals(3, dateQuestion.getFields().size());
            assertEquals(DateFieldType.YEAR, dateQuestion.getFields().get(0));
            assertEquals(DateFieldType.MONTH, dateQuestion.getFields().get(1));
            assertEquals(DateFieldType.DAY, dateQuestion.getFields().get(2));

            question = ((QuestionBlockDef) section.getBlocks().get(4)).getQuestion();
            assertEquals(QuestionType.PICKLIST, question.getQuestionType());
            assertTrue(question.isRestricted());
            assertTrue(question.isDeprecated());
            assertTrue(question.shouldHideNumber());

            PicklistQuestionDef picklistQuestion = (PicklistQuestionDef) question;
            assertEquals(4, picklistQuestion.getPicklistOptions().size());

            PicklistOptionDef option = picklistQuestion.getPicklistOptions().get(0);
            assertEquals("OP1", option.getStableId());
            assertTrue(option.isDetailsAllowed());
            assertTrue(option.isExclusive());

            option = picklistQuestion.getPicklistOptions().get(1);
            assertEquals("OP2", option.getStableId());
            assertFalse(option.isDetailsAllowed());
            assertTrue(option.isExclusive());

            option = picklistQuestion.getPicklistOptions().get(2);
            assertEquals("OP3", option.getStableId());
            assertTrue(option.isDetailsAllowed());
            assertFalse(option.isExclusive());

            option = picklistQuestion.getPicklistOptions().get(3);
            assertEquals("OP4", option.getStableId());
            assertFalse(option.isDetailsAllowed());
            assertFalse(option.isExclusive());

            question = ((QuestionBlockDef) section.getBlocks().get(5)).getQuestion();
            assertEquals(QuestionType.COMPOSITE, question.getQuestionType());
            CompositeQuestionDef compositeQuestion = (CompositeQuestionDef) question;
            assertEquals(2, compositeQuestion.getChildren().size());
            assertEquals(QuestionType.TEXT, compositeQuestion.getChildren().get(0).getQuestionType());
            assertEquals(QuestionType.DATE, compositeQuestion.getChildren().get(1).getQuestionType());

            section = actual.getSections().get(1);
            assertEquals(4, section.getBlocks().size());

            ComponentBlockDef component = (ComponentBlockDef) section.getBlocks().get(0);
            assertEquals(ComponentType.MAILING_ADDRESS, component.getComponentType());

            PhysicianInstitutionComponentDef comp = (PhysicianInstitutionComponentDef) section.getBlocks().get(1);
            assertEquals(ComponentType.PHYSICIAN, comp.getComponentType());
            assertEquals(InstitutionType.PHYSICIAN, comp.getInstitutionType());

            comp = (PhysicianInstitutionComponentDef) section.getBlocks().get(2);
            assertEquals(ComponentType.INSTITUTION, comp.getComponentType());
            assertEquals(InstitutionType.INITIAL_BIOPSY, comp.getInstitutionType());

            comp = (PhysicianInstitutionComponentDef) section.getBlocks().get(3);
            assertEquals(ComponentType.INSTITUTION, comp.getComponentType());
            assertEquals(InstitutionType.INSTITUTION, comp.getInstitutionType());

            section = actual.getSections().get(2);
            assertEquals(1, section.getBlocks().size());
            assertEquals(BlockType.CONDITIONAL, section.getBlocks().get(0).getBlockType());

            ConditionalBlockDef conditional = (ConditionalBlockDef) section.getBlocks().get(0);
            assertEquals(QuestionType.PICKLIST, conditional.getControl().getQuestionType());
            assertEquals(1, conditional.getNested().size());
            assertEquals(BlockType.CONTENT, conditional.getNested().get(0).getBlockType());

            section = actual.getSections().get(3);
            assertEquals(1, section.getBlocks().size());
            assertEquals(BlockType.GROUP, section.getBlocks().get(0).getBlockType());

            GroupBlockDef group = (GroupBlockDef) section.getBlocks().get(0);
            assertEquals(1, group.getNested().size());
            assertEquals(BlockType.QUESTION, group.getNested().get(0).getBlockType());
            assertEquals(QuestionType.BOOLEAN, ((QuestionBlockDef) group.getNested().get(0)).getQuestion().getQuestionType());

            handle.rollback();
        });
    }
}
