package org.broadinstitute.dsm.model.nonpepperkit;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.util.DDPKitRequest;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.util.DeliveryAddress;

@Slf4j
public class NonPepperKitCreationService {
    public static final String JUNIPER = "JUNIPER";
    public static final String JUNIPER_UNDERSCORE = "JUNIPER_";

    //These are the Error Strings that are expected by Juniper
    public static final String ADDRESS_VALIDATION_ERROR = "UNABLE_TO_VERIFY_ADDRESS";
    public static final String UNKNOWN_KIT_TYPE = "UNKNOWN_KIT_TYPE";
    public static final String UNKNOWN_STUDY = "UNKNOWN_STUDY";
    public static final String MISSING_JUNIPER_KIT_ID = "MISSING_JUNIPER_KIT_ID";
    public static final String MISSING_JUNIPER_PARTICIPANT_ID = "MISSING_JUNIPER_PARTICIPANT_ID";
    public static final String DSM_ERROR = "DSM_ERROR_SOMETHING_WENT_WRONG";

    public KitResponse createNonPepperKit(JuniperKitRequest juniperKitRequest, String studyGuid, String kitTypeName) {
        if (StringUtils.isBlank(juniperKitRequest.getJuniperParticipantID())) {
            return new KitResponseError(MISSING_JUNIPER_PARTICIPANT_ID, juniperKitRequest.getJuniperKitId(),
                    juniperKitRequest.getJuniperParticipantID());
        }
        if (StringUtils.isBlank(juniperKitRequest.getJuniperKitId())) {
            return new KitResponseError(MISSING_JUNIPER_KIT_ID, null, juniperKitRequest.getJuniperKitId());
        }
        //getting the instance with isHasRole being set to true if the instance has role juniper_study
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, "juniper_study");
        if (ddpInstance == null) {
            log.error(studyGuid + " is not a study!");
            return new KitResponseError(UNKNOWN_STUDY, juniperKitRequest.getJuniperKitId(), studyGuid);
        }
        if (!ddpInstance.isHasRole()) {
            log.error(studyGuid + " is not a Juniper study!");
            return new KitResponseError(UNKNOWN_STUDY, juniperKitRequest.getJuniperKitId(), studyGuid);
        }
        HashMap<String, KitType> kitTypes = KitType.getKitLookup();
        String key = KitType.createKitTypeKey(kitTypeName, ddpInstance.getDdpInstanceId());
        KitType kitType = kitTypes.get(key);
        if (kitType == null) {
            return new KitResponseError(UNKNOWN_KIT_TYPE, juniperKitRequest.getJuniperKitId(), kitTypeName);
        }

