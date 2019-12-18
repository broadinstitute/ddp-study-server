package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.util.GuidUtils.UPPER_ALPHA_NUMERIC;
import static org.junit.Assert.assertNotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityAnswerCopierSqlTest extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityAnswerCopierSqlTest.class);

    private static final String DATE_QUESTION_STABLE_ID = "COPY_ANSWER_DATE_Q";

    private static final String ACTIVITY_CODE = "COPY_ANS_TEST";

    private static ActivityDef activityDef;

    private static ActivityInstanceDto sourceActivityInstance;

    private static ActivityInstanceDto destinationActivityInstance;

    private static UserDto testUser;

    private static StudyDto testStudy;

    private static QuestionDef dateQuestionDef;

    private static DateAnswer dateAnswer;

    private static ActivityDef createTestActivityDef(Handle handle, String userGuid, String studyGuid, String activityCode) {
        // todo arz refactor, copied from JdbiActivityTest

        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "test");

        List<Translation> names = Collections.singletonList(new Translation("en", "dummy activity"));
        List<Translation> subtitles = Collections.singletonList(new Translation("en", "dummy subtitle"));
        List<Translation> dashboardNames = Collections.singletonList(new Translation("en", "dummy dashboard name"));
        List<Translation> descriptions = Collections.singletonList(new Translation("en", "dummy description"));
        List<SummaryTranslation> dashboardSummaries = Collections.singletonList(
                new SummaryTranslation("en", "dummy dashboard summary", InstanceStatusType.CREATED)
        );
        List<FormSectionDef> sections = new ArrayList<>();

        dateQuestionDef = DateQuestionDef.builder().addFields(DateFieldType.DAY, DateFieldType.MONTH, DateFieldType.YEAR)
                .setStableId(DATE_QUESTION_STABLE_ID).setRenderMode(DateRenderMode.PICKLIST)
        .setPrompt(Template.text("Date question")).build();

        QuestionBlockDef dateQuestionBlock = new QuestionBlockDef(dateQuestionDef);

        List<FormBlockDef> blocks = new ArrayList<>();
        blocks.add(dateQuestionBlock);

        FormSectionDef formSectionDef = new FormSectionDef("COPY_ANSWER_TEST", blocks);

        sections.add(formSectionDef);

        Template readonlyHint = Template.html("Please contact your organization");
        FormActivityDef form = new FormActivityDef(
                FormType.GENERAL, activityCode, "v1", studyGuid, 1, 1, false,
                names, subtitles, dashboardNames, descriptions, dashboardSummaries,
                readonlyHint, null, sections, null, null, null, false
        );
        handle.attach(FormActivityDao.class).insertActivity(form, revId);

        assertNotNull(form.getActivityId());
        return form;
    }

    @BeforeClass
    public static void createTestingUser() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.GeneratedTestData generatedTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
            testStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);
            String userGuid = DBUtils.uniqueUserGuid(handle);
            String userHruid = DBUtils.uniqueUserHruid(handle);
            long testUserId = handle.attach(JdbiUser.class)
                    .insert(GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                            userGuid,
                            generatedTestData.getClientId(),
                            userHruid);
            testUser = handle.attach(JdbiUser.class).findByUserId(testUserId);

            activityDef = createTestActivityDef(handle, testUser.getUserGuid(), testStudy.getGuid(), ACTIVITY_CODE);


            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);

            sourceActivityInstance = activityInstanceDao.insertInstance(activityDef.getActivityId(), testUser.getUserGuid());

            destinationActivityInstance = activityInstanceDao.insertInstance(activityDef.getActivityId(), testUser.getUserGuid());


            JdbiAnswer jdbiAnswer = handle.attach(JdbiAnswer.class);
            JdbiDateAnswer jdbiDateAnswer = handle.attach(JdbiDateAnswer.class);

            long answerId = jdbiAnswer.insertBaseAnswer(dateQuestionDef.getQuestionId(),
                    testUser.getUserGuid(), sourceActivityInstance.getId());
            jdbiDateAnswer.insertAnswer(answerId, new DateValue(1, 1, 2020));

            dateAnswer = jdbiDateAnswer.getById(answerId).get();
        });

    }

    @Test
    public void testBaseAnswerCopy() {
        TransactionWrapper.useTxn(handle -> {
            LOG.info("Created activity instance {} for user {} in study {}", sourceActivityInstance.getGuid(), testUser.getUserGuid(),
                    testStudy.getGuid());

            ActivityAnswerCopierSql answerCopierSql = handle.attach(ActivityAnswerCopierSql.class);
            JdbiAnswer jdbiAnswerDao = handle.attach(JdbiAnswer.class);

            long now = System.currentTimeMillis();

            int initialNumberOfAnswers = handle.createQuery("select count(1) from answer").mapTo(Integer.class).findOnly();

            String copiedAnswerGuid = DBUtils.uniqueStandardGuid(handle, SqlConstants.AnswerTable.TABLE_NAME,
                    SqlConstants.AnswerTable.GUID);

            // tooo arz methods for copying date answer too
            long copiedAnswerId = answerCopierSql.copyBaseAnswer(copiedAnswerGuid, dateQuestionDef.getStableId(),
                    dateQuestionDef.getStableId(), sourceActivityInstance.getId(), destinationActivityInstance.getId(), now, now);

            int newAnswerCount = handle.createQuery("select count(1) from answer").mapTo(Integer.class).findOnly();

            Assert.assertEquals("Number of answer rows went from " + initialNumberOfAnswers + " to " + newAnswerCount,
                    initialNumberOfAnswers + 1, newAnswerCount);


            Optional<AnswerDto> copiedAnswer = jdbiAnswerDao.findDtoById(copiedAnswerId);

            Assert.assertTrue(copiedAnswer.isPresent());
            Assert.assertEquals(DATE_QUESTION_STABLE_ID, copiedAnswer.get().getQuestionStableId());
            Assert.assertEquals(QuestionType.DATE, copiedAnswer.get().getQuestionType());
            Assert.assertEquals(dateQuestionDef.getQuestionId().longValue(), copiedAnswer.get().getQuestionId());


        });
    }
}
