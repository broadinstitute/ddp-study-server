package org.broadinstitute.ddp.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerSql;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.study.ActivityMapping;
import org.broadinstitute.ddp.model.study.ActivityMappingType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches the information related to the user medical record
 */
public class MedicalRecordService {

    private static final Logger LOG = LoggerFactory.getLogger(MedicalRecordService.class);

    private ConsentService consentService;

    public MedicalRecordService(ConsentService consentService) {
        this.consentService = consentService;
    }

    /**
     * Find value for "date of diagnosis". Looks through all submitted activity instances mapped by the study, sorted by latest
     * first-completion date, and looks for the first non-null date value.
     *
     * @param handle            the database handle
     * @param participantUserId the participant user id
     * @param studyId           the study id
     * @return first non-null date-of-diagnosis value, empty if none exists
     */
    public Optional<DateValue> getDateOfDiagnosis(Handle handle, long participantUserId, long studyId) {
        var answerSql = handle.attach(AnswerSql.class);

        Map<Long, ActivityMapping> mappings = handle.attach(ActivityDao.class)
                .findActivityMappings(studyId, ActivityMappingType.DATE_OF_DIAGNOSIS)
                .stream()
                .collect(Collectors.toMap(ActivityMapping::getActivityId, Function.identity()));


        try (Stream<ActivityResponse> responseStream = handle.attach(ActivityInstanceDao.class)
                .findBaseResponsesByStudyAndUserIds(studyId, Set.of(participantUserId), true, mappings.keySet())) {
            return responseStream.filter(instance -> instance.getFirstCompletedAt() != null)
                    .sorted(Comparator.comparing(ActivityResponse::getFirstCompletedAt).reversed())
                    .flatMap(instance -> answerSql
                            .findLatestDateValueByQuestionStableIdAndUserId(
                                    mappings.get(instance.getActivityId()).getSubActivityStableId(), participantUserId, studyId)
                            .stream()
                    ).findFirst();
        }
    }

    /**
     * Find value for "date of birth". Looks through all submitted activity instances mapped by the study, sorted by latest first-completion
     * date, and looks for the first non-null date value.
     *
     * @param handle            the database handle
     * @param participantUserId the participant user id
     * @param studyId           the study id
     * @return first non-null date-of-birth value, empty if none exists
     */
    public Optional<DateValue> getDateOfBirth(Handle handle, long participantUserId, long studyId) {
        var answerSql = handle.attach(AnswerSql.class);

        Map<Long, ActivityMapping> mappings = handle.attach(ActivityDao.class)
                .findActivityMappings(studyId, ActivityMappingType.DATE_OF_BIRTH)
                .stream()
                .collect(Collectors.toMap(ActivityMapping::getActivityId, Function.identity()));

        try (Stream<ActivityResponse> responseStream = handle.attach(ActivityInstanceDao.class)
                .findBaseResponsesByStudyAndUserIds(studyId, Set.of(participantUserId), true, mappings.keySet())) {
            return responseStream
                    .filter(instance -> instance.getFirstCompletedAt() != null)
                    .sorted(Comparator.comparing(ActivityResponse::getFirstCompletedAt).reversed())
                    .flatMap(instance -> answerSql
                            .findLatestDateValueByQuestionStableIdAndUserId(
                                    mappings.get(instance.getActivityId()).getSubActivityStableId(), participantUserId, studyId)
                            .stream()
                    ).findFirst();
        }
    }

