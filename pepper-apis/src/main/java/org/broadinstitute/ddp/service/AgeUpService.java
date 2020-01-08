package org.broadinstitute.ddp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.AgeUpCandidate;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgeUpService {

    private static final Logger LOG = LoggerFactory.getLogger(AgeUpService.class);

    private final Clock clock;

    public AgeUpService() {
        // This is the clock `Instant.now()` uses.
        this(Clock.systemUTC());
    }

    // Use this constructor and a fixed clock for testing purposes.
    AgeUpService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Check age-up process for potential candidates based on given study policy.
     *
     * @param handle      the database handle
     * @param interpreter the pex interpreter
     * @param policy      the study age-up policy
     * @return number of participants who has aged up in this run
     */
    public int runAgeUpCheck(Handle handle, PexInterpreter interpreter, GovernancePolicy policy) {
        StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
        JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

        List<AgeUpCandidate> potentialCandidates = studyGovernanceDao
                .findAllAgeUpCandidatesByStudyId(policy.getStudyId())
                .collect(Collectors.toList());
        Collections.shuffle(potentialCandidates);

        String studyGuid = policy.getStudyGuid();
        Set<Long> exitedCandidates = new HashSet<>();
        Set<Long> preppedCandidateIds = new HashSet<>();
        Set<Long> agedUpCandidateIds = new HashSet<>();

        for (var candidate : potentialCandidates) {
            String userGuid = candidate.getParticipantUserGuid();
            if (candidate.getBirthDate() == null) {
                LOG.info("Age-up candidate with guid {} in study {} does not have birth date, skipping", userGuid, studyGuid);
                continue;
            } else if (candidate.getStatus().isExited()) {
                LOG.info("Age-up candidate with guid {} has exited study {}, will be removed", userGuid, studyGuid);
                exitedCandidates.add(candidate.getId());
                continue;
            }

            AgeOfMajorityRule rule;
            try {
                rule = policy.getApplicableAgeOfMajorityRule(handle, interpreter, userGuid).orElse(null);
            } catch (PexException e) {
                LOG.error("Error while evaluating age-of-majority rules for participant {} and study {}, skipping", userGuid, studyGuid, e);
                continue;
            }
            if (rule == null) {
                LOG.warn("No applicable age-of-majority rules found for participant {} in study {}", userGuid, studyGuid);
                continue;
            }

            LocalDate today = Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate();
            boolean agedUp = rule.hasReachedAgeOfMajority(candidate.getBirthDate(), today);
            boolean shouldPrepForAgeUp = rule.hasReachedAgeOfMajorityPrep(candidate.getBirthDate(), today).orElse(false);

            if (agedUp) {
                LOG.info("Age-up candidate {} in study {} has reached age-of-majority", userGuid, studyGuid);
                jdbiEnrollment.changeUserStudyEnrollmentStatus(userGuid, studyGuid, EnrollmentStatusType.CONSENT_SUSPENDED);
                // todo: trigger event service with REACHED_AOM signal
                agedUpCandidateIds.add(candidate.getId());
            } else if (!candidate.hasInitiatedPrep() && shouldPrepForAgeUp) {
                LOG.info("Age-up candidate {} in study {} has reached preparation for age-of-majority", userGuid, studyGuid);
                // todo: trigger event service with REACHED_AOM_PREP signal
                preppedCandidateIds.add(candidate.getId());
            }
        }

        int numRows = studyGovernanceDao.markAgeUpPrepInitiated(preppedCandidateIds);
        LOG.info("Initiated age-of-majority preparation for {} candidates", numRows);

        numRows = studyGovernanceDao.removeAgeUpCandidates(agedUpCandidateIds);
        LOG.info("Removed {} already aged up candidates", numRows);

        numRows = studyGovernanceDao.removeAgeUpCandidates(exitedCandidates);
        LOG.info("Removed {} exited age-up candidates", numRows);

        return agedUpCandidateIds.size();
    }
}
