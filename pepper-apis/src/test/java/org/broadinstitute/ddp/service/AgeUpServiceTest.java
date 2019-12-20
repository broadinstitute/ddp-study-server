package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.AgeUpCandidate;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
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
    private static AgeUpService service = new AgeUpService();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        service.setToday(LocalDate.of(2020, 3, 14));
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
    public void testRunAgeUpCheck_reachedAgeOfMajorityPrep() {
        TransactionWrapper.useTxn(handle -> {
            User user1 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 1, 14));

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, null));
            assertEquals(1, service.runAgeUpCheck(handle, interpreter, policy));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(0, candidates.size());

            Optional<EnrollmentStatusType> status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user1.getId(), testData.getStudyId());
            assertEquals(EnrollmentStatusType.CONSENT_SUSPENDED, status.get());

            handle.rollback();
        });
    }

    @Test
    public void testRunAgeUpCheck_reachedAgeOfMajority() {
        TransactionWrapper.useTxn(handle -> {
            User user1 = createAgeUpTestCandidate(handle, EnrollmentStatusType.REGISTERED, LocalDate.of(2000, 5, 14));

            GovernancePolicy policy = new GovernancePolicy(1L, testData.getStudyId(), testData.getStudyGuid(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 20, 4));
            assertEquals(0, service.runAgeUpCheck(handle, interpreter, policy));

            List<AgeUpCandidate> candidates = handle.attach(StudyGovernanceDao.class)
                    .findAllAgeUpCandidatesByStudyId(testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(1, candidates.size());
            assertEquals(user1.getId(), candidates.get(0).getParticipantUserId());
            assertTrue(candidates.get(0).hasInitiatedPrep());

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
}
