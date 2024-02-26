package org.broadinstitute.dsm.model.at;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class ReceiveKitRequest {
    // Due to an unfortunate design choice, these values the ordinals of the possible field settings values,
    // in this case for field_type AT_GROUP_GENOME_STUDY and column_name GENOME_STUDY_STATUS.
    // There is a way to get this info from the field_settings table but it is still not conclusive, so this seemed
    // to be an appropriate improvement. -DC
    public enum ATKitShipStatus {
        NOT_SHIIPED(1),
        SENT(2),
        RECEIVED(3),
        SEQUENCED(4),
        COMPLETED(5),
        REPEAT(6);

        private final int value;

        ATKitShipStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    protected static final String GENOME_STUDY_DATE_RECEIVED = "GENOME_STUDY_DATE_RECEIVED";
    protected static final String GENOME_STUDY_STATUS = "GENOME_STUDY_STATUS";
    private static final String SQL_UPDATE_KIT_REQUEST =
            "UPDATE ddp_participant_data SET data = ? WHERE participant_data_id = ?";
    private static final String NOTIFICATION_SUBJECT = "Sample received by Broad Institute";
    private static final String NOTIFICATION_MESSAGE =
            "Sample GENOME_STUDY_SPIT_KIT_BARCODE has been received at the Broad Institute as of GENOME_STUDY_DATE_RECEIVED.";


    public static boolean receiveATKitRequest(String kitBarcode, DDPInstanceDto ddpInstanceDto,
                                              NotificationUtil notificationUtil) {
        ParticipantData participantData = SearchKitRequest.findATKitRequest(kitBarcode);
        if (participantData == null || participantData.getData().isEmpty()) {
            log.info("Did not find AT kit with kit barcode {}", kitBarcode);
            return false;
        }
        Map<String, String> dataMap = participantData.getDataMap();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = formatter.format(Instant.now().toEpochMilli());
        dataMap.put(GENOME_STUDY_DATE_RECEIVED, formattedDate);
        dataMap.put(GENOME_STUDY_STATUS, Integer.toString(ATKitShipStatus.RECEIVED.getValue()));

        updateData(dataMap, participantData.getParticipantDataId(), ddpInstanceDto);

        List<String> recipients = ddpInstanceDto.getNotificationRecipients();
        if (recipients != null && !recipients.isEmpty()) {
            for (String recipient : recipients) {
                String message = NOTIFICATION_MESSAGE.replace("GENOME_STUDY_SPIT_KIT_BARCODE", kitBarcode)
                        .replace("GENOME_STUDY_DATE_RECEIVED", formattedDate);
                notificationUtil.sentNotification(recipient, message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE,
                        NOTIFICATION_SUBJECT);
            }
            log.info("Notified study staff of kit received");
        }
        return true;
    }

    private static void updateData(Map<String, String> dataMap, int participantDataId, DDPInstanceDto ddpInstanceDto) {
        String dataString = new Gson().toJson(dataMap);
        inTransaction(conn -> {
            String errorMsg =
                    String.format("Error updating ParticipantData (id=%d) for AT kit received", participantDataId);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT_REQUEST)) {
                stmt.setString(1, dataString);
                stmt.setInt(2, participantDataId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new DsmInternalError(errorMsg + ". Number of rows updated: " + result);
                }
                return result;
            } catch (SQLException ex) {
                throw new DsmInternalError(errorMsg, ex);
            }
        });

        log.info(String.format("Updated ParticipantData (id=%d) for AT kit received ", participantDataId));

        ParticipantData participantData = new ParticipantData.Builder()
                .withData(dataString)
                .withParticipantDataId(participantDataId)
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId()).build();

        try {
            UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_DATA_ALIAS, participantData, ddpInstanceDto,
                    ESObjectConstants.PARTICIPANT_DATA_ID, ESObjectConstants.PARTICIPANT_DATA_ID, participantDataId,
                    new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            log.error(String.format("Error updating ES ParticipantData (id=%d) for AT kit received", participantDataId));
            e.printStackTrace();
        }
    }
}
