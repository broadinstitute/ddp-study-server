package org.broadinstitute.dsm.model.at;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchKitRequest {
    public static final String SQL_SELECT_KIT_REQUEST =
            "SELECT participant_data_id, ddp_participant_id, data, JSON_EXTRACT(data,'$.GENOME_STUDY_KIT_TRACKING_NUMBER'), "
                    + "JSON_EXTRACT(data,'$.GENOME_STUDY_SPIT_KIT_BARCODE'), JSON_EXTRACT(data,'$.GENOME_STUDY_CPT_ID'), "
                    + "JSON_EXTRACT(data,'$.GENOME_STUDY_DATE_RECEIVED') "
                    + "FROM ddp_participant_data where ";
    private static final Logger logger = LoggerFactory.getLogger(SearchKitRequest.class);
    private static final String SHORT_ID = "SHORT_ID";
    private static final String SEARCH_TRACKING_NUMBER = "TRACKING_NUMBER";
    protected static final String SEARCH_MF_BAR = "MF_BAR";

    private static final String TRACKING_ID = "JSON_EXTRACT(data,'$.GENOME_STUDY_KIT_TRACKING_NUMBER')";
    private static final String MF_BARCODE = "JSON_EXTRACT(data,'$.GENOME_STUDY_SPIT_KIT_BARCODE')";
    private static final String COLLABORATOR_PARTICIPANT_ID = "JSON_EXTRACT(data,'$.GENOME_STUDY_CPT_ID')";
    private static final String RECEIVED_DATE = "JSON_EXTRACT(data,'$.GENOME_STUDY_DATE_RECEIVED')";

    public static ParticipantData findATKitRequest(String mfBarcode) {
        return inTransaction(conn -> {
            ParticipantData participantData = null;
            try (PreparedStatement stmt = conn.prepareStatement(
                    SQL_SELECT_KIT_REQUEST.concat(MF_BARCODE + " like \"%" + mfBarcode + "%\""))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        logger.info("Found AT kit with mfBarcode {}",  mfBarcode);
                        participantData = new ParticipantData.Builder()
                                .withParticipantDataId(rs.getInt(DBConstants.PARTICIPANT_DATA_ID))
                                .withData(rs.getString(DBConstants.DATA)).build();

                    }
                }
            } catch (SQLException ex) {
                throw new DsmInternalError("Error searching for AT kit with mfBarcode " + mfBarcode, ex);
            }
            return participantData;
        });
    }

    public static List<KitRequestShipping> findATKitRequest(String field, String value, DDPInstance ddpInstance) {
        Map<String, KitRequestShipping> kitRequests = new HashMap<>();
        inTransaction(conn -> {
            String search = "";
            if (SEARCH_TRACKING_NUMBER.equals(field)) {
                search = TRACKING_ID + " like \"%" + value + "%\"";
            } else if (SEARCH_MF_BAR.equals(field)) {
                search = MF_BARCODE + " like \"%" + value + "%\"";
            } else if (SHORT_ID.equals(field)) {
                // TODO THis is wrong (SHORT_ID != COLLABORATOR_PARTICIPANT_ID) but there is general concern about
                // fixing since users may have become used to this behavior. Seriously. -DC
                search = COLLABORATOR_PARTICIPANT_ID + " like \"%" + value + "%\"";
            } else {
                throw new DSMBadRequestException("Invalid search field: " + field);
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST.concat(search))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        String bspParticipantId = rs.getString(COLLABORATOR_PARTICIPANT_ID);
                        String trackingId = rs.getString(TRACKING_ID);
                        String mfBarcode = rs.getString(MF_BARCODE);
                        String receivedDate = rs.getString(RECEIVED_DATE);
                        bspParticipantId = removeApostrophe(bspParticipantId);
                        trackingId = removeApostrophe(trackingId);
                        mfBarcode = removeApostrophe(mfBarcode);
                        receivedDate = removeApostrophe(receivedDate);
                        kitRequests.put(ddpParticipantId,
                                new KitRequestShipping(ddpParticipantId, bspParticipantId, mfBarcode, "AT",
                                        trackingId, receivedDate, "hruid", "gender"));
                    }
                }
            } catch (SQLException ex) {
                throw new DsmInternalError(
                        String.format("Error searching for AT kit, field=%s, value=%s", field, value), ex);
            }
            return null;
        });
        logger.info("Found {} AT kits", kitRequests.values().size());

        if (!kitRequests.isEmpty()) {
            populateFromElastic(kitRequests, ddpInstance);
        }
        return new ArrayList<>(kitRequests.values());
    }

    private static String removeApostrophe(String input) {
        if (StringUtils.isNotBlank(input)) {
            return input.replace("\"", "");
        }
        return input;
    }

    private static void populateFromElastic(Map<String, KitRequestShipping> ptpToKitRequest, DDPInstance ddpInstance) {
        ptpToKitRequest.forEach((ddpParticipantId, kitRequestShipping) -> {
            Map<String, Map<String, Object>> participantESData =
                    ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance, ElasticSearchUtil.BY_GUID + ddpParticipantId);
            if (participantESData == null || participantESData.isEmpty()) {
                participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                        ElasticSearchUtil.BY_LEGACY_ALTPID + ddpParticipantId);
            }
            if (participantESData == null || participantESData.isEmpty()) {
                // TODO: not sure if this is an error case, but for now handling as it was in the prior code -DC
                return;
            }
            Map<String, Object> esParticipant = participantESData.get(ddpParticipantId);
            Map<String, Object> profile = (Map<String, Object>) esParticipant.get("profile");
            if (profile != null && profile.containsKey("hruid")) {
                kitRequestShipping.setHruid((String) profile.get("hruid"));
            }

            String gender = getGenderFromActivities((List<Map<String, Object>>) esParticipant.get("activities"));
            if (gender != null) {
                kitRequestShipping.setGender(gender);
            }
        });
    }

    private static String getGenderFromActivities(List<Map<String, Object>> activities) {
        if (activities == null || activities.isEmpty()) {
            return null;
        }
        Map<String, Object> genomeStudyActivity = activities.stream()
                .filter(activity -> "GENOME_STUDY".equals(activity.get("activityCode")))
                .findFirst()
                .orElse(null);
        if (genomeStudyActivity == null || !genomeStudyActivity.containsKey("questionsAnswers")) {
            return null;
        }

        List<Map<String, Object>> questionsAnswers =
                (List<Map<String, Object>>) genomeStudyActivity.get("questionsAnswers");
        for (Map<String, Object> questionsAnswer : questionsAnswers) {
            if ("PARTICIPANT_GENDER".equals(questionsAnswer.get("stableId"))) {
                List<String> answer = (List<String>) questionsAnswer.get("answer");
                if (answer != null && answer.size() == 1) {
                    return answer.get(0);
                }
            }
        }
        return null;
    }
}
