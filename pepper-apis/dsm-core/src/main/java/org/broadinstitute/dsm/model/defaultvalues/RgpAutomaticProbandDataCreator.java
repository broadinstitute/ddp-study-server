package org.broadinstitute.dsm.model.defaultvalues;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.model.participant.data.ParticipantData;
import org.broadinstitute.dsm.service.adminoperation.ReferralSourceService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;


@Slf4j
public class RgpAutomaticProbandDataCreator extends BasicDefaultDataMaker {

    public static final String RGP_FAMILY_ID = "rgp_family_id";


    /**
     * Given an elasticSearchParticipantDto, get selected data from ES and put it in DSM DB.
     * Log errors for severe problems.
     *
     * @return false if participant does not have an ES profile, true for all other cases, including some errors
     * @throws RuntimeException when there is no way to continue. We use this sparingly since the error handling
     *      in callers is not robust
     */
    @Override
    protected boolean setDefaultData() {
        // expecting ptp has a profile and has completed the enrollment activity
        if (elasticSearchParticipantDto.getProfile().isEmpty() || elasticSearchParticipantDto.getActivities().isEmpty()) {
            throw new ESMissingParticipantDataException("Participant does not yet have profile and activities in ES");
        }
        Profile esProfile = elasticSearchParticipantDto.getProfile().get();
        log.info("Got ES profile of participant: {}", esProfile.getGuid());

        List<FieldSettingsDto> fieldSettings =
                FieldSettingsDao.of().getOptionAndRadioFieldSettingsByInstanceId(Integer.parseInt(instance.getDdpInstanceId()));
        String participantId = StringUtils.isNotBlank(esProfile.getLegacyAltPid()) ? esProfile.getLegacyAltPid() : esProfile.getGuid();
        String instanceName = instance.getName();

        // ensure we can get a family ID before writing things to the DB
        // this will increment the family value (which we want to ensure we are not messed up by concurrency)
        // but will leave an unused family ID if we abort later. As things stand now that is not a concern.
        long familyId = getFamilyId(participantId, bookmark);
        writeFamilyIdToElastic(instance.getParticipantIndexES(), participantId, familyId);

        Map<String, String> probandDataMap = buildDataMap(participantId, familyId, instanceName,
                elasticSearchParticipantDto.getActivities(), esProfile);

        Map<String, String> columnsWithDefaultOptions =
                this.fieldSettings.getColumnsWithDefaultValues(fieldSettings);
        Map<String, String> columnsWithDefaultOptionsFilteredByElasticExportWorkflow =
                this.fieldSettings.getColumnsWithDefaultOptionsFilteredByElasticExportWorkflow(fieldSettings);
        ParticipantData participantData = new ParticipantData(participantDataDao);

        participantData.setData(participantId, Integer.parseInt(instance.getDdpInstanceId()),
                instanceName.toUpperCase() + ParticipantData.FIELD_TYPE_PARTICIPANTS, probandDataMap);
        participantData.addDefaultOptionsValueToData(columnsWithDefaultOptions);
        participantData.insertParticipantData(SystemUtil.SYSTEM);

        columnsWithDefaultOptionsFilteredByElasticExportWorkflow.forEach((col, val) -> ElasticSearchUtil.writeWorkflow(
                WorkflowForES.createInstanceWithStudySpecificData(instance, participantId, col, val,
                        new WorkflowForES.StudySpecificData(probandDataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                                probandDataMap.get(FamilyMemberConstants.FIRSTNAME),
                                probandDataMap.get(FamilyMemberConstants.LASTNAME))), false));

        log.info("Created proband data for participant with id: {}", participantId);
        return true;
    }

    protected static long getFamilyId(String participantId, Bookmark bookmark) {
        try {
            return bookmark.getThenIncrementBookmarkValue(RGP_FAMILY_ID);
        } catch (Exception e) {
            String msg = String.format("Could not set DSM default values for participant %s and DDP instance %s: "
                    + "RGP family ID not found in Bookmark table", participantId, RGP_FAMILY_ID);
            throw new DsmInternalError(msg, e);
        }
    }

