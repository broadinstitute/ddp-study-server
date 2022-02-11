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
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.FileWrongSeparator;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class KitUploadRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitUploadRoute.class);

    private NotificationUtil notificationUtil;
    private ElasticSearch elasticSearch;

    public KitUploadRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
        this.elasticSearch = new ElasticSearch();
    }

    private static final String SQL_SELECT_CHECK_KIT_ALREADY_EXISTS = "SELECT count(*) as found FROM ddp_kit_request request LEFT JOIN ddp_kit kit on (request.dsm_kit_request_id = kit.dsm_kit_request_id) " +
            "LEFT JOIN ddp_participant_exit ex on (ex.ddp_instance_id = request.ddp_instance_id AND ex.ddp_participant_id = request.ddp_participant_id) WHERE ex.ddp_participant_exit_id is null " +
            "AND kit.deactivated_date is null AND request.ddp_instance_id = ? AND request.kit_type_id = ? AND request.ddp_participant_id = ?";

    private static final String PARTICIPANT_ID = "participantId";
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
        String userIdRequest = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(realm, userId, "kit_upload", userIdRequest)) {
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

            AtomicBoolean uploadAnyway = new AtomicBoolean(false);
            if (queryParams.value("uploadAnyway") != null) {
                uploadAnyway.set(queryParams.get("uploadAnyway").booleanValue());
            }

            HttpServletRequest rawRequest = request.raw();
            String content = SystemUtil.getBody(rawRequest);
            try {
                List<KitRequest> kitUploadContent = null;
                if (uploadAnyway.get()) { //already participants and no file
                    kitUploadContent = Arrays.asList(new Gson().fromJson(content, KitUploadObject[].class));
                }
                else {
                    try {
                        kitUploadContent = isFileValid(content, realm);
                    }
                    catch (Exception e) {
                        return new Result(500, e.getMessage());
                    }
                }
                final List<KitRequest> kitUploadObjects = kitUploadContent;
                if (kitUploadObjects == null || kitUploadObjects.isEmpty()) {
                    return "Text file was empty or couldn't be parsed to the agreed format";
                }

                DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
                InstanceSettings instanceSettings = new InstanceSettings();
                InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(realm);
                StringBuilder specialMessage = new StringBuilder();
                Value upload = instanceSettingsDto.
                        getKitBehaviorChange()
                        .map(kitBehavior -> {
                            Optional<Value> maybeKitBehaviorValue =
                                    kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_UPLOAD))
                                            .findFirst();
                            maybeKitBehaviorValue.ifPresent(value -> specialMessage.append(value.getValue()));
                            return maybeKitBehaviorValue.orElse(null);
                        })
                        .orElse(null);

                HashMap<String, KitType> kitTypes = KitType.getKitLookup();
                String key = kitTypeName + "_" + ddpInstance.getDdpInstanceId();
                KitType kitType = kitTypes.get(key);
                if (kitType == null) {
                    throw new RuntimeException("KitType unknown");
                }

                Map<Integer, KitRequestSettings> kitRequestSettingsMap = KitRequestSettings.getKitRequestSettings(ddpInstance.getDdpInstanceId());
                KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());
                // if the kit type has sub kits > like for testBoston
                boolean kitHasSubKits = kitRequestSettings.getHasSubKits() != 0;

                logger.info("Setup EasyPost...");
                EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstance.getName());

                Map<String, KitRequest> invalidAddressList = checkAddress(kitUploadObjects, kitRequestSettings.getPhone());
                List<KitRequest> duplicateKitList = new ArrayList<>();
                List<KitRequest> specialKitList = new ArrayList<>();
                ArrayList<KitRequest> orderKits = new ArrayList<>();

                TransactionWrapper.inTransaction(conn -> {
                    uploadKit(ddpInstance, kitType, kitUploadObjects, kitHasSubKits, kitRequestSettings, easyPostUtil, userIdRequest, kitTypeName,
                            uploadAnyway.get(), invalidAddressList, duplicateKitList, orderKits, specialKitList, upload, kitUploadReason.get(), shippingCarrier.get(), conn);

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
                return new KitUploadResponse(invalidAddressList.values(), duplicateKitList, specialKitList, specialMessage.toString());
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
                           List<KitRequest> duplicateKitList, ArrayList<KitRequest> orderKits, List<KitRequest> specialKitList, Value behavior, String uploadReason, String carrier, Connection conn) {

        for (KitRequest kit : kitUploadObjects) {
            String externalOrderNumber = DDPKitRequest.generateExternalOrderNumber();
            if (invalidAddressList.get(kit.getShortId()) == null) { //kit is not in the noValid list, so enter into db
                String errorMessage = "";
                String participantGuid = "";
                String participantLegacyAltPid = "";
                //if kit has ddpParticipantId use that (RGP!)
                if (StringUtils.isBlank(kit.getParticipantId())) {
                    ElasticSearchParticipantDto participantByShortId =
                            elasticSearch.getParticipantById(ddpInstance.getParticipantIndexES(), kit.getShortId());
                    participantGuid = participantByShortId.getProfile().map(ESProfile::getGuid).orElse("");
                    participantLegacyAltPid = participantByShortId.getProfile().map(ESProfile::getLegacyAltPid).orElse("");
                    kit.setParticipantId(!participantGuid.isEmpty() ? participantGuid : participantLegacyAltPid);
                }
                else {
                    participantGuid = kit.getParticipantId();
                }
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
                        boolean isKitExsist = checkAndSetParticipantIdIfKitExists(ddpInstance, conn, kit, participantGuid, participantLegacyAltPid, subKit.getKitTypeId());

                        if (isKitExsist && !uploadAnyway) {
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

    private boolean checkAndSetParticipantIdIfKitExists(DDPInstance ddpInstance, Connection conn, KitRequest kit, String participantGuid,
                                                        String participantLegacyAltPid, int kitTypeId) {
        boolean isKitExsist = false;
        if (checkIfKitAlreadyExists(conn, participantGuid, ddpInstance.getDdpInstanceId(), kitTypeId)) {
            isKitExsist = true;
            kit.setParticipantId(participantGuid);
        }
        else if (checkIfKitAlreadyExists(conn, participantLegacyAltPid, ddpInstance.getDdpInstanceId(), kitTypeId)) {
            isKitExsist = true;
            kit.setParticipantId(participantLegacyAltPid);
        }
        return isKitExsist;
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
        if (StringUtils.isBlank(ddpInstance.getParticipantIndexES())) {//bringing old code back for RGP (can be removed after migration is finished)
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
        else {
            String participantGuid = elasticSearch.getParticipantById(ddpInstance.getParticipantIndexES(), kit.getShortId()).getProfile().map(ESProfile::getGuid).orElse("");
            String participantLegacyAltPid = elasticSearch.getParticipantById(ddpInstance.getParticipantIndexES(), kit.getShortId()).getProfile().map(ESProfile::getLegacyAltPid).orElse("");
            if (checkAndSetParticipantIdIfKitExists(ddpInstance, conn, kit, participantGuid, participantLegacyAltPid, kitType.getKitTypeId()) && !uploadAnyway) {
                duplicateKitList.add(kit);
            }
            else {
                String shippingId = DDPKitRequest.UPLOADED_KIT_REQUEST + KitRequestShipping.createRandom(20);
                addKitRequest(conn, kitTypeName, kitRequestSettings, ddpInstance, kitType.getKitTypeId(),
                        collaboratorParticipantId, errorMessage, userIdRequest, easyPostUtil, kit, externalOrderNumber, shippingId, uploadReason, carrier);
                orderKits.add(kit);
            }
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
                    errorMessage, externalOrderNumber, false, uploadReason, ddpInstance);
            kit.setDdpLabel(shippingId);
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
                    errorMessage, kit.getExternalOrderNumber(), false, uploadReason, ddpInstance);
            kit.setDdpLabel(shippingId);
        }
    }

    public List<KitRequest> isFileValid(String fileContent, String realm) {
        if (fileContent == null) {
            throw new RuntimeException("File is empty");
        }

        String[] rows = fileContent.split(System.lineSeparator());
        if (rows.length < 2) {
            throw new RuntimeException("Text file does not contain enough information");
        }

        String firstRow = rows[0];
        if (!firstRow.contains(SystemUtil.SEPARATOR)) {
            throw new FileWrongSeparator("Please use tab as separator in the text file");
        }

        List<String> fieldNamesFromFileHeader = Arrays.asList(firstRow.trim().split(SystemUtil.SEPARATOR));
        String missingHeader = getMissingHeader(fieldNamesFromFileHeader);
        if (missingHeader != null) {
            throw new FileColumnMissing("File is missing column " + missingHeader);
        }

        List<KitRequest> kitRequestsToUpload = new ArrayList<>();
        parseParticipantDataToUpload(realm, rows, fieldNamesFromFileHeader, kitRequestsToUpload);
        logger.info(kitRequestsToUpload.size() + " participants were uploaded for manual kits ");

        return kitRequestsToUpload;
    }

    private void parseParticipantDataToUpload(String realm, String[] rows, List<String> fieldNamesFromHeader,
                                              List<KitRequest> kitRequestsToUpload) {

        boolean nameInOneColumn = fieldNamesFromHeader.contains(SIGNATURE);
        DDPInstance ddpInstanceByRealm = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.HAS_KIT_REQUEST_ENDPOINTS);

        int lastNonEmptyRowIndex = getLastNonEmptyRowIndex(rows);

        for (int rowIndex = 1; rowIndex <= lastNonEmptyRowIndex; rowIndex++) {

            Map<String, String> participantDataByFieldName = getParticipantDataAsMap(rows[rowIndex], fieldNamesFromHeader, rowIndex);

            String shortId = participantDataByFieldName.get(SHORT_ID);

            if (ddpInstanceByRealm.isHasRole()) { //only check if participant shortId exists if ddp has kit request endpoints (not RGP!)
                String message = null;
                try {
                    message = userExistsInRealm(ddpInstanceByRealm, participantDataByFieldName);
                }
                catch (Exception e) {
                    message = "Participant does not belong to this study";
                }
                if (StringUtils.isNotBlank(message)) {
                    throw new RuntimeException("user with shortId: " + shortId + " had a problem: \n" + message);
                }
            }

            KitUploadObject participantKitToUpload;
            if (nameInOneColumn) {
                participantKitToUpload = new KitUploadObject(null, participantDataByFieldName.get(PARTICIPANT_ID), shortId,
                        null, participantDataByFieldName.get(SIGNATURE),
                        participantDataByFieldName.get(STREET1), participantDataByFieldName.get(STREET2), participantDataByFieldName.get(CITY),
                        participantDataByFieldName.get(STATE), participantDataByFieldName.get(POSTAL_CODE), participantDataByFieldName.get(COUNTRY), participantDataByFieldName.getOrDefault(PHONE_NUMBER, null));
            }
            else {
                participantKitToUpload = new KitUploadObject(null, participantDataByFieldName.get(PARTICIPANT_ID), shortId,
                        participantDataByFieldName.get(FIRST_NAME), participantDataByFieldName.get(LAST_NAME),
                        participantDataByFieldName.get(STREET1), participantDataByFieldName.get(STREET2), participantDataByFieldName.get(CITY),
                        participantDataByFieldName.get(STATE), participantDataByFieldName.get(POSTAL_CODE), participantDataByFieldName.get(COUNTRY), participantDataByFieldName.getOrDefault(PHONE_NUMBER, null));
            }
            kitRequestsToUpload.add(participantKitToUpload);

        }
    }

    Map<String, String> getParticipantDataAsMap(String row, List<String> fieldNamesFromHeader, int rowIndex) {
        Map<String, String> participantDataByFieldName = new LinkedHashMap<>();
        String[] rowItems = row.trim().split(SystemUtil.SEPARATOR);
        if (rowItems.length != fieldNamesFromHeader.size()) {
            throw new UploadLineException("Error in line " + (rowIndex + 1));
        }

        for (int columnIndex = 0; columnIndex < fieldNamesFromHeader.size(); columnIndex++) {
            participantDataByFieldName.put(fieldNamesFromHeader.get(columnIndex), rowItems[columnIndex]);
        }
        return participantDataByFieldName;
    }

    private int getLastNonEmptyRowIndex(String[] rows) {

        int lastNonEmptyRowIndex = rows.length - 1;

        for (int i = rows.length - 1; i > 0; i--) {
            String[] row = rows[i].trim().split(SystemUtil.SEPARATOR);
            if (!"".equals(String.join("", row))) {
                lastNonEmptyRowIndex = i;
                break;
            }
        }

        return lastNonEmptyRowIndex;
    }

    private String userExistsInRealm(DDPInstance ddpInstanceByRealm,
                                     Map<String, String> participantDataByFieldName) throws Exception {
        String participantIdFromDoc = participantDataByFieldName.get(SHORT_ID).trim();
        String participantFirstNameFromDoc = participantDataByFieldName.get(FIRST_NAME).trim().toLowerCase();
        String participantLastNameFromDoc = participantDataByFieldName.get(LAST_NAME).trim().toLowerCase();

        ElasticSearchParticipantDto participantByShortId;
        try {
            participantByShortId = elasticSearch.getParticipantById(ddpInstanceByRealm.getParticipantIndexES(), participantIdFromDoc);
        } catch (Exception e) {
            throw new RuntimeException("Participant " + participantIdFromDoc + " does not belong to this study", e);
        }
        return checkKitUploadNameMatchesToEsName(participantFirstNameFromDoc, participantLastNameFromDoc, participantByShortId);
    }

    String checkKitUploadNameMatchesToEsName(String participantFirstNameFromDoc, String participantLastNameFromDoc,
                                             ElasticSearchParticipantDto maybeParticipant) {

        Map<String, String> participantProfile = new HashMap<>();
        String firstName = maybeParticipant.getProfile().map(ESProfile::getFirstName).orElse("");
        String lastName = maybeParticipant.getProfile().map(ESProfile::getLastName).orElse("");
        participantProfile.put("firstName", firstName);
        participantProfile.put("lastName", lastName);
        String message = "";

        if (!participantFirstNameFromDoc.equalsIgnoreCase(participantProfile.get("firstName"))) {
            message += "First names ";
        }

        if (!participantLastNameFromDoc.equalsIgnoreCase(participantProfile.get("lastName"))) {
            if (StringUtils.isNotBlank(message)) {
                message += "and ";
            }
            message += " Last names ";
        }
        if (StringUtils.isNotBlank(message)) {
            message += "in kit Upload don't match the participant";
            logger.error(message);
        }
        return message;
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
                    logger.info("Address is not valid " + object.getShortId());
                    noValidAddress.put(object.getShortId(), object);
                }
            }
            else {
                noValidAddress.put(object.getShortId(), object);
            }
        }
        return noValidAddress;
    }

    public String getMissingHeader(List<String> fieldName) {
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
