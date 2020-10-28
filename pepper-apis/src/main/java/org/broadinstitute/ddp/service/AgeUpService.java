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
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.AgeUpCandidate;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgeUpService {

    private static final Logger LOG = LoggerFactory.getLogger(AgeUpService.class);
    private static final String SP_AGEUP = "ageup";
    private static final String SP_PREP = "prep";
    private static final String SP_STATUS = "status";

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
     * @return number of participants who has aged up and completed processing events in this run
     */
    public int runAgeUpCheck(Handle handle, PexInterpreter interpreter, GovernancePolicy policy) {
        StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
        InvitationDao invitationDao = handle.attach(InvitationDao.class);
        JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

        List<AgeUpCandidate> potentialCandidates;
        try (Stream<AgeUpCandidate> governanceStream = studyGovernanceDao.findAllAgeUpCandidatesByStudyId(policy.getStudyId())) {
            potentialCandidates = governanceStream.collect(Collectors.toList());
        }
        Collections.shuffle(potentialCandidates);

        String studyGuid = policy.getStudyGuid();
        Set<Long> exitedCandidates = new HashSet<>();
        Set<Long> preppedCandidateIds = new HashSet<>();
        Set<Long> completedCandidateIds = new HashSet<>();

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

            // First, handle updating consent status.
            if (agedUp && candidate.getStatus() != EnrollmentStatusType.CONSENT_SUSPENDED) {
                LOG.info("Candidate {} in study {} has reached age-of-majority, suspending consent status", userGuid, studyGuid);
                try {
                    TransactionWrapper.useSavepoint(named(SP_STATUS, studyGuid, userGuid), handle, h -> {
                        jdbiEnrollment.suspendUserStudyConsent(candidate.getParticipantUserId(), policy.getStudyId());
                        EventSignal signal = new EventSignal(
                                candidate.getParticipantUserId(),
                                candidate.getParticipantUserId(),
                                candidate.getParticipantUserGuid(),
                                policy.getStudyId(),
                                EventTriggerType.CONSENT_SUSPENDED);
                        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
                    });
                } catch (Exception e) {
                    LOG.error("Candidate {} in study {} has reached age-of-majority"
                            + " but could not suspend consent status, skipping", userGuid, studyGuid, e);
                    continue;
                }
            }

            // Second, see if we need to initiate the age-up process.
            if (shouldPrepForAgeUp && !candidate.hasInitiatedPrep()) {
                LOG.info("Candidate {} in study {} has reached preparation for age-of-majority,"
                        + " processing age-up preparation events", userGuid, studyGuid);
                try {
                    TransactionWrapper.useSavepoint(named(SP_PREP, studyGuid, userGuid), handle, h -> {
                        EventSignal signal = new EventSignal(
                                candidate.getParticipantUserId(),
                                candidate.getParticipantUserId(),
                                candidate.getParticipantUserGuid(),
                                policy.getStudyId(),
                                EventTriggerType.REACHED_AOM_PREP);
                        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
                    });
                    preppedCandidateIds.add(candidate.getId());
                } catch (Exception e) {
                    LOG.error("Error processing age-up preparation events for candidate {} in study {},"
                            + " rolling back and skipping", userGuid, studyGuid, e);
                    continue;
                }
            }

            // Lastly, see if we're ready to run the remaining age-up process.
            if (agedUp) {
                try {
                    boolean hasInvitation = invitationDao.findInvitations(policy.getStudyId(), candidate.getParticipantUserId())
                            .stream()
                            .filter(invitation -> invitation.getInvitationType() == InvitationType.AGE_UP)
                            .anyMatch(invitation -> !invitation.isVoid() && !invitation.isAccepted());
                    if (hasInvitation) {
                        LOG.info("Candidate {} in study {} has an age-up invitation, processing age-up events", userGuid, studyGuid);
                        TransactionWrapper.useSavepoint(named(SP_AGEUP, studyGuid, userGuid), handle, h -> {
                            EventSignal signal = new EventSignal(
                                    candidate.getParticipantUserId(),
                                    candidate.getParticipantUserId(),
                                    candidate.getParticipantUserGuid(),
                                    policy.getStudyId(),
                                    EventTriggerType.REACHED_AOM);
                            EventService.getInstance().processAllActionsForEventSignal(handle, signal);
                        });
                        completedCandidateIds.add(candidate.getId());
                    } else {
                        LOG.warn("Candidate {} in study {} does not have an age-up invitation,"
                                + " postponing age-up events", userGuid, studyGuid);
                    }
                } catch (Exception e) {
                    LOG.error("Error processing age-up events for candidate {} in study {},"
                            + " rolling back and skipping", userGuid, studyGuid, e);
                }
            }
        }

        int numRows = studyGovernanceDao.markAgeUpPrepInitiated(preppedCandidateIds);
        LOG.info("Initiated age-of-majority preparation for {} candidates", numRows);

        numRows = studyGovernanceDao.removeAgeUpCandidates(completedCandidateIds);
        LOG.info("Removed {} already aged up and completed candidates", numRows);

        numRows = studyGovernanceDao.removeAgeUpCandidates(exitedCandidates);
        LOG.info("Removed {} exited age-up candidates", numRows);

        return completedCandidateIds.size();
    }

    // Convenient helper to create a savepoint name that should be unique within the running transaction.
    private String named(String prefix, String studyGuid, String userGuid) {
        return String.format("%s_%s_%s", prefix, studyGuid, userGuid);
    }
}
