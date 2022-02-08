package org.broadinstitute.dsm.model;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class KitDDPSummary {

    private static final Logger logger = LoggerFactory.getLogger(KitDDPSummary.class);

    private static final String GET_UNSENT_KIT_REQUESTS_FOR_REALM = "select\n" +
            "        inst.ddp_instance_id,\n" +
            "        inst.instance_name,\n" +
            "        kType.kit_type_name,\n" +
            "        kType.required_role,\n" +
            "        (select count(realm.instance_name) as kitRequestCount\n" +
            "            from\n" +
            "            ddp_kit_request request\n" +
            "            left join ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id\n" +
            "            left join ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id\n" +
            "            left join kit_type kt on request.kit_type_id = kt.kit_type_id\n" +
            "            left join ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id and\n" +
            "\t\t\t\trequest.ddp_instance_id = ex.ddp_instance_id)\n" +
            "            where\n" +
            "            realm.instance_name = inst.instance_name\n" +
            "            and request.kit_type_id = kType.kit_type_id\n" +
            "            and ex.ddp_participant_exit_id is null\n" +
            "            and not (kit.kit_complete <=> 1)\n" +
            "            and not (kit.error <=> 1)\n" +
            "            and kit.label_url_to is null\n" +
            "            and kit.label_date is null\n" +
            "            and kit.deactivated_date is null) as kitRequestCountNoLabel,\n" +
            "        (select min(request.created_date) as kitRequestCount\n" +
            "            from\n" +
            "            ddp_kit_request request\n" +
            "            left join ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id\n" +
            "            left join ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id\n" +
            "            left join kit_type kt on request.kit_type_id = kt.kit_type_id\n" +
            "            left join ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id and\n" +
            "\t\t\t\trequest.ddp_instance_id = ex.ddp_instance_id)\n" +
            "            where\n" +
            "            realm.instance_name = inst.instance_name\n" +
            "            and request.kit_type_id = kType.kit_type_id\n" +
            "            and ex.ddp_participant_exit_id is null\n" +
            "            and not (kit.kit_complete <=> 1)\n" +
            "            and not (kit.error <=> 1)\n" +
            "            and kit.label_url_to is null\n" +
            "            and kit.label_date is null\n" +
            "            and kit.deactivated_date is null) as oldestKitRequestWithoutLabel,\n" +
            "        (select count(realm.instance_name) as kitRequestCount\n" +
            "            from\n" +
            "            ddp_kit_request request\n" +
            "            left join ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id\n" +
            "            left join ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id\n" +
            "            left join kit_type kt on request.kit_type_id = kt.kit_type_id\n" +
            "            left join ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id and\n" +
            "\t\t\t\trequest.ddp_instance_id = ex.ddp_instance_id)\n" +
            "            where\n" +
            "            realm.instance_name = inst.instance_name\n" +
            "            and request.kit_type_id = kType.kit_type_id\n" +
            "            and ex.ddp_participant_exit_id is null\n" +
            "            and not (kit.kit_complete <=> 1)\n" +
            "            and not (kit.error <=> 1)\n" +
            "            and not (kit.express <=> 1)\n" +
            "            and kit.label_url_to is not null\n" +
            "            and kit.deactivated_date is null) as kitRequestCountQueue,\n" +
            "        (select count(realm.instance_name) as kitRequestCount\n" +
            "            from\n" +
            "            ddp_kit_request request\n" +
            "            left join ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id\n" +
            "            left join ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id\n" +
            "            left join kit_type kt on request.kit_type_id = kt.kit_type_id\n" +
            "            left join ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id and\n" +
            "\t\t\t\trequest.ddp_instance_id = ex.ddp_instance_id)\n" +
            "            where\n" +
            "            realm.instance_name = inst.instance_name\n" +
            "            and request.kit_type_id = kType.kit_type_id\n" +
            "            and ex.ddp_participant_exit_id is null\n" +
            "            and not (kit.kit_complete <=> 1)\n" +
            "            and kit.error = 1\n" +
            "            and kit.deactivated_date is null) as kitRequestCountError,\n" +
            "        (select count(role.name)\n" +
            "            from ddp_instance realm,\n" +
            "            ddp_instance_role inRol,\n" +
            "            instance_role role\n" +
            "            where realm.ddp_instance_id = inRol.ddp_instance_id\n" +
            "            and inRol.instance_role_id = role.instance_role_id\n" +
            "            and role.name = ?\n" +
            "            and realm.ddp_instance_id = inst.ddp_instance_id) as 'has_role'\n" +
            "        from\n" +
            "        ddp_instance inst,\n" +
            "        ddp_kit_request_settings kSetting,\n" +
            "        kit_type kType\n" +
            "        where inst.ddp_instance_id = kSetting.ddp_instance_id\n" +
            "        and kType.kit_type_id = kSetting.kit_type_id\n" +
            "        and inst.is_active = 1";

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
            try (PreparedStatement stmt = conn.prepareStatement(GET_UNSENT_KIT_REQUESTS_FOR_REALM)) {
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
            } catch (SQLException ex) {
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
