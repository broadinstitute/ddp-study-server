package org.broadinstitute.dsm.route;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.DeliveryAddress;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.exception.FileWrongSeparator;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.externalShipper.ExternalShipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletRequest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class KitUploadRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitUploadRoute.class);

    private NotificationUtil notificationUtil;

    public KitUploadRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    private static final String SQL_SELECT_CHECK_KIT_ALREADY_EXISTS = "SELECT count(*) as found FROM ddp_kit_request request LEFT JOIN ddp_kit kit on (request.dsm_kit_request_id = kit.dsm_kit_request_id) " +
            "LEFT JOIN ddp_participant_exit ex on (ex.ddp_instance_id = request.ddp_instance_id AND ex.ddp_participant_id = request.ddp_participant_id) WHERE ex.ddp_participant_exit_id is null " +
            "AND kit.deactivated_date is null AND request.ddp_instance_id = ? AND request.kit_type_id = ? AND request.ddp_participant_id = ?";

    private static final String PARTICIPANT_ID = "participantId";
    private static final String ORDER_NUMBER = "orderNumber";
    private static final String SHORT_ID = "shortId";
    private static final String SIGNATURE = "signature";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String NAME = "name";
    private static final String STREET1 = "street1";
    private static final String STREET2 = "street2";
    private static final String CITY = "city";
    private static final String STATE = "state";
    private static final String POSTAL_CODE = "postalCode";
    private static final String COUNTRY = "country";
    private static final String PHONE_NUMBER = "phoneNumber";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        else {
            throw new RuntimeException("No realm query param was sent");
        }
        if (UserUtil.checkUserAccess(realm, userId, "kit_upload")) {
            String kitTypeName;
            AtomicReference<String> kitUploadReason = new AtomicReference<>();
            AtomicReference<String> shippingCarrier = new AtomicReference<>();
            if (queryParams.value(RoutePath.KIT_TYPE) != null) {
                kitTypeName = queryParams.get(RoutePath.KIT_TYPE).value();
            }
            else {
                throw new RuntimeException("No kitType query param was sent");
            }
            if (queryParams.value("reason") != null) {
                kitUploadReason.set(queryParams.get("reason").value());
            }
            if (queryParams.value("carrier") != null) {
                shippingCarrier.set(queryParams.get("carrier").value());
            }

            final AtomicBoolean uploadAnyway = new AtomicBoolean(false);
            if (queryParams.value("uploadAnyway") != null) {
                uploadAnyway.set(queryParams.get("uploadAnyway").booleanValue());
            }

            String userIdRequest = UserUtil.getUserId(request);
            if (!userId.equals(userIdRequest)) {
                throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
            }
            HttpServletRequest rawRequest = request.raw();
            String content = SystemUtil.getBody(rawRequest);
            try {
                List<KitRequest> kitUploadContent = null;
                if (uploadAnyway.get()) { //already participants and no file
                    String requestBody = request.body();
                    kitUploadContent = Arrays.asList(new Gson().fromJson(content, KitUploadObject[].class));
                }
                else {
                    kitUploadContent = isFileValid(content);
                }
                final List<KitRequest> kitUploadObjects = kitUploadContent;
                if (kitUploadObjects == null || kitUploadObjects.isEmpty()) {
                    return "Text file was empty or couldn't be parsed to the agreed format";
                }

                DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
                InstanceSettings instanceSettings = InstanceSettings.getInstanceSettings(realm);
                final AtomicReference<Value> upload = new AtomicReference<>();
                String specialMessage = null;
                if (instanceSettings != null && instanceSettings.getKitBehaviorChange() != null) {
                    List<Value> kitBehavior = instanceSettings.getKitBehaviorChange();
                    try {
                        upload.set(kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_UPLOAD)).findFirst().get());
                        if (upload.get() != null) {
                            specialMessage = upload.get().getValue();
                        }
                    }
                    catch (NoSuchElementException e) {
                        upload.set(null);
                    }
                }

                HashMap<String, KitType> kitTypes = KitType.getKitLookup();
                String key = kitTypeName + "_" + ddpInstance.getDdpInstanceId();
                KitType kitType = kitTypes.get(key);
                if (kitType == null) {
                    throw new RuntimeException("KitType unknown");
                }

                Map<Integer, KitRequestSettings> kitRequestSettingsMap = KitRequestSettings.getKitRequestSettings(ddpInstance.getDdpInstanceId());
                KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());
                // if the kit type has sub kits > like for promise
                boolean kitHasSubKits = kitRequestSettings.getHasSubKits() != 0;

                logger.info("Setup EasyPost...");
                EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstance.getName());

                Map<String, KitRequest> invalidAddressList = checkAddress(kitUploadObjects, kitRequestSettings.getPhone());
                List<KitRequest> duplicateKitList = new ArrayList<>();
                List<KitRequest> specialKitList = new ArrayList<>();
                ArrayList<KitRequest> orderKits = new ArrayList<>();

                TransactionWrapper.inTransaction(conn -> {
                    uploadKit(ddpInstance, kitType, kitUploadObjects, kitHasSubKits, kitRequestSettings, easyPostUtil, userIdRequest, kitTypeName,
                            uploadAnyway.get(), invalidAddressList, duplicateKitList, orderKits, specialKitList, upload.get(), kitUploadReason.get(), shippingCarrier.get(), conn);

                    //only order if external shipper name is set for that kit request
                    if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
                        try {
                            logger.info("placing order with external shipper");
                            ExternalShipper shipper = (ExternalShipper) Class.forName(DSMServer.getClassName(kitRequestSettings.getExternalShipper())).newInstance();
                            shipper.orderKitRequests(orderKits, easyPostUtil, kitRequestSettings, shippingCarrier.get());
                            // mark kits as transmitted so that background jobs don't try to double order it
                            for (KitRequest orderKit : orderKits) {
                                KitRequestShipping.markOrderTransmittedAt(conn, orderKit.getExternalOrderNumber(), Instant.now());
                            }
                        }
                        catch (Exception e) {
                            logger.error("Failed to sent kit request order to " + kitRequestSettings.getExternalShipper(), e);
                            response.status(500);
                            return new Result(500, "Failed to sent kit request order to " + kitRequestSettings.getExternalShipper());
                        }
                    }
                    return null;
                });


                //send not valid address back to client
                logger.info(kitUploadObjects.size() + " " + ddpInstance.getName() + " " + kitTypeName + " kit uploaded");
                logger.info(invalidAddressList.size() + " uploaded addresses were not valid and " + duplicateKitList.size() + " are already in DSM");
                logger.info(specialKitList.size() + " kits didn't meet the kit behaviour");
                return new KitUploadResponse(invalidAddressList.values(), duplicateKitList, specialKitList, specialMessage);
            }
            catch (UploadLineException e) {
                return e.getMessage();
            }
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    private void uploadKit(@NonNull DDPInstance ddpInstance, @NonNull KitType kitType, List<KitRequest> kitUploadObjects, boolean kitHasSubKits,
                           @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil, @NonNull String userIdRequest,
                           @NonNull String kitTypeName, boolean uploadAnyway, Map<String, KitRequest> invalidAddressList,
                           List<KitRequest> duplicateKitList, ArrayList<KitRequest> orderKits, List<KitRequest> specialKitList, Value behavior,
                           String uploadReason, String carrier, Connection conn) {

        for (KitRequest kit : kitUploadObjects) {
            String externalOrderNumber = DDPKitRequest.generateExternalOrderNumber();
            if (invalidAddressList.get(kit.getParticipantId()) == null) { //kit is not in the noValid list, so enter into db
                String errorMessage = "";
                String collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                        ddpInstance.getCollaboratorIdPrefix(), kit.getParticipantId(), kit.getShortId(),
                        kitRequestSettings.getCollaboratorParticipantLengthOverwrite());
                //subkits is currently only used by test boston
                if (kitHasSubKits) {
                    List<KitSubKits> subKits = kitRequestSettings.getSubKits();
                    boolean alreadyExists = false;
                    String shippingId = DDPKitRequest.UPLOADED_KIT_REQUEST + KitRequestShipping.createRandom(20);
                    for (int j = 0; j < subKits.size(); j++) {
                        KitSubKits subKit = subKits.get(j);
                        if (j > 0) {
                            shippingId += "_" + j;
                        }
                        //check with ddp_participant_id if participant already has a kit in DSM db
                        if (checkIfKitAlreadyExists(conn, kit.getParticipantId(), ddpInstance.getDdpInstanceId(), subKit.getKitTypeId()) && !uploadAnyway) {
                            alreadyExists = true;
                        }
                        else {
                            for (int i = 0; i < subKit.getKitCount(); i++) {
                                if (i > 0) {
                                    shippingId += "_" + i;
                                }
                                addKitRequest(conn, subKit.getKitName(), kitRequestSettings, ddpInstance, subKit.getKitTypeId(),
                                        collaboratorParticipantId, errorMessage, userIdRequest, easyPostUtil, kit, externalOrderNumber, shippingId, uploadReason, carrier);
                            }
                        }
                    }
                    if (alreadyExists) {
                        duplicateKitList.add(kit);
                    }
                    else {
                        orderKits.add(kit);
                    }
                }
                else {
                    //all cmi ddps are currently using this!
                    handleNormalKit(conn, ddpInstance, kitType, kit, kitRequestSettings, easyPostUtil, userIdRequest, kitTypeName,
                            collaboratorParticipantId, errorMessage, uploadAnyway, duplicateKitList, orderKits, specialKitList, behavior, externalOrderNumber, uploadReason, carrier);
                }
            }
        }
    }

    private void handleNormalKit(@NonNull Connection conn, @NonNull DDPInstance ddpInstance, @NonNull KitType kitType, @NonNull KitRequest kit,
                                 @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil, @NonNull String userIdRequest,
                                 @NonNull String kitTypeName, String collaboratorParticipantId, String errorMessage, boolean uploadAnyway,
                                 List<KitRequest> duplicateKitList, ArrayList<KitRequest> orderKits, List<KitRequest> specialKitList, Value behavior, String externalOrderNumber,
                                 String uploadReason, String carrier) {
        if (behavior != null && StringUtils.isNotBlank(ddpInstance.getParticipantIndexES()) && !uploadAnyway) {
            Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                    ElasticSearchUtil.BY_GUID + kit.getParticipantId());
            Map<String, Object> participant = participants.get(kit.getParticipantId());
            boolean specialKit = InstanceSettings.shouldKitBehaveDifferently(participant, behavior);
            if (specialKit) {
                if (InstanceSettings.TYPE_ALERT.equals(behavior.getType())) {
                    specialKitList.add(kit);
                }
                else if (InstanceSettings.TYPE_NOTIFICATION.equals(behavior.getType())) {
                    String message = "Kit uploaded for participant " + kit.getParticipantId() + ". \n" +
                            behavior.getValue();
                    notificationUtil.sentNotification(ddpInstance.getNotificationRecipient(), message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NotificationUtil.DSM_SUBJECT);
                }
                else {
                    logger.error("Instance settings behavior for kit was not known " + behavior.getType());
                }
            }
            else {
                //check with ddp_participant_id if participant already has a kit in DSM db
                handleKit(conn, ddpInstance, kitType, kit, kitRequestSettings, easyPostUtil, userIdRequest, kitTypeName,
                        collaboratorParticipantId, errorMessage, uploadAnyway, duplicateKitList, orderKits, externalOrderNumber, uploadReason, carrier);
            }
        }
        else {
            handleKit(conn, ddpInstance, kitType, kit, kitRequestSettings, easyPostUtil, userIdRequest, kitTypeName,
                    collaboratorParticipantId, KitUtil.IGNORE_AUTO_DEACTIVATION, uploadAnyway, duplicateKitList, orderKits, externalOrderNumber, uploadReason, carrier);
        }
    }

    private void handleKit(@NonNull Connection conn, @NonNull DDPInstance ddpInstance, @NonNull KitType kitType, @NonNull KitRequest kit,
                           @NonNull KitRequestSettings kitRequestSettings, @NonNull EasyPostUtil easyPostUtil, @NonNull String userIdRequest,
                           @NonNull String kitTypeName, String collaboratorParticipantId, String errorMessage, boolean uploadAnyway,
                           List<KitRequest> duplicateKitList, ArrayList<KitRequest> orderKits, String externalOrderNumber, String uploadReason, String carrier) {
        if (checkIfKitAlreadyExists(conn, kit.getParticipantId(), ddpInstance.getDdpInstanceId(), kitType.getKitTypeId()) && !uploadAnyway) {
            duplicateKitList.add(kit);
        }
        else {
            String shippingId = DDPKitRequest.UPLOADED_KIT_REQUEST + KitRequestShipping.createRandom(20);
            addKitRequest(conn, kitTypeName, kitRequestSettings, ddpInstance, kitType.getKitTypeId(),
                    collaboratorParticipantId, errorMessage, userIdRequest, easyPostUtil, kit, externalOrderNumber, shippingId, uploadReason, carrier);
            orderKits.add(kit);
        }
    }

    private void addKitRequest(Connection conn, String kitTypeName, KitRequestSettings kitRequestSettings, DDPInstance ddpInstance,
                               int kitTypeId, String collaboratorParticipantId, String errorMessage, String userId, EasyPostUtil easyPostUtil,
                               KitRequest kit, String externalOrderNumber, String shippingId, String uploadReason, String carrier) {
        String collaboratorSampleId = null;
        String bspCollaboratorSampleType = kitTypeName;
        String addressId = null;
        try {
            Address address = easyPostUtil.getAddress(((KitUploadObject) kit).getEasyPostAddressId());
            if (address != null) {
                addressId = address.getId();
            }
        }
        catch (EasyPostException e) {
            throw new RuntimeException("EasyPost addressId could not be received ", e);
        }

        if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            collaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId);
            KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), shippingId,
                    kitTypeId, kit.getParticipantId().trim(), collaboratorParticipantId,
                    collaboratorSampleId, userId, addressId,
                    errorMessage, externalOrderNumber, false, uploadReason);
            kit.setShippingId(shippingId);
            kit.setExternalOrderNumber(externalOrderNumber);
        }
        else {

            if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
            }
            if (StringUtils.isNotBlank(collaboratorParticipantId)) {
                collaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitTypeId);
                if (collaboratorParticipantId == null) {
                    errorMessage += "collaboratorParticipantId was too long ";
                }
                if (collaboratorSampleId == null) {
                    errorMessage += "collaboratorSampleId was too long ";
                }
            }
            KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), shippingId,
                    kitTypeId, kit.getParticipantId().trim(), collaboratorParticipantId,
                    collaboratorSampleId, userId, addressId,
                    errorMessage, kit.getExternalOrderNumber(), false, uploadReason);
            kit.setShippingId(shippingId);
        }
    }

    public List<KitRequest> isFileValid(String fileContent) {
        if (fileContent != null) {
            String linebreak = SystemUtil.lineBreak(fileContent);
            String[] rows = fileContent.split(linebreak);
            if (rows.length > 1) {
                String firstRow = rows[0];
                if (firstRow.contains(SystemUtil.SEPARATOR)) {
                    List<String> fieldNames = new ArrayList<>(Arrays.asList(firstRow.trim().split(SystemUtil.SEPARATOR)));
                    String missingFieldName = fieldNameMissing(fieldNames);
                    if (missingFieldName == null) {
                        boolean nameInOneColumn = fieldNames.contains(SIGNATURE);
                        boolean containsOrderNumber = fieldNames.contains(ORDER_NUMBER);
                        List<KitRequest> uploadObjects = new ArrayList<>();
                        for (int rowIndex = 1; rowIndex < rows.length; rowIndex++) {
                            Map<String, String> obj = new LinkedHashMap<>();
                            String[] row = rows[rowIndex].trim().split(SystemUtil.SEPARATOR);
                            if (row.length == fieldNames.size()) {
                                for (int columnIndex = 0; columnIndex < fieldNames.size(); columnIndex++) {
                                    obj.put(fieldNames.get(columnIndex), row[columnIndex]);
                                }
                                try {
                                    String shortId = obj.get(SHORT_ID);
                                    if (StringUtils.isBlank(shortId)) {
                                        shortId = obj.get(PARTICIPANT_ID);
                                    }

                                    KitUploadObject object;
                                    if (nameInOneColumn) {
                                        object = new KitUploadObject(null, obj.get(PARTICIPANT_ID), shortId,
                                                null, obj.get(SIGNATURE),
                                                obj.get(STREET1), obj.get(STREET2), obj.get(CITY),
                                                obj.get(STATE), obj.get(POSTAL_CODE), obj.get(COUNTRY), obj.getOrDefault(PHONE_NUMBER, null));
                                    }
                                    else if (containsOrderNumber) {
                                        object = new KitUploadObject(obj.get(ORDER_NUMBER), obj.get(PARTICIPANT_ID), null,
                                                null, obj.get(NAME),
                                                obj.get(STREET1), obj.get(STREET2), obj.get(CITY),
                                                obj.get(STATE), obj.get(POSTAL_CODE), obj.get(COUNTRY), obj.getOrDefault(PHONE_NUMBER, null));
                                    }
                                    else {
                                        object = new KitUploadObject(null, obj.get(PARTICIPANT_ID), shortId,
                                                obj.get(FIRST_NAME), obj.get(LAST_NAME),
                                                obj.get(STREET1), obj.get(STREET2), obj.get(CITY),
                                                obj.get(STATE), obj.get(POSTAL_CODE), obj.get(COUNTRY), obj.getOrDefault(PHONE_NUMBER, null));
                                    }
                                    uploadObjects.add(object);
                                }
                                catch (Exception e) {
                                    throw new RuntimeException("Text file is not valid. Couldn't be parsed to upload object ", e);
                                }
                            }
                            else {
                                throw new UploadLineException("Expected " + fieldNames.size() + " items in line " + (rowIndex + 1) + " but found " + row.length);
                            }
                        }
                        logger.info(uploadObjects.size() + " participants were uploaded for manual kits ");
                        return uploadObjects;
                    }
                    else {
                        throw new FileColumnMissing("File is missing column " + missingFieldName);
                    }
                }
                else {
                    throw new FileWrongSeparator("Please use tab as separator in the text file");
                }
            }
        }
        return null;
    }

    public Map<String, KitRequest> checkAddress(List<KitRequest> kitUploadObjects, String phone) {
        Map<String, KitRequest> noValidAddress = new HashMap<>();
        for (KitRequest o : kitUploadObjects) {
            KitUploadObject object = (KitUploadObject) o;
            //only if participant has shortId, first- and lastName
            if ((StringUtils.isNotBlank(object.getShortId()) || StringUtils.isNotBlank(object.getExternalOrderNumber())) && StringUtils.isNotBlank(object.getLastName())) {
                //let's validate the participant's address
                String name = "";
                if (StringUtils.isNotBlank(object.getFirstName())) {
                    name += object.getFirstName() + " ";
                }
                name += object.getLastName();
                DeliveryAddress deliveryAddress = new DeliveryAddress(object.getStreet1(), object.getStreet2(),
                        object.getCity(), object.getState(), object.getPostalCode(), object.getCountry(),
                        name, phone);
                deliveryAddress.validate();

                if (deliveryAddress.isValid()) {
                    //store the address back
                    object.setEasyPostAddressId(deliveryAddress.getId());
                }
                else {
                    logger.info("Address is not valid " + object.getParticipantId());
                    noValidAddress.put(object.getParticipantId(), object);
                }
            }
            else {
                noValidAddress.put(object.getParticipantId(), object);
            }
        }
        return noValidAddress;
    }

    public String fieldNameMissing(List<String> fieldName) {
        if (fieldName.contains(ORDER_NUMBER)) {
            //Promise file upload
            if (!fieldName.contains(ORDER_NUMBER)) {
                return ORDER_NUMBER;
            }
            if (!fieldName.contains(NAME)) {
                return NAME;
            }
        }
        else {
            //file upload for other ddps
            if (!fieldName.contains(SIGNATURE)) {
                if (!fieldName.contains(SHORT_ID)) {
                    return SHORT_ID;
                }
                if (!fieldName.contains(FIRST_NAME)) {
                    return FIRST_NAME + " or " + SIGNATURE;
                }
                if (!fieldName.contains(LAST_NAME)) {
                    return LAST_NAME + " or " + SIGNATURE;
                }
            }
        }
        if (!fieldName.contains(PARTICIPANT_ID)) {
            return PARTICIPANT_ID;
        }
        if (!fieldName.contains(STREET1)) {
            return STREET1;
        }
        if (!fieldName.contains(STATE)) {
            return STATE;
        }
        if (!fieldName.contains(CITY)) {
            return CITY;
        }
        if (!fieldName.contains(POSTAL_CODE)) {
            return POSTAL_CODE;
        }
        if (!fieldName.contains(COUNTRY)) {
            return COUNTRY;
        }
        return null;
    }

    public boolean checkIfKitAlreadyExists(@NonNull Connection conn, @NonNull String ddpParticipantId,
                                           @NonNull String instanceId, @NonNull int kitTypeId) {
        SimpleResult dbVals = new SimpleResult(0);
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_CHECK_KIT_ALREADY_EXISTS)) {
            stmt.setString(1, instanceId);
            stmt.setInt(2, kitTypeId);
            stmt.setString(3, ddpParticipantId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    dbVals.resultValue = rs.getInt(DBConstants.FOUND);
                }
            }
        }
        catch (SQLException ex) {
            dbVals.resultException = ex;
        }
        if (dbVals.resultException != null) {
            throw new RuntimeException("Error getting id of new kit request ", dbVals.resultException);
        }
        if (dbVals.resultValue == null) {
            throw new RuntimeException("Error getting id of new kit request ");
        }
        logger.info("Found " + dbVals.resultValue + " kit requests for ddp_participant_id " + ddpParticipantId);
        return (int) dbVals.resultValue > 0;
    }
}
