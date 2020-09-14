package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.GuidUtils.UPPER_ALPHA_NUMERIC;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfButtonFormField;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.JdbiUserNotificationPdf;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.ProfileSubstitution;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.jdbi.v3.core.Handle;

// This class is the container for utility methods not intended for instantiation,
// hence "final" and the private constructor
public final class PdfTestingUtil {

    public static final String NON_ASCII_NAME = "ần Hữu";
    public static final String MAILING_ADDRESS_FIRST_NAME_FIELD_VALUE = "firstName";
    public static final String MAILING_ADDRESS_LAST_NAME_FIELD_VALUE = "lastName";
    public static final String MAILING_ADDRESS_STREET_FIELD_VALUE = "street";
    public static final String MAILING_ADDRESS_CITY_FIELD_VALUE = "city";
    public static final String MAILING_ADDRESS_STATE_FIELD_VALUE = "state";
    public static final String MAILING_ADDRESS_PHONE_FIELD_VALUE = "phone";
    public static final String MAILING_ADDRESS_ZIP_FIELD_VALUE = "zip";
    public static final String MAILING_ADDRESS_COUNTRY_FIELD_VALUE = "country";
    public static final String MAILING_ADDRESS_FILE_PATH = "src/test/resources/ReleaseForm_firstPage.pdf";
    public static final String TEXT_ANSWER = "blue";
    public static final DateValue TESTING_DATE = new DateValue(2018, 10, 3);
    public static final String ANOTHER_DATE = "anotherDate";
    public static final DateValue TEST_DATE_VALUE = new DateValue(1978, 5, 16);
    // Dummy placeholders for physician/institution fields (aka field labels embedded in the actual pdf)
    protected static final String NAME_FIELD_NAME = "dummyName";
    protected static final String INSTITUTION_FIELD_NAME = "dummyInstitutionName";
    protected static final String CITY_FIELD_NAME = "dummyCity";
    protected static final String STATE_FIELD_NAME = "dummyState";
    protected static final String CHECKBOX1 = "checkbox1";
    protected static final String CHECKBOX2 = "checkbox2";
    protected static final String CHECKBOX3 = "checkbox3";
    protected static final String CHECKBOX4 = "checkbox4";
    protected static final String FAVORITE_COLOR = "color";
    protected static final String DATE = "date";
    private static final Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);
    // mapping between institution type and key/value pdfField/value
    private static final String tempDir = System.getProperty("java.io.tmpdir");

    /**
     * Helper to print the field names within a pdf file to stdout.
     */
    public static void printPdfFieldNames(String filename) throws IOException {
        PdfDocument doc = new PdfDocument(new PdfReader(filename));
        PdfAcroForm form = PdfAcroForm.getAcroForm(doc, false);
        form.getFormFields().keySet().forEach(System.out::println);
    }

    /**
     * Build a html-based template with a single english variable translation.
     */
    public static Template buildTemplate(String varName, String varText) {
        Translation enTrans = new Translation("en", varText);
        TemplateVariable var = new TemplateVariable(varName, Collections.singletonList(enTrans));
        String templateText = Template.VELOCITY_VAR_PREFIX + varName;
        Template tmpl = new Template(TemplateType.HTML, null, templateText);
        tmpl.addVariable(var);
        return tmpl;
    }

    /**
     * Inserts the test Pdf into the database along with all supporting data
     */
    public static PdfDbInfo insertTestPdfWithConfiguration(
            PdfMappingType pdfMappingType,
            long userId,
            String userGuid,
            long studyId,
            String studyGuid,
            String configurationName,
            String pdfFilename,
            String answerText
    ) {
        return TransactionWrapper.withTxn(handle -> {
            String questionSid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
            FormActivityDef activity = FormActivityDef.generalFormBuilder(UUID.randomUUID().toString(), "v1", studyGuid)
                    .addName(new Translation("en", "dummy translation"))
                    .addSection(new FormSectionDef(UUID.randomUUID().toString(), Collections.singletonList(new QuestionBlockDef(
                            TextQuestionDef.builder(TextInputType.TEXT, questionSid, Template.text("blah")).build())
                    )))
                    .build();
            ActivityVersionDto activityVersion = handle.attach(ActivityDao.class).insertActivity(
                    activity, RevisionMetadata.now(userId, "dummy activity")
            );

            ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activity.getActivityId(), userGuid);

            handle.attach(AnswerDao.class).createAnswer(userId, instanceDto.getId(),
                    new TextAnswer(null, questionSid, null, answerText));

            PdfConfigInfo info = new PdfConfigInfo(studyId, configurationName, pdfFilename, UUID.randomUUID().toString());
            PdfVersion version = new PdfVersion("v1", activityVersion.getRevId());
            version.addDataSource(new PdfActivityDataSource(activity.getActivityId(), activityVersion.getId()));
            PdfConfiguration config = new PdfConfiguration(info, version);

            String fieldName = UUID.randomUUID().toString();
            CustomTemplate tmpl = new CustomTemplate(generateSingleFieldPdf(fieldName));
            tmpl.addSubstitution(new AnswerSubstitution(fieldName, activity.getActivityId(), QuestionType.TEXT, questionSid));
            config.addTemplate(tmpl);

            long pdfConfigId = handle.attach(PdfDao.class).insertNewConfig(config);
            long mappingId = handle.attach(JdbiStudyPdfMapping.class).insert(studyId, pdfMappingType, pdfConfigId);
            return new PdfDbInfo(mappingId, pdfConfigId);
        });
    }

    // Helper to create a dummy pdf with a single text field.
    private static byte[] generateSingleFieldPdf(String fieldName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));

        Document doc = new Document(pdf);
        doc.add(new Paragraph("a text box:").setFontSize(12));
        doc.flush();

        PdfAcroForm form = PdfAcroForm.getAcroForm(pdf, true);
        form.addField(PdfTextFormField.createText(pdf, new Rectangle(100, 788, 80, 12), fieldName, ""));
        form.flush();

        pdf.close();

        return out.toByteArray();
    }

    public static PdfInfo makePhysicianInstitutionPdf() throws Exception {
        PdfInfo pdfInfo = new PdfInfo();
        pdfInfo.getExpectedValuesByInstitutionType().put(InstitutionType.INSTITUTION, new HashMap<>());
        pdfInfo.getExpectedValuesByInstitutionType().put(InstitutionType.INITIAL_BIOPSY, new HashMap<>());
        pdfInfo.getExpectedValuesByInstitutionType().put(InstitutionType.PHYSICIAN, new HashMap<>());

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            pdfInfo.setData(TestDataSetupUtil.generateBasicUserTestData(handle));
            TestDataSetupUtil.setUserEnrollmentStatus(handle, pdfInfo.getData(), EnrollmentStatusType.ENROLLED);
            TestDataSetupUtil.createTestingMailAddress(handle, pdfInfo.getData());

            String pdfFilePath = tempDir + "/physicianInstitution.pdf";
            try (PdfDocument pdf = new PdfDocument(new PdfWriter(pdfFilePath))) {
                var doc = new Document(pdf);
                createTestingForm(doc);
            }

            // define questions and insert them
            List<QuestionType> questionTypes = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                questionTypes.add(QuestionType.BOOLEAN);
            }
            questionTypes.add(QuestionType.TEXT);
            questionTypes.add(QuestionType.DATE);
            List<QuestionDef> questions = insertQuestions(handle, pdfInfo, questionTypes);
            pdfInfo.setQuestionDef(questions.get(0));

            // define the corresponding answers
            // 4 boolean answers
            List<Boolean> booleanAnswerValues = new ArrayList<>(Arrays.asList(true, false, false, true));
            // 1 text answers
            List<String> textAnswerValues = new ArrayList<>(Arrays.asList(TEXT_ANSWER));
            // and the date value
            List<DateValue> dateAnswerValues = new ArrayList<>(Arrays.asList(TESTING_DATE));

            List<String> pdfDocFieldNames = Arrays.asList(CHECKBOX1, CHECKBOX2, CHECKBOX3, CHECKBOX4, FAVORITE_COLOR, DATE, ANOTHER_DATE);
            List<String> expectedValues = Arrays.asList(
                    PdfGenerationService.CHECKED_VALUE,
                    PdfGenerationService.CHECKED_VALUE,
                    PdfGenerationService.UNCHECKED_VALUE,
                    PdfGenerationService.UNCHECKED_VALUE,
                    TEXT_ANSWER,
                    TESTING_DATE.toDefaultDateFormat());

            // turn on check in pdf if the value is "false"
            List<Boolean> checkIfFalseValues = Arrays.asList(false, true, false, true);

            //we update expectedCustomValues inside insertAnswerAndSubstitutions
            pdfInfo.setCustomPdfSubstitutions(insertAnswersAndGetSubstitutions(handle, pdfInfo,
                    questions, booleanAnswerValues, textAnswerValues, dateAnswerValues, pdfDocFieldNames,
                    expectedValues, checkIfFalseValues));
            pdfInfo.getCustomPdfSubstitutions().add(new ProfileSubstitution(NAME_FIELD_NAME, "hruid"));
            //one more expectedCustomValue !

            pdfInfo.getExpectedCustomValues().put(NAME_FIELD_NAME, pdfInfo.getData().getUserHruid());

            pdfInfo.getCustomPdfSubstitutions().add(new ActivityDateSubstitution(ANOTHER_DATE, pdfInfo.getTestActivityId()));

            byte[] testPdfBytes = IOUtils.toByteArray(new FileInputStream(pdfFilePath));
            CustomTemplate customTemplate = new CustomTemplate(testPdfBytes);
            customTemplate.getSubstitutions().addAll(pdfInfo.getCustomPdfSubstitutions());

            PhysicianInstitutionTemplate institutionTemplate = new PhysicianInstitutionTemplate(
                    testPdfBytes, InstitutionType.INSTITUTION,
                    NAME_FIELD_NAME, INSTITUTION_FIELD_NAME, CITY_FIELD_NAME, STATE_FIELD_NAME);
            PhysicianInstitutionTemplate physicianTemplate = new PhysicianInstitutionTemplate(
                    testPdfBytes, InstitutionType.PHYSICIAN,
                    NAME_FIELD_NAME, INSTITUTION_FIELD_NAME, CITY_FIELD_NAME, STATE_FIELD_NAME);
            PhysicianInstitutionTemplate biopsyTemplate = new PhysicianInstitutionTemplate(
                    testPdfBytes, InstitutionType.INITIAL_BIOPSY,
                    NAME_FIELD_NAME, INSTITUTION_FIELD_NAME, CITY_FIELD_NAME, STATE_FIELD_NAME);

            MailingAddressTemplate addressTemplate = new MailingAddressTemplate(
                    IOUtils.toByteArray(new FileInputStream(MAILING_ADDRESS_FILE_PATH)),
                    MAILING_ADDRESS_FIRST_NAME_FIELD_VALUE,
                    MAILING_ADDRESS_LAST_NAME_FIELD_VALUE,
                    null,
                    null,
                    MAILING_ADDRESS_STREET_FIELD_VALUE,
                    MAILING_ADDRESS_CITY_FIELD_VALUE,
                    MAILING_ADDRESS_STATE_FIELD_VALUE,
                    MAILING_ADDRESS_ZIP_FIELD_VALUE,
                    MAILING_ADDRESS_COUNTRY_FIELD_VALUE,
                    MAILING_ADDRESS_PHONE_FIELD_VALUE);

            UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserId(pdfInfo.getData().getUserId()).get();
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_FIRST_NAME_FIELD_VALUE, profile.getFirstName());
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_LAST_NAME_FIELD_VALUE, profile.getLastName());

            MailAddress address = handle.attach(JdbiMailAddress.class)
                    .findDefaultAddressForParticipant(pdfInfo.getData().getUserGuid())
                    .orElseThrow(() -> new Exception("Could not find default address for participant " + pdfInfo.getData().getUserGuid()
                            + " while setting up a mailing address template for pdf configuration " + pdfInfo.getConfigurationId()));
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_STREET_FIELD_VALUE, address.getCombinedStreet());
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_CITY_FIELD_VALUE, address.getCity());
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_STATE_FIELD_VALUE, address.getState());
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_PHONE_FIELD_VALUE, address.getPhone());
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_ZIP_FIELD_VALUE, address.getZip());
            pdfInfo.getExpectedMailingAddressValues().put(MAILING_ADDRESS_COUNTRY_FIELD_VALUE, address.getCountry());

            JdbiMedicalProvider jdbiMedicalProvider = handle.attach(JdbiMedicalProvider.class);

            int nameIndex = 0;
            String fieldPostFix = "_0";
            for (Map.Entry<InstitutionType, Map<String, String>> instTypeExpectedVals
                    : pdfInfo.getExpectedValuesByInstitutionType().entrySet()) {
                InstitutionType institutionType = instTypeExpectedVals.getKey();
                String generatedGuid = GuidUtils.randomUserGuid();
                pdfInfo.getMedicalProviderRowsToDelete().add(generatedGuid);

                // put the vars we're using into the map to assert against them during subclass tests
                String institutionName = TestMedicalProviderData.INSTITUTION_NAME + nameIndex;
                String physicianName = NON_ASCII_NAME + nameIndex;
                String city = TestMedicalProviderData.INSTITUTION_CITY + nameIndex;
                String state = TestMedicalProviderData.INSTITUTION_STATE + nameIndex;

                Map<String, String> expectedPdfSubstitutions = new HashMap<>();


                if (institutionType == InstitutionType.PHYSICIAN) {
                    expectedPdfSubstitutions.put(NAME_FIELD_NAME + fieldPostFix, physicianName);
                }

                expectedPdfSubstitutions.put(INSTITUTION_FIELD_NAME + fieldPostFix, institutionName);
                expectedPdfSubstitutions.put(CITY_FIELD_NAME + fieldPostFix, city);
                expectedPdfSubstitutions.put(STATE_FIELD_NAME + fieldPostFix, state);
                pdfInfo.getExpectedValuesByInstitutionType().put(institutionType, expectedPdfSubstitutions);

                jdbiMedicalProvider.insert(new MedicalProviderDto(
                        null,
                        generatedGuid,
                        pdfInfo.getData().getUserId(),
                        pdfInfo.getData().getStudyId(),
                        institutionType,
                        institutionName,
                        physicianName,
                        city,
                        state,
                        null,
                        null,
                        null,
                        null));
                nameIndex++;
            }


            //adding extra physician row to make sure can reuse single input stream to render the templates for multiple dtos
            fieldPostFix = "_1";
            Map<String, String> physicianValues = pdfInfo.getExpectedValuesByInstitutionType().get(InstitutionType.PHYSICIAN);
            String institutionName = TestMedicalProviderData.INSTITUTION_NAME;
            String physicianName = "Doctor Nick";
            String city = TestMedicalProviderData.INSTITUTION_CITY;
            String state = TestMedicalProviderData.INSTITUTION_STATE;
            physicianValues.put(NAME_FIELD_NAME + fieldPostFix, physicianName);
            physicianValues.put(INSTITUTION_FIELD_NAME + fieldPostFix, institutionName);
            physicianValues.put(CITY_FIELD_NAME + fieldPostFix, city);
            physicianValues.put(STATE_FIELD_NAME + fieldPostFix, state);

            String generatedGuid = GuidUtils.randomUserGuid();
            pdfInfo.getMedicalProviderRowsToDelete().add(generatedGuid);
            jdbiMedicalProvider.insert(new MedicalProviderDto(
                    null,
                    generatedGuid,
                    pdfInfo.getData().getUserId(),
                    pdfInfo.getData().getStudyId(),
                    InstitutionType.PHYSICIAN,
                    institutionName,
                    physicianName,
                    city,
                    state,
                    null,
                    null,
                    null,
                    null));


            PdfConfigInfo info = new PdfConfigInfo(pdfInfo.getStudyId(),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), pdfInfo.getData().getUserId(),
                    "Made pdf config: " + info.getConfigName() + " for study:" + pdfInfo.getStudyId()
                            + " and user: " + pdfInfo.getData().getUserId());
            PdfVersion version = new PdfVersion("v1", revId);
            version.addDataSource(new PdfDataSource(PdfDataSourceType.PARTICIPANT));
            version.addDataSource(new PdfActivityDataSource(pdfInfo.getTestActivityId(), pdfInfo.getTestActivityVersionId()));

            PdfConfiguration config = new PdfConfiguration(info, version);
            config.addTemplate(institutionTemplate);
            config.addTemplate(physicianTemplate);
            config.addTemplate(biopsyTemplate);
            config.addTemplate(customTemplate);
            config.addTemplate(addressTemplate);

            PdfDao pdfDao = handle.attach(PdfDao.class);
            pdfInfo.setConfigurationId(pdfDao.insertNewConfig(config));

            config = pdfDao.findFullConfig(version.getId());
            pdfInfo.setPdfConfiguration(config);

            pdfInfo.setInstitutionTemplate((PhysicianInstitutionTemplate) config.getTemplates().get(0));
            pdfInfo.setPhysicianTemplate((PhysicianInstitutionTemplate) config.getTemplates().get(1));
            pdfInfo.setBiopsyTemplate((PhysicianInstitutionTemplate) config.getTemplates().get(2));
            pdfInfo.setMailingAddressTemplate((MailingAddressTemplate) config.getTemplates().get(4));

            pdfInfo.setCustomTemplate((CustomTemplate) config.getTemplates().get(3));
            pdfInfo.setCustomPdfSubstitutions(pdfInfo.getCustomTemplate().getSubstitutions());
        });

        return pdfInfo;
    }

    private static void createTestingForm(Document doc) {
        Paragraph title = new Paragraph("Testing Form")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(16);
        doc.add(title);
        doc.add(new Paragraph("Physician:").setFontSize(12));
        doc.add(new Paragraph("Institution:").setFontSize(12));
        doc.add(new Paragraph("City:").setFontSize(12));
        doc.add(new Paragraph("State:").setFontSize(12));
        doc.add(new Paragraph("Checkbox1").setFontSize(12));
        doc.add(new Paragraph("Checkbox2").setFontSize(12));
        doc.add(new Paragraph("Checkbox3").setFontSize(12));
        doc.add(new Paragraph("Checkbox4").setFontSize(12));
        doc.add(new Paragraph("Favorite Color:").setFontSize(12));
        doc.add(new Paragraph("Date of a thing:").setFontSize(12));
        doc.add(new Paragraph("An activity date:").setFontSize(12));

        PdfAcroForm form = PdfAcroForm.getAcroForm(doc.getPdfDocument(), true);

        int fieldHeight = 26;
        int startingYPosition = 753;

        PdfTextFormField nameField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(99, 753, 425, 15), NAME_FIELD_NAME, "");
        form.addField(nameField);

        PdfTextFormField institutionField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(99, 728, 425, 15), INSTITUTION_FIELD_NAME, "");
        form.addField(institutionField);

        PdfTextFormField cityField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(99, 703, 425, 15), CITY_FIELD_NAME, "");
        form.addField(cityField);

        PdfTextFormField stateField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(99, 677, 425, 15), STATE_FIELD_NAME, "");
        form.addField(stateField);

        // todo fix x/y coords


        // checkbox that should be checked because checkIfFalse is false
        PdfButtonFormField checkField1 = PdfFormField.createCheckBox(doc.getPdfDocument(),
                new Rectangle(119, 651, 15, 15),
                CHECKBOX1,
                "Off",
                PdfFormField.TYPE_CHECK);

        // checkbox that should be checked because checkIfFalse is true
        PdfButtonFormField checkField2 = PdfFormField.createCheckBox(doc.getPdfDocument(),
                new Rectangle(119, 624, 15, 15),
                CHECKBOX2,
                "Off",
                PdfFormField.TYPE_CHECK);

        // checkbox that should be unchecked because checkIfFalse is false
        PdfButtonFormField checkField3 = PdfFormField.createCheckBox(doc.getPdfDocument(),
                new Rectangle(119, 624, 15, 15),
                CHECKBOX3,
                "Off",
                PdfFormField.TYPE_CHECK);

        // checkbox that should be unchecked because checkIfFalse is true
        PdfButtonFormField checkField4 = PdfFormField.createCheckBox(doc.getPdfDocument(),
                new Rectangle(119, 597, 15, 15),
                CHECKBOX4,
                "Off",
                PdfFormField.TYPE_CHECK);
        form.addField(checkField1);
        form.addField(checkField2);
        form.addField(checkField3);
        form.addField(checkField4);

        PdfTextFormField favoriteColorField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(120, 545, 425, 15),
                FAVORITE_COLOR, "");
        form.addField(favoriteColorField);

        PdfTextFormField dateField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(120, 519, 425, 15), DATE, "");
        form.addField(dateField);

        PdfTextFormField anotherDateField = PdfTextFormField.createText(doc.getPdfDocument(),
                new Rectangle(120, 493, 425, 15), ANOTHER_DATE, "");
        form.addField(anotherDateField);
    }

    private static List<QuestionDef> insertQuestions(Handle handle, PdfInfo pdfInfo, List<QuestionType> questionTypes) {
        long now = Instant.now().toEpochMilli();
        List<QuestionBlockDef> questionBlockDefs = new ArrayList<>();
        for (int i = 0; i < questionTypes.size(); i++) {
            String stableId = "SIDQ" + i + now;

            switch (questionTypes.get(i)) {
                case BOOLEAN:
                    Template yesTmpl = new Template(TemplateType.TEXT, null, "yes");
                    Template noTmpl = new Template(TemplateType.TEXT, null, "no");
                    questionBlockDefs.add(new QuestionBlockDef(BoolQuestionDef.builder().setStableId(stableId)
                            .setPrompt(new Template(TemplateType.TEXT, null, "prompt"))
                            .setTrueTemplate(yesTmpl)
                            .setFalseTemplate(noTmpl)
                            .addValidation(new RequiredRuleDef(null))
                            .build()));
                    break;
                case TEXT:
                    Template textTmpl = Template.text("this is text");
                    questionBlockDefs.add(new QuestionBlockDef(TextQuestionDef.builder(
                            TextInputType.TEXT,
                            stableId,
                            textTmpl)
                            .addValidation(new LengthRuleDef(null, 1, 9000))
                            .build()));
                    break;
                case DATE:
                    Template birthPrompt = buildTemplate("birth_date", "Date of birth *");
                    Template birthReqHint = buildTemplate("birth_req", "Please enter date of birth in MM DD YYYY format");
                    Template monthReqHint = buildTemplate("month_req", "Please enter birth month in MM format");
                    Template dayReqHint = buildTemplate("day_req", "Please enter birth day in DD format");
                    Template yearReqHint = buildTemplate("year_req", "Please enter birth year in YYYY format");
                    List<RuleDef> birthRules = Arrays.asList(
                            new RequiredRuleDef(birthReqHint),
                            new DateFieldRequiredRuleDef(RuleType.MONTH_REQUIRED, monthReqHint),
                            new DateFieldRequiredRuleDef(RuleType.DAY_REQUIRED, dayReqHint),
                            new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, yearReqHint));
                    List<DateFieldType> birthFields = Arrays.asList(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR);
                    questionBlockDefs.add(new QuestionBlockDef(
                            DateQuestionDef.builder(DateRenderMode.TEXT, "SID3" + now, birthPrompt)
                                    .setDisplayCalendar(false)
                                    .setRenderMode(DateRenderMode.TEXT)
                                    .addFields(birthFields)
                                    .addValidations(birthRules)
                                    .build()));
                    break;
                default:
                    throw new UnsupportedOperationException("Currently do not support inserting questions of type: " + questionTypes.get(i)
                            + " in PdfGeneration");
            }
        }

        FormActivityDef form = insertDummyActivity(handle, pdfInfo, pdfInfo.getData().getStudyGuid(), questionBlockDefs);
        ActivityInstanceDto dto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(form.getActivityId(), pdfInfo.getData().getUserGuid(),
                        pdfInfo.getData().getUserGuid(), InstanceStatusType.COMPLETE, false);
        pdfInfo.setTestActivityInstanceGuid(dto.getGuid());
        pdfInfo.setTestActivityInstanceId(dto.getId());

        return questionBlockDefs.stream().map(QuestionBlockDef::getQuestion).collect(Collectors.toList());
    }

    private static List<PdfSubstitution> insertAnswersAndGetSubstitutions(Handle handle,
                                                                          PdfInfo pdfInfo,
                                                                          List<QuestionDef> questions,
                                                                          List<Boolean> booleanAnswerValues,
                                                                          List<String> textAnswerValues,
                                                                          List<DateValue> dateAnswerValues,
                                                                          List<String> fieldNames,
                                                                          List<String> expectedValues,
                                                                          List<Boolean> checkIfFalseValues) {
        int counter = -1;
        int booleanQuestionCounter = -1;
        int textQuestionCounter = -1;
        int dateQuestionCounter = -1;

        List<PdfSubstitution> newSubstitutions = new ArrayList<>();
        var answerDao = handle.attach(AnswerDao.class);
        for (QuestionDef question : questions) {
            counter++;
            pdfInfo.getExpectedCustomValues().put(fieldNames.get(counter), expectedValues.get(counter));
            switch (question.getQuestionType()) {
                case BOOLEAN:
                    booleanQuestionCounter++;
                    answerDao.createAnswer(pdfInfo.getData().getUserId(), pdfInfo.getTestActivityInstanceId(),
                            new BoolAnswer(null, question.getStableId(), null, booleanAnswerValues.get(booleanQuestionCounter)));
                    newSubstitutions.add(new BooleanAnswerSubstitution(
                            fieldNames.get(counter),
                            pdfInfo.getTestActivityId(),
                            question.getStableId(),
                            checkIfFalseValues.get(booleanQuestionCounter), null));
                    break;
                case TEXT:
                    textQuestionCounter++;
                    answerDao.createAnswer(pdfInfo.getData().getUserId(), pdfInfo.getTestActivityInstanceId(),
                            new TextAnswer(null, question.getStableId(), null, textAnswerValues.get(textQuestionCounter)));
                    newSubstitutions.add(new AnswerSubstitution(fieldNames.get(counter),
                            pdfInfo.getTestActivityId(), question.getQuestionType(), question.getStableId()));
                    break;
                case DATE:
                    dateQuestionCounter++;
                    answerDao.createAnswer(pdfInfo.getData().getUserId(), pdfInfo.getTestActivityInstanceId(),
                            new DateAnswer(null, question.getStableId(), null, dateAnswerValues.get(dateQuestionCounter)));
                    newSubstitutions.add(new AnswerSubstitution(fieldNames.get(counter),
                            pdfInfo.getTestActivityId(), question.getQuestionType(), question.getStableId()));
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Tried to insert answer for unsupported question type: " + question.getQuestionType()
                    );
            }
        }
        return newSubstitutions;
    }

    private static FormActivityDef insertDummyActivity(Handle handle, PdfInfo pdfInfo,
                                                       String studyGuid, List<QuestionBlockDef> questionBlocks) {
        FormActivityDef.FormBuilder builder = FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "test activity"));

        for (QuestionBlockDef questionBlock : questionBlocks) {
            builder.addSection(new FormSectionDef(null, Collections.singletonList(questionBlock)));
        }
        FormActivityDef form = builder.build();

        ActivityVersionDto activityVersion = handle.attach(ActivityDao.class)
                .insertActivity(form, RevisionMetadata.now(pdfInfo.getData().getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());

        pdfInfo.setTestActivityId(form.getActivityId());
        pdfInfo.setTestActivityVersionId(activityVersion.getId());

        return form;
    }

    public static void removeConfigurationData(PdfInfo pdfInfo) {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            TestDataSetupUtil.deleteEnrollmentStatus(handle, pdfInfo.getData());
            TestDataSetupUtil.deleteTestMailAddress(handle, pdfInfo.getData());

            JdbiMedicalProvider jdbiMedicalProvider = handle.attach(JdbiMedicalProvider.class);
            pdfInfo.getMedicalProviderRowsToDelete().forEach(guid -> jdbiMedicalProvider.deleteByGuid(guid));

            if (pdfInfo.getEventConfigurationId() != null) {
                handle.attach(JdbiEventConfiguration.class).deleteById(pdfInfo.getEventConfigurationId());
                handle.attach(JdbiUserNotificationPdf.class).deleteByPdfDocumentConfigurationId(pdfInfo.getConfigurationId());
            }

            handle.attach(PdfDao.class).deleteAllConfigVersions(pdfInfo.getConfigurationId());
        });
    }

    private PdfTestingUtil() {
    }

    public static final class TestMedicalProviderData {
        public static final String GUID = "BBAB095933";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final String INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final String PHYSICIAN_NAME = "House MD";
        public static final String INSTITUTION_CITY = "West Windsor Township";
        public static final String INSTITUTION_STATE = "New Jersey";
    }

    public static class PdfDbInfo {
        private long mappingId;
        private long pdfConfigId;

        PdfDbInfo(long mappingId, long pdfConfigId) {
            this.mappingId = mappingId;
            this.pdfConfigId = pdfConfigId;
        }

        public long mappingId() {
            return mappingId;
        }

        public long pdfConfigId() {
            return pdfConfigId;
        }
    }

    public static class PdfInfo {
        private final Map<InstitutionType, Map<String, String>> expectedValuesByInstitutionType = new LinkedHashMap<>();
        private TestDataSetupUtil.GeneratedTestData data;
        private PdfConfiguration pdfConfiguration;
        private PhysicianInstitutionTemplate institutionTemplate;
        private PhysicianInstitutionTemplate physicianTemplate;
        private PhysicianInstitutionTemplate biopsyTemplate;
        private MailingAddressTemplate mailingAddressTemplate;
        private CustomTemplate customTemplate;
        private Collection<PdfSubstitution> customPdfSubstitutions;
        private long configurationId;
        private List<String> medicalProviderRowsToDelete = new ArrayList<>();
        private long testActivityId = -1L;
        private long testActivityVersionId = -1L;
        private long testActivityInstanceId = -1L;
        private String testActivityInstanceGuid;
        private Map<String, String> expectedCustomValues = new HashMap<>();
        private QuestionDef question;
        private Map<String, String> expectedMailingAddressValues = new HashMap<>();
        private Long eventConfigurationId;

        public TestDataSetupUtil.GeneratedTestData getData() {
            return data;
        }

        public void setData(TestDataSetupUtil.GeneratedTestData data) {
            this.data = data;
        }

        public long getConfigurationId() {
            return configurationId;
        }

        public void setConfigurationId(long configurationId) {
            this.configurationId = configurationId;
        }

        public PdfConfiguration getPdfConfiguration() {
            return pdfConfiguration;
        }

        public void setPdfConfiguration(PdfConfiguration pdfConfiguration) {
            this.pdfConfiguration = pdfConfiguration;
        }

        public PhysicianInstitutionTemplate getInstitutionTemplate() {
            return institutionTemplate;
        }

        public void setInstitutionTemplate(PhysicianInstitutionTemplate institutionTemplate) {
            this.institutionTemplate = institutionTemplate;
        }

        public PhysicianInstitutionTemplate getPhysicianTemplate() {
            return physicianTemplate;
        }

        public void setPhysicianTemplate(PhysicianInstitutionTemplate physicianTemplate) {
            this.physicianTemplate = physicianTemplate;
        }

        public PhysicianInstitutionTemplate getBiopsyTemplate() {
            return biopsyTemplate;
        }

        public void setBiopsyTemplate(PhysicianInstitutionTemplate biopsyTemplate) {
            this.biopsyTemplate = biopsyTemplate;
        }

        public MailingAddressTemplate getMailingAddressTemplate() {
            return mailingAddressTemplate;
        }

        public void setMailingAddressTemplate(MailingAddressTemplate mailingAddressTemplate) {
            this.mailingAddressTemplate = mailingAddressTemplate;
        }

        public CustomTemplate getCustomTemplate() {
            return customTemplate;
        }

        public void setCustomTemplate(CustomTemplate customTemplate) {
            this.customTemplate = customTemplate;
        }

        public Collection<PdfSubstitution> getCustomPdfSubstitutions() {
            return customPdfSubstitutions;
        }

        public void setCustomPdfSubstitutions(Collection<PdfSubstitution> customPdfSubstitutions) {
            this.customPdfSubstitutions = customPdfSubstitutions;
        }

        public Map<String, String> getExpectedCustomValues() {
            return expectedCustomValues;
        }

        public void setExpectedCustomValues(Map<String, String> expectedCustomValues) {
            this.expectedCustomValues = expectedCustomValues;
        }

        public List<String> getMedicalProviderRowsToDelete() {
            return medicalProviderRowsToDelete;
        }

        public void setMedicalProviderRowsToDelete(List<String> medicalProviderRowsToDelete) {
            this.medicalProviderRowsToDelete = medicalProviderRowsToDelete;
        }

        public long getTestActivityId() {
            return testActivityId;
        }

        public void setTestActivityId(long testActivityId) {
            this.testActivityId = testActivityId;
        }

        public long getTestActivityVersionId() {
            return testActivityVersionId;
        }

        public void setTestActivityVersionId(long testActivityVersionId) {
            this.testActivityVersionId = testActivityVersionId;
        }

        public long getTestActivityInstanceId() {
            return testActivityInstanceId;
        }

        public void setTestActivityInstanceId(long testActivityInstanceId) {
            this.testActivityInstanceId = testActivityInstanceId;
        }

        public String getTestActivityInstanceGuid() {
            return testActivityInstanceGuid;
        }

        public void setTestActivityInstanceGuid(String testActivityInstanceGuid) {
            this.testActivityInstanceGuid = testActivityInstanceGuid;
        }

        public Map<InstitutionType, Map<String, String>> getExpectedValuesByInstitutionType() {
            return expectedValuesByInstitutionType;
        }

        public Map<String, String> getExpectedMailingAddressValues() {
            return expectedMailingAddressValues;
        }

        public void setExpectedMailingAddressValues(Map<String, String> expectedMailingAddressValues) {
            this.expectedMailingAddressValues = expectedMailingAddressValues;
        }

        public QuestionDef getQuestionDef() {
            return question;
        }

        public void setQuestionDef(QuestionDef question) {
            this.question = question;
        }

        public Long getEventConfigurationId() {
            return eventConfigurationId;
        }

        public void setEventConfigurationId(long eventConfigurationId) {
            this.eventConfigurationId = eventConfigurationId;
        }

        public long getStudyId() {
            return this.data.getStudyId();
        }
    }
}
