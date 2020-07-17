package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityDef form;
    private static String textSid;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);

            long now = Instant.now().toEpochMilli();
            textSid = "text" + now;

            form = FormActivityDef.generalFormBuilder("ACT" + now, "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "dummy activity"))
                    .addSection(new FormSectionDef(null, Arrays.asList(
                            new QuestionBlockDef(TextQuestionDef
                                    .builder(TextInputType.TEXT, textSid, Template.text("text"))
                                    .build()))))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            assertNotNull(form.getActivityId());
        });
    }

    @Test
    public void testDeleteTempUsers_noExpiredUsers() {
        TransactionWrapper.useTxn(handle -> {
            long now = Instant.now().toEpochMilli();
            UserDao userDao = handle.attach(UserDao.class);

            assertTrue(userDao.findExpiredTemporaryUserIds(now).isEmpty());
            assertEquals(0, userDao.deleteAllExpiredTemporaryUsers());
        });
    }

    @Test
    public void testDeleteTempUsers_onlyDeletesDataForExpiredTempUsers() {
        TransactionWrapper.useTxn(handle -> {
            User tempUser1 = newTempUser(handle);
            populateData(handle, tempUser1);

            User tempUser2 = newTempUser(handle);
            populateData(handle, tempUser2);

            User permUser = newTempUser(handle);
            populateData(handle, permUser);
            handle.attach(UserDao.class).upgradeUserToPermanentById(permUser.getId(), "fake123");

            // Expire the temp user
            long now = Instant.now().toEpochMilli();
            handle.attach(JdbiUser.class).updateExpiresAtById(tempUser1.getId(), now - 10000);

            UserDao userDao = handle.attach(UserDao.class);
            assertEquals(1, userDao.deleteAllExpiredTemporaryUsers());

            ensureDataExists(handle, tempUser2);
            ensureDataExists(handle, permUser);

            handle.rollback();
        });
    }

    private User newTempUser(Handle handle) {
        return handle.attach(UserDao.class).createTempUser(testData.getTestingClient().getClientId());
    }

    private void populateData(Handle handle, User tempUser) {
        long langId = LanguageStore.getDefault().getId();

        handle.attach(UserProfileDao.class).createProfile(
                new UserProfile.Builder(tempUser.getId())
                        .setFirstName("first").setLastName("last").setPreferredLangId(langId).setDoNotContact(false)
                        .build());

        ActivityInstanceDto instance = handle.attach(ActivityInstanceDao.class)
                .insertInstance(form.getActivityId(), tempUser.getGuid());

        assertNotNull(handle.attach(AnswerDao.class)
                .createAnswer(tempUser.getId(), instance.getId(), new TextAnswer(null, textSid, null, "ans")));
    }

    private void ensureDataExists(Handle handle, User user) {
        assertTrue(handle.attach(UserProfileDao.class)
                .findProfileByUserId(user.getId()).isPresent());

        List<ActivityInstanceDto> instances = handle.attach(JdbiActivityInstance.class)
                .findAllByUserIdAndStudyId(user.getId(), testData.getStudyId());

        assertEquals(1, instances.size());
        assertTrue(handle.attach(AnswerDao.class)
                .findAnswerByInstanceIdAndQuestionStableId(instances.get(0).getId(), textSid)
                .isPresent());
    }
}
