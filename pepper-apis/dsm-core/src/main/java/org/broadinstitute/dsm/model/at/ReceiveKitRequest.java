package org.broadinstitute.dsm.model.at;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class ReceiveKitRequest {

    private static final Logger logger = LoggerFactory.getLogger(ReceiveKitRequest.class);

    public static final String SQL_UPDATE_KIT_REQUEST = "UPDATE ddp_participant_data SET data = ? WHERE participant_data_id = ?";

    private static final String RECEIVED_DATE = "GENOME_STUDY_DATE_RECEIVED";
    private static final String GENOME_STUDY_STATUS = "GENOME_STUDY_STATUS";
    private static final String NOTIFICATION_SUBJECT = "Sample received by Broad Institute";
    private static final String NOTIFICATION_MESSAGE = "Sample GENOME_STUDY_SPIT_KIT_BARCODE has been received at the Broad Institute as of GENOME_STUDY_DATE_RECEIVED.";

    public static boolean receiveATKitRequest(@NonNull NotificationUtil notificationUtil, @NonNull String mfBarcode) {
        org.broadinstitute.dsm.db.ParticipantData participantData = SearchKitRequest.findATKitRequest(mfBarcode);
        if (participantData != null && StringUtils.isNotBlank(participantData.getData())) {
            Map<String,String> data = new Gson().fromJson(participantData.getData(), new TypeToken<Map<String, String>>(){}.getType());
            long now = Instant.now().toEpochMilli();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            String formattedDate = formatter.format(now);
            data.put(RECEIVED_DATE, formattedDate);
            data.put(GENOME_STUDY_STATUS, "3");
            DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName("atcp").orElseThrow();
            String dataString = new Gson().toJson(data);
            if (updateData(dataString, participantData.getParticipantDataId(), ddpInstanceDto)) {
                List<String> recipients = ddpInstanceDto.getNotificationRecipients();
                if (recipients != null && !recipients.isEmpty()) {
                    for (String recipient : recipients) {
                        String message = NOTIFICATION_MESSAGE.replace("GENOME_STUDY_SPIT_KIT_BARCODE", mfBarcode).replace("GENOME_STUDY_DATE_RECEIVED", formattedDate);
                        notificationUtil.sentNotification(recipient, message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NOTIFICATION_SUBJECT);
                    }
                    logger.info("Notified study staff of kit received");
                }
                return true;
            }
        }//no else because if participantData then study manager wouldn't have been able to find the kit request!
        return false;
    }

    private static boolean updateData(@NonNull String data, @NonNull long participantDataId, DDPInstanceDto ddpInstanceDto) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT_REQUEST)){
                stmt.setString(1, data);
                stmt.setObject(2, participantDataId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Set AT kit to received " + participantDataId);
                }
                else {
                    throw new RuntimeException("Error setting AT kit to received with id " + participantDataId + ": it was updating " + result + " rows");
                }
            }
            catch (SQLException ex){
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error setting AT kit to received with id " + participantDataId, results.resultException);
        }

        ParticipantData participantData = new ParticipantData.Builder()
                .withData(data)
                .withParticipantDataId((int) participantDataId)
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .build();

        UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_DATA_ALIAS, participantData, ddpInstanceDto, ESObjectConstants.PARTICIPANT_DATA_ID,
                        ESObjectConstants.PARTICIPANT_DATA_ID, participantDataId)
                .export();

        return true;
    }
}
