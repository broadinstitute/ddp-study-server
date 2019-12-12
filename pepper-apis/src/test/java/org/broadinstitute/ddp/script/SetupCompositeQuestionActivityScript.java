package org.broadinstitute.ddp.script;

import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility script to create an form activity with a composite question.
 */
@Ignore
public class SetupCompositeQuestionActivityScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(SetupCompositeQuestionActivityScript.class);

    @Test
    public void setUpExampleActivities() {
        TransactionWrapper.useTxn(handle -> {
            String code = "COMPOSITE_SAMP" + Instant.now().toEpochMilli();
            TextQuestionDef textRequiredDef = createBasicTextQuestionBuild("RequiredText")
                    .addValidation(new RequiredRuleDef(new Template(TemplateType.TEXT, null, "This is required!!!")))
                    .build();

            TextQuestionDef textDefFiveLettersMinDef = createBasicTextQuestionBuild("5 chars please!")
                    .addValidation(new LengthRuleDef(new Template(TemplateType.TEXT, null, "You did not give me 5 "
                            + "characters!"), 5, 300))
                    .build();

            Template birthReqHint = buildTemplate("birth_req", "Please enter date of birth in MM DD YYYY format");
            Template monthReqHint = buildTemplate("month_req", "Please enter birth month in MM format");
            Template dayReqHint = buildTemplate("day_req", "Please enter birth day in DD format");
            Template yearReqHint = buildTemplate("year_req", "Please enter birth year in YYYY format");
            List<RuleDef> birthRules = Arrays.asList(
                    new RequiredRuleDef(birthReqHint),
                    new DateFieldRequiredRuleDef(RuleType.MONTH_REQUIRED, monthReqHint),
                    new DateFieldRequiredRuleDef(RuleType.DAY_REQUIRED, dayReqHint),
                    new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, yearReqHint));

            DateQuestionDef dateDef = createDateBuilder().addValidations(birthRules).build();

            QuestionDef compositeDef = createCompositeQuestionDef("This is the parent question", "Add Another One",
                    "More data below", true, textRequiredDef, textDefFiveLettersMinDef, dateDef);

            FormSectionDef compositeSection = new FormSectionDef(null, TestUtil.wrapQuestions(compositeDef));

            FormActivityDef formActivity = FormActivityDef.generalFormBuilder(code, "v1", TestConstants.TEST_STUDY_GUID)
                    .addName(new Translation("en", "activity " + code))
                    .addSections(Arrays.asList(compositeSection))
                    .build();
            FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
            JdbiRevision revisionDao = handle.attach(JdbiRevision.class);
            JdbiUser userDao = handle.attach(JdbiUser.class);

            long revisionId = revisionDao.insert(userDao.getUserIdByGuid(TestConstants.TEST_USER_GUID),
                    Instant.now().toEpochMilli(),
                    null,
                    "embedded components test " + System.currentTimeMillis());


            formActivityDao.insertActivity(formActivity, revisionId);
            ActivityInstanceDao activityInstanceDao = handle.attach(org.broadinstitute.ddp.db.dao
                    .ActivityInstanceDao
                    .class);

            ActivityInstanceDto createdActivityInstance = activityInstanceDao.insertInstance(
                    formActivity.getActivityId(), TestConstants.TEST_USER_GUID,
                    TestConstants.TEST_USER_GUID,
                    CREATED,
                    false);
            LOG.info("Created test activity instance {} for activity {} for user {}", createdActivityInstance
                    .getGuid(), formActivity.getActivityCode(), TestConstants.TEST_USER_GUID);
        });
    }

    private DateQuestionDef.Builder createDateBuilder() {
        Template datePrompt = new Template(TemplateType.TEXT, null, "date prompt");
        String dateStableId = "CHILD_DATE" + Instant.now().toEpochMilli();
        return DateQuestionDef.builder(DateRenderMode.TEXT, dateStableId, datePrompt)
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .setDisplayCalendar(true);

    }

    private TextQuestionDef.Builder createBasicTextQuestionBuild(String promptText) {
        Template textPrompt = new Template(TemplateType.TEXT, null, promptText);
        String textStableId = "CHILD_TEXT" + Instant.now().toEpochMilli();
        return TextQuestionDef.builder(TextInputType.TEXT, textStableId, textPrompt);

    }

    private QuestionDef createCompositeQuestionDef(String prompText, String addButtonText, String additionalItemText,
                                                   boolean allowMultiple, QuestionDef... childQuestions) {
        String compositeQuestionId = "COMP_" + Instant.now().toEpochMilli();

        Template addButtonTextTemplate = new Template(TemplateType.TEXT, null, addButtonText);
        Template additionalItemTemplate = new Template(TemplateType.TEXT, null, additionalItemText);
        Template promptTemplate = new Template(TemplateType.TEXT, null, prompText);
        return CompositeQuestionDef.builder()
                .setStableId(compositeQuestionId)
                .setPrompt(promptTemplate)
                .addChildrenQuestions(childQuestions)
                .setAllowMultiple(allowMultiple)
                .setAddButtonTemplate(addButtonTextTemplate)
                .setAdditionalItemTemplate(additionalItemTemplate)
                .build();
    }

    private static Template buildTemplate(String varName, String varText) {
        Translation enTrans = new Translation("en", varText);
        TemplateVariable var = new TemplateVariable(varName, Collections.singletonList(enTrans));
        String templateText = Template.VELOCITY_VAR_PREFIX + varName;
        Template tmpl = new Template(TemplateType.HTML, null, templateText);
        tmpl.addVariable(var);
        return tmpl;
    }
}

