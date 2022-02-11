package org.broadinstitute.dsm.files;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.FileProcessingException;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

public abstract class PDFProcessor implements BasicProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PDFProcessor.class);

    protected boolean flatten;

    /**
     * Loads pdf form template, fills in some values, and creates new stream with completed form.
     *
     * @param fields text fields to complete
     * @param bytes  byte[] of template file
     * @return stream with complete form as ByteArrayInputStream
     * <p>
     * IMPORTANT: This method returns ByteArrayInputStream. So you should NOT use this class if you have large pdf files you want
     * to generate!
     */
    public ByteArrayInputStream generateStreamFromPdfForm(@NonNull Map<String, Object> fields, @NonNull byte[] bytes) {
        ByteArrayInputStream input = null;
        PDDocument pdfDocument = null;
        boolean success = false;
        Throwable error = null;
        try {
            //load the template document
            pdfDocument = PDDocument.load(bytes);

            //get the document catalog
            PDAcroForm acroForm = pdfDocument.getDocumentCatalog().getAcroForm();
            acroForm.setNeedAppearances(true);

            //find fields and fill them in
            for (String fieldName : fields.keySet()) {
                PDField field = acroForm.getField(fieldName);
                if (field != null) {
                    if (field instanceof PDCheckBox) {
                        if (fields.get(fieldName) != null) {
                            if (fields.get(fieldName) instanceof Boolean) {
                                if ((Boolean) fields.get(fieldName)) {
                                    ((PDCheckBox) field).check();
                                }
                            }
                        }
                    }
                    else {
                        field.setValue((fields.get(fieldName) != null) ? fields.get(fieldName).toString() : null);
                        field.setReadOnly(true);
                    }
                }
            }

            if (flatten) {
                acroForm.flatten(); //converts from pdf form to regular pdf
            }

            //save the completed form
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdfDocument.save(output);

            input = new ByteArrayInputStream(output.toByteArray());

            logger.info("New pdf saved to stream.");
            success = true;
        }
        catch (Exception ex) {
            logger.error("A file generation exception occurred.", ex);
            error = ex;
        }
        finally {
            if (pdfDocument != null) {
                try {
                    pdfDocument.close();
                    logger.info("Closed pdf document.");
                }
                catch (IOException ex) {
                    logger.error("Error closing pdf document.", ex);
                }
            }
        }

        if (!success) {
            throw new FileProcessingException("Unable to generate pdf stream.", error);
        }
        return input;
    }

    public static byte[] getTemplateFromGoogleBucket(@NonNull String fileName) {
        String gcpName = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
        if (StringUtils.isNotBlank(gcpName)) {
            String bucketName = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_CONFIG_BUCKET);
            try {
                if (GoogleBucket.bucketExists(null, gcpName, bucketName)) {
                    logger.info("Downloading template " + fileName + " from bucket " + bucketName);
                    return GoogleBucket.downloadFile(null, gcpName, bucketName, fileName);
                }
                else {
                    logger.error("Google bucket " + bucketName + " does not exist");
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Couldn't get template from google bucket ", e);
            }
        }
        else {
            logger.error("Google project name missing to download pdf template");
        }
        return null;
    }
}
