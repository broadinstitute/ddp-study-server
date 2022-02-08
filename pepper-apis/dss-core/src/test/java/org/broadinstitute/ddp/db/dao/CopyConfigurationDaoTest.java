package org.broadinstitute.ddp.db.dao;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.model.copy.CopyPreviousInstanceFilter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CopyConfigurationDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreateCopyConfig_notSupported_nonAnswerSource() {
        thrown.expect(DaoException.class);
        thrown.expectMessage(containsString("answer source locations"));
        TransactionWrapper.useTxn(handle -> {
            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(new CopyConfigurationPair(
                    new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME),
                    new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_LAST_NAME))));
            handle.attach(CopyConfigurationDao.class).createCopyConfig(config);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCreateCopyConfig_notSupported_mismatchedQuestionTypes() {
        thrown.expect(DaoException.class);
        thrown.expectMessage(containsString("between different question types"));
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(new CopyConfigurationPair(
                    new CopyAnswerLocation(act.getBoolQuestion().getStableId()),
                    new CopyAnswerLocation(act.getTextQuestion().getStableId()))));
            handle.attach(CopyConfigurationDao.class).createCopyConfig(config);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCreateCopyConfig_notSupported_topLevelComposite() {
        thrown.expect(DaoException.class);
        thrown.expectMessage(containsString("top-level composite question"));
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withCompositeQuestion(true, TextQuestionDef.builder(TextInputType.TEXT, "c1", Template.text("child")).build())
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(new CopyConfigurationPair(
                    new CopyAnswerLocation(act.getCompositeQuestion().getStableId()),
                    new CopyAnswerLocation(act.getCompositeQuestion().getStableId()))));
            handle.attach(CopyConfigurationDao.class).createCopyConfig(config);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCreateAndFindCopyConfig() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            var config = new CopyConfiguration(testData.getStudyId(), true,
                    List.of(new CopyPreviousInstanceFilter(new CopyAnswerLocation(act.getTextQuestion().getStableId()))),
                    List.of(new CopyConfigurationPair(
                            new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME))));

            CopyConfiguration actual = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            assertNotNull(actual);
            assertTrue(actual.getId() > 0);
            assertTrue(actual.shouldCopyFromPreviousInstance());
            assertEquals(1, actual.getPreviousInstanceFilters().size());
            assertEquals(1, actual.getPairs().size());

            CopyPreviousInstanceFilter actualFilter = actual.getPreviousInstanceFilters().get(0);
            assertTrue(actualFilter.getId() > 0);
            assertTrue(actualFilter.getLocation().getQuestionStableCodeId() > 0);
            assertEquals(act.getTextQuestion().getStableId(), actualFilter.getLocation().getQuestionStableId());

            CopyConfigurationPair actualPair = actual.getPairs().get(0);
            assertTrue(actualPair.getId() > 0);

            CopyLocation actualSource = actualPair.getSource();
            assertTrue(actualSource.getId() > 0);
            assertEquals(CopyLocationType.ANSWER, actualSource.getType());
            assertTrue(((CopyAnswerLocation) actualSource).getQuestionStableCodeId() > 0);
            assertEquals(act.getTextQuestion().getStableId(), ((CopyAnswerLocation) actualSource).getQuestionStableId());

            CopyLocation actualTarget = actualPair.getTarget();
            assertTrue(actualTarget.getId() > 0);
            assertEquals(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME, actualTarget.getType());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAndFindCopyConfig_multiplePairs() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME)),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.OPERATOR_PROFILE_FIRST_NAME))));

            CopyConfiguration actual = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            assertNotNull(actual);
            assertEquals(2, actual.getPairs().size());

            CopyConfigurationPair pair1 = actual.getPairs().get(0);
            assertTrue(pair1.getId() > 0);
            assertEquals(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME, pair1.getTarget().getType());

            CopyConfigurationPair pair2 = actual.getPairs().get(1);
            assertTrue(pair2.getId() > 0);
            assertEquals(CopyLocationType.OPERATOR_PROFILE_FIRST_NAME, pair2.getTarget().getType());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAndFindCopyConfig_compositeChild() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity.builder()
                    .withCompositeQuestion(true,
                            DateQuestionDef.builder(DateRenderMode.TEXT, "c1a", Template.text("")).addFields(DateFieldType.YEAR).build(),
                            TextQuestionDef.builder(TextInputType.TEXT, "c2a", Template.text("")).build())
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            TestFormActivity.builder()
                    .withCompositeQuestion(true,
                            DateQuestionDef.builder(DateRenderMode.TEXT, "c1b", Template.text("")).addFields(DateFieldType.YEAR).build(),
                            TextQuestionDef.builder(TextInputType.TEXT, "c2b", Template.text("")).build())
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(
                    new CopyConfigurationPair(new CopyAnswerLocation("c1a"), new CopyAnswerLocation("c1b")),
                    new CopyConfigurationPair(new CopyAnswerLocation("c2a"), new CopyAnswerLocation("c2b"))));

            CopyConfiguration actual = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            assertNotNull(actual);
            assertEquals(2, actual.getPairs().size());

            CopyConfigurationPair pair1 = actual.getPairs().get(0);
            assertTrue(pair1.getId() > 0);
            assertEquals("c1a", ((CopyAnswerLocation) pair1.getSource()).getQuestionStableId());
            assertEquals("c1b", ((CopyAnswerLocation) pair1.getTarget()).getQuestionStableId());

            CopyConfigurationPair pair2 = actual.getPairs().get(1);
            assertTrue(pair2.getId() > 0);
            assertEquals("c2a", ((CopyAnswerLocation) pair2.getSource()).getQuestionStableId());
            assertEquals("c2b", ((CopyAnswerLocation) pair2.getTarget()).getQuestionStableId());

            handle.rollback();
        });
    }

    @Test
    public void testFindCopyConfigById() {
        TransactionWrapper.useTxn(handle -> {
            Optional<CopyConfiguration> result = handle.attach(CopyConfigurationDao.class).findCopyConfigById(123456L);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        });
    }

    @Test
    public void testRemoveCopyConfig() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            var config = new CopyConfiguration(testData.getStudyId(), true,
                    List.of(new CopyPreviousInstanceFilter(new CopyAnswerLocation(act.getTextQuestion().getStableId()))),
                    List.of(new CopyConfigurationPair(
                            new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME))));

            CopyConfigurationDao copyConfigurationDao = handle.attach(CopyConfigurationDao.class);
            CopyConfiguration actual = copyConfigurationDao.createCopyConfig(config);
            assertNotNull(actual);
            assertTrue(actual.getId() > 0);

            copyConfigurationDao.removeCopyConfig(actual.getId());
            assertTrue(copyConfigurationDao.findCopyConfigById(actual.getId()).isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testRemoveCopyConfig_notFound() {
        thrown.expect(DaoException.class);
        thrown.expectMessage(containsString("does not exist"));
        TransactionWrapper.useTxn(handle -> {
            handle.attach(CopyConfigurationDao.class).removeCopyConfig(123456L);
            fail("expected exception not thrown");
        });
    }
}
