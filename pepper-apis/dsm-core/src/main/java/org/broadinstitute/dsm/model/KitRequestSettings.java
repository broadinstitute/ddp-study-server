package org.broadinstitute.dsm.model;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
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
public class KitRequestSettings {

    private static final Logger logger = LoggerFactory.getLogger(KitRequestSettings.class);

    private static final String SQL_SELECT_CARRIER = "SELECT dkc.kit_type_id, kt.kit_type_name, kt.customs_json, ddp_instance_id, cs_to.carrier as carrierTo, cs_to.easypost_carrier_id as carrierToId, " +
            "cs_to.carrier_account_number as carrierToAccountNumber, cs_to.service as serviceTo, cs_return.carrier as carrierReturn, cs_return.easypost_carrier_id as carrierReturnId, " +
            "cs_return.carrier_account_number as carrierReturnAccountNumber, cs_return.service as serviceReturn, dim.kit_height, dim.kit_weight, dim.kit_length, dim.kit_width, " +
            "dkc.collaborator_sample_type_overwrite, dkc.collaborator_participant_length_overwrite, ret.return_address_name, ret.return_address_street1, ret.return_address_street2, " +
            "ret.return_address_city, ret.return_address_state, ret.return_address_zip, ret.return_address_country, ret.return_address_phone, dkc.kit_type_display_name, dkc.external_shipper, " +
            "dkc.external_name, dkc.external_client_id, subK.kit_type_id, subK.external_name, subK.kit_count, (SELECT kit.kit_type_name FROM kit_type kit WHERE kit.kit_type_id = subK.kit_type_id) AS subKitName, " +
            "(SELECT count(dkc2.ddp_kit_request_settings_id) FROM ddp_kit_request_settings dkc2 LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc2.ddp_kit_request_settings_id) " +
            "WHERE subK.ddp_kit_request_settings_id = dkc2.ddp_kit_request_settings_id AND dkc2.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id) AS has_sub_kits FROM ddp_kit_request_settings dkc " +
            "LEFT JOIN kit_dimension dim ON (dkc.kit_dimension_id = dim.kit_dimension_id) LEFT JOIN carrier_service cs_to ON (dkc.carrier_service_to_id=cs_to.carrier_service_id) " +
            "LEFT JOIN carrier_service cs_return ON (dkc.carrier_service_return_id=cs_return.carrier_service_id) LEFT JOIN kit_return_information ret ON (dkc.kit_return_id=ret.kit_return_id) " +
            "LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id) LEFT JOIN kit_type kt ON (dkc.kit_type_id = kt.kit_type_id)";

    private String carrierTo;
    private String serviceTo;
    private String carrierToId;
    private String carrierToAccountNumber;
    private String carrierReturnId;
    private String carrierReturn;
    private String serviceReturn;
    private String carrierRetrunAccountNumber;
    private String width;
    private String length;
    private String height;
    private String weight;
    private String collaboratorSampleTypeOverwrite;
    private String collaboratorParticipantLengthOverwrite;
    private String returnName;
    private String returnStreet1;
    private String returnStreet2;
    private String returnCity;
    private String returnZip;
    private String returnState;
    private String returnCountry;
    private String phone;
    private String displayName;
    private String externalShipper;
    private String externalClientId;
    private String externalShipperKitName;
    private int hasSubKits;
    private List<KitSubKits> subKits;
    private Integer ddpInstanceId;

    public KitRequestSettings(String carrierTo, String carrierToId, String serviceTo, String carrierToAccountNumber,
                              String carrierReturn, String carrierReturnId, String serviceReturn, String carrierRetrunAccountNumber,
                              String length, String height, String width, String weight, String collaboratorSampleTypeOverwrite,
                              String collaboratorParticipantLengthOverwrite, String returnName, String returnStreet1, String returnStreet2, String returnCity,
                              String returnZip, String returnState, String returnCountry, String phone, String displayName,
                              String externalShipper, String externalClientId, String externalShipperKitName, int hasSubKits, List<KitSubKits> subKits,
                              Integer ddpInstanceId) {
        this.carrierTo = carrierTo;
        this.carrierToId = carrierToId;
        this.serviceTo = serviceTo;
        this.carrierToAccountNumber = carrierToAccountNumber;
        this.carrierReturn = carrierReturn;
        this.carrierReturnId = carrierReturnId;
        this.serviceReturn = serviceReturn;
        this.carrierRetrunAccountNumber = carrierRetrunAccountNumber;
        this.length = length;
        this.height = height;
        this.width = width;
        this.weight = weight;
        this.collaboratorSampleTypeOverwrite = collaboratorSampleTypeOverwrite;
        this.collaboratorParticipantLengthOverwrite = collaboratorParticipantLengthOverwrite;
        this.returnName = returnName;
        this.returnStreet1 = returnStreet1;
        this.returnStreet2 = returnStreet2;
        this.returnCity = returnCity;
        this.returnZip = returnZip;
        this.returnState = returnState;
        this.returnCountry = returnCountry;
        this.phone = phone;
        this.displayName = displayName;
        this.externalShipper = externalShipper;
        this.externalClientId = externalClientId;
        this.externalShipperKitName = externalShipperKitName;
        this.hasSubKits = hasSubKits;
        this.subKits = subKits;
        this.ddpInstanceId = ddpInstanceId;
    }

