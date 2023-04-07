package org.broadinstitute.dsm.model.pdf;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.files.CoverPDFProcessor;
import org.broadinstitute.dsm.files.PDFProcessor;
import org.broadinstitute.dsm.files.RequestPDFProcessor;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;

@Slf4j
public class MRCoverPDF {
    private final String jsonStartDate = "startDate";
    private final String jsonEndDate = "endDate";
    private DownloadPDF originalDownloadPDF;

    public MRCoverPDF(@NonNull DownloadPDF downloadPDF) {
        this.originalDownloadPDF = downloadPDF;
    }

    public Map<String, Object> getValuesFromRequest(@NonNull String requestBody, DDPInstance ddpInstance, UserDto user) {
        JsonObject jsonObject = new JsonParser().parse(requestBody).getAsJsonObject();
        Set keySet = jsonObject.keySet();
        String startDate = null;
        String endDate = null;
        if (keySet.contains(jsonStartDate)) {
            startDate = jsonObject.get(jsonStartDate).getAsString();
            if (!"0/0".equals(startDate) && !startDate.contains("/") && startDate.contains("-")) {
                startDate = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, startDate);
            } else if (StringUtils.isNotBlank(startDate) && startDate.startsWith("0/") && !startDate.equals("0/0")) {
                startDate = "01/" + startDate.split("/")[1];
            }
        }
        if (keySet.contains(jsonEndDate)) {
            endDate = jsonObject.get(jsonEndDate).getAsString();
            endDate = SystemUtil.changeDateFormat(SystemUtil.DATE_FORMAT, SystemUtil.US_DATE_FORMAT, endDate);
        }
        if (StringUtils.isBlank(originalDownloadPDF.getMedicalRecordId())) {
            throw new RuntimeException("MedicalRecordID is missing. Can't create cover pdf");
        }
        //get information from db
        MedicalRecord medicalRecord = MedicalRecord.getMedicalRecord(ddpInstance.getName(), originalDownloadPDF.getDdpParticipantId(),
                originalDownloadPDF.getMedicalRecordId());
        if (medicalRecord == null) {
            log.info( String.format("No medical record was found for participant with guid %s, trying legacy id"), originalDownloadPDF.getDdpParticipantId());
            Profile participantProfile = ElasticSearchUtil.getParticipantProfileByGuidOrAltPid(
                    ddpInstance.getParticipantIndexES(), originalDownloadPDF.getDdpParticipantId()).orElseThrow();
            if (StringUtils.isNotBlank(participantProfile.getLegacyAltPid())) {
                log.info("Participant has legacy id "+ participantProfile.getLegacyAltPid());
                medicalRecord = MedicalRecord.getMedicalRecord(ddpInstance.getName(), participantProfile.getLegacyAltPid(),
                        originalDownloadPDF.getMedicalRecordId());
            }
            if (medicalRecord == null) {
                throw new RuntimeException(String.format("Medical Record with id %s not found for participant with guid %s",
                        originalDownloadPDF.getMedicalRecordId(), originalDownloadPDF.getDdpParticipantId()));
            }
        }
        Map<String, Object> valueMap = new HashMap<>();
        //values same no matter from where participant/institution data comes from
        valueMap.put(CoverPDFProcessor.FIELD_CONFIRMED_INSTITUTION_NAME, medicalRecord.getName());
        valueMap.put(CoverPDFProcessor.FIELD_CONFIRMED_INSTITUTION_NAME_2, medicalRecord.getName() + ",");
        valueMap.put(CoverPDFProcessor.FIELD_CONFIRMED_FAX, medicalRecord.getFax());
        String today = new SimpleDateFormat("MM/dd/yyyy").format(new Date());
        valueMap.put(CoverPDFProcessor.FIELD_DATE, today);
        valueMap.put(CoverPDFProcessor.FIELD_DATE_2, StringUtils.isNotBlank(endDate) ? endDate : today); //end date

        valueMap.put(RequestPDFProcessor.USER_NAME, user.getName().get());
        user.getPhoneNumber().ifPresent(phone -> {
            valueMap.put(RequestPDFProcessor.USER_PHONE, phone);
        });


        //adding checkboxes configured under instance_settings
        InstanceSettings instanceSettings = new InstanceSettings();
        InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(ddpInstance.getName());
        instanceSettingsDto
                .getMrCoverPdf()
                .orElse(Collections.emptyList())
                .forEach(mrCoverSetting -> {
                    if (keySet.contains(mrCoverSetting.getValue())) {
                        valueMap.put(mrCoverSetting.getValue(),
                                BooleanUtils.toBoolean(jsonObject.get(mrCoverSetting.getValue()).getAsBoolean()));
                    }
                });

        originalDownloadPDF.addDDPParticipantDataToValueMap(ddpInstance, valueMap, true, originalDownloadPDF.getDdpParticipantId());

        valueMap.put(CoverPDFProcessor.START_DATE_2,
                StringUtils.isNotBlank(startDate) ? startDate : valueMap.get(CoverPDFProcessor.FIELD_DATE_OF_DIAGNOSIS)); //start date
        return valueMap;
    }

    public byte[] getMRCoverPDF(@NonNull String requestBody, DDPInstance ddpInstance, UserDto user) {
        PDFProcessor processor = new CoverPDFProcessor(ddpInstance.getName());
        return originalDownloadPDF.generatePDFFromValues(getValuesFromRequest(requestBody, ddpInstance, user), ddpInstance, processor);
    }

}
