package org.broadinstitute.dsm.files;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.lddp.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoverPDFProcessor extends PDFProcessor {
    // Fields which are needed for cover pdf
    public static final String FIELD_CONFIRMED_INSTITUTION_NAME = "confirmedInstitutionName";
    public static final String FIELD_CONFIRMED_INSTITUTION_NAME_2 = "confirmedInstitutionName_page2";
    public static final String FIELD_CONFIRMED_FAX = "confirmedFax";
    public static final String FIELD_FULL_NAME = "fullName";
    public static final String FIELD_DATE = "today";
    public static final String FIELD_DATE_2 = "today_page2";
    public static final String FIELD_DATE_OF_BIRTH = "dob";
    public static final String FIELD_DATE_OF_DIAGNOSIS = "dateOfDiagnosis";
    public static final String START_DATE_2 = "start_page2";
    public static final String USER_NAME = "username";
    public static final String USER_PHONE = "userPhone";
    private static final Logger logger = LoggerFactory.getLogger(CoverPDFProcessor.class);
    private static final String GENERATED_COVER_FILENAME = "cover.pdf";
    private static final String TEMPLATE_COVER_FILENAME = "%1-MR-Request-Template.pdf";
    private String ddp;

    public CoverPDFProcessor(String ddp) {
        this.ddp = ddp;
    }

    public ByteArrayInputStream generateStream(Map<String, Object> valueMap) {
        logger.info("Generating new stream...");

        ByteArrayInputStream inputStream = null;
        try {
            HashMap<String, Object> fields = new HashMap<>();
            fields.put(FIELD_FULL_NAME, valueMap.get(FIELD_FULL_NAME));
            fields.put(FIELD_DATE_OF_BIRTH, valueMap.get(FIELD_DATE_OF_BIRTH));
            fields.put(FIELD_CONFIRMED_INSTITUTION_NAME, valueMap.get(FIELD_CONFIRMED_INSTITUTION_NAME));
            fields.put(FIELD_CONFIRMED_INSTITUTION_NAME_2, valueMap.get(FIELD_CONFIRMED_INSTITUTION_NAME_2));
            fields.put(FIELD_CONFIRMED_FAX, valueMap.get(FIELD_CONFIRMED_FAX));
            fields.put(FIELD_DATE, valueMap.get(FIELD_DATE));
            fields.put(FIELD_DATE_2, valueMap.get(FIELD_DATE_2));
            fields.put(START_DATE_2, valueMap.get(START_DATE_2));

            fields.put(USER_NAME, valueMap.get(USER_NAME));
            fields.put(USER_PHONE, valueMap.get(USER_PHONE));

            //adding checkboxes configured under instance_settings
            InstanceSettings instanceSettings = new InstanceSettings();
            InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(ddp);
            instanceSettingsDto.getMrCoverPdf()
                    .orElse(Collections.emptyList())
                    .forEach(mrCoverSetting -> fields.put(mrCoverSetting.getValue(), valueMap.get(mrCoverSetting.getValue())));

            byte[] bytes = PDFProcessor.getTemplateFromGoogleBucket(TEMPLATE_COVER_FILENAME.replace("%1", ddp));
            inputStream = generateStreamFromPdfForm(fields, bytes);
        } catch (Exception ex) {
            throw new FileProcessingException("COVER PDF - Unable to generate consent stream.", ex);
        }

        return inputStream;
    }

    public String getFileName() {
        return GENERATED_COVER_FILENAME;
    }
}
