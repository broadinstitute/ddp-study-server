package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class CopyAnswerServiceTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData data;
    private static String instanceGuid;
    private static CopyAnswerService copyService;
    private static ActivityInstanceDto instance;
    private static String textQuestionStableId;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            setupActivityAndInstance(handle);
        });
        copyService = CopyAnswerService.getInstance();
    }

    private static void setupActivityAndInstance(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        textQuestionStableId = "ANS_TEXT_" + timestamp;
        TextQuestionDef textDef = buildTextQuestionDef(textQuestionStableId);

        String activityCode = "ANS_ACT_" + timestamp;
        FormActivityDef form = FormActivityDef.generalFormBuilder(activityCode, "v1", data.getStudyGuid())
                .addName(new Translation("en", "test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(textDef)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(data.getTestingUser().getUserId(), "insert test "
                + "activity"));
        assertNotNull(form.getActivityId());

        instance = handle.attach(ActivityInstanceDao.class).insertInstance(form.getActivityId(),
                data.getTestingUser().getUserGuid());
        instanceGuid = instance.getGuid();
    }

    private static TextQuestionDef buildTextQuestionDef(String stableId) {
        return TextQuestionDef.builder().setStableId(stableId)
                .setInputType(TextInputType.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                .build();
    }

    @Test
    public void testCopyAnswerToProfileLastName() {
        TransactionWrapper.useTxn(handle -> {
            JdbiProfile profileDao = handle.attach(JdbiProfile.class);
            UserProfileDto originalProfile = profileDao.getUserProfileByUserId(data.getUserId());

            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            String lastNameFromAnswer = "Sargent" + Instant.now().toEpochMilli();
            Answer expected = new TextAnswer(null, textQuestionStableId, null, lastNameFromAnswer);
            String answerGuid = answerDao.createAnswer(handle, expected, data.getUserGuid(), instanceGuid);

            //Let's make sure they start being different
            assertNotEquals(originalProfile.getLastName(), expected.getValue());

            //set only the stuff that matters
            EventConfigurationDto eventConfig = new EventConfigurationDto(null, null, 0L, null, null, 0L, null, null,
                    EventActionType.COPY_ANSWER, 0L, textQuestionStableId, CopyAnswerTarget.PARTICIPANT_PROFILE_LAST_NAME);
            copyService.copyAnswerValue(eventConfig, instance, 0L, handle);

            UserProfileDto profile = profileDao.getUserProfileByUserId(data.getUserId());

            assertEquals(lastNameFromAnswer, profile.getLastName());
        });
    }

}