    /**
     * Find consent election statuses for blood/tissue. Looks for the latest submitted activity instance for each election mapping and
     * determines its election status. Defaults to `false` when none exists or couldn't determine status.
     *
     * @param handle            the database handle
     * @param participantUserId the participant user id
     * @param participantGuid   the participant user guid
     * @param studyId           the study id
     * @param studyGuid         the study guid
     * @return the election statuses
     */
    public ParticipantConsents fetchBloodAndTissueConsents(Handle handle, long participantUserId, String participantGuid,
                                                           String operatorGuid, long studyId, String studyGuid) {
        var activityDao = handle.attach(ActivityDao.class);
        Map<Long, ActivityMapping> bloodMappings = activityDao
                .findActivityMappings(studyId, ActivityMappingType.BLOOD)
                .stream()
                .collect(Collectors.toMap(ActivityMapping::getActivityId, Function.identity()));
        Map<Long, ActivityMapping> tissueMappings = activityDao
                .findActivityMappings(studyId, ActivityMappingType.TISSUE)
                .stream()
                .collect(Collectors.toMap(ActivityMapping::getActivityId, Function.identity()));

        Set<Long> activityIds = new HashSet<>();
        activityIds.addAll(bloodMappings.keySet());
        activityIds.addAll(tissueMappings.keySet());

        List<ActivityResponse> sortedSubmittedInstances;
        try (Stream<ActivityResponse> responseStream = handle.attach(ActivityInstanceDao.class)
                .findBaseResponsesByStudyAndUserIds(studyId, Set.of(participantUserId), true, activityIds)) {
            sortedSubmittedInstances = responseStream
                    .filter(instance -> instance.getFirstCompletedAt() != null)
                    .sorted(Comparator.comparing(ActivityResponse::getFirstCompletedAt).reversed())
                    .collect(Collectors.toList());
        }

        boolean hasConsentedToBloodDraw = determineElectionStatus(handle, participantGuid, operatorGuid, studyGuid,
                ActivityMappingType.BLOOD, bloodMappings, sortedSubmittedInstances);
        boolean hasConsentedToTissueSample = determineElectionStatus(handle, participantGuid, operatorGuid, studyGuid,
                ActivityMappingType.TISSUE, tissueMappings, sortedSubmittedInstances);

        return new ParticipantConsents(hasConsentedToBloodDraw, hasConsentedToTissueSample);
    }

    private boolean determineElectionStatus(Handle handle, String participantGuid, String operatorGuid, String studyGuid,
                                            ActivityMappingType mappingType, Map<Long, ActivityMapping> mappings,
                                            List<ActivityResponse> instances) {
        ActivityResponse instance = instances.stream()
                .filter(inst -> mappings.containsKey(inst.getActivityId()))
                .findFirst()
                .orElse(null);
        if (instance == null) {
            LOG.info("No completed activity instance found for user {} and study {} for mapping type {}",
                    participantGuid, studyGuid, mappingType);
            return false;
        }

        ConsentSummary summary = consentService
                .getLatestConsentSummary(handle, participantGuid, operatorGuid, studyGuid, instance.getActivityCode())
                .orElse(null);
        if (summary == null) {
            LOG.error("No consent summary found for activity {} user {} study {} for mapping type {}",
                    instance.getActivityCode(), participantGuid, studyGuid, mappingType);
            return false;
        }

        String electionStableId = mappings.get(instance.getActivityId()).getSubActivityStableId();
        ConsentElection election = summary.getElections()
                .stream()
                .filter(consentElection -> consentElection.getStableId().equals(electionStableId))
                .findFirst()
                .orElse(null);
        if (election != null) {
            return election.getSelected() != null ? election.getSelected() : false;
        } else {
            LOG.error("No consent election found for stableId {} activity {} user {} study {} for mapping type {}",
                    electionStableId, instance.getActivityCode(), participantGuid, studyGuid, mappingType);
            return false;
        }
    }

    public static class ParticipantConsents {
        private boolean hasConsentedToBloodDraw;
        private boolean hasConsentedToTissueSample;

        public ParticipantConsents(boolean hasConsentedToBloodDraw, boolean hasConsentedToTissueSample) {
            this.hasConsentedToBloodDraw = hasConsentedToBloodDraw;
            this.hasConsentedToTissueSample = hasConsentedToTissueSample;
        }

        public boolean hasConsentedToBloodDraw() {
            return hasConsentedToBloodDraw;
        }

        public boolean hasConsentedToTissueSample() {
            return hasConsentedToTissueSample;
        }
    }

}
