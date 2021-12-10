package org.broadinstitute.lddp.file;

import lombok.Data;
import lombok.NonNull;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.datstat.SurveyInstance;
import java.io.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.broadinstitute.lddp.exception.FileProcessingException;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Used to generate pdf SMALL pdf files.
 */
@Data
public abstract class SmallPdfProcessor<T extends SurveyInstance> implements BasicProcessor<T>
{
    private static final Logger logger = LoggerFactory.getLogger(SmallPdfProcessor.class);

    public static final String OUTPUT_FOLDER = "output";

    protected DatStatUtil datStatUtil;
    protected boolean flatten;
    private boolean addNeedAppearances = false;
    private boolean autoRetry = true;

    public SmallPdfProcessor(boolean flatten)
    {
        this.flatten = flatten;
    }

    public SmallPdfProcessor(DatStatUtil datStatUtil, boolean flatten)
    {
        this.datStatUtil = datStatUtil;
        this.flatten = flatten;
    }

    /**
     * Loads pdf form template, fills in some values, and creates new stream with completed form.
     * @param fields text fields to complete
     * @param pdfPath path of template file
     * @return stream with complete form as ByteArrayInputStream
     *
     * IMPORTANT: This method returns ByteArrayInputStream. So you should NOT use this class if you have large pdf files you want
     * to generate!
     */
    public ByteArrayInputStream generateStreamFromPdfForm(@NonNull Map<String, Object> fields, @NonNull String pdfPath) {
        ByteArrayInputStream input;

        input = createPdf(fields, pdfPath);

        //in case there was for example a character error try again with setAppearances(true)
        //if this works the pdf will not be flattened and therefore may look funny on an iPhone
        //but should work via Adobe products still
        if ((input == null)&&autoRetry) {
            logger.info("PDF - Retry pdf generation.");
            addNeedAppearances = true;
            input = createPdf(fields, pdfPath);
        }

        if (input == null) {
            throw new FileProcessingException("PDF - Unable to generate pdf stream.");
        }

        return input;
    }

    public void saveFile(T surveyInstance, String fileName)
    {
        try
        {
            InputStream inputStream = generateStream(surveyInstance);

            File folder = new File(OUTPUT_FOLDER);
            if (!folder.exists()) {
                folder.mkdir();
            }

            Path path = Paths.get(OUTPUT_FOLDER + "/" + fileName);
            Files.write(path, IOUtils.toByteArray(inputStream));
        }
        catch (Exception ex)
        {
            throw new FileProcessingException("PDF - Unable to save pdf to file.", ex);
        }
    }

    public void addLastUpdatedDate(@NonNull SurveyInstance survey, @NonNull HashMap<String, Object> fields, @NonNull String fieldName,
                                   @NonNull String dateFormat) throws Exception
    {
        fields.put(fieldName, Utility.convertUTCDateTimeToOtherFormat(survey.getSurveyLastUpdated(), dateFormat));
    }

    private ByteArrayInputStream createPdf(@NonNull Map<String, Object> fields, @NonNull String pdfPath) {
        ByteArrayInputStream input = null;
        PDDocument pdfDocument = null;

        try
        {
            //load the template document
            pdfDocument = PDDocument.load(new File(pdfPath));

            //get the document catalog
            PDAcroForm acroForm = pdfDocument.getDocumentCatalog().getAcroForm();

            if (addNeedAppearances) acroForm.setNeedAppearances(true);

            //find fields and fill them in
            for (String fieldName : fields.keySet())
            {
                PDField field = acroForm.getField(fieldName);
                field.setValue((fields.get(fieldName) != null) ? fields.get(fieldName).toString(): null);
                field.setReadOnly(true);
            }

            if (flatten) {
                if (!addNeedAppearances) acroForm.flatten();
                else logger.warn("PDF - Pdf cannot be flattened when addNeedAppearances is true.");
            }

            //save the completed form
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdfDocument.save(output);

            input = new ByteArrayInputStream(output.toByteArray());

            logger.info("PDF - New pdf saved to stream.");
        }
        catch (Exception ex)
        {
            logger.error("PDF - A file generation exception occurred.", ex);
        }
        finally
        {
            if (pdfDocument != null)
            {
                try
                {
                    pdfDocument.close();
                    logger.info("PDF - Closed pdf document.");
                }
                catch (IOException ex)
                {
                    logger.error("PDF - Error closing pdf document.", ex);
                }
            }
        }

        return input;
    }
}