package org.broadinstitute.ddp.copy;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.LocalDate;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AnswerToProfileCopierTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCopy_noSourceAnswer() {
        TransactionWrapper.useTxn(handle -> {
            String expected = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId())
                    .get().getFirstName();

            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            var question = new QuestionDto(QuestionType.TEXT, 1L, "q", 1L, 1L, 1L, 1L, false, false, false, false, 1L, 1L, 1L);
            new AnswerToProfileCopier(handle, testData.getUserId())
                    .copy(instance, question, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);

            String actual = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId())
                    .get().getFirstName();
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testCopy_toOperatorName() {
        TransactionWrapper.useTxn(handle -> {
            var instance = new FormResponse(1L, "a", 1L, false, 1L, 1L, 1L, "b", "c", null);
            instance.putAnswer(new TextAnswer(1L, "q1", "a", "op-first"));
            instance.putAnswer(new TextAnswer(2L, "q2", "b", "op-last"));
            var copier = new AnswerToProfileCopier(handle, testData.getUserId());

            var q1 = new QuestionDto(QuestionType.TEXT, 1L, "q1", 1L, 1L, 1L, 1L, false, false, false, false, 1L, 1L, 1L);
            var q2 = new QuestionDto(QuestionType.TEXT, 2L, "q2", 2L, 2L, 2L, 2L, false, false, false, false, 2L, 2L, 2L);
            copier.copy(instance, q1, CopyLocationType.OPERATOR_PROFILE_FIRST_NAME);
            copier.copy(instance, q2, CopyLocationType.OPERATOR_PROFILE_LAST_NAME);

            UserProfile profile = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId()).get();
            assertEquals("op-first", profile.getFirstName());
            assertEquals("op-last", profile.getLastName());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_toParticipantName() {
        TransactionWrapper.useTxn(handle -> {
            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            instance.putAnswer(new TextAnswer(1L, "q1", "a", "ptp-first"));
            instance.putAnswer(new TextAnswer(2L, "q2", "b", "ptp-last"));
            var copier = new AnswerToProfileCopier(handle, 1L);

            var q1 = new QuestionDto(QuestionType.TEXT, 1L, "q1", 1L, 1L, 1L, 1L, false, false, false, false, 1L, 1L, 1L);
            var q2 = new QuestionDto(QuestionType.TEXT, 2L, "q2", 2L, 2L, 2L, 2L, false, false, false, false, 2L, 2L, 2L);
            copier.copy(instance, q1, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);
            copier.copy(instance, q2, CopyLocationType.PARTICIPANT_PROFILE_LAST_NAME);

            UserProfile profile = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId()).get();
            assertEquals("ptp-first", profile.getFirstName());
            assertEquals("ptp-last", profile.getLastName());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_toParticipantBirthDate() {
        TransactionWrapper.useTxn(handle -> {
            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            instance.putAnswer(new DateAnswer(1L, "q1", "a", 1987, 3, 14));
            var copier = new AnswerToProfileCopier(handle, testData.getUserId());

            var q1 = new QuestionDto(QuestionType.DATE, 1L, "q1", 1L, 1L, 1L, 1L, false, false, false, false, 1L, 1L, 1L);
            copier.copy(instance, q1, CopyLocationType.PARTICIPANT_PROFILE_BIRTH_DATE);

            UserProfile profile = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId()).get();
            assertEquals(LocalDate.of(1987, 3, 14), profile.getBirthDate());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_toParticipantBirthDate_notValidDateAnswer() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("invalid date"));
        TransactionWrapper.useTxn(handle -> {
            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            instance.putAnswer(new DateAnswer(1L, "q1", "a", 1987, null, null));
            var copier = new AnswerToProfileCopier(handle, testData.getUserId());
            var q1 = new QuestionDto(QuestionType.DATE, 1L, "q1", 1L, 1L, 1L, 1L, false, false, false, false, 1L, 1L, 1L);
            copier.copy(instance, q1, CopyLocationType.PARTICIPANT_PROFILE_BIRTH_DATE);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCopy_fromUnsupportedAnswerType() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("unable to convert answer"));
        TransactionWrapper.useTxn(handle -> {
            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            instance.putAnswer(new DateAnswer(1L, "q1", "a", 1987, null, null));
            var copier = new AnswerToProfileCopier(handle, testData.getUserId());
            var q1 = new QuestionDto(QuestionType.DATE, 1L, "q1", 1L, 1L, 1L, 1L, false, false, false, false, 1L, 1L, 1L);
            copier.copy(instance, q1, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCopy_fromCompositeChild() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef child = TextQuestionDef.builder(TextInputType.TEXT, "c1", Template.text("")).build();
            TextQuestionDef child2 = TextQuestionDef.builder(TextInputType.TEXT, "c2", Template.text("")).build();
            TestFormActivity act = TestFormActivity.builder()
                    .withCompositeQuestion(true, child, child2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            QuestionDto cq = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(child.getQuestionId()).get();
            QuestionDto cq2 = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(child2.getQuestionId()).get();

            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            CompositeAnswer answer = new CompositeAnswer(1L, act.getCompositeQuestion().getStableId(), "a");
            answer.addRowOfChildAnswers(new TextAnswer(2L, cq.getStableId(), "b", "child-text"));
            instance.putAnswer(answer);

            var copier = new AnswerToProfileCopier(handle, testData.getUserId());
            copier.copy(instance, cq, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);
            copier.copy(instance, cq2, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);

            UserProfile profile = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId()).get();
            assertEquals("child-text", profile.getFirstName());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_fromCompositeChild_noChildAnswers() {
        TransactionWrapper.useTxn(handle -> {
            String expected = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId())
                    .get().getFirstName();

            TextQuestionDef child = TextQuestionDef.builder(TextInputType.TEXT, "c1", Template.text("")).build();
            TestFormActivity.builder()
                    .withCompositeQuestion(true, child)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            QuestionDto cq = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(child.getQuestionId()).get();

            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            var copier = new AnswerToProfileCopier(handle, testData.getUserId());
            copier.copy(instance, cq, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);

            String actual = handle.attach(UserProfileDao.class)
                    .findProfileByUserId(testData.getUserId())
                    .get().getFirstName();
            assertEquals(expected, actual);

            handle.rollback();
        });
    }

    @Test
    public void testCopy_fromCompositeChild_multipleChildAnswers() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("multiple answers"));
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef child = TextQuestionDef.builder(TextInputType.TEXT, "c1", Template.text("")).build();
            TestFormActivity act = TestFormActivity.builder()
                    .withCompositeQuestion(true, child)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            QuestionDto cq = handle.attach(JdbiQuestion.class)
                    .getQuestionDtoById(child.getQuestionId()).get();

            var instance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "b", "c", null);
            CompositeAnswer answer = new CompositeAnswer(1L, act.getCompositeQuestion().getStableId(), "a");
            answer.addRowOfChildAnswers(new TextAnswer(2L, cq.getStableId(), "b", "child-text"));
            answer.addRowOfChildAnswers(new TextAnswer(3L, cq.getStableId(), "c", "child-text-2"));
            instance.putAnswer(answer);

            var copier = new AnswerToProfileCopier(handle, testData.getUserId());
            copier.copy(instance, cq, CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME);
            fail("expected exception not thrown");
        });
    }
}
