package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.json.UserActivity;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;
    private static String studyGuid;

    private static ActivityInstanceDao dao;

    @BeforeClass
    public static void setup() {
        SectionBlockDao sectionBlockDao = new SectionBlockDao();
        FormInstanceDao formInstanceDao = FormInstanceDao.fromDaoAndConfig(sectionBlockDao, sqlConfig);
        dao = new ActivityInstanceDao(formInstanceDao);
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getTestingUser().getUserGuid();
            studyGuid = data.getStudyGuid();
        });
    }

    @Test
    public void testGetGuidOfLatestInstanceForUserAndActivities_userNotFound() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, userGuid, studyGuid);

            Optional<String> actual = dao.getGuidOfLatestInstanceForUserAndActivity(
                    handle, "abcxyz", form.getActivityCode(), data.getStudyId()
            );
            assertNotNull(actual);
            assertFalse(actual.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testGetGuidOfLatestInstanceForUserAndActivities_activityNotFound() {
        Optional<String> instanceGuid = TransactionWrapper.withTxn(
                handle -> dao.getGuidOfLatestInstanceForUserAndActivity(
                        handle, TestConstants.TEST_USER_GUID, "abcxyz", data.getStudyId()
                )
        );
        assertNotNull(instanceGuid);
        assertFalse(instanceGuid.isPresent());
    }

    @Test
    public void testGetGuidOfLatestInstanceForUserAndActivities_prequalInstance() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            Optional<String> actual = dao.getGuidOfLatestInstanceForUserAndActivity(
                    handle, userGuid, form.getActivityCode(), data.getStudyId()
            );
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertEquals(instanceGuid, actual.get());

            handle.rollback();
        });
    }


    @Test(expected = DaoException.class)
    public void testGetTranslatedSummaryByGuid_activityNotFound() {
        UserActivity summary = TransactionWrapper.withTxn(
                handle -> dao.getTranslatedSummaryByGuid(handle, "abcxyz", "en"));
    }

    @Test
    public void testGetTranslatedSummaryByGuid_fallbackToEnglishOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            UserActivity summary = dao.getTranslatedSummaryByGuid(handle, instanceGuid, "xyz");
            assertNotNull(summary);
            assertEquals("en", summary.getIsoLanguageCode());

            handle.rollback();
        });
    }

    @Test
    public void testGetTranslatedSummaryByGuid_PreferredLanguageChosenOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithPreferredLang(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            UserActivity summary = dao.getTranslatedSummaryByGuid(handle, instanceGuid, "ru");

            assertNotNull(summary);
            assertEquals("ru", summary.getIsoLanguageCode());
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());
            BiFunction<List<Translation>, String, Optional<Translation>> transFinder = (translations, langCode) ->
                    translations.stream().filter(each -> each.getLanguageCode().equals(langCode)).findFirst();

            Optional<Translation> optionalTitleRussianTranslation = transFinder.apply(form.getTranslatedTitles(), "ru");
            assertTrue(optionalTitleRussianTranslation.isPresent());
            assertEquals(optionalTitleRussianTranslation.get().getText(), summary.getTitle());

            Optional<Translation> optionalSubtitleRussianTranslation = transFinder.apply(form.getTranslatedSubtitles(), "ru");
            assertTrue(optionalSubtitleRussianTranslation.isPresent());
            assertEquals(optionalSubtitleRussianTranslation.get().getText(), summary.getSubtitle());

            handle.rollback();
        });
    }

    @Test
    public void testGetTranslatedSummaryByGuidWithoutSubtitle_PreferredLanguageChosenOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = buildActivityWithPreferredLanguageWithoutSubtitle(studyGuid);
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
            assertNotNull(form.getActivityId());

            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            UserActivity summary = dao.getTranslatedSummaryByGuid(handle, instanceGuid, "ru");

            assertNotNull(summary);
            assertEquals("ru", summary.getIsoLanguageCode());
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());
            BiFunction<List<Translation>, String, Optional<Translation>> transFinder = (translations, langCode) ->
                    translations.stream().filter(each -> each.getLanguageCode().equals(langCode)).findFirst();

            Optional<Translation> optionalTitleRussianTranslation = transFinder.apply(form.getTranslatedTitles(), "ru");
            assertTrue(optionalTitleRussianTranslation.isPresent());
            assertEquals(optionalTitleRussianTranslation.get().getText(), summary.getTitle());

            Optional<Translation> optionalSubtitleRussianTranslation = transFinder.apply(form.getTranslatedSubtitles(), "ru");
            assertFalse(optionalSubtitleRussianTranslation.isPresent());


            handle.rollback();
        });
    }

    @Test
    public void testGetTranslatedSummaryByGuid_fallbackToSecondaryLanguageOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithPreferredAndSecondaryLang(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            UserActivity summary = dao.getTranslatedSummaryByGuid(handle, instanceGuid, "ru");

            assertNotNull(summary);
            assertEquals("fr", summary.getIsoLanguageCode());
            assertEquals(instanceGuid, summary.getActivityInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_fallbackToEnglishOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<ActivityInstanceSummary> summary = dao.listActivityInstancesForUser(handle, userGuid, studyGuid, "xyz");
            assertNotNull(summary);
            assertEquals("en", summary.get(0).getIsoLanguageCode());
            String savedSubtitle = form.getTranslatedSubtitles().get(0).getText();
            assertNotNull(savedSubtitle);
            assertEquals(savedSubtitle, summary.get(0).getActivitySubtitle());
            String title = form.getTranslatedTitles().get(0).getText();
            assertNotNull(title);
            assertEquals(title, summary.get(0).getActivityTitle());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_noSubtitle_fallbackToEnglishOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithoutSubtitle(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);

            List<ActivityInstanceSummary> summary = dao.listActivityInstancesForUser(handle, userGuid, studyGuid, "xyz");
            assertNotNull(summary);
            assertEquals("en", summary.get(0).getIsoLanguageCode());

            assertNull(summary.get(0).getActivitySubtitle());

            String originalTitle = form.getTranslatedTitles().get(0).getText();
            assertNotNull(originalTitle);
            assertEquals(originalTitle, summary.get(0).getActivityTitle());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_PreferredLanguageChosenOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithPreferredLang(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            List<ActivityInstanceSummary> summary = dao.listActivityInstancesForUser(handle, userGuid, studyGuid, "ru");

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertEquals("ru", summary.get(0).getIsoLanguageCode());
            assertEquals(instanceGuid, summary.get(0).getActivityInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_fallbackToSecondaryLanguageOK() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivityWithPreferredAndSecondaryLang(handle, userGuid, studyGuid);
            String instanceGuid = insertNewInstance(handle, form.getActivityId(), userGuid);
            List<ActivityInstanceSummary> summary = dao.listActivityInstancesForUser(handle, userGuid, studyGuid, "ru");

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertEquals("fr", summary.get(0).getIsoLanguageCode());
            assertEquals(instanceGuid, summary.get(0).getActivityInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testGetGuidOfLatestInstanceForUserAndActivities_lastPrequalInstanceGoesFirst() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, userGuid, studyGuid);
            long now = System.currentTimeMillis();
            Arrays.asList(now, now + 10, now + 20, now + 30, now + 40).forEach(
                    ts -> insertNewInstance(handle, form.getActivityId(), userGuid, ts)
            );
            Optional<String> actual = dao.getGuidOfLatestInstanceForUserAndActivity(
                    handle, userGuid, form.getActivityCode(), data.getStudyId()
            );
            long latestInstanceCreatedAt = handle.attach(JdbiActivityInstance.class)
                    .getByActivityInstanceGuid(actual.get()).get().getCreatedAtMillis();
            assertEquals(now + 40, latestInstanceCreatedAt);

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
                handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(data.getUserId(), "add test activity"));
                String guid = insertNewInstance(handle, form.getActivityId(), userGuid);
                instanceGuids.add(guid);
            }
            List<ActivityInstanceSummary> summary = dao.listActivityInstancesForUser(handle, userGuid, studyGuid, "en");
            assertEquals(5, summary.size());
            assertEquals(
                    "Activities must be sorted by display_order in ascendent order, this is not the case",
                    summary.get(0).getActivityInstanceGuid(), instanceGuids.get(instanceGuids.size() - 1)
            );
            handle.rollback();
        });
    }

    @Test
    public void testListActivityInstancesForUser_multipleActivitiesAreListedCorrectly() throws InterruptedException {
        TransactionWrapper.useTxn(handle -> {
            insertNewInstance(handle, insertNewActivity(handle, userGuid, studyGuid).getActivityId(), userGuid);
            insertNewInstance(handle, insertNewActivity(handle, userGuid, studyGuid).getActivityId(), userGuid);
            List<ActivityInstanceSummary> summary = dao.listActivityInstancesForUser(handle, userGuid, studyGuid, "en");
            assertEquals("Inserted 2 activities for the user/study, but got something different", 2, summary.size());
            handle.rollback();
        });
    }

    @Test
    public void testRenderActivitySummary_rendersNameEvenWhenThereIsNoSummaryText() {
        var summaries = List.of(new ActivityInstanceSummary(
                "activity", 1L, "guid", "name", null, null, null, null, null, "type", "form", "status",
                null, false, "en", "type", false, false, 1L, false, "version", 1L, 1L));
        summaries.get(0).setInstanceNumber(2);

        TransactionWrapper.useTxn(handle ->
                dao.renderActivitySummary(handle, data.getUserId(), summaries));

        assertEquals("name #2", summaries.get(0).getActivityName());
    }

    private String insertNewInstance(Handle handle, long activityId, String userGuid, long createdAt) {
        return handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false, createdAt)
                .getGuid();
    }

    private String insertNewInstance(Handle handle, long activityId, String userGuid) {
        return handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
    }

    private FormActivityDef insertNewActivity(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .addSubtitle(new Translation("en", "test subtitle"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(data.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private FormActivityDef insertNewActivityWithoutSubtitle(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("en", "test activity"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(data.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private FormActivityDef insertNewActivityWithPreferredLang(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = buildActivityWithPreferredLanguage(studyGuid);
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private FormActivityDef buildActivityWithPreferredLanguageWithoutSubtitle(String studyGuid) {
        return FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("ru", "activity name"))
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("ru", "Тестовая активити"))
                .addTitle(new Translation("en", "test activity"))
                .build();
    }

    private FormActivityDef buildActivityWithPreferredLanguage(String studyGuid) {
        return FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("ru", "activity name"))
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("ru", "Тестовая активити"))
                .addTitle(new Translation("en", "test activity"))
                .addSubtitle(new Translation("ru", "подзаголовок"))
                .addSubtitle(new Translation("en", "subtitle"))
                .build();
    }

    private FormActivityDef insertNewActivityWithPreferredAndSecondaryLang(Handle handle, String userGuid, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("fr", "activité de test"))
                .build();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
