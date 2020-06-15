package org.broadinstitute.ddp.export;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVReader;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.MedicalRecordService;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataExporterTest extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(DataExporterTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String TEST_USER_GUID = "blah-guid";
    LocalDate testDateOfMajority = LocalDate.now();
    DateValue testBirthdate = new DateValue(1978, 5, 16);
    private MedicalRecordService mockMedicalRecordService;
    private GovernancePolicy mockGovernancePolicy;
    private DataExporter exporter;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
    }

    @AfterClass
    public static void cleanup() throws Exception {
        TestDataSetupUtil.deleteGeneratedTestData();
    }

    @Before
    public void setupTest() {
        exporter = new DataExporter(cfg);
        mockMedicalRecordService = Mockito.mock(MedicalRecordService.class);
        Mockito.when(mockMedicalRecordService.getDateOfBirth(Mockito.any(Handle.class),
                Mockito.anyLong(), Mockito.anyLong())).thenReturn(Optional.of(testBirthdate));
        Mockito.when(mockMedicalRecordService.fetchBloodAndTissueConsents(Mockito.any(Handle.class),
                Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyString()))
                .thenReturn(new MedicalRecordService.ParticipantConsents(true, true));

        AgeOfMajorityRule ageOfMajorityRule = Mockito.mock(AgeOfMajorityRule.class);

        mockGovernancePolicy = Mockito.mock(GovernancePolicy.class);
        Mockito.when(mockGovernancePolicy.getApplicableAgeOfMajorityRule(Mockito.any(Handle.class),
                Mockito.any(PexInterpreter.class),
                Mockito.anyString())).thenReturn(Optional.of(ageOfMajorityRule));

        Mockito.when(ageOfMajorityRule.getDateOfMajority(Mockito.any(LocalDate.class))).thenReturn(testDateOfMajority);

    }

    @Test
    public void testExtractActivities() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef def = FormActivityDef.generalFormBuilder("act", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .build();
            ActivityVersionDto versioDto = handle.attach(ActivityDao.class)
                    .insertActivity(def, RevisionMetadata.now(testData.getUserId(), "test"));

            List<ActivityExtract> extracts = exporter.extractActivities(handle, testData.getTestingStudy());

            assertNotNull(extracts);
            assertEquals(1, extracts.size());
            assertEquals("act_v1", extracts.get(0).getTag());

            ActivityDef actualDef = extracts.get(0).getDefinition();
            assertEquals(def.getActivityCode(), actualDef.getActivityCode());
            assertEquals(ActivityType.FORMS, actualDef.getActivityType());

            ActivityVersionDto actualVersion = extracts.get(0).getVersionDto();
            assertEquals(versioDto.getVersionTag(), actualVersion.getVersionTag());
            assertEquals(versioDto.getRevStart(), actualVersion.getRevStart());
            assertEquals(versioDto.getRevEnd(), actualVersion.getRevEnd());

            handle.rollback();
        });
    }

    @Test
    public void testExtractParticipants() {
        TransactionWrapper.useTxn(handle -> {
            // Create a dummy activity
            FormActivityDef def = FormActivityDef.generalFormBuilder("act", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .addSection(new FormSectionDef(null, Arrays.asList(
                            new QuestionBlockDef(TextQuestionDef
                                    .builder(TextInputType.TEXT, "Q_TEXT", Template.text("text prompt"))
                                    .setDeprecated(true)
                                    .build()))))
                    .build();
            ActivityVersionDto versioDto = handle.attach(ActivityDao.class)
                    .insertActivity(def, RevisionMetadata.now(testData.getUserId(), "test"));

            // Enroll the user in study
            TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.ENROLLED);

            // Create a address for them
            MailAddress address = new MailAddress("foo bar", "85 Main St", "Apt 2", "Boston", "MA", "US", "02115", "6171112233",
                    null, "", DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS, true);
            JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
            jdbiMailAddress.insertAddress(address, testData.getUserGuid(), testData.getUserGuid());
            jdbiMailAddress.setDefaultAddressForParticipant(address.getGuid());

            // Create a medical provider for them
            MedicalProviderDto provider = new MedicalProviderDto(null, UUID.randomUUID().toString(),
                    testData.getUserId(), testData.getStudyId(),
                    InstitutionType.PHYSICIAN, "inst a", "dr. a", "boston", "ma", null, null, null, "street1");
            handle.attach(JdbiMedicalProvider.class).insert(provider);

            // Create an instance for them
            ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(def.getActivityId(), testData.getUserGuid());

            // Answer deprecated question
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new TextAnswer(null, "Q_TEXT", null, "my value"));

            // Complete the instance
            long completeTimestamp = Instant.now().toEpochMilli();
            handle.attach(ActivityInstanceStatusDao.class)
                    .insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, completeTimestamp, testData.getUserGuid());

            // Extract and test
            List<Participant> extracts = exporter.extractParticipantDataSet(handle, testData.getTestingStudy());

            assertNotNull(extracts);
            assertEquals(1, extracts.size());

            Participant actual = extracts.get(0);
            assertEquals(EnrollmentStatusType.ENROLLED, actual.getStatus().getEnrollmentStatus());
            assertEquals(testData.getUserGuid(), actual.getStatus().getUserGuid());
            assertEquals(testData.getStudyGuid(), actual.getStatus().getStudyGuid());

            assertEquals(testData.getTestingUser().getEmail(), actual.getUser().getEmail());

            assertTrue(actual.getUser().hasProfile());
            assertEquals(testData.getProfile().getFirstName(), actual.getUser().getProfile().getFirstName());
            assertEquals(testData.getProfile().getLastName(), actual.getUser().getProfile().getLastName());

            assertTrue(actual.getUser().hasAddress());
            assertEquals(address.getGuid(), actual.getUser().getAddress().getGuid());
            assertEquals(address.getName(), actual.getUser().getAddress().getName());
            assertEquals(address.getStreet1(), actual.getUser().getAddress().getStreet1());

            assertEquals(1, actual.getProviders().size());
            assertEquals(provider.getPhysicianName(), actual.getProviders().get(0).getPhysicianName());
            assertEquals(provider.getStreet(), actual.getProviders().get(0).getStreet());

            assertEquals(1, actual.getAllResponses().size());
            assertEquals(instanceDto.getGuid(), actual.getAllResponses().get(0).getGuid());

            ActivityResponse actualInstance = actual.getResponses(def.getTag()).get(0);
            assertNotNull(actualInstance);
            assertEquals(instanceDto.getGuid(), actualInstance.getGuid());
            assertEquals(instanceDto.getCreatedAtMillis(), actualInstance.getCreatedAt());
            assertEquals((Long) completeTimestamp, actualInstance.getFirstCompletedAt());

            assertNotNull(actualInstance.getLatestStatus());
            assertEquals(InstanceStatusType.COMPLETE, actualInstance.getLatestStatus().getType());

            FormResponse actualForm = (FormResponse) actualInstance;
            assertEquals(1, actualForm.getAnswers().size());
            assertEquals("my value", actualForm.getAnswers().get(0).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testEnrichWithDSMEventDates() {
        Participant participant = new Participant(
                new EnrollmentStatusDto(0L,
                        testData.getUserId(),
                        testData.getUserGuid(),
                        testData.getStudyId(),
                        testData.getStudyGuid(),
                        EnrollmentStatusType.ENROLLED,
                        Instant.now().toEpochMilli(),
                        null),
                new User(testData.getUserId(),
                        testData.getUserGuid(),
                        null,
                        null,
                        null,
                        false,
                        0L,
                        0L,
                        null,
                        0,
                        0,
                        0L));

        assertNull(participant.getBirthDate());
        assertNull(participant.getDateOfMajority());

        TransactionWrapper.useTxn(handle -> exporter.enrichWithDSMEventDates(handle,
                mockMedicalRecordService,
                mockGovernancePolicy,
                testData.getStudyId(),
                Collections.singletonList(participant)));

        assertEquals(testBirthdate.asLocalDate().orElse(null), participant.getBirthDate());
        assertEquals(testDateOfMajority, participant.getDateOfMajority());
    }

    @Test
    public void testExtractActivityDefinitions() {
        TransactionWrapper.useTxn(handle -> {
            PicklistQuestionDef picklistDef = PicklistQuestionDef.builder().setStableId("TEST_PLQ")
                    .setSelectMode(PicklistSelectMode.MULTIPLE)
                    .setRenderMode(PicklistRenderMode.LIST)
                    .setPrompt(new Template(TemplateType.TEXT, null, "picklist prompt"))
                    .addOption(new PicklistOptionDef("OPTION_YES", new Template(TemplateType.TEXT, null, "yes")))
                    .addOption(new PicklistOptionDef("OPTION_NO", new Template(TemplateType.TEXT, null, "no")))
                    .addOption(new PicklistOptionDef("OPTION_NA", new Template(TemplateType.TEXT, null, "n/a")))
                    .build();

            DateQuestionDef dateDef = DateQuestionDef.builder().setStableId("TEST_DATEQ")
                    .setRenderMode(DateRenderMode.TEXT)
                    .setPrompt(new Template(TemplateType.TEXT, null, "date prompt"))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .build();

            TextQuestionDef textDef = TextQuestionDef.builder().setStableId("TEST_TEXTQ")
                    .setInputType(TextInputType.TEXT)
                    .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                    .build();

            NumericQuestionDef numericDef = NumericQuestionDef.builder().setStableId("TEST_NUMERICQ")
                    .setNumericType(NumericType.INTEGER)
                    .setPrompt(new Template(TemplateType.TEXT, null, "numeric prompt"))
                    .build();

            TextQuestionDef childTextDef = TextQuestionDef.builder().setStableId("TEST_CHILD_TEXTQ")
                    .setInputType(TextInputType.TEXT)
                    .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                    .build();

            DateQuestionDef childDateDef = DateQuestionDef.builder().setStableId("TEST_CHILD_DATEQ")
                    .setRenderMode(DateRenderMode.TEXT)
                    .setPrompt(new Template(TemplateType.TEXT, null, "date prompt"))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .build();

            CompositeQuestionDef compQ = CompositeQuestionDef.builder()
                    .setStableId("TEST_COMPOSITEQ")
                    .setPrompt(new Template(TemplateType.TEXT, null, "Comp1"))
                    .addChildrenQuestions(childTextDef, childDateDef)
                    .setAllowMultiple(true)
                    .setAddButtonTemplate(new Template(TemplateType.TEXT, null, "Add Button Text"))
                    .setAdditionalItemTemplate(new Template(TemplateType.TEXT, null, "Add Another Item"))
                    .build();

            String activityCode = "TEST_ACT";
            FormActivityDef def = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "test activity"))
                    .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(textDef, picklistDef, dateDef, numericDef, compQ)))
                    .build();


            ActivityVersionDto versioDto = handle.attach(ActivityDao.class)
                    .insertActivity(def, RevisionMetadata.now(testData.getUserId(), "test"));

            List<ActivityExtract> extracts = exporter.extractActivities(handle, testData.getTestingStudy());

            assertNotNull(extracts);
            assertEquals(1, extracts.size());
            assertEquals("TEST_ACT_v1", extracts.get(0).getTag());

            ActivityDef actualDef = extracts.get(0).getDefinition();
            assertEquals(def.getActivityCode(), actualDef.getActivityCode());
            assertEquals(ActivityType.FORMS, actualDef.getActivityType());

            ActivityVersionDto actualVersion = extracts.get(0).getVersionDto();
            assertEquals(versioDto.getVersionTag(), actualVersion.getVersionTag());
            assertEquals(versioDto.getRevStart(), actualVersion.getRevStart());
            assertEquals(versioDto.getRevEnd(), actualVersion.getRevEnd());

            ActivityExtract activity = extracts.get(0);
            ActivityResponseCollector formatter = new ActivityResponseCollector(activity.getDefinition());
            Map<String, Object> data = formatter.questionDefinitions();
            //Object is List<Map<String, Object>>
            Object value = data.get("questions");

            //covert to json and verify
            Gson gson = new GsonBuilder().serializeNulls().create();
            String esDoc = gson.toJson(value);
            LOG.info("JSON DATA: \n {} ", esDoc);
            Assert.assertTrue(esDoc.contains("{\"stableId\":\"TEST_TEXTQ\",\"questionType\":\"TEXT\",\"questionText\":\"text prompt\"}"));

            //check Date Question
            Assert.assertTrue(esDoc.contains("{\"stableId\":\"TEST_DATEQ\",\"questionType\":\"DATE\",\"questionText\":\"date prompt\"}"));

            //check Numeric Question
            Assert.assertTrue(esDoc.contains("{\"stableId\":\"TEST_NUMERICQ\","
                    + "\"questionType\":\"NUMERIC\",\"questionText\":\"numeric prompt\","
                    + "\"numericType\":\"INTEGER\"}"));

            //check Picklist Question
            Assert.assertTrue(esDoc.contains("{\"stableId\":\"TEST_PLQ\",\"questionType\":\"PICKLIST\",\"questionText\":"
                    + "\"picklist prompt\",\"selectMode\":\"MULTIPLE\",\"options\":[{\"optionStableId\":\"OPTION_YES\","
                    + "\"optionText\":\"yes\"},{\"optionStableId\":"
                    + "\"OPTION_NO\",\"optionText\":\"no\"},{\"optionStableId\":\"OPTION_NA\",\"optionText\":\"n/a\"}]}"));

            //check Composite Question
            Assert.assertTrue(esDoc.contains("{\"stableId\":\"TEST_COMPOSITEQ\",\"allowMultiple\":true,\"questionType\":\"COMPOSITE\","
                    + "\"questionText\":\"Comp1\",\"childQuestions\":[{\"stableId\":\"TEST_CHILD_TEXTQ\",\"questionType\":\"TEXT\","
                    + "\"questionText\":\"text prompt\"},{\"stableId\":\"TEST_CHILD_DATEQ\",\"questionType\":\"DATE\","
                    + "\"questionText\":\"date prompt\"}]}]"));

            handle.rollback();
        });
    }

    @Test
    public void testExportDataSetAsJsonEmptyActivity() {
        DataExporterTestData dataExporterTestData = DataExporterTestData.createWithEmptyActivity();
        List<ActivityExtract> activities = dataExporterTestData.getActivities();
        List<Participant> participants = dataExporterTestData.getParticipants();

        StudyExtract studyExtract = new StudyExtract(activities, Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        // Run the test!
        boolean exportStructuredDocument = false;
        Map<String, String> result = TransactionWrapper.withTxn(handle ->
                exporter.prepareParticipantRecordsForJSONExport(
                        studyExtract, participants, exportStructuredDocument, handle, mockMedicalRecordService)
        );
        assertEquals(1, result.size());

        String output = result.get(TEST_USER_GUID);
        assertFalse(output.isEmpty());

        Gson gson = new GsonBuilder().create();
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> results = gson.fromJson(output, type);

        List<String> headers = Arrays.asList("participant_guid", "participant_hruid", "legacy_altpid", "legacy_shortid",
                "first_name", "last_name", "email", "do_not_contact", "created_at", "status", "status_timestamp",
                "ACT_v1", "ACT_v1_status", "ACT_v1_created_at", "ACT_v1_updated_at", "ACT_v1_completed_at",
                "Q_BIRTHDAY_DAY", "Q_BIRTHDAY_MONTH", "Q_BIRTHDAY_YEAR", "Q_BOOL", "Q_TEXT", "Q_NUMERIC",
                "ADDRESS_FULLNAME", "ADDRESS_STREET1", "ADDRESS_STREET2", "ADDRESS_CITY", "ADDRESS_STATE",
                "ADDRESS_ZIP", "ADDRESS_COUNTRY", "ADDRESS_PHONE", "ADDRESS_PLUSCODE", "ADDRESS_STATUS", "PHYSICIAN");

        List<String> answers = Arrays.asList(TEST_USER_GUID, "blah-hruid", "blah-legacy-altpid", "blah-shortid",
                "first-foo", "last-bar", "test@datadonationplatform.org", "true", "10/18/2018 20:18:01", "ENROLLED", "10/18/2018 20:18:01",
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "", "", "", "", "");

        Map<String, String> expected = IntStream.range(0, Math.min(headers.size(), answers.size()))
                .boxed()
                .collect(Collectors.toMap(
                        headers::get,
                        answers::get));

        // We expect all the entries! Some of them may be null.
        assertTrue(results.keySet().containsAll(headers));

        for (String key : results.keySet()) {
            if (expected.get(key).equals("")) {
                // Why are we null checking instead of "" checking? Because stupid streams have a stupid bug, where you can't fold null
                // into a map collector. Its not part of the production code anyway. Stupid Streams!
                assertNull(results.get(key));
            } else {
                assertEquals(expected.get(key), results.get(key));
            }
        }
    }

    private void evaluateJsonOutput(String output) {
        assertFalse(output.isEmpty());

        Gson gson = new GsonBuilder().create();
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> results = gson.fromJson(output, type);

        List<String> headers = Arrays.asList("participant_guid", "participant_hruid", "legacy_altpid", "legacy_shortid",
                "first_name", "last_name", "email", "do_not_contact", "created_at", "status", "status_timestamp",
                "ACT_v1", "ACT_v1_status", "ACT_v1_created_at", "ACT_v1_updated_at", "ACT_v1_completed_at",
                "Q_BIRTHDAY_DAY", "Q_BIRTHDAY_MONTH", "Q_BIRTHDAY_YEAR", "Q_BOOL", "Q_TEXT", "Q_NUMERIC",
                "ADDRESS_FULLNAME", "ADDRESS_STREET1", "ADDRESS_STREET2", "ADDRESS_CITY", "ADDRESS_STATE",
                "ADDRESS_ZIP", "ADDRESS_COUNTRY", "ADDRESS_PHONE", "ADDRESS_STATUS", "PHYSICIAN", "ADDRESS_PLUSCODE");
        List<String> answers = Arrays.asList(TEST_USER_GUID, "blah-hruid", "blah-legacy-altpid", "blah-shortid",
                "first-foo", "last-bar", "test@datadonationplatform.org", "true", "10/18/2018 20:18:01",
                "ENROLLED", "10/18/2018 20:18:01",
                "instance-guid-xyz", "COMPLETE", "10/18/2018 20:18:01", "10/18/2018 20:18:21", "10/18/2018 20:18:11",
                "16", "05", "1978", "true", "john smith", "25",
                "foo bar", "85 Main St", "Apt 2", "Boston", "MA", "02115", "US", "6171112233", "INVALID",
                "dr. a;inst a;boston;ma", "87JC9WFP+HV", "");

        Map<String, String> expected = IntStream.range(0, Math.min(headers.size(), answers.size()))
                .boxed()
                .collect(Collectors.toMap(
                        headers::get,
                        answers::get));

        for (String key : results.keySet()) {
            assertEquals("Failure for lookup of key " + key, expected.get(key), results.get(key));
        }
    }

    @Test
    public void testExportDataSetAsJson() {
        DataExporterTestData dataExporterTestData = new DataExporterTestData(false, false);
        List<ActivityExtract> activities = dataExporterTestData.getActivities();
        List<Participant> participants = dataExporterTestData.getParticipants();
        StudyExtract studyExtract = new StudyExtract(activities, Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());

        // Run the test!
        boolean exportStructuredDocument = false;
        Map<String, String> result = TransactionWrapper.withTxn(handle ->
                exporter.prepareParticipantRecordsForJSONExport(
                        studyExtract, participants, exportStructuredDocument, handle, mockMedicalRecordService
                )
        );
        assertEquals(1, result.size());

        evaluateJsonOutput(result.get(TEST_USER_GUID));
    }

    @Test
    public void testExportDataSetAsStructuredJson() {
        DataExporterTestData dataExporterTestData = new DataExporterTestData(false, false);
        List<ActivityExtract> activities = dataExporterTestData.getActivities();
        List<Participant> participants = dataExporterTestData.getParticipants();

        boolean exportStructuredDocument = true;
        InvitationDto invite = new InvitationDto(1L, "invite123", InvitationType.RECRUITMENT,
                Instant.now(), null, null, null, testData.getStudyId(), 1L, null, "invite notes here");
        StudyExtract studyExtract = new StudyExtract(activities, Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), List.of(invite));

        Map<String, String> result = TransactionWrapper.withTxn(handle ->
                exporter.prepareParticipantRecordsForJSONExport(
                        studyExtract, participants, exportStructuredDocument, handle, mockMedicalRecordService
                )
        );
        assertEquals(1, result.size());
        assertTrue(result.get(TEST_USER_GUID).contains("invite123"));
    }

    @Test
    public void testExportDataSetAsStructuredJsonWithEmptyMailAddress() {
        DataExporterTestData dataExporterTestData = DataExporterTestData.createWithEmptyMailAddress();
        List<ActivityExtract> activities = dataExporterTestData.getActivities();
        List<Participant> participants = dataExporterTestData.getParticipants();
        boolean exportStructuredDocument = true;
        StudyExtract studyExtract = new StudyExtract(activities, Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        Map<String, String> result = TransactionWrapper.withTxn(handle ->
                exporter.prepareParticipantRecordsForJSONExport(
                        studyExtract, participants, exportStructuredDocument, handle, mockMedicalRecordService
                )
        );
        assertEquals(1, result.size());
    }

    @Test
    public void testExportDataSetAsCsv() throws IOException {
        DataExporterTestData dataExporterTestData = new DataExporterTestData(false, false);
        List<ActivityExtract> activities = dataExporterTestData.getActivities();
        List<Participant> participants = dataExporterTestData.getParticipants();

        // Run the test!
        StringWriter buffer = new StringWriter();
        int numWritten = exporter.exportDataSetAsCsv(testData.getTestingStudy(), activities, participants, buffer);
        assertEquals(1, numWritten);

        String output = buffer.toString();
        assertFalse(output.isEmpty());

        CSVReader reader = new CSVReader(new StringReader(output));
        Iterator<String[]> iter = reader.iterator();

        String[] expected = new String[] {
                "participant_guid", "participant_hruid", "legacy_altpid", "legacy_shortid",
                "first_name", "last_name", "email", "do_not_contact", "created_at", "status", "status_timestamp",
                "ACT_v1", "ACT_v1_status", "ACT_v1_created_at", "ACT_v1_updated_at", "ACT_v1_completed_at",
                "Q_BIRTHDAY_DAY", "Q_BIRTHDAY_MONTH", "Q_BIRTHDAY_YEAR", "Q_BOOL", "Q_TEXT", "Q_NUMERIC",
                "ADDRESS_FULLNAME", "ADDRESS_STREET1", "ADDRESS_STREET2", "ADDRESS_CITY", "ADDRESS_STATE",
                "ADDRESS_ZIP", "ADDRESS_COUNTRY", "ADDRESS_PHONE", "ADDRESS_PLUSCODE", "ADDRESS_STATUS", "PHYSICIAN"};
        String[] actual = iter.next();
        assertArrayEquals(expected, actual);

        expected = new String[] {
                TEST_USER_GUID, "blah-hruid", "blah-legacy-altpid", "blah-shortid",
                "first-foo", "last-bar", "test@datadonationplatform.org", "true", "10/18/2018 20:18:01", "ENROLLED", "10/18/2018 20:18:01",
                "instance-guid-xyz", "COMPLETE", "10/18/2018 20:18:01", "10/18/2018 20:18:21", "10/18/2018 20:18:11",
                "16", "05", "1978", "true", "john smith", "25",
                "foo bar", "85 Main St", "Apt 2", "Boston", "MA", "02115", "US", "6171112233",
                "87JC9WFP+HV", "INVALID", "dr. a;inst a;boston;ma"};
        actual = iter.next();
        assertArrayEquals(expected, actual);
        assertFalse(iter.hasNext());
    }

    @Test
    public void testExportStudyDataMappings_hasAllExpectedProperties() {
        DataExporterTestData dataExporterTestData = new DataExporterTestData(false, false);
        List<ActivityExtract> activities = dataExporterTestData.getActivities();

        Map<String, Object> mappings = exporter.exportStudyDataMappings(activities);
        String[] actual = mappings.keySet().toArray(new String[] {});

        String[] expected = new String[] {
                "participant_guid", "participant_hruid", "legacy_altpid", "legacy_shortid",
                "first_name", "last_name", "email", "do_not_contact", "created_at", "status", "status_timestamp",
                "ACT_v1", "ACT_v1_status", "ACT_v1_created_at", "ACT_v1_updated_at", "ACT_v1_completed_at",
                "Q_BIRTHDAY_DAY", "Q_BIRTHDAY_MONTH", "Q_BIRTHDAY_YEAR", "Q_BOOL", "Q_TEXT", "Q_NUMERIC",
                "ADDRESS_FULLNAME", "ADDRESS_STREET1", "ADDRESS_STREET2", "ADDRESS_CITY", "ADDRESS_STATE",
                "ADDRESS_ZIP", "ADDRESS_COUNTRY", "ADDRESS_PHONE", "ADDRESS_PLUSCODE", "ADDRESS_STATUS", "PHYSICIAN"};

        assertArrayEquals(expected, actual);
    }

    private static class DataExporterTestData {
        private List<ActivityExtract> activities;
        private List<Participant> participants;

        public static DataExporterTestData createWithEmptyActivity() {
            return new DataExporterTestData(true, false);
        }

        public static DataExporterTestData createWithEmptyMailAddress() {
            return new DataExporterTestData(false, true);
        }

        public DataExporterTestData(boolean emptyActivity, boolean emptyAddress) {
            // Build test values
            long timestamp = 1539893881000L;                // -> 10/18/2018 20:18:01 UTC
            long firstCompletedAt = timestamp + 10000L;     // -> 10/18/2018 20:18:11 UTC
            long lastUpdatedAt = firstCompletedAt + 10000L; // -> 10/18/2018 20:18:21 UTC

            FormActivityDef def = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .addSection(new FormSectionDef(null, Arrays.asList(new QuestionBlockDef(
                            DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, "Q_BIRTHDAY", Template.text(""))
                                    .addFields(DateFieldType.DAY, DateFieldType.MONTH, DateFieldType.YEAR)
                                    .build()
                    ))))
                    .addSection(new FormSectionDef(null, Arrays.asList(
                            new QuestionBlockDef(BoolQuestionDef
                                    .builder("Q_BOOL", Template.text(""), Template.text(""), Template.text(""))
                                    .build()),
                            new QuestionBlockDef(TextQuestionDef
                                    .builder(TextInputType.ESSAY, "Q_TEXT", Template.text("text prompt"))
                                    .build()),
                            new QuestionBlockDef(NumericQuestionDef
                                    .builder(NumericType.INTEGER, "Q_NUMERIC", Template.text("numeric prompt"))
                                    .build()))))
                    .addSection(new FormSectionDef(null, Arrays.asList(
                            new MailingAddressComponentDef(null, null),
                            new PhysicianComponentDef(true, null, null, null, InstitutionType.PHYSICIAN, true, false))))
                    .build();
            ActivityVersionDto versionDto = new ActivityVersionDto(1L, 1L, "v1", 1L, timestamp, null);
            activities = List.of(new ActivityExtract(def, versionDto));

            EnrollmentStatusDto status = new EnrollmentStatusDto(1L, 1L, "user", 1L, testData.getStudyGuid(),
                    EnrollmentStatusType.ENROLLED, timestamp, null);

            User user = new User(1L, TEST_USER_GUID, "blah-hruid", "blah-legacy-altpid", "blah-shortid",
                    false, 1L, 1L, "auth", timestamp, timestamp, null);
            user.setEmail("test@datadonationplatform.org");
            user.setProfile(new UserProfile(1L, "first-foo", "last-bar", null, null, 1L, "en", true, null));
            if (!emptyAddress) {
                MailAddress address = new MailAddress("foo bar", "85 Main St", "Apt 2", "Boston", "MA", "US", "02115", "6171112233", null,
                        "", DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS, true);
                address.setPlusCode("87JC9WFP+HV");
                user.setAddress(address);
            }

            Participant participant = new Participant(status, user);
            participant.addProvider(new MedicalProviderDto(null, UUID.randomUUID().toString(),
                    testData.getUserId(), testData.getStudyId(),
                    InstitutionType.PHYSICIAN, "inst a", "dr. a", "boston", "ma", null, null, null, null));
            if (!emptyActivity) {
                FormResponse instance = new FormResponse(1L, "instance-guid-xyz", 1L, false, timestamp, firstCompletedAt, 1L, "ACT", "v1",
                        new ActivityInstanceStatusDto(2L, 2L, 1L, 1L, lastUpdatedAt, InstanceStatusType.COMPLETE));
                instance.putAnswer(new BoolAnswer(1L, "Q_BOOL", "guid", true));
                instance.putAnswer(new TextAnswer(2L, "Q_TEXT", "guid", "john smith"));
                instance.putAnswer(new NumericIntegerAnswer(3L, "Q_NUMERIC", "guid", 25L));
                instance.putAnswer(new DateAnswer(4L, "Q_BIRTHDAY", "guid", new DateValue(1978, 5, 16)));
                participant.addResponse(instance);
            }

            participants = new ArrayList<>();
            participants.add(participant);
        }

        public List<ActivityExtract> getActivities() {
            return activities;
        }

        public List<Participant> getParticipants() {
            return participants;
        }
    }
}
