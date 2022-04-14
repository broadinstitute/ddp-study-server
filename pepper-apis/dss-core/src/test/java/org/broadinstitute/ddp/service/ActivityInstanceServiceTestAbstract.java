package org.broadinstitute.ddp.service;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;

/**
 * Abstract class which could be extended by concrete test classes
 * testing an {@link ActivityInstance} creation process
 */
public class ActivityInstanceServiceTestAbstract extends TxnAwareBaseTest {

    protected static TestDataSetupUtil.GeneratedTestData testData;
    protected static ActivityInstanceService service;
    protected static String userGuid;
    protected static String studyGuid;

    @BeforeClass
    public static void setup() {
        org.broadinstitute.ddp.db.ActivityInstanceDao actInstDao =
                new org.broadinstitute.ddp.db.ActivityInstanceDao();
        service = new ActivityInstanceService(actInstDao,  new TreeWalkInterpreter(), new I18nContentRenderer());
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        userGuid = testData.getUserGuid();
        studyGuid = testData.getStudyGuid();
    }

    protected String setupActivityAndInstance(Handle handle) {
        FormActivityDef formDef = buildTestFormActivityDefinition();
        handle.attach(ActivityDao.class).insertActivity(formDef, RevisionMetadata.now(testData.getUserId(), "add activity"));
        assertNotNull(formDef.getActivityId());

        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(formDef.getActivityId(), testData.getUserGuid())
                .getGuid();
    }

    protected FormActivityDef buildTestFormActivityDefinition() {
        String code = "ACT" + Instant.now().toEpochMilli();

        Template lastUpdatedTextTemplate = new Template(TemplateType.HTML, null, "$LUNAR_MESSAGE $"
                + I18nTemplateConstants.LAST_UPDATED);

        lastUpdatedTextTemplate.addVariable(new TemplateVariable("LUNAR_MESSAGE", Arrays.asList(
                new Translation("en", "The Eagle landed on "))));

        return FormActivityDef.formBuilder(FormType.PREQUALIFIER, code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "test prequal activity"))
                .addSection(new FormSectionDef(null, Collections.emptyList()))
                .setLastUpdatedTextTemplate(lastUpdatedTextTemplate)
                .setLastUpdated(LocalDateTime.of(1969, 7, 20, 4, 17))
                .build();
    }

    protected String insertNewInstance(Handle handle, long activityId, String userGuid) {
        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
    }

    protected FormActivityDef insertNewActivity(Handle handle, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addSecondName(new Translation("en", "activity second name"))
                .addTitle(new Translation("en", "test activity"))
                .addSubtitle(new Translation("en", "test subtitle"))
                .addDescription(new Translation("en", "test description"))
                .addSummary(new SummaryTranslation("en", "test summary", InstanceStatusType.CREATED))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    protected FormActivityDef insertNewActivityWithoutSubtitle(Handle handle, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    protected FormActivityDef insertNewActivityWithPreferredLang(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("ru", "activity name"))
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("ru", "Тестовая активити"))
                .addTitle(new Translation("en", "test activity"))
                .addSubtitle(new Translation("ru", "подзаголовок"))
                .addSubtitle(new Translation("en", "subtitle"))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    protected FormActivityDef insertNewActivityWithStudyDefaultLang(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("fr", "activité de test"))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    protected FormActivityDef insertDummyActivity(Handle handle, String userGuid, String studyGuid) {
        Template template = Template.html("$contact_your_organization");
        TemplateVariable templateVariable = TemplateVariable.single(
                "contact_your_organization",
                "en",
                "Please contact your organization for details"
        );
        template.addVariable(templateVariable);
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addName(new Translation("ru", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .addTitle(new Translation("ru", "тестовая деятельность"))
                .addSubtitle(new Translation("en", "test subtitle"))
                .addSubtitle(new Translation("ru", "тестовый субтитр"))
                .setReadonlyHintTemplate(template)
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    protected void testTranslation(
            FormActivityDef form,
            Function<FormActivityDef,
                    List<Translation>> formDefListMethod,
            String expectedValue,
            String languageCode
    ) {
        List<Translation> translations =
                formDefListMethod.apply(form).stream().filter(st -> st.getLanguageCode().equals(languageCode)).collect(toList());
        assertEquals(1, translations.size());
        assertNotNull(expectedValue);
        assertEquals(translations.get(0).getText(), expectedValue);
    }

    protected FormActivityDef insertDummyActivityWithoutSubtitle(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addName(new Translation("ru", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .addTitle(new Translation("ru", "тестовая деятельность"))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
