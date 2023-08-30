package org.broadinstitute.dsm.model.defaultvalues;

import static org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate.isProband;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Slf4j
public class RgpReferralSource {

    public enum UpdateStatus {
        UPDATED,
        ERROR,
        NO_ACTIVITIES,
        NO_REFERRAL_SOURCE_IN_ACTIVITY,
        NO_PARTICIPANT_DATA
    }

    private static final String RGP_REALM = "RGP";
    private static final String RGP_PARTICIPANT_DATA = "RGP_PARTICIPANTS";
    private static final Gson gson = new Gson();

    private final String userId;

    public RgpReferralSource(String userId) {
        this.userId = userId;
    }

    public UpdateStatus updateReferralSource(String ddpParticipantId) {
        List<Activities> activities = getParticipantActivities(ddpParticipantId);
        if (activities.isEmpty()) {
            return UpdateStatus.NO_ACTIVITIES;
        }
        List<String> refSources = getReferralSources(activities);
        if (refSources.isEmpty()) {
            return UpdateStatus.NO_REFERRAL_SOURCE_IN_ACTIVITY;
        }
        return xxx(ddpParticipantId, convertReferralSources(refSources));
    }

    protected UpdateStatus xxx(String ddpParticipantId, String refSourceId) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData> dataList =
                dataDao.getParticipantDataByParticipantId(ddpParticipantId);
        if (dataList.isEmpty()) {
            return UpdateStatus.NO_PARTICIPANT_DATA;
        }

        List<org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData> rgpData = dataList.stream().filter(
                participantDataDto -> {
                    if (participantDataDto.getFieldTypeId().isPresent()) {
                        String ft = participantDataDto.getFieldTypeId().get();
                        return ft.equals(RGP_PARTICIPANT_DATA);
                    }
                    return false;
                }).collect(Collectors.toList());

        if (rgpData.isEmpty()) {
            return UpdateStatus.NO_PARTICIPANT_DATA;
        }

        for (var participantData: rgpData) {
            String msg = String.format("for field type %s, participant %s, participantDataId %s in realm %s",
                    RGP_PARTICIPANT_DATA, ddpParticipantId, participantData.getParticipantDataId(), RGP_REALM);
            log.info("Updating REFERRAL_SOURCE data {}", msg);

            Optional<String> data = participantData.getData();
            if (data.isEmpty() || StringUtils.isEmpty(data.get())) {
                throw new DsmInternalError("Participant data empty " + msg);
            }

            try {
                Map<String, String> props = gson.fromJson(data.get(), Map.class);

                if (isProband(props)) {
                    props.put(DBConstants.REFERRAL_SOURCE_ID, refSourceId);
                    dataDao.updateParticipantDataColumn(
                            new org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData.Builder()
                                    .withParticipantDataId(participantData.getParticipantDataId())
                                    .withDdpParticipantId(ddpParticipantId)
                                    .withDdpInstanceId(participantData.getDdpInstanceId())
                                    .withFieldTypeId(RGP_PARTICIPANT_DATA)
                                    .withData(gson.toJson(props))
                                    .withLastChanged(System.currentTimeMillis())
                                    .withChangedBy(userId).build());
                }
            } catch (JsonSyntaxException | JsonIOException | ClassCastException e) {
                throw new DsmInternalError("Invalid data format " + msg, e);
            }
        }
        return UpdateStatus.UPDATED;
    }

    private List<Activities> getParticipantActivities(String ddpParticipantId) {
        DDPInstance instance = DDPInstance.getDDPInstance(RGP_REALM);
        if (instance == null) {
            throw new DsmInternalError("Invalid realm: " + RGP_REALM);
        }
        String esIndex = instance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + RGP_REALM);
        }

        Optional<ElasticSearchParticipantDto> esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        if (esParticipant.isEmpty()) {
            throw new ESMissingParticipantDataException("Participant ES data is null for participant " + ddpParticipantId);
        }
        return esParticipant.get().getActivities();
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
        return RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
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