        Map<Integer, KitRequestSettings> kitRequestSettingsMap =
                KitRequestSettings.getKitRequestSettings(String.valueOf(ddpInstance.getDdpInstanceId()));
        KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());

        // if the kit type has sub kits > like for testBoston
        //        boolean kitHasSubKits = kitRequestSettings.getHasSubKits() != 0;

        log.info("Setup EasyPost...");
        EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstance.getName());

        if (!checkAddress(juniperKitRequest, kitRequestSettings.getPhone(), easyPostUtil)) {
            return new KitResponseError(ADDRESS_VALIDATION_ERROR, juniperKitRequest.getJuniperKitId(), null);
        }

        ArrayList<KitRequest> orderKits = new ArrayList<>();


        SimpleResult result = TransactionWrapper.inTransaction(conn -> {
            SimpleResult transactionResults = new SimpleResult();
            return createKit(ddpInstance, kitType, juniperKitRequest, kitRequestSettings, easyPostUtil, kitTypeName, orderKits, conn,
                    transactionResults);

            //            only order if external shipper name is set for that kit request, not needed for now
            //            if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            //                return orderExternalKits(kitRequestSettings, orderKits, easyPostUtil, shippingCarrier, conn);
            //            }
        });

        if (result.resultException != null) {
            log.error(String.format("Unable to create Juniper kit for %s", juniperKitRequest), result.resultException);
            return new KitResponseError(DSM_ERROR, juniperKitRequest.getJuniperKitId());

        }

        log.info(juniperKitRequest.getJuniperKitId() + " " + ddpInstance.getName() + " " + kitTypeName + " kit created");
        //TODO in PEPPER-841 change this to status
        return new KitResponseError(null, juniperKitRequest.getJuniperKitId(), null);
    }

    /**
     * checkAddress tries creating an address in EasyPost. If it is successful,
     * sets the kit's easypostAddressId and returns true, if not returns false
     * An address is valid only if participant has shortId, first - and lastName, for Juniper shortId is the juniperParticipantId
     *
     * @param juniperKitRequest the JuniperKitRequest with address to check
     */

    public boolean checkAddress(JuniperKitRequest juniperKitRequest, String phone, EasyPostUtil easyPostUtil) {
        if ((StringUtils.isBlank(juniperKitRequest.getJuniperParticipantID()))
                || StringUtils.isBlank(juniperKitRequest.getLastName())) {
            return false;
        }
        //let's validate the participant's address
        String name = "";
        if (StringUtils.isNotBlank(juniperKitRequest.getFirstName())) {
            name += juniperKitRequest.getFirstName() + " ";
        }
        name += juniperKitRequest.getLastName();
        if (juniperKitRequest.isSkipAddressValidation()) {
            try {
                Address address = easyPostUtil.createBroadAddress(name, juniperKitRequest.getStreet1(), juniperKitRequest.getStreet2(),
                        juniperKitRequest.getCity(),
                        juniperKitRequest.getPostalCode(), juniperKitRequest.getState(), juniperKitRequest.getCountry(), phone);
                juniperKitRequest.setEasypostAddressId(address.getId());
                return true;
            } catch (EasyPostException e) {
                // log the reason for address creation failure and return false. The method will then return the error code
                log.warn("Easypost couldn't create an address for " + juniperKitRequest.getShortId(), e);
                return false;
            }
        }
        DeliveryAddress deliveryAddress =
                new DeliveryAddress(juniperKitRequest.getStreet1(), juniperKitRequest.getStreet2(), juniperKitRequest.getCity(),
                        juniperKitRequest.getState(),
                        juniperKitRequest.getPostalCode(), juniperKitRequest.getCountry(), name, phone);
        deliveryAddress.validate();
        if (deliveryAddress.isValid()) {
            //store the address back
            juniperKitRequest.setEasypostAddressId(deliveryAddress.getId());
            return true;
        }
        log.info("Address is not valid " + juniperKitRequest.getShortId());
        return false;

    }


    /**
     * This method creates a collaborator participant id for the kit and then inserts in DB
     *
     * @return SimpleResult of the interaction with database
     */
    private SimpleResult createKit(@NonNull DDPInstance ddpInstance, @NonNull KitType kitType, JuniperKitRequest kit,
                                   @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil,
                                   @NonNull String kitTypeName, ArrayList<KitRequest> orderKits, Connection conn,
                                   SimpleResult transactionResults) {
        String juniperKitRequestId;
        String userId;
        //checking ddpInstance.isHasRole() to know this is a Juniper Kit
        if (ddpInstance.isHasRole()) {
            juniperKitRequestId = JUNIPER_UNDERSCORE + kit.getJuniperKitId();
            userId = JUNIPER;
        } else {
            log.warn("Seems like {} is not configured as a JUNIPER study! ", ddpInstance.getName());
            transactionResults.resultException = new Exception(ddpInstance.getName() + " study is not configured to use this method.");
            return transactionResults;
        }

        String externalOrderNumber = null;
        if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            externalOrderNumber = DDPKitRequest.generateExternalOrderNumber();
        }
        String errorMessage = "";
        // collaboratorParticipantId is the id of the participant in GP's view
        String collaboratorParticipantId = KitRequestShipping
                .getCollaboratorParticipantId(null, ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                        ddpInstance.getCollaboratorIdPrefix(), kit.getJuniperParticipantID(), kit.getJuniperParticipantID(),
                        kitRequestSettings.getCollaboratorParticipantLengthOverwrite());

        //In case it's needed, subkits handling should be added here

        addJuniperKitRequest(conn, kitTypeName, kitRequestSettings, ddpInstance, kitType.getKitTypeId(), collaboratorParticipantId,
                errorMessage, easyPostUtil, kit, externalOrderNumber, juniperKitRequestId, null, userId, transactionResults);

        // Not needed now, uncomment later when needed for external shippers
        //        orderKits.add(kit);

        return transactionResults;
    }

    private SimpleResult addJuniperKitRequest(Connection conn, String kitTypeName, KitRequestSettings kitRequestSettings,
                                              DDPInstance ddpInstance,
                                              int kitTypeId, String collaboratorParticipantId, String errorMessage,
                                              EasyPostUtil easyPostUtil,
                                              JuniperKitRequest kit, String externalOrderNumber, String juniperKitRequestId,
                                              String ddpLabel, String userId, SimpleResult transactionResults) {
        String collaboratorSampleId = null;
        String bspCollaboratorSampleType = kitTypeName;
        String addressId = null;
        try {
            Address address = easyPostUtil.getAddress(kit.getEasypostAddressId());
            if (address != null) {
                addressId = address.getId();
            }
        } catch (EasyPostException e) {
            throw new RuntimeException("EasyPost addressId could not be received ", e);
        }

        if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            //             Not needed now but in general, we don't check for overwrite length for an external shipper
            try {
                // collaborator sample id is the id of the physical sample that GP will work with.
                collaboratorSampleId =
                        KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId);
                KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), juniperKitRequestId, kitTypeId,
                        kit.getParticipantId().trim(),
                        collaboratorParticipantId, collaboratorSampleId, userId, addressId, errorMessage, externalOrderNumber, false,
                        null, ddpInstance, bspCollaboratorSampleType, ddpLabel);
                kit.setExternalOrderNumber(externalOrderNumber);
            } catch (Exception e) {
                transactionResults.resultException = e;
            }
        } else {
            // trying to generate collaboratorSampleId based on collaboratorParticipantId --
            // it will be skipped if study did not have the config to get a collaboratorParticipantId
            if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
            }
            if (StringUtils.isNotBlank(collaboratorParticipantId)) {
                collaboratorSampleId =
                        KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId);
                if (collaboratorSampleId == null) {
                    errorMessage += "collaboratorSampleId was too long ";
                }
            }

            String participantID = kit.getJuniperParticipantID();
            try {
                //insert the kit into DB
                String dsmKitRequestId =
                        KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), juniperKitRequestId, kitTypeId, participantID,
                                collaboratorParticipantId, collaboratorSampleId, userId, addressId, errorMessage,
                                kit.getExternalOrderNumber(),
                                false,
                                null, ddpInstance, bspCollaboratorSampleType, ddpLabel);
                log.info("Created new kit in DSM with dsm_kit_request_id {} and ddpLabel {} for JuniperKitId {}", dsmKitRequestId, ddpLabel,
                        juniperKitRequestId);
            } catch (Exception e) {
                transactionResults.resultException = e;
            }

        }
        return transactionResults;
    }

    //    private Result orderExternalKits(KitRequestSettings kitRequestSettings, ArrayList<KitRequest> orderKits,
    //    EasyPostUtil easyPostUtil, AtomicReference<String> shippingCarrier, Connection conn) {
    //        try {
    //            logger.info("placing order with external shipper");
    //            ExternalShipper shipper =
    //                    (ExternalShipper) Class.forName(DSMServer.getClassName(kitRequestSettings.getExternalShipper()))
    //                            .newInstance();
    //            shipper.orderKitRequests(orderKits, easyPostUtil, kitRequestSettings, shippingCarrier.get());
    //            // mark kits as transmitted so that background jobs don't try to double order it
    //            for (KitRequest orderKit : orderKits) {
    //                KitRequestShipping.markOrderTransmittedAt(conn, orderKit.getExternalOrderNumber(), Instant.now());
    //            }
    //        } catch (Exception e) {
    //            logger.error("Failed to sent kit request order to " + kitRequestSettings.getExternalShipper(), e);
    //            return new Result(500, "Failed to sent kit request order to " + kitRequestSettings.getExternalShipper());
    //        }
    //        return null;
    //    }

}
