package org.broadinstitute.dsm.model.PDF;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.FileProcessingException;
import org.broadinstitute.ddp.handlers.util.MedicalInfo;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.files.CoverPDFProcessor;
import org.broadinstitute.dsm.files.PDFProcessor;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.ddp.PDF;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

@Getter
public class DownloadPDF {
    private static final String COVER = "cover";
    private static final String TISSUE = "tissue";
    private static final String REQUEST = "request";
    private static final String IRB = "irb";
    private static final String JSON_START_DATE = "startDate";
    private static final String JSON_END_DATE = "endDate";

    private String ddpParticipantId;
    private String configName;
    private String medicalRecordId;
    private List<PDF> pdfs;
    List<String> oncHistoryIDs;

    Logger logger = LoggerFactory.getLogger(DownloadPDF.class);

    public DownloadPDF(@NonNull String requestBody) {
        JSONObject jsonObject = new JSONObject(requestBody);

        this.ddpParticipantId = (String) jsonObject.get(RequestParameter.DDP_PARTICIPANT_ID);
        this.configName = null;
        if (jsonObject.has(RequestParameter.CONFIG_NAME)) {
            configName = (String) jsonObject.get(RequestParameter.CONFIG_NAME);
        }
        this.medicalRecordId = null;
        if (jsonObject.has("medicalRecordId")) {
            this.medicalRecordId = (String) jsonObject.get("medicalRecordId");
        }
        if (jsonObject.has("requestId")) {
            this.oncHistoryIDs = new ArrayList<>();
            JSONArray array = (JSONArray) jsonObject.get("requestId");
            for (int i = 0; i < array.length(); i++) {
                this.oncHistoryIDs.add((String) array.get(i));
            }
        }
        if (jsonObject.has("pdfs")) {
            this.pdfs = Arrays.asList(new Gson().fromJson(jsonObject.getString("pdfs"), PDF[].class));
        }
    }


