package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.PdfGenerationService.UNCHECKED_VALUE;
import static org.broadinstitute.ddp.util.PdfTestingUtil.TestMedicalProviderData.INSTITUTION_CITY;
import static org.broadinstitute.ddp.util.PdfTestingUtil.TestMedicalProviderData.INSTITUTION_NAME;
import static org.broadinstitute.ddp.util.PdfTestingUtil.TestMedicalProviderData.INSTITUTION_STATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfButtonFormField;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.ProfileSubstitution;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.util.PdfTestingUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfGenerationServiceTest extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationServiceTest.class);

    private PdfTestingUtil.PdfInfo pdfInfo;
    private PdfGenerationService pdfGenerationService = new PdfGenerationService();

    @Before
    public void setup() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
    }

    @After
    public void cleanup() {
        PdfTestingUtil.removeConfigurationData(pdfInfo);
    }

    @Test
    public void testModifyGeneratedPhysicianInstitutionPdf() throws Exception {
        int fakePdfOrderIndex = 0;

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        byte[] institutionPdf = TransactionWrapper.withTxn(handle -> pdfGenerationService.generatePhysicianInstitutionPdf(
                pdfInfo.getData().getStudyGuid(),
                pdfInfo.getInstitutionTemplate(),
                fakePdfOrderIndex,
                pdfInfo.getPdfConfiguration().getConfigName(),
                participant,
                new ArrayList<>()));
        byte[] physicianPdf = TransactionWrapper.withTxn(handle -> pdfGenerationService.generatePhysicianInstitutionPdf(
                pdfInfo.getData().getStudyGuid(),
                pdfInfo.getPhysicianTemplate(),
                fakePdfOrderIndex,
                pdfInfo.getPdfConfiguration().getConfigName(),
                participant,
                new ArrayList<>()));
        byte[] biopsyPdf = TransactionWrapper.withTxn(handle -> pdfGenerationService.generatePhysicianInstitutionPdf(
                pdfInfo.getData().getStudyGuid(),
                pdfInfo.getBiopsyTemplate(),
                fakePdfOrderIndex,
                pdfInfo.getPdfConfiguration().getConfigName(),
                participant,
                new ArrayList<>()));


        assertFieldsCorrectlyWrittenToPdf(new PdfDocument(new PdfReader(new ByteArrayInputStream(institutionPdf))),
                pdfInfo.getExpectedValuesByInstitutionType().get(InstitutionType.INSTITUTION));

        assertFieldsCorrectlyWrittenToPdf(new PdfDocument(new PdfReader(new ByteArrayInputStream(biopsyPdf))),
                pdfInfo.getExpectedValuesByInstitutionType().get(InstitutionType.INITIAL_BIOPSY));


        assertFieldsCorrectlyWrittenToPdf(new PdfDocument(new PdfReader(new ByteArrayInputStream(physicianPdf))),
                pdfInfo.getExpectedValuesByInstitutionType().get(InstitutionType.PHYSICIAN));


        createDebugFileWithFlattenedFields(physicianPdf);
        createDebugFileWithFlattenedFields(institutionPdf);
        createDebugFileWithFlattenedFields(biopsyPdf);
    }

    /**
     * Flattens the fields for the given pdf bytes and writes it out to a temporary file.  This is helpful
     * for manual inspection of PDFs.
     */
    private void createDebugFileWithFlattenedFields(byte[] pdfBytes) throws IOException {
        PdfDocument flattenedDoc = null;
        File flattenedFile = null;
        try {
            flattenedFile = File.createTempFile("pdftest", ".pdf");
            PdfWriter tempWriter = new PdfWriter(new FileOutputStream(flattenedFile));
            flattenedDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)), tempWriter);

            PdfAcroForm form = PdfAcroForm.getAcroForm(flattenedDoc, false);
            form.flattenFields();
        } finally {
            flattenedDoc.close();
            LOG.info("Flattened file {}", flattenedFile.getAbsolutePath());
        }
    }

    /**
     * Runs through the map of field/value pairs and asserts that
     * they are all present in the given pdf
     *
     * @param renderedPdf                the pdf that contains the substitutions
     * @param expectedFieldSubstitutions mapping of pdf field name and string value
     */
    public void assertFieldsCorrectlyWrittenToPdf(PdfDocument renderedPdf,
                                                  Map<String, String> expectedFieldSubstitutions) {
        PdfAcroForm form = PdfAcroForm.getAcroForm(renderedPdf, false);
        form.setGenerateAppearance(true);

        for (Map.Entry<String, String> expectedFieldAndValue : expectedFieldSubstitutions.entrySet()) {
            String expectedFieldName = expectedFieldAndValue.getKey();
            String expectedFieldValue = expectedFieldAndValue.getValue();

            boolean foundField = false;
            for (Map.Entry<String, PdfFormField> actualFieldAndValue : form.getFormFields().entrySet()) {
                if (expectedFieldName.equals(actualFieldAndValue.getKey())) {
                    foundField = true;
                    Assert.assertEquals("Unexpected value for " + expectedFieldName, expectedFieldValue, actualFieldAndValue
                            .getValue().getValueAsString());
                }
            }
            Assert.assertTrue("Could not find " + expectedFieldName, foundField);
        }

    }

    /**
     * Runs through the list of values and asserts that
     * they are all present in the given pdf
     *
     * @param renderedPdf                the pdf that contains the substitutions
     * @param expectedFieldSubstitutions list of the expected value of the field
     * @param type                       what kind of template is being reviewed
     */
    public void assertFieldsCorrectlyWrittenToPdf(PdfDocument renderedPdf, List<String> expectedFieldSubstitutions, PdfTemplateType type) {
        PdfAcroForm form = PdfAcroForm.getAcroForm(renderedPdf, false);
        form.setGenerateAppearance(true);
        Map<String, Integer> fieldsToBeFound = new HashMap<>();

        for (int i = 0; i < expectedFieldSubstitutions.size(); i++) {
            String substitution = expectedFieldSubstitutions.get(i);
            if (fieldsToBeFound.containsKey(substitution)) {
                fieldsToBeFound.put(substitution, fieldsToBeFound.get(substitution) + 1);
            } else {
                fieldsToBeFound.put(substitution, 1);
            }
        }

        Collection<String> fieldValues = new ArrayList<>();
        for (Map.Entry<String, PdfFormField> entry : form.getFormFields().entrySet()) {
            fieldValues.add(entry.getValue().getValueAsString());
        }

        Map<String, Integer> fieldsFound = new HashMap<>();
        for (String foundFieldValue : fieldValues) {
            if (fieldsFound.containsKey(foundFieldValue)) {
                fieldsFound.put(foundFieldValue, fieldsFound.get(foundFieldValue) + 1);
            } else {
                fieldsFound.put(foundFieldValue, 1);
            }
        }

        for (String foundFieldValue : fieldsFound.keySet()) {
            if (foundFieldValue.equals("") || (type == PdfTemplateType.PHYSICIAN_INSTITUTION && foundFieldValue.equals(UNCHECKED_VALUE))) {
                continue;
            }

            int actualCount = fieldsFound.get(foundFieldValue);
            if (foundFieldValue.equals(UNCHECKED_VALUE) && type == PdfTemplateType.CUSTOM) {
                assertTrue("Error finding the field: " + foundFieldValue + ". Expected at least one. Actual count: " + actualCount,
                        actualCount > 0);
                continue;
            }
            LOG.info("Checking field with value {}", foundFieldValue);
            int expectedCount = fieldsToBeFound.getOrDefault(foundFieldValue, 0);

            assertEquals("Error finding the field: " + foundFieldValue + ". Expected count: " + expectedCount
                            + ". Actual count: " + actualCount,
                    expectedCount, actualCount);
        }
    }

    @Test
    public void testModifyGeneratedCustomPdf() throws Exception {
        int fakePdfOrderIndex = 0;

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        byte[] outputDoc = TransactionWrapper.withTxn(handle -> pdfGenerationService.generateCustomPdf(
                pdfInfo.getCustomTemplate(),
                fakePdfOrderIndex,
                pdfInfo.getPdfConfiguration().getConfigName(),
                participant,
                instances,
                new ArrayList<>()));
        createDebugFileWithFlattenedFields(outputDoc);
        assertFieldsCorrectlyWrittenToPdf(new PdfDocument(new PdfReader(new ByteArrayInputStream(outputDoc))),
                pdfInfo.getExpectedCustomValues());
    }

    @Test
    public void testModifyLoadedMailingAddressPdf() throws Exception {
        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        byte[] outputDoc = TransactionWrapper.withTxn(handle -> pdfGenerationService.generateMailingAddressPdf(
                pdfInfo.getMailingAddressTemplate(),
                participant.getUser(),
                new ArrayList<>(), pdfInfo.getPdfConfiguration().getStudyGuid(), handle
        ));

        createDebugFileWithFlattenedFields(outputDoc);
        assertFieldsCorrectlyWrittenToPdf(new PdfDocument(new PdfReader(new ByteArrayInputStream(outputDoc))),
                pdfInfo.getExpectedMailingAddressValues());
    }

    @Test
    public void testModifyGeneratedPdfsForConfiguration() throws Exception {
        byte[] outputDoc = TransactionWrapper.withTxn(handle ->
                pdfGenerationService
                        .generatePdfForConfiguration(pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid(),
                                handle));
        List<String> fieldsToVerify = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            fieldsToVerify.add(INSTITUTION_NAME + i);
            fieldsToVerify.add(INSTITUTION_CITY + i);
            fieldsToVerify.add(INSTITUTION_STATE + i);
        }
        fieldsToVerify.add("Doctor Nick");
        fieldsToVerify.add(INSTITUTION_NAME);
        fieldsToVerify.add(INSTITUTION_CITY);
        fieldsToVerify.add(INSTITUTION_STATE);
        fieldsToVerify.add(PdfTestingUtil.NON_ASCII_NAME + 2);

        fieldsToVerify.add(pdfInfo.getData().getUserHruid());
        fieldsToVerify.add(PdfGenerationService.CHECKED_VALUE);
        fieldsToVerify.add(PdfGenerationService.CHECKED_VALUE);
        fieldsToVerify.add(PdfGenerationService.UNCHECKED_VALUE);
        fieldsToVerify.add(PdfGenerationService.UNCHECKED_VALUE);
        fieldsToVerify.add(PdfTestingUtil.TEXT_ANSWER);

        //Fancy way to get mm/dd/yyyy in UTC timezone, which is what we expect to find in PDF
        fieldsToVerify.add(PdfTestingUtil.TESTING_DATE.toDefaultDateFormat());
        ZonedDateTime zonedTimeStampNow = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
        fieldsToVerify.add(DateTimeFormatter.ofPattern("MM/dd/yyyy").format(zonedTimeStampNow));

        fieldsToVerify.addAll(pdfInfo.getExpectedMailingAddressValues().values());

        assertFieldsCorrectlyWrittenToPdf(new PdfDocument(new PdfReader(new ByteArrayInputStream(outputDoc))),
                fieldsToVerify, PdfTemplateType.CUSTOM);

        createDebugFileWithFlattenedFields(outputDoc);
    }

    @Test
    public void testFlattenedGeneratedPdfForConfiguration() throws Exception {
        byte[] outputDoc = TransactionWrapper.withTxn(handle ->
                pdfGenerationService
                        .generateFlattenedPdfForConfiguration(pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid(),
                                handle));
        PdfDocument sourceDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(outputDoc)),
                new PdfWriter(new ByteArrayOutputStream()));
        PdfAcroForm form = PdfAcroForm.getAcroForm(sourceDoc, true);
        form.setGenerateAppearance(true);
        Map<String, PdfFormField> fields = form.getFormFields();

        assertTrue(fields.isEmpty());
    }

    @Test
    public void testGenerateMailingAddressFailsWithMissingFields() throws IOException {
        String fieldName = "name";
        pdfInfo.getMailingAddressTemplate().setLastNamePlaceholder(fieldName);

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        List<String> errors = new ArrayList<>();
        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateMailingAddressPdf(
                    pdfInfo.getMailingAddressTemplate(),
                    participant.getUser(),
                    errors, pdfInfo.getPdfConfiguration().getStudyGuid(), handle
            );
            assertEquals(1, errors.size());
            assertEquals(errors.get(0), "Could not find PDFFormField field with name: " + fieldName);
        });
    }

    @Test
    public void testGenerateMailingAddressFailsWithMissingProxyField() throws IOException {
        String fieldName = "proxyLastName";
        pdfInfo.getMailingAddressTemplate().setProxyLastNamePlaceholder(fieldName);

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        List<String> errors = new ArrayList<>();
        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateMailingAddressPdf(
                    pdfInfo.getMailingAddressTemplate(),
                    participant.getUser(),
                    errors, pdfInfo.getPdfConfiguration().getStudyGuid(), handle
            );
            assertEquals(1, errors.size());
            assertEquals(errors.get(0), "Could not find PDFFormField field with name: " + fieldName);
        });
    }

    @Test
    public void testGenerateCustomPdfSuccessfulWhenNoActivityGuidPresent_AnswerSubstitution() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            byte[] bytes = pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertTrue(errors.isEmpty());
            assertNotEquals(0, bytes.length);
        });
    }

    @Test
    public void testGenerateCustomPdfWhenNoActivityGuidPresent_ActivityDateSubstitution() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            byte[] bytes = pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertTrue(errors.isEmpty());
            assertNotEquals(0, bytes.length);
        });
    }

    @Test
    public void testConvertSubstitutionToPdfFailsWhenMissingFields_ActivityDate() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();
        pdfInfo.getCustomTemplate().getSubstitutions()
                .add(new ActivityDateSubstitution("fake", pdfInfo.getTestActivityId()));

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertTrue(errors.contains("Could not find field with name: fake in body of PDF template"));
        });
    }

    @Test
    public void testConvertSubstitutionToPdfFailsWhenMissingActivity_ActivityDate() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();
        pdfInfo.getCustomTemplate().getSubstitutions()
                .add(new ActivityDateSubstitution(PdfTestingUtil.ANOTHER_DATE, 123456));

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertEquals(1, errors.size());
            assertTrue(errors.get(0).contains("Did not find activity instance"));
            assertTrue(errors.get(0).contains("user guid " + pdfInfo.getData().getUserGuid()));
            assertTrue(errors.get(0).contains("activity id 123456"));
            assertTrue(errors.get(0).contains("PDF ACTIVITY_DATE substitutions"));
        });
    }

    @Test
    public void testConvertSubstitutionToPdfFailsWhenUnsupportedProfileField_Profile() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();
        pdfInfo.getCustomTemplate().getSubstitutions()
                .add(new ProfileSubstitution("fake", "unsupported"));

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertTrue(errors.contains("Unsupported pdf substitution profile field name 'unsupported'"));
        });
    }

    @Test
    public void testConvertSubstitutionToPdfFailsWhenMissingFields_Profile() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();
        pdfInfo.getCustomTemplate().getSubstitutions()
                .add(new ProfileSubstitution("fake", "first_name"));

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertTrue(errors.contains("template " + fakePdfOrderIndex + " for configuration "
                    + pdfInfo.getPdfConfiguration().getConfigName()
                    + " is missing necessary field: fake"));
        });
    }

    @Test
    public void testConvertSubstitutionToPdfFailsWhenMissingFields_Answer() throws IOException {
        int fakePdfOrderIndex = 0;
        List<String> errors = new ArrayList<>();
        pdfInfo.getCustomTemplate().getSubstitutions().add(new AnswerSubstitution(
                "fake", pdfInfo.getTestActivityId(), pdfInfo.getQuestionDef().getQuestionType(), pdfInfo.getQuestionDef().getStableId()));

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));
        Map<Long, ActivityResponse> instances = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadActivityInstanceData(
                handle, pdfInfo.getPdfConfiguration(), participant));

        TransactionWrapper.useTxn(handle -> {
            pdfGenerationService.generateCustomPdf(
                    pdfInfo.getCustomTemplate(),
                    fakePdfOrderIndex,
                    pdfInfo.getPdfConfiguration().getConfigName(),
                    participant,
                    instances,
                    errors);
            assertTrue(errors.contains("Could not find PDFFormField field with name: fake"));
        });
    }

    @Test
    public void testModifyDummyGeneratedPdfWithMultipleQuestionTypes() throws Exception {
        String destination = "/tmp/customizedDummyPdfTrial.pdf";
        PdfDocument pdf = new PdfDocument(new PdfWriter(destination));
        PageSize ps = PageSize.A4;
        pdf.setDefaultPageSize(ps);

        Document document = null;
        try {
            document = new Document(pdf);
            PdfAcroForm form = createDummyAcroForm(document);

            Map<String, PdfFormField> fields = form.getFormFields();
            fields.get("name").setValue("James Bond");
            fields.get("language").setValue("English");
            fields.get("experience1").setValue("Off");
            fields.get("experience2").setValue("Yes");
            fields.get("experience3").setValue("Yes");
            fields.get("info").setValue("I was 38 years old when I became an MI6 agent.");
            fields.get("dob").setValue("03/15/1993");
        } finally {
            document.close();
        }
    }

    private PdfAcroForm createDummyAcroForm(Document doc) {
        Paragraph title = new Paragraph("Application for nothing in particular")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(16);
        doc.add(title);
        doc.add(new Paragraph("Full name:").setFontSize(12));
        doc.add(new Paragraph("Native language:      English         French       German        Russian        Spanish").setFontSize(12));
        doc.add(new Paragraph("Experience in:       cooking        driving           software development").setFontSize(12));
        /* todo SMA - add this functionality when experimenting with PickLists
        doc.add(new Paragraph("Preferred time slot:").setFontSize(12));
        */
        doc.add(new Paragraph("Date of birth:").setFontSize(12));
        doc.add(new Paragraph("Additional information:").setFontSize(12));


        PdfAcroForm form = PdfAcroForm.getAcroForm(doc.getPdfDocument(), true);

        PdfTextFormField nameField = PdfTextFormField.createText(doc.getPdfDocument(), new Rectangle(99, 753, 425, 15), "name", "");
        form.addField(nameField);

        PdfButtonFormField group = PdfFormField.createRadioGroup(doc.getPdfDocument(), "language", "");
        PdfFormField.createRadioButton(doc.getPdfDocument(), new Rectangle(130, 728, 15, 15), group, "English");
        PdfFormField.createRadioButton(doc.getPdfDocument(), new Rectangle(200, 728, 15, 15), group, "French");
        PdfFormField.createRadioButton(doc.getPdfDocument(), new Rectangle(260, 728, 15, 15), group, "German");
        PdfFormField.createRadioButton(doc.getPdfDocument(), new Rectangle(330, 728, 15, 15), group, "Russian");
        PdfFormField.createRadioButton(doc.getPdfDocument(), new Rectangle(400, 728, 15, 15), group, "Spanish");
        form.addField(group);

        for (int i = 0; i < 3; i++) {
            PdfButtonFormField checkField = PdfFormField.createCheckBox(doc.getPdfDocument(),
                    new Rectangle(119 + i * 69, 701, 15, 15),
                    "experience".concat(String.valueOf(i + 1)),
                    "Off",
                    PdfFormField.TYPE_CHECK);
            form.addField(checkField);
        }

        /* todo CA - add this functionality when experimenting with Picklists. Replace options with picklist answers
        String[] options = {"Any", "6.30 am - 2.30 pm", "1.30 pm - 9.30 pm"};
        PdfChoiceFormField choiceField = PdfFormField.createComboBox(
            doc.getPdfDocument(), new Rectangle(163, 676, 115, 15),
            "shift", "Any", options);
        form.addField(choiceField);
         */

        PdfTextFormField birthdayField = PdfTextFormField.createText(doc.getPdfDocument(), new Rectangle(125, 674, 200, 15), "dob", "");
        form.addField(birthdayField);

        PdfTextFormField infoField = PdfTextFormField.createMultilineText(doc.getPdfDocument(),
                new Rectangle(158, 625, 366, 40),
                "info", "");
        form.addField(infoField);


        PdfButtonFormField button = PdfFormField.createPushButton(doc.getPdfDocument(),
                new Rectangle(479, 594, 45, 15), "reset", "RESET");
        button.setAction(PdfAction.createResetForm(new String[] {"name", "language", "experience1",
                "experience2", "experience3", "dob", "info"}, 0));
        form.addField(button);

        return form;
    }

    @Test
    public void testPhysicianInstitutionPdf_missingPhysicians_returnsNoPages() throws IOException {
        clearGeneratedMedicalProviders();

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        String studyGuid = pdfInfo.getData().getStudyGuid();
        PhysicianInstitutionTemplate tmpl = pdfInfo.getPhysicianTemplate();
        List<String> errors = new ArrayList<>();
        byte[] actual = TransactionWrapper.withTxn(handle ->
                pdfGenerationService.generatePhysicianInstitutionPdf(studyGuid, tmpl, 1, "dummyName", participant, errors));

        assertNull(actual);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void testPhysicianInstitutionPdf_missingBiopsy_returnsBlankPage() throws IOException {
        clearGeneratedMedicalProviders();

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        String studyGuid = pdfInfo.getData().getStudyGuid();
        PhysicianInstitutionTemplate tmpl = pdfInfo.getBiopsyTemplate();
        List<String> errors = new ArrayList<>();
        byte[] actual = TransactionWrapper.withTxn(handle ->
                pdfGenerationService.generatePhysicianInstitutionPdf(studyGuid, tmpl, 1, "dummyName", participant, errors));

        assertNotNull(actual);
        assertTrue(actual.length > 0);
        assertTrue(errors.isEmpty());

        Set<String> expectedFields = new HashSet<>();
        expectedFields.add(tmpl.getPhysicianNamePlaceholder());
        expectedFields.add(tmpl.getInstitutionNamePlaceholder());
        expectedFields.add(tmpl.getCityPlaceholder());
        expectedFields.add(tmpl.getStatePlaceholder());

        PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(actual)));
        PdfAcroForm form = PdfAcroForm.getAcroForm(doc, false);
        assertFalse(form.getFormFields().isEmpty());

        boolean expectedFieldsFound = false;
        for (Map.Entry<String, PdfFormField> entry : form.getFormFields().entrySet()) {
            if (expectedFields.contains(entry.getKey())) {
                assertTrue(entry.getValue().getValueAsString().isEmpty());
                expectedFieldsFound = true;
            }
        }
        assertTrue(expectedFieldsFound);
    }

    @Test
    public void testPhysicianInstitutionPdf_missingInstitutions_returnsNoPages() throws IOException {
        clearGeneratedMedicalProviders();

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        String studyGuid = pdfInfo.getData().getStudyGuid();
        PhysicianInstitutionTemplate tmpl = pdfInfo.getInstitutionTemplate();
        List<String> errors = new ArrayList<>();
        byte[] actual = TransactionWrapper.withTxn(handle ->
                pdfGenerationService.generatePhysicianInstitutionPdf(studyGuid, tmpl, 1, "dummyName", participant, errors));

        assertNull(actual);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void testPhysicianInstitutionPdf_partialMedicalProvider_filledIn() throws IOException {
        clearGeneratedMedicalProviders();
        TransactionWrapper.useTxn(handle -> {
            String guid = UUID.randomUUID().toString();
            handle.attach(JdbiMedicalProvider.class).insert(new MedicalProviderDto(
                    null,
                    guid,
                    pdfInfo.getData().getUserId(),
                    pdfInfo.getData().getStudyId(),
                    InstitutionType.PHYSICIAN,
                    "institute A",
                    "physician A",
                    null, // missing city
                    null, // missing state
                    null,
                    null,
                    null,
                    null
            ));
            pdfInfo.getMedicalProviderRowsToDelete().add(guid);
        });

        Participant participant = TransactionWrapper.withTxn(handle -> pdfGenerationService.loadParticipantData(
                handle, pdfInfo.getPdfConfiguration(), pdfInfo.getData().getUserGuid()));

        String studyGuid = pdfInfo.getData().getStudyGuid();
        PhysicianInstitutionTemplate tmpl = pdfInfo.getPhysicianTemplate();
        List<String> errors = new ArrayList<>();
        byte[] actual = TransactionWrapper.withTxn(handle ->
                pdfGenerationService.generatePhysicianInstitutionPdf(studyGuid, tmpl, 1, "dummyName", participant, errors));

        assertNotNull(actual);
        assertTrue(actual.length > 0);
        assertTrue(errors.isEmpty());

        PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(actual)));
        PdfAcroForm form = PdfAcroForm.getAcroForm(doc, false);
        assertFalse(form.getFormFields().isEmpty());

        // Note: fields are renamed, need to append an index.
        assertEquals("physician A", form.getField(tmpl.getPhysicianNamePlaceholder() + "_0").getValueAsString());
        assertEquals("institute A", form.getField(tmpl.getInstitutionNamePlaceholder() + "_0").getValueAsString());
        assertTrue("city should be empty", form.getField(tmpl.getCityPlaceholder() + "_0").getValueAsString().isEmpty());
        assertTrue("state should be empty", form.getField(tmpl.getStatePlaceholder() + "_0").getValueAsString().isEmpty());
    }

    private void clearGeneratedMedicalProviders() {
        TransactionWrapper.useTxn(handle -> {
            JdbiMedicalProvider jdbiProvider = handle.attach(JdbiMedicalProvider.class);
            for (String providerGuid : pdfInfo.getMedicalProviderRowsToDelete()) {
                assertEquals(1, jdbiProvider.deleteByGuid(providerGuid));
            }
            pdfInfo.getMedicalProviderRowsToDelete().clear();
        });
    }
}