    public static Map<String, String> buildDataMap(String participantId, long familyId, String instanceName,
                                                      List<Activities> activities, Profile esProfile) {
        Map<String, String> probandDataMap;

        try {
            probandDataMap = extractProbandDefaultData(esProfile, activities, familyId, instanceName);
        } catch (Exception e) {
            // if we can't make the participant data map, abort
            String msg = String.format("Error creating participant data map for participant %s and DDP instance %s: %s",
                    participantId, instanceName, e.getMessage());
            throw new DsmInternalError(msg, e);
        }

        try {
            String refSourceId = ReferralSourceService.deriveReferralSourceId(activities);
            probandDataMap.put(DBConstants.REFERRAL_SOURCE_ID, refSourceId);
        } catch (Exception e) {
            // not good: we could not convert referral source, but not fatal for this process.
            // Use error level so humans get alerted to intervene and possibly fix the issue
            log.error("Error deriving participant referral source for participant {} and DDP instance {}: {}",
                    participantId, instanceName, e.getMessage());
        }
        return probandDataMap;
    }

    private static Map<String, String> extractProbandDefaultData(Profile esProfile, List<Activities> participantActivities,
                                                                 long familyId, String instanceName) {
        String mobilePhone = getPhoneNumberFromActivities(participantActivities);
        log.info("Extracting data from participant {} ES profile...", esProfile.getGuid());
        String firstName = esProfile.getFirstName();
        String lastName = esProfile.getLastName();
        String collaboratorParticipantId =
                instanceName.toUpperCase() + "_" + familyId + "_" + FamilyMemberConstants.PROBAND_RELATIONSHIP_ID;
        String memberType = FamilyMemberConstants.MEMBER_TYPE_SELF;
        String email = esProfile.getEmail();
        FamilyMemberDetails probandMemberDetails =
                new FamilyMemberDetails(firstName, lastName, memberType, familyId, collaboratorParticipantId);
        probandMemberDetails.setMobilePhone(mobilePhone);
        probandMemberDetails.setEmail(email);
        probandMemberDetails.setApplicant(true);
        return probandMemberDetails.toMap();
    }

    private static String getPhoneNumberFromActivities(List<Activities> activities) {
        Optional<Activities> maybeEnrollmentActivity =
                activities.stream().filter(activity -> DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode()))
                        .findFirst();
        return (String) maybeEnrollmentActivity.map(enrollment -> {
            List<Map<String, Object>> questionsAnswers = enrollment.getQuestionsAnswers();
            Optional<Map<String, Object>> maybePhoneQuestionAnswer = questionsAnswers.stream()
                    .filter(q -> DDPActivityConstants.ENROLLMENT_ACTIVITY_PHONE.equals(q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID)))
                    .findFirst();
            return maybePhoneQuestionAnswer.map(answer -> answer.get(DDPActivityConstants.ACTIVITY_QUESTION_ANSWER)).orElse("");
        }).orElse("");
    }

    protected static void writeFamilyIdToElastic(@NonNull String esIndex, @NonNull String participantId, long familyId) {
        try {
            Map<String, Object> esObjectMap = ElasticSearchUtil.getObjectsMap(esIndex, participantId, ESObjectConstants.DSM);
            Map<String, Object> esDsmObjectMap = (Map<String, Object>) esObjectMap.get(ESObjectConstants.DSM);
            esDsmObjectMap.put(ESObjectConstants.FAMILY_ID, familyId);
            ElasticSearchUtil.updateRequest(participantId, esIndex, esObjectMap);
            log.info("Family id for participant {} successfully added to ES", participantId);
        } catch (Exception e) {
            throw new DsmInternalError("Could not insert family id for participant: " + participantId, e);
        }
    }
}
