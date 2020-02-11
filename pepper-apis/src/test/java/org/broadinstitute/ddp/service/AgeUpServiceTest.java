package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.AgeUpCandidate;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class AgeUpServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static PexInterpreter interpreter = new TreeWalkInterpreter();
    private static AgeUpService service;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        Instant fixedDate = LocalDate.of(2020, 3, 14).atStartOfDay(ZoneOffset.UTC).toInstant();
        service = new AgeUpService(Clock.fixed(fixedDate, ZoneOffset.UTC));
    }

    @Test
    public void testRunAgeUpCheck_noCandidates() {
        TransactionWrapper.useTxn(handle -> {
            GovernancePolicy policy = new GovernancePolicy(testData.getStudyId(), new Expression("true"));
            assertEquals(0, service.runAgeUpCheck(handle, interpreter, policy));
        });
    }

    @Test
    public void testRunAgeUpCheck_skipWhenNoBirthDate() {
        TransactionWrapper.useTxn(handle -> {
            User user1 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, null);
            User user2 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 1, 14));
            handle.attach(InvitationFactory.class)
                    .createInvitation(InvitationType.AGE_UP, testData.getStudyId(), user2.getId(), "test@datadonationplatform.org");

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, null));
            assertEquals(1, service.runAgeUpCheck(handle, interpreter, policy));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(1, candidates.size());
            assertEquals(user1.getId(), candidates.get(0).getParticipantUserId());

            handle.rollback();
        });
    }

    @Test
    public void testRunAgeUpCheck_removeWhenExitedStudy() {
        TransactionWrapper.useTxn(handle -> {
            createAgeUpTestCandidate(handle, EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT, LocalDate.of(2000, 5, 14));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(1, candidates.size());

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, null));
            assertEquals(0, service.runAgeUpCheck(handle, interpreter, policy));

            candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(0, candidates.size());

            handle.rollback();
        });
    }

    @Test
    public void testRunAgeUpCheck_reachedAgeOfMajority() {
        TransactionWrapper.useTxn(handle -> {
            User user1 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 1, 14));

            // Setup downstream event and test data
            ActivityInstanceDto instanceDto = createActivityAndInstance(handle, user1);
            long triggerId = handle.attach(EventTriggerDao.class)
                    .insertStaticTrigger(EventTriggerType.REACHED_AOM);
            long actionId = handle.attach(EventActionDao.class)
                    .insertMarkActivitiesReadOnlyAction(Set.of(instanceDto.getActivityId()));
            handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testData.getStudyId(),
                    Instant.now().toEpochMilli(), null, 0, null, null, false, 1);

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, null));
            assertEquals("should be skipped since there's no invitation",
                    0, service.runAgeUpCheck(handle, interpreter, policy));

            // Create invitation so they're ready for age-up
            handle.attach(InvitationFactory.class)
                    .createInvitation(InvitationType.AGE_UP, testData.getStudyId(), user1.getId(), "test@datadonationplatform.org");
            assertEquals(1, service.runAgeUpCheck(handle, interpreter, policy));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(0, candidates.size());

            Optional<EnrollmentStatusType> status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user1.getId(), testData.getStudyId());
            assertEquals(EnrollmentStatusType.CONSENT_SUSPENDED, status.get());

            assertTrue("activity instance should have been made read-only", handle.attach(JdbiActivityInstance.class)
                    .getByActivityInstanceId(instanceDto.getId()).get().isReadonly());

            handle.rollback();
        });
    }

    @Test
    public void testRunAgeUpCheck_reachedAgeOfMajorityPrep() {
        TransactionWrapper.useTxn(handle -> {
            User user1 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 5, 14));

            // Setup downstream event and test data
            ActivityInstanceDto instanceDto = createActivityAndInstance(handle, user1);
            long triggerId = handle.attach(EventTriggerDao.class)
                    .insertStaticTrigger(EventTriggerType.REACHED_AOM_PREP);
            long actionId = handle.attach(EventActionDao.class)
                    .insertMarkActivitiesReadOnlyAction(Set.of(instanceDto.getActivityId()));
            handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testData.getStudyId(),
                    Instant.now().toEpochMilli(), null, 0, null, null, false, 1);

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, 4));
            assertEquals(0, service.runAgeUpCheck(handle, interpreter, policy));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(1, candidates.size());
            assertEquals(user1.getId(), candidates.get(0).getParticipantUserId());
            assertTrue(candidates.get(0).hasInitiatedPrep());

            assertTrue("activity instance should have been made read-only", handle.attach(JdbiActivityInstance.class)
                    .getByActivityInstanceId(instanceDto.getId()).get().isReadonly());

            handle.rollback();
        });
    }

    @Test
    public void testRunAgeUpCheck_errorInOneCandidateDoesNotAffectAnother() {
        TransactionWrapper.useTxn(handle -> {
            User user1 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 1, 14));
            User user2 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 1, 24));
            handle.attach(InvitationFactory.class)
                    .createInvitation(InvitationType.AGE_UP, testData.getStudyId(), user1.getId(), "test@datadonationplatform.org");

            // Setup downstream event which will fail for second candidate
            long triggerId = handle.attach(EventTriggerDao.class)
                    .insertStaticTrigger(EventTriggerType.REACHED_AOM_PREP);
            long actionId = handle.attach(EventActionDao.class)
                    .insertInvitationEmailNotificationAction(new SendgridEmailEventActionDto("template", "en"));
            handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testData.getStudyId(),
                    Instant.now().toEpochMilli(), null, 0, null, null, true, 1);

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, 4));
            assertEquals("should have only completed 1 candidate",
                    1, service.runAgeUpCheck(handle, interpreter, policy));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(1, candidates.size());
            assertEquals(user2.getId(), candidates.get(0).getParticipantUserId());

            Optional<EnrollmentStatusType> status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user1.getId(), testData.getStudyId());
            assertEquals("should persist changes for first candidate",
                    EnrollmentStatusType.CONSENT_SUSPENDED, status.get());
            List<QueuedEventDto> events = handle.attach(EventDao.class).findAllQueuedEvents();
            assertTrue("should have an event for first candidate", events.stream()
                    .anyMatch(event -> event.getParticipantGuid().equals(user1.getGuid())));

            status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user2.getId(), testData.getStudyId());
            assertEquals("should still change status for second candidate",
                    EnrollmentStatusType.CONSENT_SUSPENDED, status.get());
            assertFalse("should not have an event for second candidate", events.stream()
                    .anyMatch(event -> event.getParticipantGuid().equals(user2.getGuid())));

            handle.rollback();
        });
    }

    private User createAgeUpTestCandidate(Handle handle, EnrollmentStatusType status, LocalDate birthDate) {
        User user = handle.attach(UserDao.class).createUser(testData.getClientId(), null);
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(user.getGuid(), testData.getStudyGuid(), status);
        handle.attach(JdbiProfile.class).insert(new UserProfileDto(user.getId(), null, null, null, birthDate, null, null, null));
        handle.attach(StudyGovernanceDao.class).addAgeUpCandidate(testData.getStudyId(), user.getId());
        return user;
    }

    private ActivityInstanceDto createActivityAndInstance(Handle handle, User user) {
        FormActivityDef activity = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "dummy activity"))
                .build();
        handle.attach(ActivityDao.class)
                .insertActivity(activity, RevisionMetadata.now(testData.getUserId(), "test"));
        assertNotNull(activity.getActivityId());
        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(activity.getActivityId(), user.getGuid());
    }
}
