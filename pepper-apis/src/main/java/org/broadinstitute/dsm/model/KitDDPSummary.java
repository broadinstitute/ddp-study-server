package org.broadinstitute.dsm.model;

import lombok.Getter;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Getter
public class KitDDPSummary {

    private static final Logger logger = LoggerFactory.getLogger(KitDDPSummary.class);

    private String realm;
    private String kitType;
    private String kitsNoLabel;
    private Long kitsNoLabelMinDate;
    private String kitsQueue;
    private String kitsError;

    public KitDDPSummary(String realm, String kitType, String kitsNoLabel, Long kitsNoLabelMinDate, String kitsQueue, String kitsError) {
        this.realm = realm;
        this.kitType = kitType;
        this.kitsNoLabel = kitsNoLabel;
        this.kitsNoLabelMinDate = kitsNoLabelMinDate;
        this.kitsQueue = kitsQueue;
        this.kitsError = kitsError;
    }

    /**
     * Query for not shipped kit requests
     * showOnlyKitsWithNoExtraRole will filter out all kit request types which have required_role != null
     */
    public static List<KitDDPSummary> getUnsentKits(boolean showOnlyKitsWithNoExtraRole, Collection<String> allowedRealms) {
        List<KitDDPSummary> kits = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_UNSENT_KIT_REQUESTS_FOR_REALM))) {
                stmt.setString(1, DBConstants.KIT_REQUEST_ACTIVATED);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getBoolean(DBConstants.HAS_ROLE)) {
                            String realm = rs.getString(DBConstants.INSTANCE_NAME);
                            boolean addRealmInfo = true;
                            if (allowedRealms != null) {
                                if (!allowedRealms.contains(realm)) {
                                    addRealmInfo = false;
                                }
                            }
                            if (addRealmInfo) {
                                String noLabel = rs.getString(DBConstants.KIT_REQUEST_NO_LABEL_COUNT);
                                String queue = rs.getString(DBConstants.KIT_REQUEST_QUEUE_COUNT);
                                String error = rs.getString(DBConstants.KIT_REQUEST_ERROR_COUNT);
                                if (showOnlyKitsWithNoExtraRole) {
                                    if (rs.getString(DBConstants.REQUIRED_ROLE) == null) {
                                        if (!"0".equals(noLabel) || !"0".equals(queue) || !"0".equals(error)) {
                                            kits.add(new KitDDPSummary(realm,
                                                    rs.getString(DBConstants.KIT_TYPE_NAME),
                                                    rs.getString(DBConstants.KIT_REQUEST_NO_LABEL_COUNT),
                                                    rs.getLong(DBConstants.KIT_REQUEST_NO_LABEL_OLDEST_DATE),
                                                    rs.getString(DBConstants.KIT_REQUEST_QUEUE_COUNT),
                                                    rs.getString(DBConstants.KIT_REQUEST_ERROR_COUNT)));
                                        }
                                    }
                                } else {
                                    if (!"0".equals(noLabel) || !"0".equals(queue) || !"0".equals(error)) {
                                        kits.add(new KitDDPSummary(realm,
                                                rs.getString(DBConstants.KIT_TYPE_NAME),
                                                rs.getString(DBConstants.KIT_REQUEST_NO_LABEL_COUNT),
                                                rs.getLong(DBConstants.KIT_REQUEST_NO_LABEL_OLDEST_DATE),
                                                rs.getString(DBConstants.KIT_REQUEST_QUEUE_COUNT),
                                                rs.getString(DBConstants.KIT_REQUEST_ERROR_COUNT)));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up unsent kits ", results.resultException);
        }
        logger.info("Found " + kits.size() + " ddp and type combination kits");
        return kits;
    }
}
