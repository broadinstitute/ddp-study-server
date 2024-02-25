package org.broadinstitute.dsm.service.participantdata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;
import org.broadinstitute.dsm.service.adminoperation.ReferralSourceService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;

@Slf4j
public class RgpParticipantDataService {
    public static final String RGP_PARTICIPANTS_FIELD_TYPE = "RGP_PARTICIPANTS";
    protected static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static final Gson gson = new Gson();

    /**
     * Create default data for an RGP participant. Expects an ES profile to exist for the participant.
     *
     * @throws ESMissingParticipantDataException if the participant does not have an ES profile. Can be used by
     *                                         callers to retry after waiting for the ES profile to be created.
     */
    public static void createDefaultData(String ddpParticipantId, ElasticSearchParticipantDto esParticipantDto,
                                         DDPInstance instance, FamilyIdProvider familyIdProvider) {
        // expecting ptp has a profile
        if (esParticipantDto.getProfile().isEmpty()) {
            throw new ESMissingParticipantDataException("Participant does not yet have profile in ES");
        }
        Profile esProfile = esParticipantDto.getProfile().get();
        log.info("Got ES profile of participant: {}", esProfile.getGuid());
        int ddpInstanceId = Integer.parseInt(instance.getDdpInstanceId());

        List<ParticipantData> ptpData = getRgpParticipantData(ddpParticipantId);
        if (!ptpData.isEmpty()) {
            throw new DsmInternalError(String.format("Existing %s data found for participant %s",
                    RGP_PARTICIPANTS_FIELD_TYPE, ddpParticipantId));
        }

        // ensure we can get a family ID before writing things to the DB (to avoid concurrency issues)
        // This will increment the family ID value but will leave an unused family ID if we abort later.
        // As things stand now that is not a concern.
        long familyId = familyIdProvider.createFamilyId(ddpParticipantId);
        insertEsFamilyId(instance.getParticipantIndexES(), ddpParticipantId, familyId);
        Map<String, String> dataMap = buildParticipantData(esProfile, familyId);

        List<FieldSettingsDto> optionFieldSettings =
                FieldSettingsDao.of().getOptionAndRadioFieldSettingsByInstanceId(ddpInstanceId);
        FieldSettings fieldSettings = new FieldSettings();
        Map<String, String> columnsWithDefaultOptions = fieldSettings.getColumnsWithDefaultValues(optionFieldSettings);
        log.info("Found {} field settings columns with default options for RGP", columnsWithDefaultOptions.size());

        // overwrite any defaults that have extracted values
        columnsWithDefaultOptions.putAll(dataMap);

        // combine synthesized ptp data with defaults
        ParticipantData participantDataDto =
                new ParticipantData.Builder()
                        .withDdpParticipantId(ddpParticipantId)
                        .withDdpInstanceId(ddpInstanceId)
                        .withFieldTypeId(RGP_PARTICIPANTS_FIELD_TYPE)
                        .withData(gson.toJson(columnsWithDefaultOptions))
                        .withLastChanged(System.currentTimeMillis())
                        .withChangedBy(SystemUtil.SYSTEM).build();

        participantDataDao.create(participantDataDto);
        WorkflowStatusUpdate.updateEsParticipantData(ddpParticipantId, instance);

        // initialize workflows
        Map<String, String> columnsWithWorkflow =
                fieldSettings.getColumnsWithDefaultOptionsFilteredByElasticExportWorkflow(optionFieldSettings);
        log.info("Found {} field settings columns with workflows for RGP: {}", columnsWithWorkflow.size(),
                columnsWithWorkflow.keySet());

        WorkflowForES.StudySpecificData workflowData =
                new WorkflowForES.StudySpecificData(
                        dataMap.get(FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID),
                        dataMap.get(FamilyMemberConstants.FIRSTNAME),
                        dataMap.get(FamilyMemberConstants.LASTNAME));
        columnsWithWorkflow.forEach((col, val) -> ElasticSearchUtil.writeWorkflow(
                WorkflowForES.createInstanceWithStudySpecificData(instance, ddpParticipantId, col, val, workflowData),
                false));

        log.info("Created RGP proband data for participant {}", ddpParticipantId);
    }

    public static Map<String, String> buildParticipantData(Profile esProfile, long familyId) {
        log.info("Extracting data from participant {} ES profile...", esProfile.getGuid());
        // first and last name will typically be null at this point, but that's fine for now
        String firstName = esProfile.getFirstName();
        String lastName = esProfile.getLastName();
        String collaboratorParticipantId = createCollaboratorParticipantId(familyId);
        String memberType = FamilyMemberConstants.MEMBER_TYPE_SELF;
        FamilyMemberDetails probandMemberDetails =
                new FamilyMemberDetails(firstName, lastName, memberType, familyId, collaboratorParticipantId);
        probandMemberDetails.setEmail(esProfile.getEmail());
        probandMemberDetails.setApplicant(true);
        return probandMemberDetails.toMap();
    }

    protected static String createCollaboratorParticipantId(long familyId) {
        return String.format("RGP_%d_%d", familyId, FamilyMemberConstants.PROBAND_RELATIONSHIP_ID);
    }

