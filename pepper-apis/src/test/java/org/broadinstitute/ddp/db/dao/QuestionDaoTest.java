package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertArrayEquals;
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
import java.util.NoSuchElementException;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.QuestionStableIdExistsException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.IntRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.DateRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.IntRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class QuestionDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static long langCodeId;

    @org.junit.Rule
    public ExpectedException thrown = ExpectedException.none();

    private String sid;
    private Template prompt;
    private Template placeholder;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            langCodeId = testData.getProfile().getPreferredLangId();
        });
    }

    @Before
    public void refresh() {
        sid = "QID" + Instant.now().toEpochMilli();
        prompt = new Template(TemplateType.TEXT, null, "dummy prompt");
        placeholder = new Template(TemplateType.TEXT, null, "dummy placeholder");
    }

    @Test
    public void testInsertQuestion_activityNotFound() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);

            try {
                TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build();
                dao.insertQuestion(12345L, question, 1L);
            } catch (NoSuchElementException actual) {
                assertTrue(actual.getMessage().contains("activity id 12345"));
            }

            handle.rollback();
        });
    }

    @Test
    public void testInsertQuestion_stableIdAlreadyTaken() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);

            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            try {
                TextQuestionDef other = TextQuestionDef.builder(TextInputType.TEXT, sid,
                        new Template(TemplateType.TEXT, null, "another")).build();
                dao.insertQuestion(form.getActivityId(), other, version1.getRevId());
            } catch (QuestionStableIdExistsException actual) {
                assertEquals(testData.getStudyId(), actual.getStudyId());
                assertEquals(sid, actual.getStableId());
            }

            handle.rollback();
        });
    }

    @Test
    public void testInsertQuestion_deprecated() {
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);

            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setDeprecated(true)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            assertNotNull(question.getQuestionId());

            Optional<QuestionDto> actual = handle.attach(JdbiQuestion.class).getQuestionDtoById(question.getQuestionId());
            assertTrue(actual.isPresent());
            assertTrue(actual.get().isDeprecated());

            handle.rollback();
        });
    }

    @Test
    public void testDisableBoolQuestion() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);

            Template trueTmpl = new Template(TemplateType.TEXT, null, "yes");
            Template falseTmpl = new Template(TemplateType.TEXT, null, "no");
            BoolQuestionDef question = BoolQuestionDef.builder(sid, prompt, trueTmpl, falseTmpl).build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(trueTmpl.getTemplateId()).isPresent());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(falseTmpl.getTemplateId()).isPresent());

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disableBoolQuestion(question.getQuestionId(), meta);

            assertTrue(jdbiQuestion.getQuestionDtoById(question.getQuestionId()).isPresent());
            assertTrue(jdbiQuestion.getQuestionDtoById(question.getQuestionId()).isPresent());
            assertFalse(jdbiQuestion.getQuestionDtoIfActive(question.getQuestionId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(trueTmpl.getTemplateId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(falseTmpl.getTemplateId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testDisableBoolQuestion_notFound() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("active boolean question");
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            dao.disableBoolQuestion(12345L, RevisionMetadata.now(testData.getUserId(), "test"));
        });
    }

    @Test
    public void testInsertTextQuestion_withSuggestionType() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);

            TextQuestionDef def = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setSuggestionType(SuggestionType.DRUG)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), def);
            ActivityVersionDto version = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            QuestionDto questionDto = jdbiQuestion.getQuestionDtoById(def.getQuestionId()).get();
            TextQuestionDef actual = dao.findTextQuestionDefByDtoAndTimestamp(questionDto, version.getRevStart());

            assertNotNull(actual);
            assertEquals(def.getQuestionId(), actual.getQuestionId());
            assertEquals(def.getStableId(), actual.getStableId());
            assertEquals(def.getInputType(), actual.getInputType());
            assertEquals(def.getSuggestionType(), actual.getSuggestionType());

            handle.rollback();
        });
    }

    @Test
    public void testInsertTextQuestion_withSuggestions() {
        List<String> suggestions = new ArrayList<String>();

        suggestions.add("Type Ahead text#1");
        suggestions.add("Type Ahead text#2");
        suggestions.add("Type Ahead text#3");

        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);

            TextQuestionDef def = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setSuggestionType(SuggestionType.INCLUDED)
                    .addSuggestions(suggestions)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), def);
            ActivityVersionDto version = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            QuestionDto questionDto = jdbiQuestion.getQuestionDtoById(def.getQuestionId()).get();
            TextQuestionDef actual = dao.findTextQuestionDefByDtoAndTimestamp(questionDto, version.getRevStart());

            assertNotNull(actual);
            assertEquals(def.getQuestionId(), actual.getQuestionId());
            assertEquals(def.getStableId(), actual.getStableId());
            assertEquals(def.getInputType(), actual.getInputType());
            assertEquals(def.getSuggestionType(), actual.getSuggestionType());

            //verify suggestions
            assertNotNull(actual.getSuggestions());

            List<String> menu = actual.getSuggestions();
            Assert.assertNotNull(menu);
            Assert.assertEquals(3, menu.size());
            Assert.assertEquals(menu.get(0), "Type Ahead text#1");

            handle.rollback();
        });
    }

    @Test
    public void testDisableTextQuestion() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);

            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setPlaceholderTemplate(placeholder).build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(placeholder.getTemplateId()).isPresent());

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disableTextQuestion(question.getQuestionId(), meta);

            assertFalse(jdbiQuestion.getQuestionDtoIfActive(question.getQuestionId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(placeholder.getTemplateId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testDisableTextQuestion_notFound() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("active text question");
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            dao.disableTextQuestion(12345L, RevisionMetadata.now(testData.getUserId(), "test"));
        });
    }

    @Test
    public void testDisableDateQuestion() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);

            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.TEXT, sid, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disableDateQuestion(question.getQuestionId(), meta);

            assertFalse(jdbiQuestion.getQuestionDtoIfActive(question.getQuestionId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testDisableDateQuestion_notFound() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("active date question");
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            dao.disableDateQuestion(12345L, RevisionMetadata.now(testData.getUserId(), "test"));
        });
    }

    @Test
    public void testInsertPicklistQuestion_noOptionsOrGroups() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("at least one option or one group");
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);

            // force-ably create picklist with no options
            PicklistQuestionDef def = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, sid, prompt)
                    .addOption(new PicklistOptionDef("no-used", Template.text("no used")))
                    .build();
            def.getGroups().clear();
            def.getPicklistOptions().clear();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), def);
            actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            fail("expected exception was not thrown");
        });
    }

    @Test
    public void testInsertPicklistQuestion_groupWithNoOptions() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("at least one option");
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);

            // force-ably create picklist group with no options
            PicklistQuestionDef def = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, sid, prompt)
                    .addGroup(new PicklistGroupDef("G1", Template.text("group 1"), Arrays.asList(
                            new PicklistOptionDef("not-used", Template.text("not used")))))
                    .build();
            def.getGroups().get(0).getOptions().clear();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), def);
            actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            fail("expected exception was not thrown");
        });
    }

    @Test
    public void testInsertPicklistQuestion_withGroups_andStandaloneOption() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);

            PicklistQuestionDef def = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, sid, prompt)
                    .addGroup(new PicklistGroupDef("g1", Template.text("group 1"), Arrays.asList(
                            new PicklistOptionDef("g1-o1", Template.text("option 1")),
                            new PicklistOptionDef("g1-o2", Template.text("option 2"))
                    )))
                    .addGroup(new PicklistGroupDef("g2", Template.text("group 2"), Arrays.asList(
                            new PicklistOptionDef("g2-o1", Template.text("option 3")),
                            new PicklistOptionDef("g2-o2", Template.text("option 4"))
                    )))
                    .addOption(new PicklistOptionDef("o1", Template.text("option 5")))
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), def);
            ActivityVersionDto version = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            QuestionDto questionDto = jdbiQuestion.getQuestionDtoById(def.getQuestionId()).get();
            PicklistQuestionDef actual = dao.findPicklistQuestionDefByDtoAndTimestamp(questionDto, version.getRevStart());

            assertNotNull(actual);
            assertEquals(def.getQuestionId(), actual.getQuestionId());
            assertEquals(def.getStableId(), actual.getStableId());
            assertEquals(def.getSelectMode(), actual.getSelectMode());
            assertEquals(def.getRenderMode(), actual.getRenderMode());

            assertEquals(def.getGroups().size(), actual.getGroups().size());
            for (int i = 0; i < def.getGroups().size(); i++) {
                PicklistGroupDef expectedGroup = def.getGroups().get(i);
                PicklistGroupDef actualGroup = def.getGroups().get(i);
                assertNotNull(actualGroup);
                assertEquals(expectedGroup.getGroupId(), actualGroup.getGroupId());
                assertEquals(expectedGroup.getStableId(), actualGroup.getStableId());

                assertEquals(expectedGroup.getOptions().size(), actualGroup.getOptions().size());
                for (int j = 0; j < expectedGroup.getOptions().size(); j++) {
                    PicklistOptionDef expectedOption = expectedGroup.getOptions().get(i);
                    PicklistOptionDef actualOption = actualGroup.getOptions().get(i);
                    assertEquals(expectedOption.getOptionId(), actualOption.getOptionId());
                    assertEquals(expectedOption.getStableId(), actualOption.getStableId());
                }
            }

            assertEquals(def.getPicklistOptions().size(), actual.getPicklistOptions().size());
            assertEquals(def.getPicklistOptions().get(0).getStableId(), actual.getPicklistOptions().get(0).getStableId());

            handle.rollback();
        });
    }

    @Test
    public void testDisablePicklistQuestion() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);
            JdbiPicklistOption jdbiOption = handle.attach(JdbiPicklistOption.class);

            Template label = new Template(TemplateType.TEXT, null, "picklist label");
            PicklistQuestionDef question = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.DROPDOWN, sid, prompt)
                    .setLabel(label)
                    .addOption(new PicklistOptionDef("po1", new Template(TemplateType.TEXT, null, "option 1")))
                    .addOption(new PicklistOptionDef("po2", new Template(TemplateType.TEXT, null, "option 2")))
                    .addOption(new PicklistOptionDef("other", new Template(TemplateType.TEXT, null, "other option")))
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());
            assertTrue(jdbiTmpl.getRevisionIdIfActive(label.getTemplateId()).isPresent());
            assertTrue(jdbiOption.isCurrentlyActive(question.getQuestionId(), "other"));
            assertEquals(3, jdbiOption.findAllActiveOrderedOptionsByQuestionId(question.getQuestionId()).size());

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 5, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disablePicklistQuestion(question.getQuestionId(), meta);

            assertTrue(jdbiQuestion.getQuestionDtoById(question.getQuestionId()).isPresent());
            assertFalse(jdbiQuestion.getQuestionDtoIfActive(question.getQuestionId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(prompt.getTemplateId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(label.getTemplateId()).isPresent());
            assertFalse(jdbiOption.isCurrentlyActive(question.getQuestionId(), "other"));
            assertEquals(0, jdbiOption.findAllActiveOrderedOptionsByQuestionId(question.getQuestionId()).size());

            handle.rollback();
        });
    }

    @Test
    public void testDisablePicklistQuestion_notFound() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("active picklist question");
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            dao.disablePicklistQuestion(12345L, RevisionMetadata.now(testData.getUserId(), "test"));
        });
    }

    @Test
    public void testAddRequiredRule() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestionValidation jdbiQuestionVal = handle.attach(JdbiQuestionValidation.class);

            Template tmpl = new Template(TemplateType.TEXT, null, "dummy question");
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, tmpl).build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertFalse(jdbiQuestionVal.getRequiredValidationIfActive(question.getQuestionId()).isPresent());

            RequiredRuleDef rule = new RequiredRuleDef(null);
            dao.addRequiredRule(question.getQuestionId(), rule, version1.getRevId());
            Optional<ValidationDto> dto = jdbiQuestionVal.getRequiredValidationIfActive(question.getQuestionId());
            assertTrue(dto.isPresent());
            assertEquals(rule.getRuleId(), (Long) dto.get().getId());

            handle.rollback();
        });
    }

    @Test
    public void testAddRequiredRule_alreadyRequired() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestionValidation jdbiQuestionVal = handle.attach(JdbiQuestionValidation.class);

            Template tmpl = new Template(TemplateType.TEXT, null, "dummy question");
            RequiredRuleDef rule = new RequiredRuleDef(null);
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, tmpl)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertNotNull(rule.getRuleId());
            assertTrue(jdbiQuestionVal.getRequiredValidationIfActive(question.getQuestionId()).isPresent());

            try {
                dao.addRequiredRule(question.getQuestionId(), new RequiredRuleDef(null), version1.getRevId());
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("already marked required"));
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            }

            handle.rollback();
        });
    }

    @Test
    public void testDisableRequiredRule() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestionValidation jdbiQuestionVal = handle.attach(JdbiQuestionValidation.class);
            JdbiTemplate jdbiTmpl = handle.attach(JdbiTemplate.class);

            Template tmpl = new Template(TemplateType.TEXT, null, "dummy question");
            Template hintTmpl = new Template(TemplateType.TEXT, null, "required rule");
            RequiredRuleDef rule = new RequiredRuleDef(hintTmpl);
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, tmpl)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertNotNull(rule.getRuleId());
            assertNotNull(hintTmpl.getTemplateId());

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 2000L, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);
            dao.disableRequiredRule(question.getQuestionId(), meta);
            assertFalse(jdbiQuestionVal.getRequiredValidationIfActive(question.getQuestionId()).isPresent());
            assertFalse(jdbiTmpl.getRevisionIdIfActive(hintTmpl.getTemplateId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testDisableRequiredRule_notAlreadyRequired() {
        TransactionWrapper.useTxn(handle -> {
            QuestionDao dao = handle.attach(QuestionDao.class);
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiQuestionValidation jdbiQuestionVal = handle.attach(JdbiQuestionValidation.class);

            Template tmpl = new Template(TemplateType.TEXT, null, "dummy question");
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, tmpl).build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(question.getQuestionId());
            assertFalse(jdbiQuestionVal.getRequiredValidationIfActive(question.getQuestionId()).isPresent());

            RevisionMetadata meta = new RevisionMetadata(version1.getRevStart() + 2000L, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);
            try {
                dao.disableRequiredRule(question.getQuestionId(), meta);
            } catch (NoSuchElementException expected) {
                assertTrue(expected.getMessage().contains("does not have a required"));
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            }

            handle.rollback();
        });
    }

    private GetGenericQuestionData genericGetTextQuestionSetUp(Handle handle) {
        RequiredRuleDef rule = new RequiredRuleDef(null);
        TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                .setPlaceholderTemplate(placeholder)
                .addValidation(rule)
                .build();
        FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
        ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
        ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

        TextAnswer answer = (TextAnswer) handle.attach(AnswerDao.class)
                .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                        new TextAnswer(null, sid, null, "itsAnAnswer"));

        List<Long> answers = new ArrayList<>(Arrays.asList(answer.getAnswerId()));

        return new GetGenericQuestionData(form, version1.getActivityId(),
                activityInstanceDto.getGuid(), question, answers);
    }

    @Test
    public void testGetQuestionByBlockId_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            long blockId = genericQuestionData.getForm().getSections().get(0).getBlocks().get(0).getBlockId();

            Optional<Question> questionOptional = handle.attach(QuestionDao.class)
                    .getQuestionByBlockId(blockId, genericQuestionData.getActivityInstanceGuid(), langCodeId);

            assertNotNull(questionOptional.orElse(null));
            assertEquals(questionOptional.get().getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) questionOptional.get();
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByBlockId_cantGetByBlockId() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            long blockId = -1;

            thrown.expect(DaoException.class);
            thrown.expectMessage("No question found for block " + blockId
                    + " and activity instance " + genericQuestionData.getActivityInstanceGuid());
            handle.attach(QuestionDao.class)
                    .getQuestionByBlockId(blockId, genericQuestionData.getActivityInstanceGuid(), false, langCodeId);

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByBlockId_deprecatedQuestion() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setDeprecated(true)
                    .setPlaceholderTemplate(placeholder)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);
            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            long blockId = form.getSections().get(0).getBlocks().get(0).getBlockId();

            Optional<Question> questionOptional = handle.attach(QuestionDao.class)
                    .getQuestionByBlockId(blockId, activityInstanceDto.getGuid(), langCodeId);

            assertFalse(questionOptional.isPresent());

            questionOptional = handle.attach(QuestionDao.class)
                    .getQuestionByBlockId(blockId, activityInstanceDto.getGuid(), true, langCodeId);

            assertTrue(questionOptional.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByIdNoRetrieveAnswersVar_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            Question question1 = handle.attach(QuestionDao.class)
                    .getQuestionByIdAndActivityInstanceGuid(genericQuestionData.getQuestion().getQuestionId(),
                            genericQuestionData.getActivityInstanceGuid(),
                            langCodeId);

            assertEquals(question1.getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) question1;
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionById_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            Question question1 = handle.attach(QuestionDao.class)
                    .getQuestionByIdAndActivityInstanceGuid(genericQuestionData.getQuestion().getQuestionId(),
                            genericQuestionData.getActivityInstanceGuid(),
                            false,
                            langCodeId);

            assertEquals(question1.getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) question1;
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByActivityInstanceAndDtoNoRetrieveAnswerVar_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(genericQuestionData.getQuestion().getQuestionId()).get();

            Question question1 = handle.attach(QuestionDao.class)
                    .getQuestionByActivityInstanceAndDto(questionDto,
                            genericQuestionData.getActivityInstanceGuid(),
                            langCodeId);

            assertEquals(question1.getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) question1;
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByActivityInstanceAndDto_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(genericQuestionData.getQuestion().getQuestionId()).get();

            Question question1 = handle.attach(QuestionDao.class)
                    .getQuestionByActivityInstanceAndDto(questionDto,
                            genericQuestionData.getActivityInstanceGuid(),
                            false,
                            langCodeId);

            assertEquals(question1.getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) question1;
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByActivityInstanceAndDto_failureNoDto() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            thrown.expect(DaoException.class);
            thrown.expectMessage("No question dto found");
            handle.attach(QuestionDao.class)
                    .getQuestionByActivityInstanceAndDto(null,
                            genericQuestionData.getActivityInstanceGuid(),
                            false,
                            langCodeId);

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByUserGuidAndQuestionDto_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(genericQuestionData.getQuestion().getQuestionId()).get();

            Question question1 = handle.attach(QuestionDao.class)
                    .getQuestionByUserGuidAndQuestionDto(questionDto,
                            testData.getUserGuid(),
                            false,
                            langCodeId);

            assertEquals(question1.getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) question1;
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }

    @Test
    public void testGetControlQuestionByBlockId_success() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef control = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build();
            ConditionalBlockDef block = new ConditionalBlockDef(control);
            block.addNestedBlock(new ContentBlockDef(Template.text("foobar")));

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(),
                    "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .addSection(new FormSectionDef(null, Collections.singletonList(block)))
                    .build();

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            Optional<Question> res = handle.attach(QuestionDao.class)
                    .getControlQuestionByBlockId(block.getBlockId(), activityInstanceDto.getGuid(), langCodeId);

            assertTrue(res.isPresent());
            assertEquals(control.getQuestionId(), (Long) res.get().getQuestionId());

            assertEquals(1, handle.attach(JdbiQuestion.class).updateIsDeprecatedById(control.getQuestionId(), true));
            res = handle.attach(QuestionDao.class)
                    .getControlQuestionByBlockId(block.getBlockId(), activityInstanceDto.getGuid(), langCodeId);
            assertFalse(res.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testGetControlQuestionByBlockId_noControlQuestionFound() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef control = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build();
            ConditionalBlockDef block = new ConditionalBlockDef(control);
            block.addNestedBlock(new ContentBlockDef(Template.text("foobar")));
            JdbiBlock jdbiBlock = handle.attach(JdbiBlock.class);
            block.setBlockGuid(jdbiBlock.generateUniqueGuid());
            block.setBlockId(jdbiBlock.insert(
                    handle.attach(JdbiBlockType.class).getTypeId(block.getBlockType()),
                    block.getBlockGuid()
            ));

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), control);
            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            thrown.expect(DaoException.class);
            thrown.expectMessage("No control question found for block " + block.getBlockId()
                    + " and activity instance " + activityInstanceDto.getGuid());
            handle.attach(QuestionDao.class)
                    .getControlQuestionByBlockId(block.getBlockId(), activityInstanceDto.getGuid(), langCodeId);

            handle.rollback();
        });
    }

    @Test
    public void testGetQuestionByActivityInstanceAndDto() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(genericQuestionData.getQuestion().getQuestionId()).get();

            Question question1 = handle.attach(QuestionDao.class).getQuestionByActivityInstanceAndDto(questionDto,
                    genericQuestionData.getActivityInstanceGuid(), true, langCodeId);

            assertEquals(question1.getQuestionType(), QuestionType.TEXT);

            TextQuestion textQuestion = (TextQuestion) question1;
            assertEquals(textQuestion.getQuestionId(), (long) genericQuestionData.getQuestion().getQuestionId());

            handle.rollback();
        });
    }


    @Test
    public void testGetBooleanQuestion_success() {
        TransactionWrapper.useTxn(handle -> {
            Template trueTmpl = new Template(TemplateType.TEXT, null, "yup");
            Template falseTmpl = new Template(TemplateType.TEXT, null, "nope");
            RequiredRuleDef rule = new RequiredRuleDef(null);
            BoolQuestionDef question = BoolQuestionDef.builder(sid, prompt, trueTmpl, falseTmpl)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());
            BoolAnswer answer = (BoolAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                            new BoolAnswer(null, sid, null, true));

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            List<Long> answers = new ArrayList<>();
            answers.add(answer.getAnswerId());
            List<org.broadinstitute.ddp.model.activity.instance.validation.Rule> rules = new ArrayList<>();
            rules.add(new RequiredRule<BoolAnswer>(rule.getRuleId(), "hint", "message", false));
            Question returnedQuestion = handle.attach(QuestionDao.class).getBooleanQuestion(questionDto,
                    answers,
                    rules);

            assertEquals(QuestionType.BOOLEAN, returnedQuestion.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) returnedQuestion.getPromptTemplateId());
            assertEquals(sid, returnedQuestion.getStableId());

            BoolQuestion boolQuestion = (BoolQuestion) returnedQuestion;
            assertEquals(trueTmpl.getTemplateId(), (Long) boolQuestion.getTrueTemplateId());
            assertEquals(falseTmpl.getTemplateId(), (Long) boolQuestion.getFalseTemplateId());

            org.broadinstitute.ddp.model.activity.instance.validation.Rule<BoolAnswer> validation = boolQuestion.getValidations().get(0);
            assertEquals(RuleType.REQUIRED, validation.getRuleType());

            assertEquals(1, boolQuestion.getAnswers().size());
            assertTrue(boolQuestion.getAnswers().get(0).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetBooleanQuestion_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            Template trueTmpl = new Template(TemplateType.TEXT, null, "yup");
            Template falseTmpl = new Template(TemplateType.TEXT, null, "nope");
            RequiredRuleDef rule = new RequiredRuleDef(null);
            BoolQuestionDef question = BoolQuestionDef.builder(sid, prompt, trueTmpl, falseTmpl)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            questionDto = new QuestionDto(questionDto.getType(), questionDto.getId() + 1,
                    questionDto.getStableId(), questionDto.getPromptTemplateId(), null, null, questionDto.getActivityId(),
                    questionDto.isRestricted(), questionDto.isDeprecated(), questionDto.shouldHideNumber(),
                    questionDto.getRevisionId(), questionDto.getRevisionStart(), questionDto.getRevisionEnd());

            thrown.expect(Exception.class);
            thrown.expectMessage("Could not find boolean question for id " + questionDto.getId());
            handle.attach(QuestionDao.class).getBooleanQuestion(questionDto,
                    Collections.emptyList(),
                    Collections.emptyList());

            handle.rollback();
        });
    }

    @Test
    public void testGetPicklistQuestion_success() {
        TransactionWrapper.useTxn(handle -> {
            Template label = Template.text("picklist label");
            Template opt1Tmpl = Template.text("option 1");
            PicklistOptionDef option1 = new PicklistOptionDef("PO1", opt1Tmpl);
            Template opt2Tmpl = Template.text("option 2");
            Template opt2Details = Template.text("details here");
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", opt2Tmpl, opt2Details);
            RequiredRuleDef rule = new RequiredRuleDef(null);
            PicklistQuestionDef question = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.DROPDOWN, sid, prompt)
                    .setLabel(label)
                    .addOption(option1)
                    .addOption(option2)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            PicklistAnswer answer = (PicklistAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                            new PicklistAnswer(null, sid, null, List.of(new SelectedPicklistOption("PO1"))));

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            List<Long> answers = new ArrayList<>();
            answers.add(answer.getAnswerId());
            List<org.broadinstitute.ddp.model.activity.instance.validation.Rule> rules = new ArrayList<>();
            rules.add(new RequiredRule<PicklistAnswer>(rule.getRuleId(), "hint", "message", false));

            Question question1 = handle.attach(QuestionDao.class).getPicklistQuestion(
                    questionDto,
                    activityInstanceDto.getGuid(),
                    answers,
                    rules
            );

            assertEquals(QuestionType.PICKLIST, question1.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question1.getPromptTemplateId());
            assertEquals(sid, question1.getStableId());

            PicklistQuestion picklistQ = (PicklistQuestion) question1;
            assertEquals(PicklistSelectMode.SINGLE, picklistQ.getSelectMode());
            assertEquals(PicklistRenderMode.DROPDOWN, picklistQ.getRenderMode());
            assertEquals(label.getTemplateId(), picklistQ.getPicklistLabelTemplateId());

            assertEquals(2, picklistQ.getPicklistOptions().size());
            for (PicklistOption option : picklistQ.getPicklistOptions()) {
                if ("PO1".equals(option.getStableId())) {
                    assertEquals(opt1Tmpl.getTemplateId(), (Long) option.getOptionLabelTemplateId());
                    assertFalse(option.isDetailsAllowed());
                } else if ("PO2".equals(option.getStableId())) {
                    assertEquals(opt2Tmpl.getTemplateId(), (Long) option.getOptionLabelTemplateId());
                    assertEquals(opt2Details.getTemplateId(), option.getDetailLabelTemplateId());
                    assertTrue(option.isDetailsAllowed());
                } else {
                    fail("unexpected option stable id: " + option.getStableId());
                }
            }

            handle.rollback();

        });
    }

    @Test
    public void testGetPicklistQuestion_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            Template label = Template.text("picklist label");
            Template opt1Tmpl = Template.text("option 1");
            PicklistOptionDef option1 = new PicklistOptionDef("PO1", opt1Tmpl);
            Template opt2Tmpl = Template.text("option 2");
            Template opt2Details = Template.text("details here");
            PicklistOptionDef option2 = new PicklistOptionDef("PO2", opt2Tmpl, opt2Details);
            RequiredRuleDef rule = new RequiredRuleDef(null);
            PicklistQuestionDef question = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.DROPDOWN, sid, prompt)
                    .setLabel(label)
                    .addOption(option1)
                    .addOption(option2)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            questionDto = new QuestionDto(questionDto.getType(), questionDto.getId() + 1,
                    questionDto.getStableId(), questionDto.getPromptTemplateId(),
                    questionDto.getAdditionalInfoHeaderTemplateId(), questionDto.getAdditionalInfoFooterTemplateId(),
                    questionDto.getActivityId(), questionDto.isRestricted(), questionDto.isDeprecated(),
                    questionDto.shouldHideNumber(), questionDto.getRevisionId(), questionDto.getRevisionStart(),
                    questionDto.getRevisionEnd());

            thrown.expect(Exception.class);
            thrown.expectMessage("Could not find picklist question for id " + questionDto.getId());
            handle.attach(QuestionDao.class).getPicklistQuestion(questionDto,
                    activityInstanceDto.getGuid(),
                    Collections.emptyList(),
                    Collections.emptyList());

            handle.rollback();
        });
    }

    @Test
    public void testGetPicklistQuestion_withGroups() {
        TransactionWrapper.useTxn(handle -> {
            PicklistQuestionDef expected = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, sid, prompt)
                    .addGroup(new PicklistGroupDef("G1", Template.text("group 1"), Arrays.asList(
                            new PicklistOptionDef("G1_O1", Template.text("g1 option 1")),
                            new PicklistOptionDef("G1_O2", Template.text("g1 option 2")))))
                    .addGroup(new PicklistGroupDef("G2", Template.text("group 2"), Arrays.asList(
                            new PicklistOptionDef("G2_O1", Template.text("g2 option 1")),
                            new PicklistOptionDef("G2_O2", Template.text("g2 option 2")))))
                    .addOption(new PicklistOptionDef("PO1", Template.text("another option")))
                    .build();
            QuestionBlockDef block = new QuestionBlockDef(expected);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "test activity"))
                    .addSection(new FormSectionDef(null, Collections.singletonList(block)))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            String instanceGuid = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, form.getActivityId(), testData.getUserGuid())
                    .getGuid();
            QuestionDao[] daos = {new QuestionCachedDao(handle), handle.attach(QuestionDao.class)};
            Question question = null;

            for (QuestionDao dao : daos) {
                CacheService.getInstance().resetAllCaches();
                question = dao.getQuestionByBlockId(block.getBlockId(), instanceGuid, langCodeId).get();
                assertEquals(QuestionType.PICKLIST, question.getQuestionType());
                assertEquals(prompt.getTemplateId(), (Long) question.getPromptTemplateId());
                assertEquals(sid, question.getStableId());
            }

            PicklistQuestion actual = (PicklistQuestion) question;

            assertEquals(2, actual.getGroups().size());
            assertEquals("G1", actual.getGroups().get(0).getStableId());
            assertEquals("G2", actual.getGroups().get(1).getStableId());

            assertEquals(5, actual.getPicklistOptions().size());
            assertEquals("PO1", actual.getPicklistOptions().get(0).getStableId());
            assertEquals("G1_O1", actual.getPicklistOptions().get(1).getStableId());
            assertEquals("G1", actual.getPicklistOptions().get(1).getGroupStableId());
            assertEquals("G1_O2", actual.getPicklistOptions().get(2).getStableId());
            assertEquals("G1", actual.getPicklistOptions().get(2).getGroupStableId());
            assertEquals("G2_O1", actual.getPicklistOptions().get(3).getStableId());
            assertEquals("G2", actual.getPicklistOptions().get(3).getGroupStableId());
            assertEquals("G2_O2", actual.getPicklistOptions().get(4).getStableId());
            assertEquals("G2", actual.getPicklistOptions().get(4).getGroupStableId());

            handle.rollback();
        });
    }

    @Test
    public void testGetTextQuestion_success() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(genericQuestionData.getQuestion().getQuestionId()).get();

            List<Rule> rules = new ArrayList<>(Arrays.asList(
                    new RequiredRule<TextAnswer>(genericQuestionData.getQuestion().getValidations().get(0).getRuleId(), null,
                            "aVeryValidMessage", false)
            ));

            Question question1 = handle.attach(QuestionDao.class).getTextQuestion(
                    questionDto,
                    genericQuestionData.getActivityInstanceGuid(),
                    genericQuestionData.getAnswerIds(),
                    rules
            );

            assertEquals(QuestionType.TEXT, question1.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question1.getPromptTemplateId());
            assertEquals(sid, question1.getStableId());

            TextQuestion textQ = (TextQuestion) question1;
            assertEquals(TextInputType.TEXT, textQ.getInputType());

            RequiredRule validation = (RequiredRule) textQ.getValidations().get(0);
            assertEquals(RuleType.REQUIRED, validation.getRuleType());

            TextAnswer textAnswer = (TextAnswer) textQ.getAnswers().get(0);
            assertEquals("itsAnAnswer", textAnswer.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetTextQuestion_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            GetGenericQuestionData genericQuestionData = genericGetTextQuestionSetUp(handle);

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(genericQuestionData.getQuestion().getQuestionId()).get();

            questionDto = new QuestionDto(questionDto.getType(), questionDto.getId() + 1,
                    questionDto.getStableId(), questionDto.getPromptTemplateId(),
                    questionDto.getAdditionalInfoHeaderTemplateId(), questionDto.getAdditionalInfoFooterTemplateId(),
                    questionDto.getActivityId(), questionDto.isRestricted(), questionDto.isDeprecated(),
                    questionDto.shouldHideNumber(), questionDto.getRevisionId(),
                    questionDto.getRevisionStart(), questionDto.getRevisionEnd());

            thrown.expect(DaoException.class);
            thrown.expectMessage("Could not find text question for id " + questionDto.getId());
            handle.attach(QuestionDao.class).getTextQuestion(
                    questionDto,
                    genericQuestionData.getActivityInstanceGuid(),
                    genericQuestionData.getAnswerIds(),
                    Collections.emptyList()
            );

            handle.rollback();
        });
    }

    @Test
    public void testGetTextQuestion_withSuggestionType() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef expected = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setSuggestionType(SuggestionType.DRUG)
                    .build();
            QuestionBlockDef block = new QuestionBlockDef(expected);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "test activity"))
                    .addSection(new FormSectionDef(null, Collections.singletonList(block)))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            String instanceGuid = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, form.getActivityId(), testData.getUserGuid())
                    .getGuid();

            Question question = handle.attach(QuestionDao.class)
                    .getQuestionByBlockId(block.getBlockId(), instanceGuid, langCodeId).get();
            assertEquals(QuestionType.TEXT, question.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question.getPromptTemplateId());
            assertEquals(sid, question.getStableId());

            TextQuestion actual = (TextQuestion) question;
            assertEquals(TextInputType.TEXT, actual.getInputType());
            assertEquals(SuggestionType.DRUG, actual.getSuggestionType());

            handle.rollback();
        });
    }

    @Test
    public void testGetTextQuestion_withSuggestion() {
        List<String> suggestions = new ArrayList<String>();
        suggestions.add("test type ahead#1");
        suggestions.add("test type ahead#2");
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef expected = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setSuggestionType(SuggestionType.INCLUDED)
                    .addSuggestions(suggestions)
                    .build();
            QuestionBlockDef block = new QuestionBlockDef(expected);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "test activity"))
                    .addSection(new FormSectionDef(null, Collections.singletonList(block)))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            String instanceGuid = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, form.getActivityId(), testData.getUserGuid())
                    .getGuid();

            Question question = handle.attach(QuestionDao.class)
                    .getQuestionByBlockId(block.getBlockId(), instanceGuid, langCodeId).get();
            assertEquals(QuestionType.TEXT, question.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question.getPromptTemplateId());
            assertEquals(sid, question.getStableId());

            TextQuestion actual = (TextQuestion) question;
            assertEquals(TextInputType.TEXT, actual.getInputType());
            assertEquals(SuggestionType.INCLUDED, actual.getSuggestionType());
            assertNotNull(actual.getSuggestions());
            assertEquals(2, suggestions.size());
            assertEquals("test type ahead#2", suggestions.get(1));

            handle.rollback();
        });
    }

    @Test
    public void testGetDateQuestion_withPicklist_success() {
        TransactionWrapper.useTxn(handle -> {
            DateRangeRuleDef rule = new DateRangeRuleDef(Template.text("Pi Day to today"),
                    LocalDate.of(2018, 3, 14), null, true);
            DatePicklistDef datePicklistDef = new DatePicklistDef(true, 3, 80, null, 1988, true);
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.PICKLIST, sid, prompt)
                    .addFields(DateFieldType.DAY, DateFieldType.MONTH, DateFieldType.YEAR)
                    .addValidation(rule)
                    .setPicklistDef(datePicklistDef)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            DateAnswer answer = (DateAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                            new DateAnswer(null, sid, null, new DateValue(2018, 10, 10)));

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            List<Long> answers = new ArrayList<>();
            answers.add(answer.getAnswerId());
            List<org.broadinstitute.ddp.model.activity.instance.validation.Rule> rules = new ArrayList<>();
            rules.add(DateRangeRule.of(rule.getRuleId(), rule.getHintTemplate().getTemplateText(), "hint", false,
                    rule.getStartDate(), rule.getEndDate()));

            Question question1 = handle.attach(QuestionDao.class).getDateQuestion(questionDto, answers, rules);

            assertEquals(QuestionType.DATE, question.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question1.getPromptTemplateId());
            assertEquals(sid, question1.getStableId());

            DateQuestion dateQ = (DateQuestion) question1;
            DateFieldType[] expected = toArray(DateFieldType.DAY, DateFieldType.MONTH, DateFieldType.YEAR);

            assertEquals(DateRenderMode.PICKLIST, dateQ.getRenderMode());
            assertFalse(dateQ.getDisplayCalendar());
            assertEquals(expected.length, dateQ.getFields().size());
            assertArrayEquals(expected, dateQ.getFields().toArray());

            assertEquals(1, dateQ.getValidations().size());
            assertEquals(RuleType.DATE_RANGE, dateQ.getValidations().get(0).getRuleType());

            DateRangeRule dateRangeRule = (DateRangeRule) dateQ.getValidations().get(0);
            assertEquals("2018-03-14", dateRangeRule.getStartDate().toString());
            assertNull(dateRangeRule.getEndDate());
            assertEquals("Pi Day to today", dateRangeRule.getDefaultMessage());
            ;
            assertEquals(1, dateQ.getAnswers().size());
            assertEquals(new DateValue(2018, 10, 10), dateQ.getAnswers().get(0).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetDateQuestion_textMode_success() {
        TransactionWrapper.useTxn(handle -> {
            RequiredRuleDef rule = new RequiredRuleDef(null);
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.TEXT, sid, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            DateValue dateValue = new DateValue(2018, 10, 10);
            DateAnswer answer = (DateAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                            new DateAnswer(null, sid, null, dateValue));

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            List<Long> answers = new ArrayList<>();
            answers.add(answer.getAnswerId());
            List<org.broadinstitute.ddp.model.activity.instance.validation.Rule> rules = new ArrayList<>();
            rules.add(new RequiredRule<DateAnswer>(rule.getRuleId(), "hint", "message", false));

            Question question1 = handle.attach(QuestionDao.class).getDateQuestion(questionDto, answers, rules);

            assertEquals(QuestionType.DATE, question.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question1.getPromptTemplateId());
            assertEquals(sid, question1.getStableId());

            DateQuestion dateQ = (DateQuestion) question1;
            DateFieldType[] expected = toArray(DateFieldType.MONTH, DateFieldType.YEAR);

            assertEquals(DateRenderMode.TEXT, dateQ.getRenderMode());
            assertFalse(dateQ.getDisplayCalendar());
            assertEquals(expected.length, dateQ.getFields().size());
            assertArrayEquals(expected, dateQ.getFields().toArray());

            assertEquals(1, dateQ.getAnswers().size());
            assertEquals(QuestionType.DATE, dateQ.getAnswers().get(0).getQuestionType());

            DateAnswer dateAnswer = (DateAnswer) dateQ.getAnswers().get(0);
            assertEquals(dateValue, dateAnswer.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetDateQuestion_singleTextMode_success() {
        TransactionWrapper.useTxn(handle -> {
            RequiredRuleDef rule = new RequiredRuleDef(null);
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, sid, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            DateAnswer answer = (DateAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                            new DateAnswer(null, sid, null, new DateValue(2018, 10, 10)));

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            List<Long> answers = new ArrayList<>();
            answers.add(answer.getAnswerId());
            List<org.broadinstitute.ddp.model.activity.instance.validation.Rule> rules = new ArrayList<>();
            rules.add(new RequiredRule<DateAnswer>(rule.getRuleId(), "hint", "message", false));

            Question question1 = handle.attach(QuestionDao.class).getDateQuestion(questionDto, answers, rules);

            assertEquals(QuestionType.DATE, question.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question1.getPromptTemplateId());
            assertEquals(sid, question1.getStableId());

            DateQuestion dateQ = (DateQuestion) question1;
            DateFieldType[] expected = toArray(DateFieldType.MONTH, DateFieldType.YEAR);

            assertEquals(DateRenderMode.SINGLE_TEXT, dateQ.getRenderMode());
            assertFalse(dateQ.getDisplayCalendar());
            assertEquals(expected.length, dateQ.getFields().size());
            assertArrayEquals(expected, dateQ.getFields().toArray());

            handle.rollback();
        });
    }

    @Test
    public void testGetDateQuestion_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            RequiredRuleDef rule = new RequiredRuleDef(null);
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.TEXT, sid, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                    .addValidation(rule)
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            TestDataSetupUtil.generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            questionDto = new QuestionDto(questionDto.getType(), questionDto.getId() + 1,
                    questionDto.getStableId(), questionDto.getPromptTemplateId(),
                    questionDto.getAdditionalInfoHeaderTemplateId(), questionDto.getAdditionalInfoFooterTemplateId(),
                    questionDto.getActivityId(), questionDto.isRestricted(), questionDto.isDeprecated(),
                    questionDto.shouldHideNumber(), questionDto.getRevisionId(),
                    questionDto.getRevisionStart(), questionDto.getRevisionEnd());

            thrown.expect(DaoException.class);
            thrown.expectMessage("Could not find date question for id " + questionDto.getId());
            handle.attach(QuestionDao.class).getDateQuestion(questionDto, Collections.emptyList(), Collections.emptyList());
        });
    }

    @Test
    public void testGetNumericQuestion_integerType() {
        TransactionWrapper.useTxn(handle -> {
            Template placeholder = Template.text("some placeholder");
            NumericQuestionDef questionDef = NumericQuestionDef
                    .builder(NumericType.INTEGER, sid, prompt)
                    .setPlaceholderTemplate(placeholder)
                    .addValidation(new IntRangeRuleDef(Template.text("int_range"), 5L, 100L))
                    .build();
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), questionDef);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto instanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new NumericIntegerAnswer(null, sid, null, 25L));

            NumericQuestionDto questionDto = handle.attach(JdbiNumericQuestion.class)
                    .findDtoByQuestionId(questionDef.getQuestionId()).get();

            Question actual = handle.attach(QuestionDao.class)
                    .getQuestionByActivityInstanceAndDto(questionDto, instanceDto.getGuid(),
                            LanguageStore.getOrComputeDefault(handle).getId());
            assertEquals(QuestionType.NUMERIC, actual.getQuestionType());
            assertEquals(sid, actual.getStableId());
            assertEquals(prompt.getTemplateId(), (Long) actual.getPromptTemplateId());

            NumericQuestion numericQuestion = (NumericQuestion) actual;
            assertEquals(NumericType.INTEGER, numericQuestion.getNumericType());
            assertEquals(placeholder.getTemplateId(), numericQuestion.getPlaceholderTemplateId());

            assertEquals(1, numericQuestion.getValidations().size());
            assertEquals(RuleType.INT_RANGE, numericQuestion.getValidations().get(0).getRuleType());
            IntRangeRule numericRule = (IntRangeRule) numericQuestion.getValidations().get(0);
            assertEquals((Long) 5L, numericRule.getMin());
            assertEquals((Long) 100L, numericRule.getMax());

            assertEquals(1, numericQuestion.getAnswers().size());
            assertEquals(QuestionType.NUMERIC, numericQuestion.getAnswers().get(0).getQuestionType());
            NumericAnswer numericAnswer = numericQuestion.getAnswers().get(0);
            assertEquals(NumericType.INTEGER, numericAnswer.getNumericType());
            assertEquals((Long) 25L, ((NumericIntegerAnswer) numericAnswer).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetAgreementQuestion_success() {
        TransactionWrapper.useTxn(handle -> {
            Template agreeTmpl = Template.text("agreement");
            Template header = Template.text("header");
            Template footer = Template.text("footer");
            RequiredRuleDef rule = new RequiredRuleDef(null);
            AgreementQuestionDef question = new AgreementQuestionDef(sid,
                    false,
                    agreeTmpl,
                    null,
                    header,
                    footer,
                    Arrays.asList(rule),
                    true
            );
            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), question);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            AgreementAnswer answer = (AgreementAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), activityInstanceDto.getId(),
                            new AgreementAnswer(null, sid, null, true));

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(question.getQuestionId()).get();

            List<Long> answers = new ArrayList<>();
            answers.add(answer.getAnswerId());
            List<org.broadinstitute.ddp.model.activity.instance.validation.Rule> rules = new ArrayList<>();
            rules.add(new RequiredRule<BoolAnswer>(rule.getRuleId(), "hint", "message", false));

            Question question1 = handle.attach(QuestionDao.class).getAgreementQuestion(questionDto, answers, rules);

            assertEquals(QuestionType.AGREEMENT, question1.getQuestionType());
            assertEquals(sid, question1.getStableId());

            AgreementQuestion agreementQ = (AgreementQuestion) question1;
            assertEquals(true, agreementQ.passesDeferredValidations());
            assertEquals(1, agreementQ.getAnswers().size());
            assertTrue(agreementQ.getAnswers().get(0).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetCompositeQuestion_success() {
        TransactionWrapper.useTxn(handle -> {
            Template datePrompt = new Template(TemplateType.TEXT, null, "date prompt");
            String dateStableId = "CHILD_DATE" + Instant.now().toEpochMilli();
            DateQuestionDef dateQuestion = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, dateStableId, datePrompt)
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .setDisplayCalendar(true)
                    .build();

            LengthRuleDef lengthRule = new LengthRuleDef(null, 5, 300);
            Template textPrompt = new Template(TemplateType.TEXT, null, "text prompt");
            String textStableId = "CHILD_TEXT" + Instant.now().toEpochMilli();
            TextQuestionDef textQuestion = TextQuestionDef.builder(TextInputType.TEXT, textStableId, textPrompt)
                    .addValidation(lengthRule)
                    .build();

            Template addButtonTextTemplate = new Template(TemplateType.TEXT, null, "Add Button");
            Template additionalItemTemplate = new Template(TemplateType.TEXT, null, "Another Item");
            CompositeQuestionDef questionDef = CompositeQuestionDef.builder()
                    .setStableId(sid)
                    .setPrompt(prompt)
                    .addChildrenQuestions(dateQuestion, textQuestion)
                    .setChildOrientation(OrientationType.HORIZONTAL)
                    .setAllowMultiple(true)
                    .setAddButtonTemplate(addButtonTextTemplate)
                    .setAdditionalItemTemplate(additionalItemTemplate)
                    .build();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), questionDef);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            DateValue dateValue = new DateValue(2018, 10, 10);
            DateAnswer da = new DateAnswer(null, dateStableId, null, dateValue);
            String textValue = "text!";
            TextAnswer ta = new TextAnswer(null, textStableId, null, textValue);
            CompositeAnswer compAnswer = new CompositeAnswer(null, sid, null);
            compAnswer.addRowOfChildAnswers(da, ta);

            Answer ans = handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), activityInstanceDto.getId(), compAnswer);
            assertNotNull(ans);
            assertNotNull(ans.getAnswerId());
            assertNotNull(ans.getAnswerGuid());

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(questionDef.getQuestionId()).get();

            Question question = handle.attach(QuestionDao.class).getCompositeQuestion(questionDto,
                    activityInstanceDto.getGuid(),
                    List.of(ans.getAnswerId()),
                    Collections.emptyList(),
                    true,
                    langCodeId);
            assertEquals(QuestionType.COMPOSITE, question.getQuestionType());
            assertEquals(prompt.getTemplateId(), (Long) question.getPromptTemplateId());
            assertEquals(sid, question.getStableId());
            CompositeQuestion compQuestion = (CompositeQuestion) question;
            assertEquals(addButtonTextTemplate.getTemplateId(), compQuestion.getAddButtonTextTemplateId());
            assertEquals(additionalItemTemplate.getTemplateId(), compQuestion.getAdditionalItemTextTemplateId());
            assertEquals(OrientationType.HORIZONTAL, compQuestion.getChildOrientation());

            assertEquals(2, compQuestion.getChildren().size());

            Question child0 = compQuestion.getChildren().get(0);
            assertTrue(child0 instanceof DateQuestion);
            assertEquals(dateStableId, child0.getStableId());

            Question child1 = compQuestion.getChildren().get(1);
            assertTrue(child1 instanceof TextQuestion);
            assertEquals(textStableId, child1.getStableId());
            TextQuestion textChild = (TextQuestion) child1;
            assertEquals(1, textChild.getValidations().size());
            assertEquals(RuleType.LENGTH, textChild.getValidations().get(0).getRuleType());


            assertEquals(1, compQuestion.getAnswers().size());
            Answer returnedAnswer = compQuestion.getAnswers().get(0);
            assertEquals(QuestionType.COMPOSITE, returnedAnswer.getQuestionType());
            CompositeAnswer compositeAnswer = (CompositeAnswer) returnedAnswer;

            assertEquals(1, compositeAnswer.getValue().size());
            List<AnswerRow> childAnswers = compositeAnswer.getValue();
            assertEquals(2, childAnswers.get(0).getValues().size());

            AnswerRow firstRowOfAnswers = childAnswers.get(0);

            assertTrue(firstRowOfAnswers.getValues().get(0) instanceof DateAnswer);
            DateAnswer dateAnswer = (DateAnswer) firstRowOfAnswers.getValues().get(0);
            assertNotNull(firstRowOfAnswers.getValues().get(0).getAnswerId());
            assertNotNull(firstRowOfAnswers.getValues().get(0).getAnswerGuid());
            assertEquals(dateValue, dateAnswer.getValue());

            assertTrue(firstRowOfAnswers.getValues().get(1) instanceof TextAnswer);
            TextAnswer textAnswer = (TextAnswer) firstRowOfAnswers.getValues().get(1);
            assertNotNull(firstRowOfAnswers.getValues().get(1).getAnswerId());
            assertNotNull(firstRowOfAnswers.getValues().get(1).getAnswerGuid());
            assertEquals(textValue, textAnswer.getValue());

            assertTrue(compQuestion.passesDeferredValidations());

            handle.rollback();
        });
    }

    @Test
    public void testGetCompositeQuestion_cantGetById() {
        TransactionWrapper.useTxn(handle -> {
            Template datePrompt = new Template(TemplateType.TEXT, null, "date prompt");
            String dateStableId = "CHILD_DATE" + Instant.now().toEpochMilli();
            DateQuestionDef dateQuestion = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, dateStableId, datePrompt)
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .setDisplayCalendar(true)
                    .build();

            LengthRuleDef lengthRule = new LengthRuleDef(null, 5, 300);
            Template textPrompt = new Template(TemplateType.TEXT, null, "text prompt");
            String textStableId = "CHILD_TEXT" + Instant.now().toEpochMilli();
            TextQuestionDef textQuestion = TextQuestionDef.builder(TextInputType.TEXT, textStableId, textPrompt)
                    .addValidation(lengthRule)
                    .build();

            Template addButtonTextTemplate = new Template(TemplateType.TEXT, null, "Add Button");
            Template additionalItemTemplate = new Template(TemplateType.TEXT, null, "Another Item");
            CompositeQuestionDef questionDef = CompositeQuestionDef.builder()
                    .setStableId(sid)
                    .setPrompt(prompt)
                    .addChildrenQuestions(dateQuestion, textQuestion)
                    .setAllowMultiple(true)
                    .setAddButtonTemplate(addButtonTextTemplate)
                    .setAdditionalItemTemplate(additionalItemTemplate)
                    .build();

            FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), questionDef);

            ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            ActivityInstanceDto activityInstanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            DateValue dateValue = new DateValue(2018, 10, 10);
            DateAnswer da = new DateAnswer(null, dateStableId, null, dateValue);
            String textValue = "text!";
            TextAnswer ta = new TextAnswer(null, textStableId, null, textValue);
            CompositeAnswer compAnswer = new CompositeAnswer(null, sid, null);
            compAnswer.addRowOfChildAnswers(da, ta);

            Answer ans = handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), activityInstanceDto.getId(), compAnswer);
            assertNotNull(ans);
            assertNotNull(ans.getAnswerId());
            assertNotNull(ans.getAnswerGuid());

            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(questionDef.getQuestionId()).get();

            questionDto = new QuestionDto(questionDto.getType(), questionDto.getId() + 1,
                    questionDto.getStableId(), questionDto.getPromptTemplateId(),
                    questionDto.getAdditionalInfoHeaderTemplateId(), questionDto.getAdditionalInfoFooterTemplateId(),
                    questionDto.getActivityId(), questionDto.isRestricted(), questionDto.isDeprecated(),
                    questionDto.shouldHideNumber(), questionDto.getRevisionId(),
                    questionDto.getRevisionStart(), questionDto.getRevisionEnd());

            thrown.expect(DaoException.class);
            thrown.expectMessage("Could not find composite question using question id " + questionDto.getId());
            handle.attach(QuestionDao.class).getCompositeQuestion(questionDto,
                    activityInstanceDto.getGuid(),
                    List.of(ans.getAnswerId()),
                    Collections.emptyList(),
                    true,
                    langCodeId);

            handle.rollback();
        });
    }

    private FormActivityDef buildSingleSectionForm(String studyGuid, QuestionDef... questions) {
        return FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(questions)))
                .build();
    }

    // Helper to convert list of things to an array.
    @SafeVarargs
    private final <T> T[] toArray(T... objects) {
        return objects;
    }

    private class GetGenericQuestionData {
        private FormActivityDef form;
        private long activityId;
        private String activityInstanceGuid;
        private TextQuestionDef question;
        private List<Long> answerIds;

        public GetGenericQuestionData(FormActivityDef form, long activityId, String activityInstanceGuid,
                                      TextQuestionDef question, List<Long> answerIds) {
            this.form = form;
            this.activityId = activityId;
            this.activityInstanceGuid = activityInstanceGuid;
            this.question = question;
            this.answerIds = answerIds;
        }

        public FormActivityDef getForm() {
            return form;
        }

        public long getActivityId() {
            return activityId;
        }

        public String getActivityInstanceGuid() {
            return activityInstanceGuid;
        }

        public TextQuestionDef getQuestion() {
            return question;
        }

        public List<Long> getAnswerIds() {
            return answerIds;
        }
    }
}
