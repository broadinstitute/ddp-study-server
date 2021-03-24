package org.broadinstitute.dsm.model.bsp;

import lombok.Getter;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Getter
public class BSPKitQueryResult {

    public static final String BASE_URL = "base_url";
    public static final String BSP_SAMPLE_ID = "bsp_collaborator_sample_id";
    public static final String BSP_PARTICIPANT_ID = "bsp_collaborator_participant_id";
    public static final String INSTANCE_NAME = "instance_name";
    public static final String BSP_COLLECTION = "bsp_collection";
    public static final String BSP_ORGANISM = "bsp_organism";
    public static final String DDP_PARTICIPANT_ID = "ddp_participant_id";
    public static final String MATERIAL_TYPE = "bsp_material_type";
    public static final String RECEPTACLE_TYPE = "bsp_receptacle_type";
    public static final String PARTICIPANT_EXIT = "ddp_participant_exit_id";

    private final String baseUrl;
    private final String bspSampleId;
    private final String bspParticipantId;
    private final String instanceName;
    private final String bspOrganism;
    private final String bspCollection;
    private final String ddpParticipantId;
    private String bspMaterialType;
    private String bspReceptacleType;
    private boolean hasParticipantNotifications;
    private String participantExitId;
    private String deactivationDate;
    private String notificationRecipient;

    public BSPKitQueryResult (String instanceName,
                              String baseUrl,
                              String bspSampleId,
                              String bspParticipantId,
                              String bspOrganism,
                              String bspCollection,
                              String ddpParticipantId,
                              String bspMaterialType,
                              String bspReceptacleType,
                              boolean hasParticipantNotifications,
                              String participantExitId,
                              String deactivationDate,
                              String notificationRecipient) {

        this.instanceName = instanceName;
        this.baseUrl = baseUrl;
        this.bspSampleId = bspSampleId;
        this.bspParticipantId = bspParticipantId;
        this.bspOrganism = bspOrganism;
        this.bspCollection = bspCollection;
        this.ddpParticipantId = ddpParticipantId;
        this.bspMaterialType = bspMaterialType;
        this.bspReceptacleType = bspReceptacleType;
        this.hasParticipantNotifications = hasParticipantNotifications;
        this.participantExitId = participantExitId;
        this.deactivationDate = deactivationDate;
        this.notificationRecipient = notificationRecipient;
    }

    public static BSPKitQueryResult getBSPKitQueryResult (@NonNull String kitLabel) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_BSP_RESPONSE_INFORMATION_FOR_KIT))) {
                    stmt.setString(1, DBConstants.KIT_PARTICIPANT_NOTIFICATIONS_ACTIVATED);
                    stmt.setString(2, kitLabel);
                    try (ResultSet rs = stmt.executeQuery()) {
                        int numRows = 0;
                        while (rs.next()) {
                            numRows++;
                            dbVals.resultValue = new BSPKitQueryResult(
                                    rs.getString(BSPKitQueryResult.INSTANCE_NAME),
                                    rs.getString(BSPKitQueryResult.BASE_URL),
                                    rs.getString(BSPKitQueryResult.BSP_SAMPLE_ID),
                                    rs.getString(BSPKitQueryResult.BSP_PARTICIPANT_ID),
                                    rs.getString(BSPKitQueryResult.BSP_ORGANISM),
                                    rs.getString(BSPKitQueryResult.BSP_COLLECTION),
                                    rs.getString(BSPKitQueryResult.DDP_PARTICIPANT_ID),
                                    rs.getString(BSPKitQueryResult.MATERIAL_TYPE),
                                    rs.getString(BSPKitQueryResult.RECEPTACLE_TYPE),
                                    rs.getBoolean(DBConstants.HAS_ROLE),
                                    rs.getString(BSPKitQueryResult.PARTICIPANT_EXIT),
                                    rs.getString(DBConstants.DSM_DEACTIVATED_DATE),
                                    rs.getString(DBConstants.NOTIFICATION_RECIPIENT)
                            );
                        }
                        if (numRows > 1) {
                            throw new RuntimeException("Found " + numRows + " kits for kit label " + kitLabel);
                        }
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit info for kit " + kitLabel, results.resultException);
        }
        return (BSPKitQueryResult) results.resultValue;
    }
}
