package org.broadinstitute.dsm.model;

import lombok.Data;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitType {

    private static final Logger logger = LoggerFactory.getLogger(KitType.class);

    private static final String SQL_SELECT_KIT_TYPE = "SELECT ks.ddp_instance_id, type.kit_type_name, ks.kit_type_display_name, type.kit_type_id, type.bsp_receptacle_type, type.customs_json, ks.external_shipper " +
            "FROM ddp_kit_request_settings ks left join kit_type type on (ks.kit_type_id = type.kit_type_id) left join sub_kits_settings subK on (subK.ddp_kit_request_settings_id = ks.ddp_kit_request_settings_id)";
    private static final String SQL_SELECT_CARRIER_SERVICE = "SELECT dkc.kit_type_id, kt.kit_type_name, kt.customs_json, ddp_instance_id, cs_to.carrier as carrierTo, cs_to.easypost_carrier_id as carrierToId, " +
        "cs_to.carrier_account_number as carrierToAccountNumber, cs_to.service as serviceTo, cs_return.carrier as carrierReturn, cs_return.easypost_carrier_id as carrierReturnId, " +
        "cs_return.carrier_account_number as carrierReturnAccountNumber, cs_return.service as serviceReturn, dim.kit_height, dim.kit_weight, dim.kit_length, dim.kit_width, dkc.collaborator_sample_type_overwrite, " +
        "dkc.collaborator_participant_length_overwrite, ret.return_address_name, ret.return_address_street1, ret.return_address_street2, ret.return_address_city, ret.return_address_state, " +
        "ret.return_address_zip, ret.return_address_country, ret.return_address_phone, dkc.kit_type_display_name, dkc.external_shipper, dkc.external_name, " +
        "(SELECT count(dkc2.ddp_kit_request_settings_id) FROM ddp_kit_request_settings dkc2 " +
        "LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc2.ddp_kit_request_settings_id) WHERE subK.ddp_kit_request_settings_id = dkc2.ddp_kit_request_settings_id AND dkc2.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id) AS has_sub_kits " +
        "FROM ddp_kit_request_settings dkc LEFT JOIN kit_dimension dim ON (dkc.kit_dimension_id = dim.kit_dimension_id) LEFT JOIN carrier_service cs_to ON (dkc.carrier_service_to_id=cs_to.carrier_service_id) " +
        "LEFT JOIN carrier_service cs_return ON (dkc.carrier_service_return_id=cs_return.carrier_service_id) LEFT JOIN kit_return_information ret ON (dkc.kit_return_id=ret.kit_return_id) " +
        "LEFT JOIN kit_type kt ON (dkc.kit_type_id = kt.kit_type_id) WHERE external_shipper IS NOT NULL;";

    private int kitTypeId;
    private int instanceId;
    private String kitTypeName;
    private String kitDisplayName;
    private String externalShipper;
    private String customsJson;
    private String externalKitName;

    public KitType (int kitTypeId, int instanceId, String kitTypeName, String kitDisplayName, String externalShipper, String customsJson) {
        this.kitTypeId = kitTypeId;
        this.instanceId = instanceId;
        this.kitTypeName = kitTypeName;
        this.kitDisplayName = kitDisplayName;
        this.externalShipper = externalShipper;
        this.customsJson = customsJson;
    }

    public KitType(int kitTypeId, int instanceId, String kitTypeName, String kitDisplayName, String externalShipper, String customsJson, String externalKitName) {
        this.kitTypeId = kitTypeId;
        this.instanceId = instanceId;
        this.kitTypeName = kitTypeName;
        this.kitDisplayName = kitDisplayName;
        this.externalShipper = externalShipper;
        this.customsJson = customsJson;
        this.externalKitName = externalKitName;
    }

    /**
     * Getting all kit types
     * @return HashMap<String, KitType>
     *     Key: (String) kit_type_name + _ + instance_id from table kit_type
     *     Value: KitType (Information of kitType like customJson)
     */
    public static HashMap<String, KitType> getKitLookup() {
        HashMap<String, KitType> kitTypes = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_TYPE)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString(DBConstants.KIT_TYPE_NAME) + "_" + rs.getString(DBConstants.DDP_INSTANCE_ID);
                        kitTypes.put(key, new KitType(rs.getInt(DBConstants.KIT_TYPE_ID),
                                rs.getInt(DBConstants.DDP_INSTANCE_ID),
                                rs.getString(DBConstants.KIT_TYPE_NAME),
                                rs.getString(DBConstants.KIT_TYPE_DISPLAY_NAME),
                                rs.getString(DBConstants.EXTERNAL_SHIPPER),
                                rs.getString(DBConstants.CUSTOMS_JSON)));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit type  ", results.resultException);
        }
        logger.info("Found " + kitTypes.size() + " kitTypes");
        return kitTypes;
    }

    public static List<KitType> getKitTypesWithExternalShipper() {
        List<KitType> kitTypes = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_CARRIER_SERVICE)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int kitType = rs.getInt(DBConstants.KIT_TYPE_ID);
                        if (kitType != 0) {
                            kitTypes.add(new KitType(kitType,
                                    rs.getInt(DBConstants.DDP_INSTANCE_ID),
                                    rs.getString(DBConstants.KIT_TYPE_NAME),
                                    rs.getString(DBConstants.KIT_TYPE_DISPLAY_NAME),
                                    rs.getString(DBConstants.EXTERNAL_SHIPPER),
                                    rs.getString(DBConstants.CUSTOMS_JSON),
                                    rs.getString("dkc." + DBConstants.EXTERNAL_KIT_NAME)));
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
            throw new RuntimeException("Error looking up kit type  ", results.resultException);
        }
        logger.info("Found " + kitTypes.size() + " kitTypes");
        return kitTypes;
    }
}