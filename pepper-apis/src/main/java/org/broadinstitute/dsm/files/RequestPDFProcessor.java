package org.broadinstitute.dsm.files;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.broadinstitute.ddp.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class RequestPDFProcessor extends PDFProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RequestPDFProcessor.class);

    private static final String GENERATED_TISSUE_FILENAME = "request.pdf";
    private static final String TEMPLATE_TISSUE_FILENAME = "%1-Tissue-Request-Template.pdf";
    private static final String ADDITIONAL_TEMPLATE_TISSUE_FILENAME = "Tissue-Request-Template_additional.pdf";

    // Fields which are needed for cover pdf
    public static final String FIELD_CONFIRMED_INSTITUTION_NAME = "confirmedInstitutionName";
    public static final String FIELD_CONFIRMED_PHONE = "confirmedPhone";
    public static final String FIELD_CONFIRMED_FAX = "confirmedFax";
    public static final String FIELD_FULL_NAME = "fullName";
    public static final String FIELD_DATE = "today";
    public static final String FIELD_DATE_2 = "today_page2";
    public static final String FIELD_DATE_OF_BIRTH = "dob";
    public static final String FIELD_DATE_PX = "datePX";
    public static final String FIELD_TYPE_LOCATION = "typeAndLoc";
    public static final String FIELD_ACCESSION_NUMBER = "accessionNumber";

    public static final String BLOCK_COUNTER = "accessionNumber";

    private String ddp;

    public RequestPDFProcessor(String ddp) {
        this.ddp = ddp;
    }

    public ByteArrayInputStream generateStream(Map<String, Object> valueMap) {
        logger.info("Generating new stream...");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            HashMap<String, Object> fields = new HashMap<>();

            fields.put(FIELD_FULL_NAME, valueMap.get(FIELD_FULL_NAME));
            fields.put(FIELD_DATE_OF_BIRTH, valueMap.get(FIELD_DATE_OF_BIRTH));
            fields.put(FIELD_CONFIRMED_PHONE, valueMap.get(FIELD_CONFIRMED_PHONE));
            fields.put(FIELD_CONFIRMED_INSTITUTION_NAME, valueMap.get(FIELD_CONFIRMED_INSTITUTION_NAME));
            fields.put(FIELD_CONFIRMED_FAX, valueMap.get(FIELD_CONFIRMED_FAX));
            fields.put(FIELD_DATE, valueMap.get(FIELD_DATE));
            fields.put(FIELD_DATE_2, valueMap.get(FIELD_DATE_2));

            int blockCounter = (Integer) valueMap.get(BLOCK_COUNTER);

            int firstPage = Math.min(4, blockCounter);
            for (int i = 0; i < firstPage; i++) {
                fields.put(FIELD_TYPE_LOCATION + i, valueMap.get(FIELD_TYPE_LOCATION + i));
                fields.put(FIELD_ACCESSION_NUMBER + i, valueMap.get(FIELD_ACCESSION_NUMBER + i));
                fields.put(FIELD_DATE_PX + i, valueMap.get(FIELD_DATE_PX + i));
            }

            PDFMergerUtility pdfMerger = new PDFMergerUtility();
            pdfMerger.setDestinationStream(output);
            addNormalPage(pdfMerger, fields);

            // more than 4 tissues selected, pdf will need additional pages
            if (blockCounter > 4) {
                int additionalPages = (int)Math.ceil(blockCounter / 4);
                for (int page = 1; page <= additionalPages; page++) {
                    for (int block = 0; block < 4; block++) {
                        fields.put(FIELD_TYPE_LOCATION + block, valueMap.get(FIELD_TYPE_LOCATION + ((page * 4) + block)));
                        fields.put(FIELD_ACCESSION_NUMBER + block, valueMap.get(FIELD_ACCESSION_NUMBER + ((page * 4) + block)));
                        fields.put(FIELD_DATE_PX + block, valueMap.get(FIELD_DATE_PX + ((page * 4) + block)));
                    }
                    addAdditionalPage(pdfMerger, fields);
                }
            }
            pdfMerger.mergeDocuments();
        }
        catch (Exception ex) {
            throw new FileProcessingException("Unable to generate request pdf stream.", ex);
        }

        return new ByteArrayInputStream(output.toByteArray());
    }

    private void addNormalPage(PDFMergerUtility pdfMerger, HashMap<String, Object> fields) {
        byte[] bytes = PDFProcessor.getTemplateFromGoogleBucket(TEMPLATE_TISSUE_FILENAME.replace("%1", ddp));
        pdfMerger.addSource(generateStreamFromPdfForm(fields, bytes));
    }

    private void addAdditionalPage(PDFMergerUtility pdfMerger, HashMap<String, Object> fields) {
        byte[] bytes = PDFProcessor.getTemplateFromGoogleBucket(ADDITIONAL_TEMPLATE_TISSUE_FILENAME.replace("%1", ddp));
        pdfMerger.addSource(generateStreamFromPdfForm(fields, bytes));
    }


    public String getFileName()
    {
        return GENERATED_TISSUE_FILENAME;
    }
}
