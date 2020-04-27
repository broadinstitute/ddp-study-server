package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RegexRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.validation.AgeRangeRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ValidationDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String studyGuid;
    private static long enLangId;

    private TextQuestionDef.Builder builder;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = testData.getTestingUser().getUserGuid();
            studyGuid = testData.getStudyGuid();
            enLangId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId("en");
        });
    }

    @Before
    public void refreshTestData() {
        String stableId = "SID" + Instant.now().toEpochMilli();
        Template prompt = new Template(TemplateType.TEXT, null, "prompt");
        builder = TextQuestionDef.builder(TextInputType.TEXT, stableId, prompt);
    }

    @Test
    public void testGetActiveValidations_questionNotFound() {
        TransactionWrapper.useTxn(handle -> {
            List<Rule> validations = handle.attach(ValidationDao.class).getValidationRules(-1, enLangId);
            assertEquals(0, validations.size());
        });
    }

    @Test
    public void testGetActiveValidations_languageCodeNotFound() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef question = builder.build();
            insertDummyActivity(handle, userGuid, studyGuid, question);

            List<Rule> validations = handle.attach(ValidationDao.class)
                    .getValidationRules(question.getQuestionId(), -1);
            assertEquals(0, validations.size());

            handle.rollback();
        });
    }

    @Test
    public void testGetActiveValidations() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef question = builder.addValidation(new RequiredRuleDef(null)).build();
            insertDummyActivity(handle, userGuid, studyGuid, question);

            List<Rule> validations = handle.attach(ValidationDao.class)
                    .getValidationRules(question.getQuestionId(), enLangId);
            assertEquals(1, validations.size());
            Rule rule = validations.get(0);
            assertEquals(RuleType.REQUIRED, rule.getRuleType());
            assertFalse(rule.getAllowSave());

            String expected = "This question requires an answer.";
            assertEquals(expected, rule.getDefaultMessage());

            handle.rollback();
        });
    }

    @Test
    public void testInsertAgeRangeRule() {
        TransactionWrapper.useTxn(handle -> {
            AgeRangeRuleDef ageRuleDef = new AgeRangeRuleDef(null, 16, 18);
            String stableId = "SID" + Instant.now().toEpochMilli();
            Template prompt = new Template(TemplateType.TEXT, null, "prompt");
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, stableId, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR, DateFieldType.DAY)
                    .addValidation(ageRuleDef)
                    .build();
            insertDummyActivity(handle, userGuid, studyGuid, question);

            List<Rule> validations = handle.attach(ValidationDao.class)
                    .getValidationRules(question.getQuestionId(), enLangId);
            assertEquals(1, validations.size());
            Rule rule = validations.get(0);
            assertEquals(RuleType.AGE_RANGE, rule.getRuleType());
            AgeRangeRule ageRule = (AgeRangeRule) rule;
            assertEquals(16L, (long) ageRule.getMinAge());
            assertEquals(18L, (long) ageRule.getMaxAge());

            handle.rollback();
        });
    }

    @Test
    public void testGetActiveValidations_sortsRequiredFirst() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef question = builder
                    .addValidation(new RegexRuleDef(null, "abc"))
                    .addValidation(new LengthRuleDef(null, 10, 15))
                    .addValidation(new RequiredRuleDef(null))
                    .build();
            insertDummyActivity(handle, userGuid, studyGuid, question);

            List<Rule> validations = handle.attach(ValidationDao.class)
                    .getValidationRules(question.getQuestionId(), enLangId);
            assertEquals(3, validations.size());

            assertEquals(RuleType.REQUIRED, validations.get(0).getRuleType());
            assertEquals(RuleType.REGEX, validations.get(1).getRuleType());
            assertEquals(RuleType.LENGTH, validations.get(2).getRuleType());

            handle.rollback();
        });
    }

    @Test
    public void testWritingTextQuestionAllowSaveTrueValidationAttribute() {
        TransactionWrapper.useTxn(handle -> {
            List<RuleDef> ruleDefs = Arrays.asList(
                    new RequiredRuleDef(null),
                    new RegexRuleDef(null, "abc"),
                    new LengthRuleDef(null, 10, 15),
                    new CompleteRuleDef(null)
            );
            ruleDefs.forEach(validation -> validation.setAllowSave(true));

            TextQuestionDef question = builder.addValidations(ruleDefs).build();
            insertDummyActivity(handle, userGuid, studyGuid, question);

            List<Rule> validations = handle.attach(ValidationDao.class)
                    .getValidationRules(question.getQuestionId(), enLangId);
            assertEquals(ruleDefs.size(), validations.size());
            assertEquals(ruleDefs.size(), validations.stream().filter(Rule::getAllowSave).count());
            assertEquals(0, validations.stream().filter(validation -> !validation.getAllowSave()).count());
            handle.rollback();
        });
    }

    @Test
    public void testWritingDateQuestionAllowSaveTrueValidationAttribute() {
        TransactionWrapper.useTxn(handle -> {
            List<RuleDef> ruleDefs = Arrays.asList(
                    new DateRangeRuleDef(null, LocalDate.now(), LocalDate.now(), false),
                    new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, null)
            );
            ruleDefs.forEach(validation -> validation.setAllowSave(true));
            String stableId = "SID" + Instant.now().toEpochMilli();
            Template prompt = new Template(TemplateType.TEXT, null, "prompt");
            DateQuestionDef.Builder dateBuilder = DateQuestionDef.builder(DateRenderMode.PICKLIST, stableId, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR);

            DateQuestionDef question = dateBuilder.addValidations(ruleDefs).build();
            insertDummyActivity(handle, userGuid, studyGuid, question);

            List<Rule> validations = handle.attach(ValidationDao.class)
                    .getValidationRules(question.getQuestionId(), enLangId);
            assertEquals(ruleDefs.size(), validations.size());
            assertEquals(ruleDefs.size(), validations.stream().filter(Rule::getAllowSave).count());
            assertEquals(0, validations.stream().filter(validation -> !validation.getAllowSave()).count());
            handle.rollback();
        });
    }

    private FormActivityDef insertDummyActivity(Handle handle, String userGuid, String studyGuid, QuestionDef question) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(question)))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