    public static void insertEsFamilyId(String esIndex, String ddpParticipantId, long familyId) {
        try {
            Map<String, Object> esMap =
                    ElasticSearchUtil.getObjectsMap(esIndex, ddpParticipantId, ESObjectConstants.DSM);
            Map<String, Object> dsmMap = (Map<String, Object>) esMap.get(ESObjectConstants.DSM);
            if (dsmMap == null) {
                dsmMap = new HashMap<>();
                esMap.put(ESObjectConstants.DSM, dsmMap);
            }
            dsmMap.put(ESObjectConstants.FAMILY_ID, familyId);
            ElasticSearchUtil.updateRequest(ddpParticipantId, esIndex, esMap);
            log.info("Family id for participant {} successfully added to ES", ddpParticipantId);
        } catch (Exception e) {
            throw new DsmInternalError("Could not insert family id for participant: " + ddpParticipantId, e);
        }
    }

    public static void updateWithExtractedData(String ddpParticipantId, String studyGuid) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceByGuid(studyGuid);
        if (ddpInstance == null) {
            throw new DSMBadRequestException("Invalid study GUID: " + studyGuid);
        }

        String esIndex = ddpInstance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + studyGuid);
        }

        ElasticSearchParticipantDto esParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);

        // expecting ptp has a profile and has completed the enrollment activity
        if (esParticipantDto.getProfile().isEmpty() || esParticipantDto.getActivities().isEmpty()) {
            throw new ESMissingParticipantDataException(
                    String.format("Participant %s does not yet have profile and activities in ES", ddpParticipantId));
        }
        Profile esProfile = esParticipantDto.getProfile().orElseThrow();

        // get existing ptp data
        List<ParticipantData> ptpData = getRgpParticipantData(ddpParticipantId);
        if (ptpData.isEmpty()) {
            throw new DsmInternalError(String.format("No %s data found for participant %s", RGP_PARTICIPANTS_FIELD_TYPE,
                    ddpParticipantId));
        }

        // TODO should all family members be updated with the same data or just proband? -DC
        ptpData.forEach(participantData -> {
            Map<String, String> dataMap = new HashMap<>(participantData.getDataMap());

            // update with new data from ES profile and activities
            updateDataMap(ddpParticipantId, dataMap, esParticipantDto.getActivities(), esProfile);
            updateParticipantData(ddpParticipantId, participantData, dataMap, ddpInstance);
        });
    }

    public static List<ParticipantData> getRgpParticipantData(String ddpParticipantId) {
        List<ParticipantData> participantDataList = participantDataDao.getParticipantData(ddpParticipantId);

        return participantDataList.stream().filter(participantDataDto ->
                RGP_PARTICIPANTS_FIELD_TYPE.equals(participantDataDto.getRequiredFieldTypeId()))
                .collect(Collectors.toList());
    }

    protected static void updateParticipantData(String ddpParticipantId, ParticipantData participantData,
                                                Map<String, String> dataMap, DDPInstance instance) {
        participantDataDao.updateParticipantDataColumn(
                new ParticipantData.Builder()
                        .withParticipantDataId(participantData.getParticipantDataId())
                        .withDdpParticipantId(ddpParticipantId)
                        .withDdpInstanceId(participantData.getDdpInstanceId())
                        .withFieldTypeId(participantData.getRequiredFieldTypeId())
                        .withData(gson.toJson(dataMap))
                        .withLastChanged(System.currentTimeMillis())
                        .withChangedBy(SystemUtil.SYSTEM).build());
        WorkflowStatusUpdate.updateEsParticipantData(ddpParticipantId, instance);
    }

    public static void updateDataMap(String ddpParticipantId, Map<String, String> dataMap,
                                     List<Activities> activities, Profile esProfile) {
        try {
            // update these values unconditionally
            dataMap.put(FamilyMemberConstants.PHONE, getPhoneFromActivities(activities));
            dataMap.put(FamilyMemberConstants.FIRSTNAME, esProfile.getFirstName());
            dataMap.put(FamilyMemberConstants.LASTNAME, esProfile.getLastName());
        } catch (Exception e) {
            // if we can't make the participant data map, abort
            String msg = String.format("Error creating participant data map for participant %s: %s",
                    ddpParticipantId, e.getMessage());
            throw new DsmInternalError(msg, e);
        }

        try {
            String refSourceId = ReferralSourceService.deriveReferralSourceId(activities);
            dataMap.put(DBConstants.REFERRAL_SOURCE_ID, refSourceId);
        } catch (Exception e) {
            // not good: we could not convert referral source, but not fatal for this process.
            // Use error level so humans get alerted to intervene and possibly fix the issue
            log.error("Error deriving participant referral source for participant {}: {}",
                    ddpParticipantId, e.getMessage());
        }
    }

    private static String getPhoneFromActivities(List<Activities> activities) {
        Optional<Activities> enrollmentActivity = activities.stream()
                .filter(activity -> DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode()))
                .findFirst();
        return (String) enrollmentActivity.map(enrollment -> {
            List<Map<String, Object>> questionsAnswers = enrollment.getQuestionsAnswers();
            Optional<Map<String, Object>> phoneAnswer = questionsAnswers.stream()
                    .filter(q -> DDPActivityConstants.ENROLLMENT_ACTIVITY_PHONE.equals(q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID)))
                    .findFirst();
            return phoneAnswer.map(answer -> answer.get(DDPActivityConstants.ACTIVITY_QUESTION_ANSWER)).orElse("");
        }).orElse("");
    }
}
