package org.broadinstitute.dsm.model.at;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class SearchKitRequest {

    private static final Logger logger = LoggerFactory.getLogger(SearchKitRequest.class);

    public static final String SQL_SELECT_KIT_REQUEST = "SELECT participant_data_id, ddp_participant_id, data, JSON_EXTRACT(data,'$.GENOME_STUDY_KIT_TRACKING_NUMBER'), " +
            "JSON_EXTRACT(data,'$.GENOME_STUDY_SPIT_KIT_BARCODE'), JSON_EXTRACT(data,'$.GENOME_STUDY_CPT_ID'), JSON_EXTRACT(data,'$.GENOME_STUDY_DATE_RECEIVED') " +
            "FROM ddp_participant_data where ";

    private static final String SHORT_ID = "SHORT_ID";
    private static final String SEARCH_TRACKING_NUMBER = "TRACKING_NUMBER";
    private static final String SEARCH_MF_BAR = "MF_BAR";

    private static final String TRACKING_ID = "JSON_EXTRACT(data,'$.GENOME_STUDY_KIT_TRACKING_NUMBER')";
    private static final String MF_BARCODE = "JSON_EXTRACT(data,'$.GENOME_STUDY_SPIT_KIT_BARCODE')";
    private static final String COLLABORATOR_PARTICIPANT_ID = "JSON_EXTRACT(data,'$.GENOME_STUDY_CPT_ID')";
    private static final String RECEIVED_DATE = "JSON_EXTRACT(data,'$.GENOME_STUDY_DATE_RECEIVED')";

    public static ParticipantData findATKitRequest(@NonNull String mfBarcode) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST.concat(MF_BARCODE + " like \"%" + mfBarcode + "%\""))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new ParticipantData(rs.getString(DBConstants.PARTICIPANT_DATA_ID), null, rs.getString(DBConstants.DATA));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error searching for AT kit w/ mfBarcode " + mfBarcode, results.resultException);
        }
        logger.info("Found " + mfBarcode + " AT kit");
        return (ParticipantData) results.resultValue;
    }

    public static List<KitRequestShipping> findATKitRequest(@NonNull String field, @NonNull String value) {
        HashMap<String, KitRequestShipping> kitRequests = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String search = "";
            if (SEARCH_TRACKING_NUMBER.equals(field)) {
                search = TRACKING_ID + " like \"%" + value + "%\"";
            }
            else if (SEARCH_MF_BAR.equals(field)) {
                search = MF_BARCODE + " like \"%" + value + "%\"";
            }
            else if (SHORT_ID.equals(field)) {
                search = COLLABORATOR_PARTICIPANT_ID + " like \"%" + value + "%\"";
            }
            else {
                throw new RuntimeException("Search field not known: " + field);
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
                        kitRequests.put(ddpParticipantId, new KitRequestShipping(ddpParticipantId, bspParticipantId, mfBarcode, "AT", trackingId, receivedDate, "hruid", "gender"));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error searching for AT kit w/ field " + field + " and value " + value, results.resultException);
        }
        logger.info("Found " + kitRequests.values().size() + " AT kits");

        //add ES information
        if (!kitRequests.isEmpty()) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstance("atcp");
            kitRequests.forEach((ddpParticipantId, kitRequestShipping) -> {
                Map<String, Map<String, Object>> participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                        ElasticSearchUtil.BY_GUID + ddpParticipantId);
                if (participantESData == null || participantESData.isEmpty()) {
                    participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance, ElasticSearchUtil.BY_LEGACY_ALTPID + ddpParticipantId);
                }
                if (participantESData != null && !participantESData.isEmpty()) {
                    Map<String, Object> esParticipant = participantESData.get(ddpParticipantId);
                    if (esParticipant != null && !esParticipant.isEmpty()) {
                        Map<String, Object> profile = (Map<String, Object>) esParticipant.get("profile");
                        if (profile != null && !profile.isEmpty()) {
                            kitRequestShipping.setHruid((String) profile.get("hruid"));
                        }
                        List<Map<String, Object>> activities = (List<Map<String, Object>>) esParticipant.get("activities");
                        if (activities != null && !activities.isEmpty()) {
                            for (Map<String, Object> activity : activities) {
                                Object activityCode = activity.get("activityCode");
                                if ("GENOME_STUDY".equals(activityCode)) {
                                    List<Map<String, Object>> questionsAnswers = (List<Map<String, Object>>) activity.get("questionsAnswers");
                                    if (questionsAnswers != null) {
                                        for (Map<String, Object> questionsAnswer : questionsAnswers) {
                                            Object stableId = questionsAnswer.get("stableId");
                                            if ("PARTICIPANT_GENDER".equals(stableId)) {
                                                List<String> answer = (List<String>) questionsAnswer.get("answer");
                                                if (answer != null && !answer.isEmpty() && answer.size() == 1) {
                                                    kitRequestShipping.setGender(answer.get(0));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
        return new ArrayList<KitRequestShipping>(kitRequests.values());
    }

    private static String removeApostrophe(String input) {
        if (StringUtils.isNotBlank(input)) {
            return  input.replace("\"","");
        }
        return input;
    }
}
