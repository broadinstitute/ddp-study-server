package org.broadinstitute.dsm.route;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.model.KitUploadObject;
import org.broadinstitute.dsm.model.KitUploadResponse;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.util.DDPKitRequest;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.externalshipper.ExternalShipper;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.DeliveryAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class JuniperShipKitRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(JuniperShipKitRoute.class);

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        return null;
    }

    public Object createNonPepperKit(KitRequest kitUploadObject, String studyGuid, String kitTypeName,
                                    boolean skipAddressValidation, String userIdRequest, AtomicReference<String> shippingCarrier) {
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByStudyGuid(studyGuid).orElseThrow();
        StringBuilder specialMessage = new StringBuilder();

        HashMap<String, KitType> kitTypes = KitType.getKitLookup();
        String key = kitTypeName + "_" + ddpInstanceDto.getDdpInstanceId();
        KitType kitType = kitTypes.get(key);
        if (kitType == null) {
            throw new RuntimeException("KitType unknown");
        }

        Map<Integer, KitRequestSettings> kitRequestSettingsMap =
                KitRequestSettings.getKitRequestSettings(String.valueOf(ddpInstanceDto.getDdpInstanceId()));
        KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());
        // if the kit type has sub kits > like for testBoston
//        boolean kitHasSubKits = kitRequestSettings.getHasSubKits() != 0;

        logger.info("Setup EasyPost...");
        EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstanceDto.getInstanceName());

        Map<String, KitRequest> invalidAddressList =
                checkAddress(kitUploadObject, kitRequestSettings.getPhone(), skipAddressValidation, easyPostUtil);

        //TODO Pegah discuss with Juniper if they need this, and if so, how the response should look like
        List<KitRequest> duplicateKitList = new ArrayList<>();
        ArrayList<KitRequest> orderKits = new ArrayList<>();

        //TODO Pegah fix uploadKit to accept DDPInstanceDto
        //getting the instance with isHasRole being set to true if the instance has role juniper_study
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole(ddpInstanceDto.getInstanceName(), "juniper_study");

        if(!ddpInstance.isHasRole()){
            throw new RuntimeException("This is not a Juniper study!");
        }

        TransactionWrapper.inTransaction(conn -> {
            createKit(ddpInstance, kitType, kitUploadObject, kitRequestSettings, easyPostUtil, userIdRequest,
                    kitTypeName, true, invalidAddressList, duplicateKitList, orderKits,
                    null, null, conn);

            //only order if external shipper name is set for that kit request
            if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
                try {
                    logger.info("placing order with external shipper");
                    ExternalShipper shipper =
                            (ExternalShipper) Class.forName(DSMServer.getClassName(kitRequestSettings.getExternalShipper()))
                                    .newInstance();
                    shipper.orderKitRequests(orderKits, easyPostUtil, kitRequestSettings, shippingCarrier.get());
                    // mark kits as transmitted so that background jobs don't try to double order it
                    for (KitRequest orderKit : orderKits) {
                        KitRequestShipping.markOrderTransmittedAt(conn, orderKit.getExternalOrderNumber(), Instant.now());
                    }
                } catch (Exception e) {
                    logger.error("Failed to sent kit request order to " + kitRequestSettings.getExternalShipper(), e);
                    return new Result(500, "Failed to sent kit request order to " + kitRequestSettings.getExternalShipper());
                }
            }
            return null;
        });

        //send not valid address back to client
        logger.info(kitUploadObject.getJuniperKitId()+ " " + ddpInstance.getName() + " " + kitTypeName + " kit created");
        logger.info(invalidAddressList.size() + " uploaded addresses were not valid and " + duplicateKitList.size()
                + " are already in DSM");
        return new KitUploadResponse(invalidAddressList.values(), duplicateKitList, null, specialMessage.toString());
    }

    public Map<String, KitRequest> checkAddress(KitRequest kitUploadObject, String phone, boolean skipAddressValidation,
                                                EasyPostUtil easyPostUtil) {
        Map<String, KitRequest> noValidAddress = new HashMap<>();
        KitUploadObject object = (KitUploadObject) kitUploadObject;
        //only if participant has shortId, first - and lastName, for Juniper shortId is the juniperParticipantId
        if ((StringUtils.isNotBlank(object.getShortId()) || StringUtils.isNotBlank(object.getExternalOrderNumber()))
                && StringUtils.isNotBlank(object.getLastName())) {
            //let's validate the participant's address
            String name = "";
            if (StringUtils.isNotBlank(object.getFirstName())) {
                name += object.getFirstName() + " ";
            }
            name += object.getLastName();

            if (skipAddressValidation) {
                try {
                    Address address = easyPostUtil.createBroadAddress(name, object.getStreet1(), object.getStreet2(), object.getCity(),
                            object.getPostalCode(), object.getState(), object.getCountry(), phone);
                    object.setEasyPostAddressId(address.getId());
                } catch (EasyPostException e) {
                    logger.error("Easypost couldn't create an address for " + object.getShortId());
                }
            } else {
                DeliveryAddress deliveryAddress =
                        new DeliveryAddress(object.getStreet1(), object.getStreet2(), object.getCity(), object.getState(),
                                object.getPostalCode(), object.getCountry(), name, phone);
                deliveryAddress.validate();
                if (deliveryAddress.isValid()) {
                    //store the address back
                    object.setEasyPostAddressId(deliveryAddress.getId());
                } else {
                    logger.info("Address is not valid " + object.getShortId());
                    noValidAddress.put(object.getShortId(), object);
                }
            }
        } else {
            noValidAddress.put(object.getShortId(), object);
        }
        return noValidAddress;
    }

    /**
     * This method creates a collaborator participant id for the kit and then inserts in DB
     * */
    private void createKit(@NonNull DDPInstance ddpInstance, @NonNull KitType kitType, KitRequest kit,
                           @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil,
                           @NonNull String userIdRequest, @NonNull String kitTypeName, boolean uploadAnyway,
                           Map<String, KitRequest> invalidAddressList, List<KitRequest> duplicateKitList, ArrayList<KitRequest> orderKits,
                           String uploadReason, String carrier, Connection conn) {

        String externalOrderNumber = null;
        if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            externalOrderNumber = DDPKitRequest.generateExternalOrderNumber();
        }
        if (invalidAddressList.get(kit.getShortId()) == null) { //kit is not in the noValid list, so enter into db
            String errorMessage = "";
            String collaboratorParticipantId = "";
            if(StringUtils.isBlank(kit.getParticipantId())){
                //TODO Pegah error here?
            }
            collaboratorParticipantId = KitRequestShipping
                    .getCollaboratorParticipantId(null, ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                            ddpInstance.getCollaboratorIdPrefix(), kit.getParticipantId(), kit.getShortId(),
                            kitRequestSettings.getCollaboratorParticipantLengthOverwrite());

            //In case it's needed, subkits handling should be added here
            handleKit(conn, ddpInstance, kitType, kit, kitRequestSettings, easyPostUtil, userIdRequest, kitTypeName,
                    collaboratorParticipantId, KitUtil.IGNORE_AUTO_DEACTIVATION, uploadAnyway, duplicateKitList, orderKits,
                    externalOrderNumber, uploadReason, carrier);

        }
    }



    private void handleKit(@NonNull Connection conn, @NonNull DDPInstance ddpInstance, @NonNull KitType kitType, @NonNull KitRequest kit,
                           @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil,
                           @NonNull String userIdRequest, @NonNull String kitTypeName, String collaboratorParticipantId,
                           String errorMessage, boolean uploadAnyway, List<KitRequest> duplicateKitList, ArrayList<KitRequest> orderKits,
                           String externalOrderNumber, String uploadReason, String carrier) {
        //TODO Pegah do we need duplicate checks? If so, how are we sending the response?
        //TODO Pegah uploadAnyway should override everything
//        if (checkAndSetParticipantIdIfKitExists(ddpInstance, conn, collaboratorParticipantId, kitType.getKitTypeId())
//                && !uploadAnyway) {
//            duplicateKitList.add(kit);
//        } else {
            String shippingId;
            if (StringUtils.isNotBlank(kit.getJuniperKitId()) || ddpInstance.isHasRole()) {// to know this is a Juniper Kit
                shippingId = "JUNIPER_"+kit.getJuniperKitId();
            } else {
                shippingId = DDPKitRequest.UPLOADED_KIT_REQUEST + KitRequestShipping.createRandom(20);
            }
            addKitRequest(conn, kitTypeName, kitRequestSettings, ddpInstance, kitType.getKitTypeId(), collaboratorParticipantId,
                    errorMessage, userIdRequest, easyPostUtil, kit, externalOrderNumber, shippingId, uploadReason, carrier, null);
            orderKits.add(kit);
//        }
    }

    private void addKitRequest(Connection conn, String kitTypeName, KitRequestSettings kitRequestSettings, DDPInstance ddpInstance,
                               int kitTypeId, String collaboratorParticipantId, String errorMessage, String userId,
                               EasyPostUtil easyPostUtil, KitRequest kit, String externalOrderNumber, String shippingId,
                               String uploadReason, String carrier, String ddpLabel) {
        String collaboratorSampleId = null;
        String bspCollaboratorSampleType = kitTypeName;
        String addressId = null;
        try {
            Address address = easyPostUtil.getAddress(((KitUploadObject) kit).getEasyPostAddressId());
            if (address != null) {
                addressId = address.getId();
            }
        } catch (EasyPostException e) {
            throw new RuntimeException("EasyPost addressId could not be received ", e);
        }

        if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            collaboratorSampleId =
                    KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId);
            KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), shippingId, kitTypeId, kit.getParticipantId().trim(),
                    collaboratorParticipantId, collaboratorSampleId, userId, addressId, errorMessage, externalOrderNumber, false,
                    uploadReason, ddpInstance, bspCollaboratorSampleType, ddpLabel);
            kit.setDdpLabel(shippingId);
            kit.setExternalOrderNumber(externalOrderNumber);
        } else {

            if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
            }
            if (StringUtils.isNotBlank(collaboratorParticipantId)) {
                collaboratorSampleId =
                        KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId);
                if (collaboratorParticipantId == null) {
                    errorMessage += "collaboratorParticipantId was too long ";
                }
                if (collaboratorSampleId == null) {
                    errorMessage += "collaboratorSampleId was too long ";
                }
            }

            String participantID = kit.getShortId();

            //If there is a participant change the participantID to the ID of the existing
            //participant

            KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), shippingId, kitTypeId, participantID,
                    collaboratorParticipantId, collaboratorSampleId, userId, addressId, errorMessage, kit.getExternalOrderNumber(), false,
                    uploadReason, ddpInstance, bspCollaboratorSampleType, ddpLabel);
            kit.setDdpLabel(shippingId);
        }
    }
}
