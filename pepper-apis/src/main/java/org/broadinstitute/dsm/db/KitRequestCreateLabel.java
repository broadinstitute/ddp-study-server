package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitRequestCreateLabel {

    private static final Logger logger = LoggerFactory.getLogger(KitRequestCreateLabel.class);

    private final String dsmKitId;
    private final String dsmKitRequestId;
    private final String instanceID;
    private final String instanceName;
    private final String ddpParticipantId;
    private final String addressIdTo;
    private final String kitType;
    private final String baseURL;
    private final String participantCollaboratorId;
    private final String collaboratorIdPrefix;
    private final boolean hasAuth0Token;
    private final KitRequestSettings kitRequestSettings;
    private final KitType kitTyp;
    private final String billingReference;
    private final String participantIndexES;

    public KitRequestCreateLabel(@NonNull String dsmKitId, @NonNull String dsmKitRequestId, @NonNull String instanceID,
                                 @NonNull String instanceName, String ddpParticipantId,
                                 String addressIdTo, @NonNull String kitType, String baseURL, String participantCollaboratorId,
                                 String collaboratorIdPrefix, boolean hasAuth0Token, KitRequestSettings kitRequestSettings, KitType kitTyp,
                                 String billingReference, String participantIndexES) {
        this.dsmKitId = dsmKitId;
        this.dsmKitRequestId = dsmKitRequestId;
        this.instanceID = instanceID;
        this.instanceName = instanceName;
        this.ddpParticipantId = ddpParticipantId;
        this.addressIdTo = addressIdTo;
        this.kitType = kitType;
        this.baseURL = baseURL;
        this.participantCollaboratorId = participantCollaboratorId;
        this.collaboratorIdPrefix = collaboratorIdPrefix;
        this.hasAuth0Token = hasAuth0Token;
        this.kitRequestSettings = kitRequestSettings;
        this.kitTyp = kitTyp;
        this.billingReference = billingReference;
        this.participantIndexES = participantIndexES;
    }

    /**
     * Set given kitRequests to need label
     */
    public static void updateKitLabelRequested(KitRequestShipping[] kitRequests, @NonNull String userId) {
        if (kitRequests.length > 0) {
            for (KitRequestShipping kitRequest : kitRequests) {
                KitRequestShipping.updateKit(kitRequest.getDsmKitId(), userId);
            }
            logger.info("Done triggering label creation");
        }
    }

    /**
     * Select all sample requests without kit per realm
     * optional per kit type
     */
    public static void updateKitLabelRequested(String realm, String type, @NonNull String userId) {
        List<KitRequestCreateLabel> kitsNoLabel = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement bspStatement = getPreparedStatement(conn, realm, type)) {
                try (ResultSet rs = bspStatement.executeQuery()) {
                    while (rs.next()) {
                        kitsNoLabel.add(new KitRequestCreateLabel(rs.getString(DBConstants.DSM_KIT_ID),
                                rs.getString(DBConstants.DSM_KIT_REQUEST_ID), rs.getString(DBConstants.DDP_INSTANCE_ID),
                                rs.getString(DBConstants.INSTANCE_NAME), rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.EASYPOST_ADDRESS_ID_TO), rs.getString(DBConstants.KIT_TYPE_NAME),
                                rs.getString(DBConstants.BASE_URL), rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                rs.getString(DBConstants.COLLABORATOR_ID_PREFIX),
                                rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN), null, null,
                                rs.getString(DBConstants.BILLING_REFERENCE),
                                rs.getString(DBConstants.ES_PARTICIPANT_INDEX)
                        ));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up the latestKitRequests ", results.resultException);
        }
        logger.info("Found " + kitsNoLabel.size() + " kit requests which need a label");

        if (!kitsNoLabel.isEmpty()) {
            for (KitRequestCreateLabel uploadedKit : kitsNoLabel) {
                KitRequestShipping.updateKit(uploadedKit.getDsmKitId(), userId);
            }
        }
        logger.info("Done triggering label creation");
    }

    private static PreparedStatement getPreparedStatement(@NonNull Connection conn, String realm, String type) throws SQLException {
        PreparedStatement stmt = null;
        String query = KitRequestShipping.SQL_SELECT_KIT_REQUEST.concat(QueryExtension.KIT_NO_LABEL).concat(QueryExtension.KIT_LABEL_NOT_TRIGGERED);
        if (StringUtils.isNotBlank(realm) && StringUtils.isNotBlank(type)) {
            logger.info("Going to request label for all kits of realm " + realm + " and kit type " + type);
            query = query.concat(QueryExtension.BY_REALM_AND_TYPE);
            stmt = conn.prepareStatement(query);
            stmt.setString(1, realm);
            stmt.setString(2, type);
        }
        else if (StringUtils.isNotBlank(realm)) {
            logger.info("Going to request label for all kits of realm " + realm);
            query = query.concat(QueryExtension.BY_REALM);
            stmt = conn.prepareStatement(query);
            stmt.setString(1, realm);
        }
        else {
            logger.info("Going to request label for all kits");
            stmt = conn.prepareStatement(query);
        }
        return stmt;
    }
}
