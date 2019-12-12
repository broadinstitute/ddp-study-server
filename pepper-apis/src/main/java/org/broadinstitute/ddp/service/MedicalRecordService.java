package org.broadinstitute.ddp.service;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.ActivityMappingType;
import org.broadinstitute.ddp.model.dsm.StudyActivityMapping;

import org.jdbi.v3.core.Handle;

/**
 * Fetches the information related to the user medical record
 */
public class MedicalRecordService {

    private ConsentService consentService;

    public MedicalRecordService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public Optional<DateValue> getDateOfDiagnosis(Handle handle, String participantGuid, String studyGuid) {
        JdbiActivityMapping jdbiActivityMapping = handle.attach(JdbiActivityMapping.class);
        return Optional.ofNullable(getDateOfDiagnosisStudyActivityMapping(jdbiActivityMapping, studyGuid)).flatMap(
                dodMapping -> {
                    Optional<DateValue> dateOfDiagnosis = handle.attach(AnswerDao.class).findLatestDateAnswerByQuestionStableIdAndUserGuid(
                            dodMapping.getSubActivityStableId(),
                            participantGuid,
                            studyGuid
                    );
                    return dateOfDiagnosis;
                }
        );
    }

    public Optional<DateValue> getDateOfBirth(Handle handle, String participantGuid, String studyGuid) {
        JdbiActivityMapping jdbiActivityMapping = handle.attach(JdbiActivityMapping.class);
        return Optional.ofNullable(getDateOfBirthStudyActivityMapping(jdbiActivityMapping, studyGuid)).flatMap(
                dobMapping -> {
                    Optional<DateValue> dateOfBirth = handle.attach(AnswerDao.class).findLatestDateAnswerByQuestionStableIdAndUserGuid(
                            dobMapping.getSubActivityStableId(),
                            participantGuid,
                            studyGuid
                    );
                    return dateOfBirth;
                }
        );
    }

    public ParticipantConsents fetchBloodAndTissueConsents(Handle handle, String participantGuid, String studyGuid) {
        List<ConsentSummary> consentSummaries = consentService.getAllConsentSummariesByUserGuid(handle, participantGuid, studyGuid);
        JdbiActivityMapping jdbiActivityMapping = handle.attach(JdbiActivityMapping.class);
        StudyActivityMapping bloodStudyActivityMapping = getBloodStudyConsentMapping(jdbiActivityMapping, studyGuid);
        StudyActivityMapping tissueStudyActivityMapping = getTissueStudyConsentMapping(jdbiActivityMapping, studyGuid);

        long activityId = 0L;
        if (bloodStudyActivityMapping != null) {
            activityId = bloodStudyActivityMapping.getStudyActivityId();
        } else if (tissueStudyActivityMapping != null) {
            activityId = tissueStudyActivityMapping.getStudyActivityId();
        }

        boolean hasConsentedToBloodDraw = false;
        boolean hasConsentedToTissueSample = false;
        for (ConsentSummary summary : consentSummaries) {
            if (summary.getActivityId() == activityId) {
                for (ConsentElection election : summary.getElections()) {
                    boolean hasElection = election.getSelected() != null ? election.getSelected() : false;
                    if (election.getStableId().equals(bloodStudyActivityMapping.getSubActivityStableId())) {
                        hasConsentedToBloodDraw = hasElection;
                    } else if (election.getStableId().equals(tissueStudyActivityMapping.getSubActivityStableId())) {
                        hasConsentedToTissueSample = hasElection;
                    }
                }

            }
        }
        return new ParticipantConsents(hasConsentedToBloodDraw, hasConsentedToTissueSample);
    }

    /**
     * Queries the activity mapping table to figure out which Activity Id and Question Stable Id map to Date of Diagnosis
     *
     * @param jdbiActivityMapping dao for retrieving consent mappings
     * @param studyGuid study we are looking date of diagnosis up for
     * @return StudyActivityMapping for Date of Diagnosis for this study
     */
    private StudyActivityMapping getDateOfDiagnosisStudyActivityMapping(JdbiActivityMapping jdbiActivityMapping, String studyGuid) {
        return jdbiActivityMapping
                .getActivityMappingForStudyAndActivityType(studyGuid, ActivityMappingType.DATE_OF_DIAGNOSIS.toString())
                .orElse(null);
    }

    /**
     * Queries the activity mapping table to figure out which Activity Id and Question Stable Id map to Date of Birth
     *
     * @param jdbiActivityMapping dao for retrieving consent mappings
     * @param studyGuid study we are looking date of diagnosis up for
     * @return StudyActivityMapping for Date of Diagnosis for this study
     */
    private StudyActivityMapping getDateOfBirthStudyActivityMapping(JdbiActivityMapping jdbiActivityMapping, String studyGuid) {
        return jdbiActivityMapping
                .getActivityMappingForStudyAndActivityType(studyGuid, ActivityMappingType.DATE_OF_BIRTH.toString())
                .orElse(null);
    }

    /**
     * Queries the consent mapping table to figure out which Activity Id and Election Id map to Blood Draw Consent
     *
     * @param jdbiActivityMapping dao for retrieving consent mappings
     * @param studyGuid           study we are looking blood draw up for
     * @return StudyActivityMapping for Blood Draw for this study
     */
    private StudyActivityMapping getBloodStudyConsentMapping(JdbiActivityMapping jdbiActivityMapping, String studyGuid) {
        return jdbiActivityMapping
                .getActivityMappingForStudyAndActivityType(studyGuid, ActivityMappingType.BLOOD.toString())
                .orElse(null);
    }

    /**
     * Queries the consent mapping table to figure out which Activity Id and Election Id map to Tissue Collection Consent
     *
     * @param jdbiActivityMapping dao for retrieving consent mappings
     * @param studyGuid           study we are looking tissue sample up for
     * @return StudyActivityMapping for Tissue Sample for this study
     */
    private StudyActivityMapping getTissueStudyConsentMapping(JdbiActivityMapping jdbiActivityMapping, String studyGuid) {
        return jdbiActivityMapping
                .getActivityMappingForStudyAndActivityType(studyGuid, ActivityMappingType.TISSUE.toString())
                .orElse(null);
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