    public Optional<byte[]> getPDFs(long userIdRequest, String realm, String requestBody) {
        UserDto user = new UserDao().get(userIdRequest ).orElseThrow();
        Optional<byte[]> pdfBytes = Optional.empty();
        if (StringUtils.isNotBlank(this.ddpParticipantId)) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
            if (ddpInstance != null && StringUtils.isNotBlank(ddpParticipantId)) {
                String fileName = "";
                if (configName == null) {
                    pdfBytes = getPDFBundle(ddpInstance, requestBody, user);
                }
                else {
                    pdfBytes = generateSinglePDF(requestBody, configName, user, ddpInstance);
                }
                pdfBytes.ifPresent(bytes -> {
                    savePDFinBucket(ddpInstance.getName(), ddpParticipantId, new ByteArrayInputStream(bytes), fileName, user.getId());
                });
                pdfBytes.orElseThrow();
            }
            else {
                throw new RuntimeException("DDPInstance of participant " + ddpParticipantId + " not found");
            }
        }
        return pdfBytes;
    }

    private Optional<byte[]> generateSinglePDF(@NonNull String requestBody, String configName, UserDto user, DDPInstance ddpInstance) {
        byte[] pdfByte = null;
        if (COVER.equals(configName)) {
            pdfByte = new MRCoverPDF(this).getMRCoverPDF(requestBody, ddpInstance, user);
        }
        else if (IRB.equals(configName)) {
            String groupId = DDPInstance.getDDPGroupId(ddpInstance.getName());
            pdfByte = PDFProcessor.getTemplateFromGoogleBucket(groupId + "_IRB_Letter.pdf");
        }
        else if (TISSUE.equals(configName) || REQUEST.equals(configName)) {
            TissueCoverPDF tissueCoverPDF = new TissueCoverPDF(this);
            pdfByte = tissueCoverPDF.getTissueCoverPDF(ddpInstance, user);
        }
        else if (configName != null) {
            pdfByte = requestPDF(ddpInstance, ddpParticipantId, configName);
        }
        return Optional.ofNullable(pdfByte);
    }

    private Optional<byte[]> getPDFBundle(DDPInstance ddpInstance, String requestBody, UserDto user) {
        if (ddpInstance != null && StringUtils.isNotBlank(ddpParticipantId)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                PDFMergerUtility pdfMerger = new PDFMergerUtility();
                pdfMerger.setDestinationStream(output);
                pdfs.sort(Comparator.comparing(org.broadinstitute.dsm.model.ddp.PDF::getOrder));

                //make cover pdf first
                if (pdfs != null && pdfs.size() > 0) {
                    pdfs.forEach(pdf -> {
                                Optional<byte[]> pdfByte;
                                if (pdf.getOrder() > 0) {
                                    pdfByte = generateSinglePDF(requestBody, pdf.getConfigName(), user, ddpInstance);
                                    pdfByte.ifPresent(bytes -> {
                                        pdfMerger.addSource(new ByteArrayInputStream(bytes));
                                    });
                                }
                            }
                    );
                }
                pdfMerger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
                //todo get page count and add them to the cover/request pdf
            }
            catch (IOException e) {
                throw new FileProcessingException("Unable to merge documents ", e);
            }
            return Optional.ofNullable(output.toByteArray());
        }
        return Optional.empty();
    }

    protected byte[] generatePDFFromValues(Map<String, Object> valueMap, DDPInstance ddpInstance, PDFProcessor processor) {
        try (InputStream stream = processor.generateStream(valueMap)) {
            stream.mark(0);
            return IOUtils.toByteArray(stream);
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't get pdf for participant " + ddpParticipantId + " of ddpInstance " + ddpInstance.getName(), e);
        }
    }

    public void addDDPParticipantDataToValueMap(@NonNull DDPInstance ddpInstance, @NonNull Map<String, Object> valueMap, boolean addDateOfDiagnosis, String ddpParticipantId) {
        DDPParticipant ddpParticipant = null;
        MedicalInfo medicalInfo = null;
        String dob = null;
        if (StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
            Map<String, Map<String, Object>> participantsESData = ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES());
            ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, ddpParticipantId);
            medicalInfo = ElasticSearchUtil.getParticipantAsMedicalInfo(participantsESData, ddpParticipantId);
            dob = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, medicalInfo.getDob());
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

    private void savePDFinBucket(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull InputStream stream, @NonNull String fileType, @NonNull Integer userId) {
        String gcpName = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
        if (StringUtils.isNotBlank(gcpName)) {
            String bucketName = gcpName + "_dsm_" + realm.toLowerCase();
            try {
                String credentials = null;
                if (TransactionWrapper.hasConfigPath(ApplicationConfigConstants.GOOGLE_CREDENTIALS)) {
                    String tmp = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_CREDENTIALS);
                    if (StringUtils.isNotBlank(tmp) && new File(tmp).exists()) {
                        credentials = tmp;
                    }
                }
                if (GoogleBucket.bucketExists(credentials, gcpName, bucketName)) {
                    long time = System.currentTimeMillis();
                    GoogleBucket.uploadFile(credentials, gcpName, bucketName,
                            ddpParticipantId + "/readonly/" + ddpParticipantId + "_" + fileType + "_" + userId + "_download_" + time + ".pdf", stream);
                }
            }
            catch (Exception e) {
                logger.error("Failed to check for GCP bucket " + bucketName, e);
            }
        }
    }

    private byte[] requestPDF(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId, @NonNull String pdfType) {
        String dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANTS_PATH + "/" + ddpParticipantId + "/pdfs/" + pdfType;
        logger.info("Requesting pdf for participant  " + dsmRequest);
        try {
            return DDPRequestUtil.getPDFByteArray(dsmRequest, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't get pdf for participant " + ddpParticipantId + " of ddpInstance " + ddpInstance.getName(), e);
        }
    }

}
