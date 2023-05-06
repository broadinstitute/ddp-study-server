package org.broadinstitute.dsm.model.defaultvalues;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.model.participant.data.ParticipantData;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;


public class RgpAutomaticProbandDataCreator extends BasicDefaultDataMaker {

    public static final String RGP_FAMILY_ID = "rgp_family_id";
    public static final String REFERRAL_SOURCE_ID = "REF_SOURCE";

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

        List<FieldSettingsDto> fieldSettingsDtosByOptionAndInstanceId =
                FieldSettingsDao.of().getOptionAndRadioFieldSettingsByInstanceId(Integer.parseInt(instance.getDdpInstanceId()));

        return elasticSearchParticipantDto.getProfile().map(esProfile -> {
            logger.info("Got ES profile of participant: {}", esProfile.getGuid());
            String participantId = StringUtils.isNotBlank(esProfile.getLegacyAltPid()) ? esProfile.getLegacyAltPid() : esProfile.getGuid();
            String instanceName = instance.getName();

            // ensure we can get a family ID before writing things to the DB
            // this will increment the family value (which we want to ensure we are not messed up by concurrency)
            // but will leave an unused family ID if we abort later. As things stand now that is not a concern.
            long familyId = getFamilyId(participantId, bookmark);
            insertFamilyIdToDsmES(instance.getParticipantIndexES(), participantId, familyId);

            Map<String, String> probandDataMap = buildDataMap(participantId, familyId, instanceName,
                    elasticSearchParticipantDto.getActivities(), esProfile);

            Map<String, String> columnsWithDefaultOptions =
                    fieldSettings.getColumnsWithDefaultValues(fieldSettingsDtosByOptionAndInstanceId);
            Map<String, String> columnsWithDefaultOptionsFilteredByElasticExportWorkflow =
                    fieldSettings.getColumnsWithDefaultOptionsFilteredByElasticExportWorkflow(fieldSettingsDtosByOptionAndInstanceId);
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

            logger.info("Automatic proband data for participant with id: {} has been created", participantId);
            return true;
        }).orElseGet(() -> {
            logger.info("Participant does not have ES profile yet...");
            return false;
        });
    }

    protected static long getFamilyId(String participantId, Bookmark bookmark) {
        try {
            return bookmark.getThenIncrementBookmarkValue(RGP_FAMILY_ID);
        } catch (Exception e) {
            // internal error but nothing in the call stack will properly note the severity, so log the error
            String msg = String.format("Could not set DSM default values for participant %s and DDP instance %s: "
                    + "RGP family ID not found in Bookmark table", participantId, RGP_FAMILY_ID);
            logger.error(msg);
            throw new RuntimeException(msg, e);
        }
    }

    protected Map<String, String> buildDataMap(String participantId, long familyId, String instanceName,
                                               List<Activities> activities, Profile esProfile) {
        Map<String, String> probandDataMap;

        try {
            probandDataMap = extractProbandDefaultData(esProfile, activities, familyId, instanceName);
        } catch (Exception e) {
            // if we can't make the participant data map, abort
            // internal error but nothing in the call stack will properly note the severity, so log the error
            // Use error level so humans get alerted to intervene and possibly fix the issue
            String msg = String.format("Error creating participant data map for participant %s and DDP instance %s: %s",
                    participantId, instanceName, e.getMessage());
            logger.error(msg);
            throw new RuntimeException(msg, e);
        }

        try {
            String refSourceId = convertReferralSources(getReferralSources(activities));
            probandDataMap.put(REFERRAL_SOURCE_ID, refSourceId);
        } catch (Exception e) {
            // not good: we could not convert referral source, but not fatal for this process.
            // Use error level so humans get alerted to intervene and possibly fix the issue
            logger.error("Error deriving participant referral source for participant {} and DDP instance {}: {}",
                    participantId, instanceName, e.getMessage());
        }
        return probandDataMap;
    }

    private Map<String, String> extractProbandDefaultData(Profile esProfile, List<Activities> participantActivities,
                                                          long familyId, String instanceName) {
        String mobilePhone = getPhoneNumberFromActivities(participantActivities);
        logger.info("Starting extracting data from participant: " + esProfile.getGuid() + " ES profile");
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
        logger.info("Profile data extracted from participant: " + esProfile.getGuid() + " ES profile");
        return probandMemberDetails.toMap();
    }

    private String getPhoneNumberFromActivities(List<Activities> activities) {
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

    /**
     * Get ref sources from activities. Specifically get answers to FIND_OUT enrollment question.
     *
     * @return list of strings, empty if question or answers not found
     */
    protected List<String> getReferralSources(List<Activities> activities) {
        Optional<Activities> enrollmentActivity = activities.stream().filter(activity ->
                DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode())).findFirst();
        if (enrollmentActivity.isEmpty()) {
            logger.warn("Could not derive referral source data, participant has no enrollment activity");
            return new ArrayList<>();
        }
        List<Map<String, Object>> questionsAnswers = enrollmentActivity.get().getQuestionsAnswers();
        Optional<Map<String, Object>> refSourceQA = questionsAnswers.stream()
                .filter(q -> q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID).equals(
                        DDPActivityConstants.ENROLLMENT_FIND_OUT))
                .findFirst();
        if (refSourceQA.isEmpty()) {
            logger.info("Could not derive referral source data, participant has no referral source data");
            return new ArrayList<>();
        }
        Object answers = refSourceQA.get().get(DDPActivityConstants.ACTIVITY_QUESTION_ANSWER);
        return (List<String>) answers;
    }

    /**
     * Map DSS referral sources (answers to FIND_OUT enrollment question) to DSM ref sources (the stable IDs are
     * different)
     *
     * @param sources referral sources provided as DSS stable IDs
     * @return a referral source ID
     * @throws RuntimeException If the expected referral source values are not present or the referral source mapping is
     *                          missing or out of sync
     */
    protected String convertReferralSources(List<String> sources) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        Optional<FieldSettingsDto> refSource = fieldSettingsDao.getFieldSettingsByFieldTypeAndColumnName(
                "RGP_MEDICAL_RECORDS_GROUP", REFERRAL_SOURCE_ID);

        // for REF_SOURCE, the details column hold the mapping between DSS referral sources (answers to FIND_OUT
        // enrollment question) to DSM ref sources
        Optional<String> details = refSource.map(FieldSettingsDto::getDetails);
        if (details.isEmpty()) {
            throw new RuntimeException("FieldSettings 'details' is empty for RGP_MEDICAL_RECORDS_GROUP REF_SOURCE");
        }
        Optional<String> possibleValues = refSource.map(FieldSettingsDto::getPossibleValues);
        if (possibleValues.isEmpty()) {
            throw new RuntimeException("FieldSettings 'possibleValues' is empty for "
                    + "RGP_MEDICAL_RECORDS_GROUP REF_SOURCE");
        }

        return this.deriveReferralSource(sources, details.get(), possibleValues.get());
    }

    /**
     * Given the referral sources provided using DSS FOUND_OUT IDs, get the corresponding DSM referral source ID
     *
     * @param sources           referral sources provided as DSS stable IDs
     * @param refDetails        JSON string of FieldSettings REF_SOURCE details column
     * @param refPossibleValues JSON string of FieldSettings REF_SOURCE possibleValues column
     * @return a referral source ID
     * @throws RuntimeException If the expected REF_SOURCE values are not present or the referral source mapping is
     *                          out of sync, the method logs errors, but will throw if the provided referral sources cannot be mapped.
     */
    protected String deriveReferralSource(List<String> sources, String refDetails, String refPossibleValues) {
        // algorithm: if more than one answer chosen, then use REF_SOURCE MORE_THAN_ONE,
        // if no answer, or if the question is missing from enrollment, etc., then use REF_SOURCE NA

        // details column holds a map of FIND_OUT answers to REF_SOURCE IDs
        Map<String, String> refMap = ObjectMapperSingleton.readValue(
                refDetails, new TypeReference<>() {
                });

        // get the REF_SOURCE IDs to verify the map is still in sync
        List<Map<String, String>> refValues = ObjectMapperSingleton.readValue(
                refPossibleValues, new TypeReference<>() {
                });

        Set<String> refIDs = refValues.stream().map(m -> m.get("value")).collect(Collectors.toSet());
        Set<String> refMapValues = new HashSet<>(refMap.values());
        // "NA" and "MORE_THAN_ONE" are not mapped
        if (refMapValues.size() + 2 != refIDs.size() || !refIDs.containsAll(refMapValues)) {
            // if IDs have diverged, don't stop this operation: log error and see if something is salvageable (below)
            logger.error("RGP_MEDICAL_RECORDS_GROUP REF_SOURCE 'possibleValues' do not match REF_SOURCE map in 'details'");
        }

        if (sources.isEmpty()) {
            if (!refIDs.contains("NA")) {
                throw new RuntimeException("REF_SOURCE does not include a 'NA' key.");
            }
            return "NA";
        }

        if (sources.size() > 1) {
            if (!refIDs.contains("MORE_THAN_ONE")) {
                StringBuilder sb = new StringBuilder(sources.get(0));
                for (int i = 1; i <= sources.size(); i++) {
                    sb.append(", ").append(sources.get(i));
                }
                throw new RuntimeException("REF_SOURCE does not include a 'MORE_THAN_ONE' key. "
                        + "Participant provided referral sources: " + sb);
            }
            return "MORE_THAN_ONE";
        }

        String refSource = refMap.get(sources.get(0));
        if (refSource == null) {
            throw new RuntimeException("There is no corresponding REF_SOURCE for participant provided referral "
                    + "source: " + sources.get(0));
        }
        if (!refIDs.contains(refSource)) {
            throw new RuntimeException(String.format("Invalid REF_SOURCE ID for participant provided referral "
                    + "source %s: %s", sources.get(0), refSource));
        }
        return refSource;
    }

    void insertFamilyIdToDsmES(@NonNull String esIndex, @NonNull String participantId, long familyId) {
        try {
            Map<String, Object> esObjectMap = ElasticSearchUtil.getObjectsMap(esIndex, participantId, ESObjectConstants.DSM);
            Map<String, Object> esDsmObjectMap = (Map<String, Object>) esObjectMap.get(ESObjectConstants.DSM);
            esDsmObjectMap.put(ESObjectConstants.FAMILY_ID, familyId);
            ElasticSearchUtil.updateRequest(participantId, esIndex, esObjectMap);
            logger.info("Family id for participant {} successfully added to ES", participantId);
        } catch (Exception e) {
            logger.error("Could not insert family id for participant: " + participantId, e);
        }
    }

}
