package org.broadinstitute.dsm.model.nonpepperkit;

import java.sql.Connection;
import java.sql.SQLException;
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
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.util.DDPKitRequest;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.util.DeliveryAddress;

@Slf4j
public class NonPepperKitCreationService {
    public static final String JUNIPER = "JUNIPER";
    NonPepperStatusKitService nonPepperStatusKitService;

    public NonPepperKitCreationService() {
        this.nonPepperStatusKitService = new NonPepperStatusKitService();
    }

    //These are the Error Strings that are expected by Juniper

    public KitResponse createNonPepperKit(JuniperKitRequest juniperKitRequest, String kitTypeName, EasyPostUtil easyPostUtil,
                                          DDPInstance ddpInstance) {
        if (StringUtils.isBlank(juniperKitRequest.getJuniperParticipantID())) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID,
                    juniperKitRequest.getJuniperKitId(),
                    juniperKitRequest.getJuniperParticipantID());
        }
        if (StringUtils.isBlank(juniperKitRequest.getJuniperKitId())) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_KIT_ID, null,
                    juniperKitRequest.getJuniperKitId());
        }
        HashMap<String, KitType> kitTypes = KitType.getKitLookup();
        String key = KitType.createKitTypeKey(kitTypeName, ddpInstance.getDdpInstanceId());
        KitType kitType = kitTypes.get(key);
        if (kitType == null) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.UNKNOWN_KIT_TYPE, juniperKitRequest.getJuniperKitId(),
                    kitTypeName);
        }

        Map<Integer, KitRequestSettings> kitRequestSettingsMap =
                KitRequestSettings.getKitRequestSettings(String.valueOf(ddpInstance.getDdpInstanceId()));
        KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());

        // TODO for future if applicable, if the kit type has sub kits check that here

        return validateAndInsertNewNonPepperKit(kitRequestSettings, juniperKitRequest, easyPostUtil, ddpInstance, kitType, kitTypeName);
    }

    private KitResponse validateAndInsertNewNonPepperKit(KitRequestSettings kitRequestSettings, JuniperKitRequest juniperKitRequest,
                                                         EasyPostUtil easyPostUtil, DDPInstance ddpInstance,
                                                         KitType kitType, String kitTypeName) {
        //let's validate the participant's address
        //to validate the address, first we need create the Address instance
        String name = "";
        String phone = kitRequestSettings.getPhone();
        if (StringUtils.isNotBlank(juniperKitRequest.getFirstName())) {
            name += juniperKitRequest.getFirstName() + " ";
        }
        name += juniperKitRequest.getLastName();
        DeliveryAddress deliveryAddress =
                new DeliveryAddress(juniperKitRequest.getStreet1(), juniperKitRequest.getStreet2(), juniperKitRequest.getCity(),
                        juniperKitRequest.getState(), juniperKitRequest.getPostalCode(), juniperKitRequest.getCountry(), name, phone);
        try {
            String easypostAddressId = easyPostUtil.getEasyPostAddressId(juniperKitRequest, phone, deliveryAddress);
            if (StringUtils.isBlank(easypostAddressId)) {
                throw new DsmInternalError("EasyPost address Id should not be null!");
            }
            juniperKitRequest.setEasypostAddressId(easypostAddressId);
            SimpleResult result = TransactionWrapper.inTransaction(conn -> {
                SimpleResult transactionResults = new SimpleResult();
                return createKit(ddpInstance, kitType, juniperKitRequest, kitRequestSettings, easyPostUtil, kitTypeName, conn,
                        transactionResults);

                //          order external kits here if external shipper name is set for that kit request, not needed for now
            });

            if (result.resultException != null) {
                log.error(String.format("Unable to create Juniper kit for %s", juniperKitRequest), result.resultException);
                return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.DSM_ERROR_SOMETHING_WENT_WRONG,
                        juniperKitRequest.getJuniperKitId());
            }
            log.info("{} for ddpInstance {} with kit type {} has been created", juniperKitRequest.getJuniperKitId(), ddpInstance.getName(),
                    kitTypeName);
            return this.nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperKitRequest.getJuniperKitId());
        } catch (DSMBadRequestException exception) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.ADDRESS_VALIDATION_ERROR,
                    juniperKitRequest.getJuniperKitId(), exception);
        } catch (DsmInternalError exception) {
            log.error("DSM had problems inserting the new kit", exception);
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.DSM_ERROR_SOMETHING_WENT_WRONG,
                    juniperKitRequest.getJuniperKitId(), null);

        }
    }


    /**
     * This method creates a collaborator participant id for the kit and then inserts in DB
     *
     * @return SimpleResult of the interaction with database
     */
    private SimpleResult createKit(@NonNull DDPInstance ddpInstance, @NonNull KitType kitType, JuniperKitRequest kit,
                                   @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil,
                                   @NonNull String kitTypeName, Connection conn, SimpleResult transactionResults) {
        String juniperKitRequestId;
        String userId;
        //checking ddpInstance.isHasRole() to know this is a Juniper Kit
        if (ddpInstance.isHasRole()) {
            juniperKitRequestId = kit.getJuniperKitId();
            userId = JUNIPER;
        } else {
            log.warn("Seems like {} is not configured as a JUNIPER study! ", ddpInstance.getName());
            transactionResults.resultException =
                    new DsmInternalError(ddpInstance.getName() + " study is not configured to use this method.");
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
        try {
            addJuniperKitRequest(conn, kitTypeName, kitRequestSettings, ddpInstance, kitType.getKitTypeId(), collaboratorParticipantId,
                    errorMessage, easyPostUtil, kit, externalOrderNumber, juniperKitRequestId, null, userId, transactionResults);
        } catch (Exception e) {
            throw new DsmInternalError("unable to add juniper kit request", e);
        }
        return transactionResults;
    }

    private void addJuniperKitRequest(Connection conn, String kitTypeName, KitRequestSettings kitRequestSettings,
                                      DDPInstance ddpInstance, int kitTypeId, String collaboratorParticipantId,
                                      String errorMessage, EasyPostUtil easyPostUtil, JuniperKitRequest kit,
                                      String externalOrderNumber, String juniperKitRequestId,
                                      String ddpLabel, String userId, SimpleResult transactionResults) throws DsmInternalError {
        String collaboratorSampleId = null;
        String bspCollaboratorSampleType = kitTypeName;
        String addressId = null;
        try {
            Address address = easyPostUtil.getAddress(kit.getEasypostAddressId());
            if (address != null) {
                addressId = address.getId();
            }
        } catch (EasyPostException e) {
            throw new DsmInternalError("EasyPost addressId could not be received ", e);
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
                log.info("Created new kit in DSM with dsm_kit_request_id {} for JuniperKitId {}", dsmKitRequestId, juniperKitRequestId);
            } catch (Exception e) {
                transactionResults.resultException = e;
            }

        }
        if (transactionResults.resultException != null) {
            throw new DsmInternalError(transactionResults.resultException);
        }

    }

}
