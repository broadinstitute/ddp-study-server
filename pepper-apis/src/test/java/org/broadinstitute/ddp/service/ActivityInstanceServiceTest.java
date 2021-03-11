package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.FormInstanceDao;
import org.broadinstitute.ddp.db.SectionBlockDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.StudyLanguageCachedDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static ActivityInstanceService service;
    private static String userGuid;
    private static String studyGuid;

    @BeforeClass
    public static void setup() {
        PexInterpreter interpreter = new TreeWalkInterpreter();
        SectionBlockDao sectBlockDao = new SectionBlockDao();

        org.broadinstitute.ddp.db.ActivityInstanceDao actInstDao =
                new org.broadinstitute.ddp.db.ActivityInstanceDao(FormInstanceDao.fromDaoAndConfig(sectBlockDao, sqlConfig));
        service = new ActivityInstanceService(actInstDao, interpreter, new I18nContentRenderer());

        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        userGuid = testData.getUserGuid();
        studyGuid = testData.getStudyGuid();
    }

    @Test
    public void getTranslatedActivity() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);

            Optional<ActivityInstance> inst = service.getTranslatedActivity(handle,
                    testData.getUserGuid(), testData.getUserGuid(), ActivityType.FORMS, instanceGuid, "en", ContentStyle.STANDARD);
            assertTrue(inst.isPresent());
            assertEquals(inst.get().getActivityType(), ActivityType.FORMS);
            assertEquals(inst.get().getGuid(), instanceGuid);

            handle.rollback();
        });
    }

    @Test
    public void getTranslatedActivity_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityInstance> inst = service.getTranslatedActivity(handle,
                    testData.getUserGuid(), testData.getUserGuid(), ActivityType.FORMS, "random guid", "en", ContentStyle.STANDARD);
            assertNotNull(inst);
            assertFalse(inst.isPresent());
        });
    }

    @Test
    public void getTranslatedForm() {
        TransactionWrapper.useTxn(handle -> {
            String instanceGuid = setupActivityAndInstance(handle);

            Optional<FormInstance> inst = service.getTranslatedActivity(handle, testData.getUserGuid(),
                    testData.getUserGuid(), ActivityType.FORMS, instanceGuid, "en",
                    ContentStyle.STANDARD).map(i -> (FormInstance) i);
            assertTrue(inst.isPresent());
            assertEquals(inst.get().getActivityType(), ActivityType.FORMS);
            assertEquals(inst.get().getGuid(), instanceGuid);
            assertEquals(inst.get().getFormType(), FormType.PREQUALIFIER);
            assertFalse(inst.get().getBodySections().isEmpty());
            assertNotNull(inst.get().getActivityDefinitionLastUpdatedText());

            FormActivityDef testActivityDef = buildTestFormActivityDefinition();
            Translation varTransText = testActivityDef.getLastUpdatedTextTemplate().getVariable("LUNAR_MESSAGE").get()
                    .getTranslation("en").get();
            assertTrue(inst.get().getActivityDefinitionLastUpdatedText().startsWith(varTransText.getText()));
            assertTrue(inst.get().getActivityDefinitionLastUpdatedText().endsWith("July 20, 1969"));
            assertEquals(testActivityDef.getLastUpdated(), inst.get().getActivityDefinitionLastUpdated());


            handle.rollback();
        });
    }

    @Test
    public void getTranslatedForm_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<FormInstance> inst = service.getTranslatedActivity(handle, testData.getUserGuid(), testData.getUserGuid(),
                    ActivityType.FORMS, "random guid", "en",
                    ContentStyle.STANDARD).map(i -> (FormInstance) i);
            assertNotNull(inst);
            assertFalse(inst.isPresent());
        });
    }

    private String setupActivityAndInstance(Handle handle) {
        FormActivityDef formDef = buildTestFormActivityDefinition();
        handle.attach(ActivityDao.class).insertActivity(formDef, RevisionMetadata.now(testData.getUserId(), "add activity"));
        assertNotNull(formDef.getActivityId());

        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(formDef.getActivityId(), testData.getUserGuid())
                .getGuid();
    }

    private FormActivityDef buildTestFormActivityDefinition() {
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

    @Test
    public void testListActivityInstancesForUser_fallbackToEnglishOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            String expectedTitle = form.getTranslatedTitles().get(0).getText();
            String expectedSubtitle = form.getTranslatedSubtitles().get(0).getText();
            String expectedDescription = form.getTranslatedDescriptions().get(0).getText();
            String expectedStatusSummary = form.getTranslatedSummaries().get(0).getText();

            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(handle, userGuid, studyGuid, "xyz");
            assertEquals(1, summaries.size());

            ActivityInstanceSummary summary = summaries.get(0);
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());
            assertEquals(LanguageConstants.EN_LANGUAGE_CODE, summary.getIsoLanguageCode());
            assertEquals(expectedTitle, summary.getActivityTitle());
            assertEquals(expectedSubtitle, summary.getActivitySubtitle());
            assertEquals(expectedDescription, summary.getActivityDescription());
            assertEquals(expectedStatusSummary, summary.getActivitySummary());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_noSubtitle_fallbackToEnglishOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithoutSubtitle(handle, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            String expectedTitle = form.getTranslatedTitles().get(0).getText();

            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(handle, userGuid, studyGuid, "xyz");
            assertEquals(1, summaries.size());

            ActivityInstanceSummary summary = summaries.get(0);
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());
            assertEquals(LanguageConstants.EN_LANGUAGE_CODE, summary.getIsoLanguageCode());
            assertEquals(expectedTitle, summary.getActivityTitle());
            assertNull(summary.getActivitySubtitle());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_PreferredLanguageChosenOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithPreferredLang(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(handle, userGuid, studyGuid, "ru");
            assertEquals(1, summaries.size());

            ActivityInstanceSummary summary = summaries.get(0);
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());
            assertEquals("ru", summary.getIsoLanguageCode());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_fallbackToStudyDefaultLanguageOK() {
        TransactionWrapper.useTxn(handle -> {
            LanguageStore.init(handle);
            var studyLangDao = new StudyLanguageCachedDao(handle);
            boolean hasLang = studyLangDao.findLanguages(studyGuid).stream()
                    .anyMatch(lang -> lang.getLanguageCode().equals("fr"));
            if (!hasLang) {
                studyLangDao.insert(studyGuid, "fr", "french");
            }
            studyLangDao.setAsDefaultLanguage(testData.getStudyId(), "fr");

            FormActivityDef form = insertNewActivityWithStudyDefaultLang(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(handle, userGuid, studyGuid, "ru");
            assertEquals(1, summaries.size());

            ActivityInstanceSummary summary = summaries.get(0);
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());
            assertEquals("fr", summary.getIsoLanguageCode());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_multipleActivitiesAreListedCorrectly() {
        TransactionWrapper.useTxn(handle -> {
            insertNewInstance(handle, insertNewActivity(handle, studyGuid).getActivityId(), userGuid);
            insertNewInstance(handle, insertNewActivity(handle, studyGuid).getActivityId(), userGuid);

            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(handle, userGuid, studyGuid, "en");
            assertEquals("Inserted 2 activities for the user/study, but got something different", 2, summaries.size());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_activityWithLowerDisplayOrderGoesFirst() {
        TransactionWrapper.useTxn(handle -> {
            List<String> instanceGuids = new ArrayList<>();
            for (int i = 5; i > 0; i--) {
                FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                        .addName(new Translation("en", "test activity"))
                        .setDisplayOrder(i)
                        .build();
                handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add test activity"));
                String guid = insertNewInstance(handle, form.getActivityId(), userGuid);
                instanceGuids.add(guid);
            }

            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(handle, userGuid, studyGuid, "en");
            assertEquals(5, summaries.size());
            assertEquals(
                    "Activities must be sorted by display_order in ascendent order, this is not the case",
                    summaries.get(0).getActivityInstanceGuid(), instanceGuids.get(instanceGuids.size() - 1)
            );

            handle.rollback();
        });
    }

    private String insertNewInstance(Handle handle, long activityId, String userGuid) {
        return handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
    }

    private FormActivityDef insertNewActivity(Handle handle, String studyGuid) {
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

    private FormActivityDef insertNewActivityWithoutSubtitle(Handle handle, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private FormActivityDef insertNewActivityWithPreferredLang(Handle handle, String userGuid, String studyGuid) {
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

    private FormActivityDef insertNewActivityWithStudyDefaultLang(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("fr", "activité de test"))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    @Test
    public void testRenderInstanceSummaries_rendersNameEvenWhenThereIsNoSummaryText() {
        var summaries = List.of(new ActivityInstanceSummary(
                "activity", 1L, "guid", "name", null, null, null, null, null, "type", "form", "status",
                null, false, "en", false, false, 1L, false, false, "version", 1L, 1L));
        summaries.get(0).setInstanceNumber(2);

        TransactionWrapper.useTxn(handle ->
                service.renderInstanceSummaries(handle, testData.getUserId(), "study", summaries, Map.of()));

        assertEquals("name #2", summaries.get(0).getActivityName());
    }

    @Test
    public void testRenderInstanceSummaries_rendersAnswerIntoTitleAndSubtitle() {
        String title = "$ddp.answer(\"Q1\",\"text\") $ddp.answer(\"non-existing\", \"the-fallback\")";
        String subtitle = "$ddp.answer(\"Q2\",\"picklist\")";
        String activityCode = "ACT" + Instant.now().toEpochMilli();
        var summaries = List.of(new ActivityInstanceSummary(
                activityCode, 1L, "guid", "name", null, title, subtitle, null, null, "type", "form", "status",
                null, false, "en", false, false, 1L, false, false, "v1", 1L, 1L));
        summaries.get(0).setInstanceNumber(2);

        var response = new FormResponse(1L, "guid", 1L, null, 1L, 1L, null, null, 1L, activityCode, "v1",
                new ActivityInstanceStatusDto(1L, 1L, 1L, 1L, InstanceStatusType.CREATED));
        response.putAnswer(new TextAnswer(1L, "Q1", "guid1", "some-name"));
        response.putAnswer(new PicklistAnswer(2L, "Q2", "guid2", List.of(new SelectedPicklistOption("AUNT"))));

        var optionAunt = Template.text("$aunt");
        optionAunt.addVariable(TemplateVariable.single("aunt", "en", "My Aunt"));
        var optionUncle = Template.text("$uncle");
        optionUncle.addVariable(TemplateVariable.single("uncle", "en", "Should not use this one!"));
        var activity = FormActivityDef.generalFormBuilder(activityCode, "v1", "study")
                .addName(new Translation("en", "dummy activity"))
                // No need to add definition for text question since that's not needed for use-friendly display.
                .addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(
                        PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, "Q2", Template.text("picklist"))
                        .addOption(new PicklistOptionDef("AUNT", optionAunt))
                        .addOption(new PicklistOptionDef("UNCLE", optionUncle))
                        .build()))))
                .build();

        ActivityDefStore.getInstance().setActivityDef("study", activityCode, "v1", activity);
        TransactionWrapper.useTxn(handle -> service.renderInstanceSummaries(
                handle, testData.getUserId(), "study", summaries, Map.of("guid", response)));

        assertEquals("name #2", summaries.get(0).getActivityName());
        assertEquals("some-name the-fallback", summaries.get(0).getActivityTitle());
        assertEquals("My Aunt", summaries.get(0).getActivitySubtitle());
    }
}