    private void addSubKit(KitSubKits subKit) {
        if (this.subKits != null) {
            this.subKits.add(subKit);
        }
    }

    /**
     * Getting KitRequestSettings
     * @param realmId ddp_instance_id (dsm internal id for the instance. In table ddp_instance)
     * @return HashMap<Integer, KitRequestSettings>
     *     Key: (Integer) KitTypeId
     *     Value: KitRequestSettings (Information of kit box size, shipment carrier/service, collaboratorIds and return address on shipping labels)
     */
    public static HashMap<Integer, KitRequestSettings> getKitRequestSettings(@NonNull String realmId) {
        HashMap<Integer, KitRequestSettings> carrierService = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_CARRIER + QueryExtension.WHERE_INSTANCE_ID)) {
                stmt.setString(1, realmId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        KitSubKits subKit = new KitSubKits(rs.getInt(DBConstants.KIT_TYPE_SUB_KIT), rs.getString(DBConstants.SUB_KIT_NAME), rs.getInt(DBConstants.KIT_COUNT));
                        int key = rs.getInt(DBConstants.KIT_TYPE_ID);
                        if (carrierService.containsKey(key)){
                            KitRequestSettings settings = carrierService.get(key);
                            settings.addSubKit(subKit);
                        }
                        else {
                            List<KitSubKits> subKits = new ArrayList<>();
                            subKits.add(subKit);
                            carrierService.put(key, new KitRequestSettings(rs.getString(DBConstants.DSM_CARRIER_TO),
                                    rs.getString(DBConstants.DSM_CARRIER_TO_ID), rs.getString(DBConstants.DSM_SERVICE_TO),
                                    rs.getString(DBConstants.DSM_CARRIER_TO_ACCOUNT_NUMBER),
                                    rs.getString(DBConstants.DSM_CARRIER_RETURN), rs.getString(DBConstants.DSM_CARRIER_RETURN_ID),
                                    rs.getString(DBConstants.DSM_SERVICE_RETURN),
                                    rs.getString(DBConstants.DSM_CARRIER_RETURN_ACCOUNT_NUMBER),
                                    rs.getString(DBConstants.KIT_DIMENSIONS_LENGTH),
                                    rs.getString(DBConstants.KIT_DIMENSIONS_HEIGHT), rs.getString(DBConstants.KIT_DIMENSIONS_WIDTH),
                                    rs.getString(DBConstants.KIT_DIMENSIONS_WEIGHT), rs.getString(DBConstants.COLLABORATOR_SAMPLE_TYPE_OVERWRITE),
                                    rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_LENGTH_OVERWRITE),
                                    rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_NAME), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_STREET1),
                                    rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_STREET2), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_CITY),
                                    rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_ZIP), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_STATE),
                                    rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_COUNTRY), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_PHONE),
                                    rs.getString(DBConstants.KIT_TYPE_DISPLAY_NAME), rs.getString(DBConstants.EXTERNAL_SHIPPER),
                                    rs.getString(DBConstants.EXTERNAL_CLIENT_ID), rs.getString(DBConstants.EXTERNAL_KIT_NAME),
                                    rs.getInt(DBConstants.HAS_SUB_KITS), subKits,
                                    rs.getInt(DBConstants.DDP_INSTANCE_ID)
                            ));
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
            throw new RuntimeException("Error looking up carrier service  ", results.resultException);
        }
        logger.info("Found " + carrierService.size() + " carrier/service for realm w/ id " + realmId);
        return carrierService;
    }
}
