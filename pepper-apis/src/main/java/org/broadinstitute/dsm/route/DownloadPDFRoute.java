package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.MedicalInfo;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.files.CoverPDFProcessor;
import org.broadinstitute.dsm.files.PDFProcessor;
import org.broadinstitute.dsm.files.RequestPDFProcessor;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.mbc.MBCParticipant;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.*;
import org.broadinstitute.dsm.util.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DownloadPDFRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadPDFRoute.class);

    private static final String SQL_SELECT_REALM_FOR_PARTICIPANT = "SELECT inst.instance_name, inst.base_url, inst.ddp_instance_id, inst.mr_attention_flag_d, " +
            "inst.tissue_attention_flag_d, inst.es_participant_index, inst.es_activity_definition_index, inst.es_users_index, inst.auth0_token, inst.notification_recipients, inst.migrated_ddp, inst.billing_reference, part.ddp_participant_id, (SELECT count(role.name) " +
            "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id " +
            "AND role.name = ? AND realm.ddp_instance_id = inst.ddp_instance_id) as 'has_role' FROM ddp_participant part, ddp_instance inst WHERE inst.ddp_instance_id = part.ddp_instance_id " +
            "AND part.ddp_participant_id = ?";

    public static final String CONSENT_PDF = "/consentpdf";
    public static final String RELEASE_PDF = "/releasepdf";
    public static final String COVER_PDF = "/cover";
    public static final String REQUEST_PDF = "/requestpdf";
    public static final String PDF = "/pdf";

    private static final String JSON_START_DATE = "startDate";
    private static final String JSON_END_DATE = "endDate";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        logger.info(request.url());
        if (request.url().contains(RoutePath.DOWNLOAD_PDF)) {
            String realm = null;
            QueryParamsMap queryParams = request.queryMap();
            if (queryParams.value(RoutePath.REALM) != null) {
                realm = queryParams.get(RoutePath.REALM).value();
            }
            if (UserUtil.checkUserAccess(realm, userId, "pdf_download")) {
                String requestBody = request.body();
                if (StringUtils.isNotBlank(requestBody)) {
                    JSONObject jsonObject = new JSONObject(requestBody);
                    String ddpParticipantId = (String) jsonObject.get(RequestParameter.DDP_PARTICIPANT_ID);
                    String userIdR = (String) jsonObject.get(RequestParameter.USER_ID);
                    String configName = null;
                    if (jsonObject.has(RequestParameter.CONFIG_NAME)) {
                        configName = (String) jsonObject.get(RequestParameter.CONFIG_NAME);
                    }
                    Integer userIdRequest = Integer.parseInt(userIdR);
                    if (!userId.equals(userIdR)) {
                        throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdR);
                    }
                    String medicalRecord = null;
                    List<String> oncHistoryIDs = null;
                    if (StringUtils.isNotBlank(ddpParticipantId)) {
                        logger.info(request.url());
                        String pdfType = null;
                        if (request.url().endsWith(CONSENT_PDF)) {
                            pdfType = CONSENT_PDF;
                        }
                        else if (request.url().endsWith(RELEASE_PDF)) {
                            pdfType = RELEASE_PDF;
                        }
                        else if (request.url().contains(COVER_PDF)) {
                            pdfType = COVER_PDF;
                            medicalRecord = request.params(RequestParameter.MEDICALRECORDID);
                        }
                        else if (request.url().contains(REQUEST_PDF)) {
                            pdfType = REQUEST_PDF;
                            if (queryParams.value("requestId") != null) {
                                oncHistoryIDs = Arrays.asList(queryParams.get("requestId").values());
                            }
                        }
                        else if (request.url().endsWith(PDF) && StringUtils.isNotBlank(configName)) {
                            pdfType = configName;
                        }
                        if (StringUtils.isNotBlank(pdfType)) {
                            getPDFs(response, ddpParticipantId, pdfType, medicalRecord, oncHistoryIDs, realm, requestBody, userIdRequest);
                            return new Result(200);
                        }
                        else {
                            response.status(500);
                            throw new RuntimeException("Error missing pdf type");
                        }
                    }
                    else {
                        response.status(500);
                        throw new RuntimeException("Error missing participantId");
                    }
                }
                else {
                    response.status(500);
                    throw new RuntimeException("Error missing participantId");
                }
            }
            else {
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        else {
            QueryParamsMap queryParams = request.queryMap();
            String realm = null;
            if (queryParams.value(RoutePath.REALM) != null) {
                realm = queryParams.get(RoutePath.REALM).value();
            }
            if (UserUtil.checkUserAccess(realm, userId, "pdf_download")) {
                if (StringUtils.isNotBlank(realm)) {
                    String ddpParticipantId = null;
                    if (queryParams.value(RequestParameter.DDP_PARTICIPANT_ID) != null) {
                        ddpParticipantId = queryParams.get(RequestParameter.DDP_PARTICIPANT_ID).value();
                    }
                    if (StringUtils.isNotBlank(ddpParticipantId)) {
                        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.HAS_MEDICAL_RECORD_INFORMATION_IN_DB);
                        Map<String, Map<String, Object>> participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance,
                                ElasticSearchUtil.BY_GUID + ddpParticipantId);
                        if (participantESData != null && !participantESData.isEmpty()) {
                            return returnPDFS(participantESData, ddpParticipantId);
                        }
                        else {
                            participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, ElasticSearchUtil.BY_LEGACY_ALTPID + ddpParticipantId);
                            return returnPDFS(participantESData, ddpParticipantId);
                        }
                    }
                    else {
                        return getPDFs(realm);
                    }
                }
            }
            else {
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        response.status(500);
        throw new RuntimeException("Something went wrong");
    }

    public static Object returnPDFS(Map<String, Map<String, Object>> participantESData, @NonNull String ddpParticipantId) {
        if (participantESData != null && !participantESData.isEmpty() && participantESData.size() == 1) {
            Map<String, Object> participantData = participantESData.get(ddpParticipantId);
            if (participantData != null) {
                Map<String, Object> dsm = (Map<String, Object>) participantData.get(ElasticSearchUtil.DSM);
                if (dsm != null) {
                    Object pdf = dsm.get(ElasticSearchUtil.PDFS);
                    return pdf;
                }
            }
            else {
                for (Object value : participantESData.values()) {
                    participantData = (Map<String, Object>) value;
                    //check that it is really right participant
                    if (participantData != null) {
                        Map<String, Object> profile = (Map<String, Object>) participantData.get(ElasticSearchUtil.PROFILE);
                        if (profile != null) {
                            String guid = (String) profile.get(ElasticSearchUtil.GUID);
                            if (ddpParticipantId.equals(guid)) {
                                Map<String, Object> dsm = (Map<String, Object>) participantData.get(ElasticSearchUtil.DSM);
                                if (dsm != null) {
                                    Object pdf = dsm.get(ElasticSearchUtil.PDFS);
                                    return pdf;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * get pdf
     * for the given participant and pdfType
     *
     * @throws Exception
     */
    public void getPDFs(@NonNull Response response, @NonNull String ddpParticipantId, @NonNull String pdfType,
                        String medicalRecordId, List<String> oncHistoryIDs, String realm, String requestBody, Integer userId) {
        InstanceWithDDPParticipantId instanceWithDDPParticipantId = getInstanceWithDDPParticipantId(ddpParticipantId);

        if (instanceWithDDPParticipantId != null) {
            DDPInstance ddpInstance = instanceWithDDPParticipantId.getDdpInstance();

            if (ddpInstance != null && StringUtils.isNotBlank(ddpParticipantId)) {
                if (COVER_PDF.equals(pdfType)) {
                    logger.info("Generating cover pdf for onc history {}", StringUtils.join(oncHistoryIDs, ","));
                    JSONObject jsonObject = new JSONObject(requestBody);
                    Set keySet = jsonObject.keySet();
                    String startDate = null;
                    String endDate = null;
                    if (keySet.contains(JSON_START_DATE)) {
                        startDate = (String) jsonObject.get(JSON_START_DATE);
                        if (!"0/0".equals(startDate) && !startDate.contains("/") && startDate.contains("-")) {
                            startDate = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, startDate);
                        }
                        else if (StringUtils.isNotBlank(startDate) && startDate.startsWith("0/") && !startDate.equals("0/0")) {
                            startDate = "01/" + startDate.split("/")[1];
                        }
                    }
                    if (keySet.contains(JSON_END_DATE)) {
                        endDate = (String) jsonObject.get(JSON_END_DATE);
                        endDate = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, endDate);
                    }
                    if (StringUtils.isBlank(medicalRecordId)) {
                        throw new RuntimeException("MedicalRecordID is missing. Can't create cover pdf");
                    }
                    //get information from db
                    MedicalRecord medicalRecord = MedicalRecord.getMedicalRecord(realm, ddpParticipantId, medicalRecordId);

                    PDFProcessor processor = new CoverPDFProcessor(ddpInstance.getName());
                    Map<String, Object> valueMap = new HashMap<>();
                    //values same no matter from where participant/institution data comes from
                    valueMap.put(CoverPDFProcessor.FIELD_CONFIRMED_INSTITUTION_NAME, medicalRecord.getName());
                    valueMap.put(CoverPDFProcessor.FIELD_CONFIRMED_INSTITUTION_NAME_2, medicalRecord.getName() + ",");
                    valueMap.put(CoverPDFProcessor.FIELD_CONFIRMED_FAX, medicalRecord.getFax());
                    String today = new SimpleDateFormat("MM/dd/yyyy").format(new Date());
                    valueMap.put(CoverPDFProcessor.FIELD_DATE, today);
                    valueMap.put(CoverPDFProcessor.FIELD_DATE_2, StringUtils.isNotBlank(endDate) ? endDate : today); //end date

                    //adding checkboxes configured under instance_settings
                    InstanceSettings instanceSettings = InstanceSettings.getInstanceSettings(realm);
                    if (instanceSettings != null && instanceSettings.getMrCoverPdf() != null && !instanceSettings.getMrCoverPdf().isEmpty()) {
                        for (Value mrCoverSetting : instanceSettings.getMrCoverPdf()) {
                            if (keySet.contains(mrCoverSetting.getValue())) {
                                valueMap.put(mrCoverSetting.getValue(), BooleanUtils.toBoolean((Boolean) jsonObject.get(mrCoverSetting.getValue())));
                            }
                        }
                    }

                    if (ddpInstance.isHasRole()) {
                        //get information from ddp db
                        addMBCParticipantDataToValueMap(ddpParticipantId, valueMap, true);
                    }
                    else {
                        //get information from ddp
                        addDDPParticipantDataToValueMap(ddpInstance, ddpParticipantId, valueMap, true);
                    }
                    valueMap.put(CoverPDFProcessor.START_DATE_2, StringUtils.isNotBlank(startDate) ? startDate : valueMap.get(CoverPDFProcessor.FIELD_DATE_OF_DIAGNOSIS)); //start date
                    InputStream stream = null;
                    try {
                        stream = processor.generateStream(valueMap);
                        stream.mark(0);
                        savePDFinBucket(realm, ddpParticipantId, stream, "cover", userId);
                        stream.reset();

                        HttpServletResponse rawResponse = response.raw();
                        rawResponse.getOutputStream().write(IOUtils.toByteArray(stream));
                        rawResponse.setStatus(200);
                        rawResponse.getOutputStream().flush();
                        rawResponse.getOutputStream().close();
                    }
                    catch (IOException e) {
                        throw new RuntimeException("Couldn't get pdf for participant " + ddpParticipantId + " of ddpInstance " + ddpInstance.getName(), e);
                    }
                    finally {
                        try {
                            if (stream != null) {
                                stream.close();
                            }
                        }
                        catch (IOException e) {
                            throw new RuntimeException("Error closing stream", e);
                        }
                    }
                }
                else if (REQUEST_PDF.equals(pdfType)) {
                    logger.info("Generating request pdf for onc history ids {}", StringUtils.join(oncHistoryIDs, ","));
                    RequestPDFProcessor processor = new RequestPDFProcessor(ddpInstance.getName());
                    Map<String, Object> valueMap = new HashMap<>();
                    String today = new SimpleDateFormat("MM/dd/yyyy").format(new Date());
                    valueMap.put(RequestPDFProcessor.FIELD_DATE, today);
                    valueMap.put(RequestPDFProcessor.FIELD_DATE_2, "(" + today + ")");

                    if (ddpInstance.isHasRole()) {
                        //get information from ddp db
                        addMBCParticipantDataToValueMap(ddpParticipantId, valueMap, false);
                    }
                    else {
                        //get information from ddp
                        addDDPParticipantDataToValueMap(ddpInstance, ddpParticipantId, valueMap, false);
                    }
                    InputStream stream = null;
                    try {
                        int counter = 0;
                        if (oncHistoryIDs != null) {
                            for (int i = 0; i < oncHistoryIDs.size(); i++) {
                                OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(oncHistoryIDs.get(i), realm);
                                // facility information is the same in all of the requests so only need to be set ones!
                                if (i == 0) {
                                    valueMap.put(RequestPDFProcessor.FIELD_CONFIRMED_INSTITUTION_NAME, oncHistoryDetail.getFacility());
                                    valueMap.put(RequestPDFProcessor.FIELD_CONFIRMED_PHONE, oncHistoryDetail.getFPhone());
                                    valueMap.put(RequestPDFProcessor.FIELD_CONFIRMED_FAX, oncHistoryDetail.getFFax());
                                }
                                valueMap.put(RequestPDFProcessor.FIELD_DATE_PX + i, oncHistoryDetail.getDatePX());
                                valueMap.put(RequestPDFProcessor.FIELD_TYPE_LOCATION + i, oncHistoryDetail.getTypePX());
                                valueMap.put(RequestPDFProcessor.FIELD_ACCESSION_NUMBER + i, oncHistoryDetail.getAccessionNumber());
                                counter = i;
                            }
                        }
                        valueMap.put(RequestPDFProcessor.BLOCK_COUNTER, counter + 1);

                        stream = processor.generateStream(valueMap);
                        stream.mark(0);
                        savePDFinBucket(realm, ddpParticipantId, stream, "tissue", userId);
                        stream.reset();

                        HttpServletResponse rawResponse = response.raw();
                        rawResponse.getOutputStream().write(IOUtils.toByteArray(stream));
                        rawResponse.setStatus(200);
                        rawResponse.getOutputStream().flush();
                        rawResponse.getOutputStream().close();
                    }
                    catch (IOException e) {
                        throw new RuntimeException("Couldn't get pdf for participant " + ddpParticipantId + " of ddpInstance " + ddpInstance.getName(), e);
                    }
                    finally {
                        try {
                            if (stream != null) {
                                stream.close();
                            }
                        }
                        catch (IOException e) {
                            throw new RuntimeException("Error closing stream", e);
                        }
                    }
                }
                else {
                    requestPDFfromDDP(ddpInstance, ddpParticipantId, pdfType, response, userId);
                }
            }
            else {
                throw new RuntimeException("DDPInstance of participant " + ddpParticipantId + " not found");
            }
        }
        else if (StringUtils.isNotBlank(realm)) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
            requestPDFfromDDP(ddpInstance, ddpParticipantId, pdfType, response, userId);
        }
    }

    private void requestPDFfromDDP(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId, @NonNull String pdfType,
                                   @NonNull Response response, Integer userId) {
        String dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANTS_PATH + "/" + ddpParticipantId + pdfType;
        if (!CONSENT_PDF.equals(pdfType) && !RELEASE_PDF.equals(pdfType)) {
            dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANTS_PATH + "/" + ddpParticipantId + "/pdfs/" + pdfType;
        }
        logger.info("Requesting pdf for participant  " + dsmRequest);
        try {
            byte[] bytes = DDPRequestUtil.getPDFByteArray(dsmRequest, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
            if (bytes != null) {
                HttpServletResponse rawResponse = response.raw();
                String fileName = "consent";
                if (RELEASE_PDF.equals(pdfType)) {
                    fileName = "release";
                }
                savePDFinBucket(ddpInstance.getName(), ddpParticipantId, new ByteArrayInputStream(bytes), fileName, userId);

                rawResponse.getOutputStream().write(bytes);
                rawResponse.setStatus(200);
                rawResponse.getOutputStream().flush();
                rawResponse.getOutputStream().close();
            }
            else {
                throw new RuntimeException("Got null back for " + ddpParticipantId + " of ddpInstance " + ddpInstance.getName());
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't get pdf for participant " + ddpParticipantId + " of ddpInstance " + ddpInstance.getName(), e);
        }
    }

    private InstanceWithDDPParticipantId getInstanceWithDDPParticipantId(@NonNull String ddpParticipantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_REALM_FOR_PARTICIPANT)) {
                stmt.setString(1, DBConstants.HAS_MEDICAL_RECORD_INFORMATION_IN_DB);
                stmt.setString(2, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String notificationRecipient = rs.getString(DBConstants.NOTIFICATION_RECIPIENT);
                        List<String> recipients = null;
                        if (StringUtils.isNotBlank(notificationRecipient)) {
                            notificationRecipient = notificationRecipient.replaceAll("\\s", "");
                            recipients = Arrays.asList(notificationRecipient.split(","));
                        }
                        dbVals.resultValue = new InstanceWithDDPParticipantId(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                new DDPInstance(rs.getString(DBConstants.DDP_INSTANCE_ID),
                                        rs.getString(DBConstants.INSTANCE_NAME),
                                        rs.getString(DBConstants.BASE_URL), null,
                                        rs.getBoolean(DBConstants.HAS_ROLE),
                                        rs.getInt(DBConstants.DAYS_MR_ATTENTION_NEEDED),
                                        rs.getInt(DBConstants.DAYS_TISSUE_ATTENTION_NEEDED),
                                        rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN),
                                        recipients, rs.getBoolean(DBConstants.MIGRATED_DDP),
                                        rs.getString(DBConstants.BILLING_REFERENCE),
                                        rs.getString(DBConstants.ES_PARTICIPANT_INDEX),
                                        rs.getString(DBConstants.ES_ACTIVITY_DEFINITION_INDEX),
                                        rs.getString(DBConstants.ES_USERS_INDEX)));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting instance data for participant " + ddpParticipantId, results.resultException);
        }

        return (InstanceWithDDPParticipantId) results.resultValue;
    }

    private MedicalInfo getMedicalInfo(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId) {
        //get medical record information from ddp
        MedicalInfo medicalInfo = null;
        String dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_INSTITUTION_PATH.replace(RequestParameter.PARTICIPANTID, ddpParticipantId);
        try {
            medicalInfo = DDPRequestUtil.getResponseObject(MedicalInfo.class, dsmRequest, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
            if (medicalInfo == null) {
                throw new RuntimeException("Couldn't get participant information " + ddpInstance.getName());
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't get participants and institutions for ddpInstance " + ddpInstance.getName(), e);
        }
        return medicalInfo;
    }

    private void addDDPParticipantDataToValueMap(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId,
                                                 @NonNull Map<String, Object> valueMap, boolean addDateOfDiagnosis) {

        DDPParticipant ddpParticipant = null;
        MedicalInfo medicalInfo = null;
        String dob = null;
        if (StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
            Map<String, Map<String, Object>> participantsESData = ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES());
            ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, ddpParticipantId);
            medicalInfo = ElasticSearchUtil.getParticipantAsMedicalInfo(participantsESData, ddpParticipantId);
            dob = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, medicalInfo.getDob());
        }
        else {
            //DDP requested pt
            ddpParticipant = DDPParticipant.getDDPParticipant(ddpInstance.getBaseUrl(), ddpInstance.getName(),
                    ddpParticipantId, ddpInstance.isHasAuth0Token());
            medicalInfo = getMedicalInfo(ddpInstance, ddpParticipantId);
            dob = SystemUtil.changeDateFormat(SystemUtil.DDP_DATE_FORMAT, SystemUtil.US_DATE_FORMAT, medicalInfo.getDob());
        }

        //fill fields of pdf (needs to match the fields in template!)
        valueMap.put(CoverPDFProcessor.FIELD_FULL_NAME, ddpParticipant.getFirstName() + " " + ddpParticipant.getLastName());
        valueMap.put(CoverPDFProcessor.FIELD_DATE_OF_BIRTH, dob);
        if (addDateOfDiagnosis) {
            String dod = medicalInfo.getDateOfDiagnosis();
            if (StringUtils.isNotBlank(dod) && dod.startsWith("0/") && !dod.equals("0/0")) {
                dod = "01/" + dod.split("/")[1];
                valueMap.put(CoverPDFProcessor.FIELD_DATE_OF_DIAGNOSIS, dod);
            }
            else {
                valueMap.put(CoverPDFProcessor.FIELD_DATE_OF_DIAGNOSIS, medicalInfo.getDateOfDiagnosis());
            }
        }
    }

    private void addMBCParticipantDataToValueMap(@NonNull String ddpParticipantId,
                                                 @NonNull Map<String, Object> valueMap, boolean addDateOfDiagnosis) {

        MBCParticipant mbcParticipant = DSMServer.getMbcParticipants().get(ddpParticipantId);
        if (mbcParticipant != null) {
            String fullName = mbcParticipant.getFirstName() + " " +
                    mbcParticipant.getLastName();

            //fill fields of pdf (needs to match the fields in template!)
            valueMap.put(CoverPDFProcessor.FIELD_FULL_NAME, fullName);
            String dob = "";
            String consentDOB = mbcParticipant.getDOBConsent();
            String bloodDOB = mbcParticipant.getDOBBlood();
            if (StringUtils.isNotBlank(consentDOB)) {
                dob = consentDOB;
            }
            else if (StringUtils.isNotBlank(bloodDOB)) {
                dob = bloodDOB;
            }
            dob = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, dob);
            valueMap.put(CoverPDFProcessor.FIELD_DATE_OF_BIRTH, dob);

            if (addDateOfDiagnosis) {
                String diagnosed = "0/0";
                String diagnosedYear = mbcParticipant.getDiagnosedYear();
                String diagnosedMonth = mbcParticipant.getDiagnosedMonth();
                if (StringUtils.isNotBlank(diagnosedYear)) {
                    if (StringUtils.isNotBlank(diagnosedMonth)) {
                        diagnosed = diagnosedMonth + "/" + diagnosedYear;
                    }
                    else {
                        diagnosed = diagnosedYear;
                    }
                }
                valueMap.put(CoverPDFProcessor.FIELD_DATE_OF_DIAGNOSIS, diagnosed);
            }
        }

    }

    public class InstanceWithDDPParticipantId {
        private String ddpParticipantId;
        private DDPInstance ddpInstance;

        public InstanceWithDDPParticipantId(String ddpParticipantId, DDPInstance ddpInstance) {
            this.ddpParticipantId = ddpParticipantId;
            this.ddpInstance = ddpInstance;
        }

        public DDPInstance getDdpInstance() {
            return ddpInstance;
        }

        public String getDdpParticipantId() {
            return ddpParticipantId;
        }
    }

    public List<String> getPDFs(@NonNull String realm) {
        List<String> listOfPDFs = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_ROLES_LIKE);
            query = query.replace("%1", DBConstants.PDF_DOWNLOAD);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        listOfPDFs.add(rs.getString(DBConstants.NAME));
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting pdfs for " + realm, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get pdfs for realm " + realm, results.resultException);
        }
        logger.info("Found " + listOfPDFs.size() + " pdfs for realm " + realm);
        return listOfPDFs;
    }

    private void savePDFinBucket(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull InputStream stream, @NonNull String fileType, @NonNull Integer userId) {
        String gcpName = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
        if (StringUtils.isNotBlank(gcpName)) {
            String bucketName = gcpName + "_dsm_" + realm.toLowerCase();
            try {
                if (GoogleBucket.bucketExists(null, gcpName, bucketName)) {
                    long time = System.currentTimeMillis();
                    GoogleBucket.uploadFile(null, gcpName,
                            bucketName, ddpParticipantId + "/readonly/" + ddpParticipantId + "_" + fileType + "_" + userId + "_download_" + time + ".pdf",
                            stream);
                }
            }
            catch (Exception e) {
                logger.error("Failed to check for GCP bucket " + bucketName, e);
            }
        }
    }
}
