package org.broadinstitute.dsm.service.adminoperation;

import static org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate.isProband;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.service.admin.AdminOperation;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.JsonParseException;
import org.broadinstitute.dsm.util.proxy.jackson.JsonProcessingException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * Provides support for deriving participant referral source from DSS activities and updating ParticipantData as needed.
 * Uses the asynchronous AdminOperation interface.
 */
@Slf4j
public class ReferralSourceService implements AdminOperation {

    private static final String RGP_REALM = "RGP";
    protected static final String NA_REF_SOURCE = "NA";
    private String userId;
    private String esIndex;
    private Map<String, List<ParticipantData>> participantDataByPtpId;


    /**
     * Validate input and retrieve participant data, during synchronous part of operation handling
     *
     * @param userId ID of user performing operation
     * @param realm currently only supports RGP realm
     * @param attributes unused
     * @param payload request body, if any, as ReferralSourceRequest
     */
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        this.userId = userId;

        // this class currently only supports RGP realm, since it is the ony realm that supports this feature
        // recode the RGP constants in this class if that ever changes
        if (!realm.equalsIgnoreCase(RGP_REALM)) {
            throw new DsmInternalError("Invalid realm for ReferralSourceService: " + realm);
        }
        DDPInstance instance = DDPInstance.getDDPInstance(realm);
        if (instance == null) {
            throw new DsmInternalError("Invalid realm: " + realm);
        }
        esIndex = instance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + realm);
        }

        ParticipantDataDao dataDao = new ParticipantDataDao();
        participantDataByPtpId = new HashMap<>();

        // handle optional list of ptps
        if (!StringUtils.isBlank(payload)) {
            List<String> participants = getParticipantList(payload);
            for (String participantId: participants) {
                List<ParticipantData> ptpData = dataDao.getParticipantData(participantId);
                if (ptpData.isEmpty()) {
                    throw new DSMBadRequestException("Invalid participant ID: " + participantId);
                }
                participantDataByPtpId.put(participantId, ptpData);
            }
        } else {
            // get study participants and their data
            // Implementation note: we can either gather all the ptp data now (fewer queries, larger memory footprint),
            // or just gather the ptp IDs here and query for ptp data separately (more queries, smaller memory footprint)
            // At the time of implementation ptps per study were reasonably small (~100) and ptp data size was not large
            List<ParticipantData> dataList =
                    dataDao.getParticipantDataByInstanceId(instance.getDdpInstanceIdAsInt());
            participantDataByPtpId = dataList.stream().collect(Collectors.groupingBy(ParticipantData::getRequiredDdpParticipantId));
        }
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        // for each participant attempt an update and log results
        for (var entry: participantDataByPtpId.entrySet()) {
            String ddpParticipantId = entry.getKey();
            try {
                UpdateLog.UpdateStatus status = updateReferralSource(ddpParticipantId, entry.getValue(),
                        getParticipantActivities(ddpParticipantId, esIndex));
                updateLog.add(new UpdateLog(ddpParticipantId, status.name()));
            } catch (Exception e) {
                updateLog.add(new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR.name(), e.toString()));

                String msg = String.format("Exception in ReferralSourceService.run for participant %s: %s", ddpParticipantId, e);
                // many of these exceptions will require investigation, but conservatively we will just log
                // at error level for those that are definitely concerning
                if (e instanceof DsmInternalError) {
                    log.error(msg);
                    e.printStackTrace();
                } else {
                    log.warn(msg);
                }
            }
        }

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    private static List<String> getParticipantList(String payload) {
        ParticipantListRequest req = ParticipantListRequest.fromJson(payload);
        List<String> participants = req.getParticipants();
        if (participants.isEmpty()) {
            throw new DSMBadRequestException("Invalid request format. Empty participant list");
        }
        return participants;
    }

    protected UpdateLog.UpdateStatus updateReferralSource(String ddpParticipantId, List<ParticipantData> dataList,
                                                          List<Activities> activities) {
        if (dataList.isEmpty()) {
            return UpdateLog.UpdateStatus.NO_PARTICIPANT_DATA;
        }
        if (activities.isEmpty()) {
            return UpdateLog.UpdateStatus.NO_ACTIVITIES;
        }
        List<String> refSources = getReferralSources(activities);
        if (refSources.isEmpty()) {
            return UpdateLog.UpdateStatus.NO_REFERRAL_SOURCE_IN_ACTIVITY;
        }

        String refSourceId = convertReferralSources(refSources);
        if (refSourceId.equals(NA_REF_SOURCE)) {
            return UpdateLog.UpdateStatus.NA_REFERRAL_SOURCE;
        }

        // only need the RGP_PARTICIPANT_DATA type ParticipantData
        List<ParticipantData> rgpData = dataList.stream().filter(participantDataDto ->
                participantDataDto.getRequiredFieldTypeId().equals(RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE)
        ).collect(Collectors.toList());

        if (rgpData.isEmpty()) {
            return UpdateLog.UpdateStatus.NO_PARTICIPANT_DATA;
        }

        // There may be multiple participant data records due to prior update errors
        // update all that apply to this case
        int updateCount = 0;
        for (var participantData: rgpData) {
            if (updateParticipantData(participantData, ddpParticipantId, refSourceId)) {
                updateCount++;
            }
        }

        if (updateCount > 1) {
            log.warn(String.format("Multiple records found for field type %s, participant %s in realm %s",
                    RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE, ddpParticipantId, RGP_REALM));
        }
        return updateCount > 0 ? UpdateLog.UpdateStatus.UPDATED : UpdateLog.UpdateStatus.NOT_UPDATED;
    }

    private boolean updateParticipantData(ParticipantData participantData, String ddpParticipantId, String refSourceId) {
        String msg = String.format("for field type %s, participant %s, participantDataId %s in realm %s",
                RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE, ddpParticipantId,
                participantData.getParticipantDataId(), RGP_REALM);

        try {
            Map<String, String> props = getDataMap(participantData);
            if (props.isEmpty()) {
                throw new DsmInternalError("Participant data empty " + msg);
            }

            // do not overwrite an existing referral source, unless marked as "NA" which is a placeholder
            if (!isProband(props) || (props.containsKey(DBConstants.REFERRAL_SOURCE_ID)
                    && !props.get(DBConstants.REFERRAL_SOURCE_ID).equals(NA_REF_SOURCE))) {
                return false;
            }
            log.info("Updating REFERRAL_SOURCE data {}", msg);
            props.put(DBConstants.REFERRAL_SOURCE_ID, refSourceId);
            ParticipantDataDao dataDao = new ParticipantDataDao();
            dataDao.updateParticipantDataColumn(
                    new ParticipantData.Builder()
                            .withParticipantDataId(participantData.getParticipantDataId())
                            .withDdpParticipantId(ddpParticipantId)
                            .withDdpInstanceId(participantData.getDdpInstanceId())
                            .withFieldTypeId(RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE)
                            .withData(ObjectMapperSingleton.writeValueAsString(props))
                            .withLastChanged(System.currentTimeMillis())
                            .withChangedBy(userId).build());
        } catch (JsonProcessingException | ClassCastException e) {
            throw new DsmInternalError("Invalid data format " + msg, e);
        }
        return true;
    }

    protected static Map<String, String> getDataMap(ParticipantData participantData) throws JsonParseException {
        Optional<String> data = participantData.getData();
        if (data.isEmpty() || StringUtils.isEmpty(data.get())) {
            return Collections.emptyMap();
        }

        return ObjectMapperSingleton.readValue(data.get(), new TypeReference<Map<String, String>>() {});
    }

    private List<Activities> getParticipantActivities(String ddpParticipantId, String esIndex) {
        return ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId).getActivities();
    }

    /**
     * Derive REFERRAL_SOURCE ID from participant activities
     *
     * @param activities list of DSS participant activities
     * @return a referral source ID
     * @throws DsmInternalError If the expected referral source values are not present or the referral source mapping is
     *                          missing or out of sync
     */
    public static String deriveReferralSourceId(List<Activities> activities) {
        return ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
    }

    /**
     * Get ref sources from activities. Specifically get answers to FIND_OUT enrollment question.
     *
     * @return list of strings, empty if question or answers not found
     */
    protected static List<String> getReferralSources(List<Activities> activities) {
        Optional<Activities> enrollmentActivity = activities.stream().filter(activity ->
                DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode())).findFirst();
        if (enrollmentActivity.isEmpty()) {
            log.warn("Could not derive referral source data, participant has no enrollment activity");
            return new ArrayList<>();
        }
        List<Map<String, Object>> questionsAnswers = enrollmentActivity.get().getQuestionsAnswers();
        Optional<Map<String, Object>> refSourceQA = questionsAnswers.stream()
                .filter(q -> q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID).equals(
                        DDPActivityConstants.ENROLLMENT_FIND_OUT))
                .findFirst();
        if (refSourceQA.isEmpty()) {
            log.info("Could not derive referral source data, participant has no referral source data");
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
     * @throws DsmInternalError If the expected referral source values are not present or the referral source mapping is
     *                          missing or out of sync
     */
    protected static String convertReferralSources(List<String> sources) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        Optional<FieldSettingsDto> refSource = fieldSettingsDao.getFieldSettingsByFieldTypeAndColumnName(
                "RGP_MEDICAL_RECORDS_GROUP", DBConstants.REFERRAL_SOURCE_ID);

        // for REF_SOURCE, the details column hold the mapping between DSS referral sources (answers to FIND_OUT
        // enrollment question) to DSM ref sources
        Optional<String> details = refSource.map(FieldSettingsDto::getDetails);
        if (details.isEmpty()) {
            throw new DsmInternalError("FieldSettings 'details' is empty for RGP_MEDICAL_RECORDS_GROUP REF_SOURCE");
        }
        Optional<String> possibleValues = refSource.map(FieldSettingsDto::getPossibleValues);
        if (possibleValues.isEmpty()) {
            throw new DsmInternalError("FieldSettings 'possibleValues' is empty for "
                    + "RGP_MEDICAL_RECORDS_GROUP REF_SOURCE");
        }

        return deriveReferralSource(sources, details.get(), possibleValues.get());
    }

    /**
     * Given the referral sources provided using DSS FOUND_OUT IDs, get the corresponding DSM referral source ID
     *
     * @param sources           referral sources provided as DSS stable IDs
     * @param refDetails        JSON string of FieldSettings REF_SOURCE details column
     * @param refPossibleValues JSON string of FieldSettings REF_SOURCE possibleValues column
     * @return a referral source ID
     * @throws DsmInternalError If the expected REF_SOURCE values are not present or the referral source mapping is
     *                          out of sync, the method logs errors, but will throw if the provided referral sources cannot be mapped.
     */
    protected static String deriveReferralSource(List<String> sources, String refDetails, String refPossibleValues) {
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
            log.error("RGP_MEDICAL_RECORDS_GROUP REF_SOURCE 'possibleValues' do not match REF_SOURCE map in 'details'");
        }

        if (sources.isEmpty()) {
            if (!refIDs.contains("NA")) {
                throw new DsmInternalError("REF_SOURCE does not include a 'NA' key.");
            }
            return "NA";
        }

        if (sources.size() > 1) {
            if (!refIDs.contains("MORE_THAN_ONE")) {
                StringBuilder sb = new StringBuilder(sources.get(0));
                for (int i = 1; i <= sources.size(); i++) {
                    sb.append(", ").append(sources.get(i));
                }
                throw new DsmInternalError("REF_SOURCE does not include a 'MORE_THAN_ONE' key. "
                        + "Participant provided referral sources: " + sb);
            }
            return "MORE_THAN_ONE";
        }

        String refSource = refMap.get(sources.get(0));
        if (refSource == null) {
            throw new DsmInternalError("There is no corresponding REF_SOURCE for participant provided referral "
                    + "source: " + sources.get(0));
        }
        if (!refIDs.contains(refSource)) {
            throw new DsmInternalError(String.format("Invalid REF_SOURCE ID for participant provided referral "
                    + "source %s: %s", sources.get(0), refSource));
        }
        return refSource;
    }
}
