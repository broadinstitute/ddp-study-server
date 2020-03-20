package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.DataLoader.ABDOMINAL;
import static org.broadinstitute.ddp.util.DataLoader.BONELIMB;
import static org.broadinstitute.ddp.util.DataLoader.BRAIN;
import static org.broadinstitute.ddp.util.DataLoader.BREAST;
import static org.broadinstitute.ddp.util.DataLoader.HEADFACENECK;
import static org.broadinstitute.ddp.util.DataLoader.HEART;
import static org.broadinstitute.ddp.util.DataLoader.LIVER;
import static org.broadinstitute.ddp.util.DataLoader.LUNG;
import static org.broadinstitute.ddp.util.DataLoader.LYMPH;
import static org.broadinstitute.ddp.util.DataLoader.OTHER;
import static org.broadinstitute.ddp.util.DataLoader.SCALP;
import static org.broadinstitute.ddp.util.DataLoader.SPLEEN;
import static org.broadinstitute.ddp.util.DataLoader.YES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.auth0.client.auth.AuthAPI;
import com.auth0.json.mgmt.users.User;
import com.auth0.net.AuthRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiCountrySubnationalDivision;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserLegacyInfo;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.migration.AboutYouSurvey;
import org.broadinstitute.ddp.model.migration.DatstatParticipantData;
import org.broadinstitute.ddp.model.migration.ParticipantData;
import org.broadinstitute.ddp.model.migration.ReleaseSurvey;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.util.gen2.enums.LovedOneRelationTo;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLoaderTest {
    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderTest.class);
    private static String PARTICIPANT_DATA_TEST_DATA_LOCATION = "src/test/resources/dm-survey-testdata.json";
    private static OLCService olcService;
    private final String pretendAuth0UserId = "i_am_pretend";
    private final String pretendInstanceGuid = "51678L";
    private final String pretendMgmtToken = "pretendMgmtToken";
    private final String pretendUserGuid = "Nordin";
    private final String pretendUserHruid = "IM a Hruid";
    private final String pretendDomain = "fake_domain";
    private final String pretendClientName = "fake_client_name";
    private final String pretendAuth0ClientId = "fake_client_id";
    private final long pretendUserId = 51678L;
    private final Long pretendStudyId = 998877L;
    private final Long pretendPepperClientId = 12345L;
    private final Long pretendLanguageCodeId = 67890L;
    private final String pretendMailAddressGuid = "778899";
    private final Long pretendMailAddressId = 99887766L;
    private final String pretendKitGuid = "334455";
    private final Long pretendKitTypeId = 556677L;
    private final String pretendStudyGuid = "ANGIO";
    private final KitType pretendSalivaKitTypeId = new KitType(778866L, "SALIVA");
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private DataLoader mockDataLoader;
    private Handle mockHandle;
    private JdbiUser mockJdbiUser;
    private JdbiClient mockJdbiClient;
    private Auth0Util mockAuth0Util;
    private AuthRequest mockAuthRequest;
    private AuthAPI mockAuthAPI;
    private Auth0ManagementClient mockMgmtClient;
    private User mockAuth0User;

    @BeforeClass
    public static void beforeClass() {
        Config cfg = ConfigManager.getInstance().getConfig();
        olcService = new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY));
    }

    @Before
    public void setupMocks() throws Exception {
        mockDataLoader = mock(DataLoader.class);
        mockHandle = mock(Handle.class);
        mockJdbiUser = mock(JdbiUser.class);
        mockJdbiClient = mock(JdbiClient.class);
        mockAuthAPI = mock(AuthAPI.class);
        mockAuthRequest = mock(AuthRequest.class);
        mockAuth0Util = mock(Auth0Util.class);
        mockMgmtClient = mock(Auth0ManagementClient.class);
        mockAuth0User = mock(User.class);

        when(mockDataLoader.answerTextQuestion(
                any(Handle.class),
                anyString(),
                eq(pretendInstanceGuid),
                eq(pretendUserGuid),
                anyString(),
                any(AnswerDao.class))).thenReturn(null);

        when(mockDataLoader.answerDateQuestion(
                any(Handle.class),
                anyString(),
                eq(pretendInstanceGuid),
                eq(pretendUserGuid),
                any(LocalDate.class),
                any(AnswerDao.class))).thenReturn(null);

        when(mockDataLoader.answerPickListQuestion(
                any(Handle.class),
                anyString(),
                eq(pretendInstanceGuid),
                eq(pretendUserGuid),
                anyList(),
                any(AnswerDao.class))).thenReturn(null);

        when(mockDataLoader.createActivityInstance(any(ParticipantData.class),
                anyString(), anyLong(), anyString(), any(JdbiActivity.class),
                any(JdbiActivityVersion.class),
                any(ActivityInstanceDao.class),
                any(ActivityInstanceStatusDao.class),
                any(JdbiActivityInstance.class))).thenReturn(pretendInstanceGuid);

        JdbiActivityInstance mockDao = mock(JdbiActivityInstance.class);
        when(mockHandle.attach(any())).thenReturn(mockDao);

        when(mockDao.getActivityInstanceId(any())).thenReturn(0L);

    }

    @Test
    public void testLoadRelease() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);
        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);

        doCallRealMethod().when(mockDataLoader).loadReleaseSurveyData(
                any(Handle.class),
                any(ParticipantData.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                any(JdbiUserLegacyInfo.class),
                any(MedicalProviderDao.class),
                any(JdbiUserStudyEnrollment.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        JdbiUserLegacyInfo mockJdbiUserLegacyInfo = mock(JdbiUserLegacyInfo.class);
        MedicalProviderDao mockMedicalProviderDao = mock(MedicalProviderDao.class);
        JdbiUserStudyEnrollment mockJdbiUserStudyEnrollment = mock(JdbiUserStudyEnrollment.class);

        mockDataLoader.loadReleaseSurveyData(
                mockHandle,
                participantData,
                pretendUserGuid,
                pretendStudyGuid,
                pretendInstanceGuid,
                pretendUserId,
                pretendStudyId,
                mockJdbiUserLegacyInfo,
                mockMedicalProviderDao,
                mockJdbiUserStudyEnrollment,
                mockAnswerDao);

        ArgumentCaptor<String> addressCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockJdbiUserLegacyInfo).insertUserLegacyInfo(
                anyLong(),
                addressCaptor.capture()
        );

        assertEquals("{\"fullName\":\"First1539381231204 Last1539381231204\",\"street1\":\"415 Main Street\""
                + ",\"city\":\"Cambridge\",\"state\":\"MA\",\"phone\":\"555-867-5309\"}", addressCaptor.getValue());

        ArgumentCaptor<MedicalProviderDto> medicalProviderDtoArgumentCaptor = ArgumentCaptor.forClass(MedicalProviderDto.class);
        verify(mockMedicalProviderDao, times(4)).insert(medicalProviderDtoArgumentCaptor.capture());

        medicalProviderDtoArgumentCaptor.getAllValues().stream()
                .forEach(dto -> {
                    if (dto.getInstitutionName().equals("Hogwarts Castle")) {
                        assertEquals("Dr Ahhdjadkhadhakaskhdskad 11379773801730271032018018329011280812381320813-"
                                        + "13281823-123382-8132813-3hdhd0d2uxdd00u20u0dj2j02j2dd2j2jdddjjd2j2j229ddjjjcnjcfuiceer3g33"
                                        + "77344r774744f77f7f77f7f7f77f77f7chcbc b  bb bb bhssdjhdhjwdhchchuhdhdqhdsd----sdfkfjfls73027"
                                        + "1032018018329011280812381320813-13281823-123382-8132813-3hdhd0d2uxdd00u20u0dj2j02j2dd2j2END",
                                dto.getPhysicianName());
                        assertEquals("Scottland", dto.getCity());
                        assertEquals("MA", dto.getState());
                        assertEquals("02144", dto.getPostalCode());
                        assertEquals("888-888-7878", dto.getPhone());
                        assertEquals("GUID_3", dto.getLegacyGuid());
                        assertEquals("Dr Ahhdjadkhadhakaskhdskad 11379773801730271032018018329011280812381320813-"
                                        + "13281823-123382-8132813-3hdhd0d2uxdd00u20u0dj2j02j2dd2j2jdddjjd2j2j229ddjjjcnjcfuiceer3g33"
                                        + "77344r774744f77f7f77f7f7f77f77f7chcbc b  bb bb bhssdjhdhjwdhchchuhdhdqhdsd----sdfkfjfls730"
                                        + "271032018018329011280812381320813-13281823-123382-8132813-3hdhd0d2uxdd00u20u0dj2j02j2dd2j2END",
                                dto.getPhysicianName());
                        assertEquals(InstitutionType.PHYSICIAN, dto.getInstitutionType());
                    } else if (dto.getInstitutionName().equals("institution 1")) {
                        assertEquals("CA", dto.getState());
                        assertEquals("Longview", dto.getCity());
                        assertEquals("GUID_1", dto.getLegacyGuid());
                        assertEquals(InstitutionType.INSTITUTION, dto.getInstitutionType());
                    } else if (dto.getInstitutionName().equals("institution 3")) {
                        assertEquals("DC", dto.getState());
                        assertEquals("Washington", dto.getCity());
                        assertEquals("GUID_2", dto.getLegacyGuid());
                        assertEquals(InstitutionType.INSTITUTION, dto.getInstitutionType());
                    } else if (dto.getInstitutionName().equals("biopsy place")) {
                        assertEquals("DC", dto.getState());
                        assertEquals("Washington", dto.getCity());
                        assertEquals(InstitutionType.INITIAL_BIOPSY, dto.getInstitutionType());
                    } else {
                        fail("Unexpected item in bagging area");
                    }
                });

        assertTrue(medicalProviderDtoArgumentCaptor.getAllValues().stream()
                .map(dto -> dto.getInstitutionName())
                .collect(Collectors.toSet())
                .containsAll(Arrays.asList("Hogwarts Castle", "institution 1", "institution 3", "biopsy place")));

        verify(mockJdbiUserStudyEnrollment, times(1))
                .changeUserStudyEnrollmentStatus(pretendUserGuid, pretendStudyGuid, EnrollmentStatusType.ENROLLED, 1539381239000L);
    }

    @Test
    public void testLoadAboutYou() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);
        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);

        doCallRealMethod().when(mockDataLoader).loadAboutYouSurveyData(
                any(Handle.class),
                any(ParticipantData.class),
                anyString(),
                anyString(),
                any(AnswerDao.class));

        when(mockDataLoader.setOtherCancers(
                any(Handle.class),
                anyString(),
                anyString(),
                any(AboutYouSurvey.class),
                any(AnswerDao.class)
        )).thenCallRealMethod();

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        when(mockAnswerDao.createAnswer(
                anyString(),
                anyString(),
                any(CompositeAnswer.class)
        )).thenReturn(new CompositeAnswer(1L, "composite", "guid"));

        mockDataLoader.loadAboutYouSurveyData(
                mockHandle,
                participantData,
                pretendUserGuid,
                pretendInstanceGuid,
                mockAnswerDao);

        ArgumentCaptor<String> pickListQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SelectedPicklistOption>> pickListQAnswerValue = ArgumentCaptor.forClass(List.class);
        verify(mockDataLoader, times(22)).answerPickListQuestion(
                any(Handle.class),
                pickListQPepperStableId.capture(),
                anyString(),
                anyString(),
                pickListQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> dateQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateQAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(2)).answerDateQuestion(
                any(Handle.class),
                dateQPepperStableId.capture(),
                anyString(),
                anyString(),
                dateQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(9)).answerTextQuestion(
                any(Handle.class),
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));

        Map<String, DateValue> dateAnswers = IntStream.range(0, dateQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> dateQPepperStableId.getAllValues().get(i), i -> dateQAnswerValue.getAllValues().get(i)));

        Map<String, List<SelectedPicklistOption>> pickListAnswers = IntStream.range(0, pickListQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> pickListQPepperStableId.getAllValues().get(i),
                        i -> pickListQAnswerValue.getAllValues().get(i)));


        assertEquals((int) dateAnswers.get("DIAGNOSIS_DATE").getMonth(), 1);
        assertEquals((int) dateAnswers.get("DIAGNOSIS_DATE").getYear(), 1999);
        assertEquals(dateAnswers.get("DIAGNOSIS_DATE").getDay(), null);

        assertEquals(dateAnswers.get("BIRTH_YEAR").getMonth(), null);
        assertEquals((int) dateAnswers.get("BIRTH_YEAR").getYear(), 1978);
        assertEquals(dateAnswers.get("BIRTH_YEAR").getDay(), null);

        List<SelectedPicklistOption> diagnosisPrimaryLoc = pickListAnswers.get("DIAGNOSIS_PRIMARY_LOC");
        assertEquals(1, diagnosisPrimaryLoc.size());
        assertEquals("BREAST", diagnosisPrimaryLoc.get(0).getStableId());
        assertEquals(null, diagnosisPrimaryLoc.get(0).getDetailText());

        Set<String> yesNoDkQuestions = new HashSet<>(Arrays.asList(
                "DIAGNOSIS_SPREAD",
                "POST_DIAGNOSIS_SPREAD",
                "LOCAL_RECURRENCE",
                "SURGERY",
                "SURGERY_CLEAN_MARGINS",
                "RADIATION",
                "CURRENTLY_TREATED",
                "OTHER_CANCER",
                "OTHER_CANCER_RADIATION",
                "DISEASE_FREE_NOW",
                "SUPPORT_MEMBERSHIP",
                "HISPANIC"
        ));

        pickListAnswers.entrySet().stream()
                .filter(entry -> yesNoDkQuestions.contains(entry.getKey()))
                .forEach(entry -> {
                    assertEquals("We expected a YES answer on: " + entry.getKey(), 1, entry.getValue().stream()
                            .filter(answer -> answer.getStableId().equals(YES))
                            .count());
                });

        Set<String> spreadLocQuestions = new HashSet<>(Arrays.asList(
                "DIAGNOSIS_SPREAD_LOC",
                "POST_DIAGNOSIS_SPREAD_LOC",
                "LOCAL_RECURRENCE_LOC",
                "DIAGNOSIS_LOC",
                "EVER_LOCATION",
                "CURRENT_LOCATION"
        ));

        Set<String> expectedSpreadLocations = new HashSet<>(Arrays.asList(HEADFACENECK, SCALP, BREAST, HEART, LIVER,
                SPLEEN, LUNG, BRAIN, LYMPH, BONELIMB, ABDOMINAL, OTHER));

        pickListAnswers.entrySet().stream()
                .filter(entry -> spreadLocQuestions.contains(entry.getKey()))
                .forEach(entry -> {
                    Set<String> presentStableIds = entry.getValue().stream()
                            .map(answer -> answer.getStableId())
                            .collect(Collectors.toSet());

                    assertTrue(presentStableIds.containsAll(expectedSpreadLocations));
                });

        assertEquals(1, pickListAnswers.get("DIAGNOSIS_LOC").stream()
                .filter(entry -> entry.getStableId().equals("DK"))
                .count());

        assertEquals(1, pickListAnswers.get("EVER_LOCATION").stream()
                .filter(entry -> entry.getStableId().equals("DK"))
                .count());

        assertEquals(1, pickListAnswers.get("CURRENT_LOCATION").stream()
                .filter(entry -> entry.getStableId().equals("DK"))
                .count());

        assertEquals(1, pickListAnswers.get("CURRENT_LOCATION").stream()
                .filter(entry -> entry.getStableId().equals("NED"))
                .count());

        assertEquals(1, pickListAnswers.get("RACE").stream()
                .filter(entry -> entry.getStableId().equals("OTHER"))
                .filter(entry -> entry.getDetailText().equals("Klingon"))
                .count());

        assertEquals(1, pickListAnswers.get("COUNTRY").stream()
                .filter(entry -> entry.getStableId().equals("US"))
                .count());

        assertEquals(1, pickListAnswers.get("RADIATION_SURGERY").stream()
                .filter(entry -> entry.getStableId().equals("BOTH"))
                .count());

        assertEquals("Woot", pickListAnswers.get("DIAGNOSIS_SPREAD_LOC").stream()
                .filter(answer -> answer.getStableId().equals("BONELIMB"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("WootWoot", pickListAnswers.get("DIAGNOSIS_SPREAD_LOC").stream()
                .filter(answer -> answer.getStableId().equals("ABDOMINAL"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("WootWootWoot", pickListAnswers.get("DIAGNOSIS_SPREAD_LOC").stream()
                .filter(answer -> answer.getStableId().equals("OTHER"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("Stuff", pickListAnswers.get("POST_DIAGNOSIS_SPREAD_LOC").stream()
                .filter(answer -> answer.getStableId().equals("BONELIMB"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("StuffStuff", pickListAnswers.get("POST_DIAGNOSIS_SPREAD_LOC").stream()
                .filter(answer -> answer.getStableId().equals("ABDOMINAL"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("StuffStuffStuff", pickListAnswers.get("POST_DIAGNOSIS_SPREAD_LOC").stream()
                .filter(answer -> answer.getStableId().equals("OTHER"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("Things", pickListAnswers.get("LOCAL_RECURRENCE_LOC").stream()
                .filter(answer -> answer.getStableId().equals("BONELIMB"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("ThingsThings", pickListAnswers.get("LOCAL_RECURRENCE_LOC").stream()
                .filter(answer -> answer.getStableId().equals("ABDOMINAL"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("ThingsThingsThings", pickListAnswers.get("LOCAL_RECURRENCE_LOC").stream()
                .filter(answer -> answer.getStableId().equals("OTHER"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("Items", pickListAnswers.get("DIAGNOSIS_LOC").stream()
                .filter(answer -> answer.getStableId().equals("BONELIMB"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("ItemsItems", pickListAnswers.get("DIAGNOSIS_LOC").stream()
                .filter(answer -> answer.getStableId().equals("ABDOMINAL"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("ItemsItemsItems", pickListAnswers.get("DIAGNOSIS_LOC").stream()
                .filter(answer -> answer.getStableId().equals("OTHER"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("Words", pickListAnswers.get("EVER_LOCATION").stream()
                .filter(answer -> answer.getStableId().equals("BONELIMB"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("WordsWords", pickListAnswers.get("EVER_LOCATION").stream()
                .filter(answer -> answer.getStableId().equals("ABDOMINAL"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("WordsWordsWords", pickListAnswers.get("EVER_LOCATION").stream()
                .filter(answer -> answer.getStableId().equals("OTHER"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("Thoughts", pickListAnswers.get("CURRENT_LOCATION").stream()
                .filter(answer -> answer.getStableId().equals("BONELIMB"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("ThoughtsThoughts", pickListAnswers.get("CURRENT_LOCATION").stream()
                .filter(answer -> answer.getStableId().equals("ABDOMINAL"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("ThoughtsThoughtsThoughts", pickListAnswers.get("CURRENT_LOCATION").stream()
                .filter(answer -> answer.getStableId().equals("OTHER"))
                .map(SelectedPicklistOption::getDetailText).findFirst().get());

        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        assertEquals(textAnswers.get("TREATMENT_PAST"), "Zap");
        assertEquals(textAnswers.get("TREATMENT_NOW"), "ZapZap");
        assertEquals(textAnswers.get("ALL_TREATMENTS"), "ZapZapZap");
        assertEquals(textAnswers.get("CURRENT_THERAPIES"), "ZapZapZapZap");
        assertEquals(textAnswers.get("SUPPORT_MEMBERSHIP_TEXT"), "ZapZapZapZapZap");
        assertEquals(textAnswers.get("EXPERIENCE"), "ZapZapZapZapZapZap");
        assertEquals(textAnswers.get("REFERRAL_SOURCE"), "ZapZapZapZapZapZapZap");
        assertEquals(textAnswers.get("OTHER_CANCER_RADIATION_LOC"), "ZapZapZapZapZapZapZapZap");
        assertEquals(textAnswers.get("POSTAL_CODE"), "02144");

        ArgumentCaptor<CompositeAnswer> compositeAnswerArgumentCaptor = ArgumentCaptor.forClass(CompositeAnswer.class);
        verify(mockAnswerDao).createAnswer(
                anyString(),
                anyString(),
                compositeAnswerArgumentCaptor.capture()
        );


        CompositeAnswer otherCancerAnswer = compositeAnswerArgumentCaptor.getValue();
        otherCancerAnswer.getValue().stream()
                .forEach(answerRow -> {
                    List<Answer> values = answerRow.getValues();
                    if (values.get(0).getValue().equals("cancer A")) {
                        DateValue dateValue = (DateValue) values.get(1).getValue();
                        assertEquals(1990, (int) dateValue.getYear());
                        assertNull(dateValue.getMonth());
                        assertNull(dateValue.getDay());
                    } else if (values.get(0).getValue().equals("cancer B")) {
                        DateValue dateValue = (DateValue) values.get(1).getValue();
                        assertNull(dateValue.getYear());
                        assertNull(dateValue.getMonth());
                        assertNull(dateValue.getDay());
                    } else {
                        fail("Unexpected other cancer answer " + values.get(0).getValue());
                    }
                });
    }

    @Test
    public void testAddKitDetails() throws FileNotFoundException {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);
        DatstatParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class)
                .getParticipantUser().getDatstatparticipantdata();


        KitTypeDao mockKitTypeDao = mock(KitTypeDao.class);
        when(mockKitTypeDao.getSalivaKitType()).thenReturn(pretendSalivaKitTypeId);

        DsmKitRequestDao mockDsmKitRequestDao = mock(DsmKitRequestDao.class);
        when(mockDsmKitRequestDao.createKitRequest(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(0L);

        when(mockDataLoader.addKitDetails(any(DsmKitRequestDao.class),
                any(KitTypeDao.class),
                anyLong(),
                anyLong(),
                any(DatstatParticipantData.class),
                anyString())).thenCallRealMethod();


        mockDataLoader.addKitDetails(mockDsmKitRequestDao,
                mockKitTypeDao,
                pretendUserId,
                pretendMailAddressId,
                participantData,
                pretendStudyGuid);

        ArgumentCaptor<String> kitGuidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> studyGuidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> addressIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> kitIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> pepperUserIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> secondsSinceEpochCaptor = ArgumentCaptor.forClass(Long.class);

        verify(mockDsmKitRequestDao, times(1)).createKitRequest(
                kitGuidCaptor.capture(),
                studyGuidCaptor.capture(),
                addressIdCaptor.capture(),
                kitIdCaptor.capture(),
                pepperUserIdCaptor.capture(),
                secondsSinceEpochCaptor.capture());

        assertEquals("bf9f2701-ac7e-4edd-899e-2cs983ss2adc", kitGuidCaptor.getValue());
        assertEquals(pretendStudyGuid, studyGuidCaptor.getValue());
        assertEquals((Long) pretendSalivaKitTypeId.getId(), kitIdCaptor.getValue());
        assertEquals((Long) pretendUserId, pepperUserIdCaptor.getValue());
        assertEquals((Long) 1451606400L, secondsSinceEpochCaptor.getValue());
    }

    @Test
    public void testAddUserAddress() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);
        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);

        JdbiMailAddress mockJdbiMailAddress = mock(JdbiMailAddress.class);
        MailAddress mailAddress = new MailAddress();
        mailAddress.setGuid(pretendMailAddressGuid);
        when(mockJdbiMailAddress.insertLegacyAddress(
                any(MailAddress.class),
                anyString(),
                anyString(),
                anyLong())).thenReturn(mailAddress);

        when(mockDataLoader.addUserAddress(any(Handle.class),
                any(UserDto.class),
                any(DatstatParticipantData.class),
                any(ReleaseSurvey.class),
                any(JdbiMailAddress.class),
                eq(olcService))).thenCallRealMethod();

        JdbiCountrySubnationalDivision mockDao = mock(JdbiCountrySubnationalDivision.class);
        when(mockHandle.attach(any())).thenReturn(mockDao);

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null, null, now, now);
        mockDataLoader.addUserAddress(mockHandle, userDto,
                participantData.getParticipantUser().getDatstatparticipantdata(),
                participantData.getParticipantUser().getDatstatsurveydata().getReleaseSurvey(), mockJdbiMailAddress, olcService);

        ArgumentCaptor<MailAddress> mailAddressArgumentCaptor = ArgumentCaptor.forClass(MailAddress.class);
        ArgumentCaptor<String> userGuidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> operatorGuidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> createdAt = ArgumentCaptor.forClass(Long.class);
        verify(mockJdbiMailAddress, times(1))
                .insertLegacyAddress(mailAddressArgumentCaptor.capture(),
                        userGuidCaptor.capture(),
                        operatorGuidCaptor.capture(),
                        createdAt.capture());

        assertEquals("First1539381231204 Last1539381231204", mailAddressArgumentCaptor.getValue().getName());
        assertEquals("415 Main Street", mailAddressArgumentCaptor.getValue().getStreet1());
        assertNull(mailAddressArgumentCaptor.getValue().getStreet2());
        assertEquals("Cambridge", mailAddressArgumentCaptor.getValue().getCity());
        assertEquals("MA", mailAddressArgumentCaptor.getValue().getState());
        assertEquals("USA", mailAddressArgumentCaptor.getValue().getCountry());
        assertEquals("02144", mailAddressArgumentCaptor.getValue().getZip());
        assertEquals(DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS.getCode(),
                mailAddressArgumentCaptor.getValue().getValidationStatus());

        verify(mockJdbiMailAddress, times(1)).setDefaultAddressForParticipant(anyString());
    }

    @Test
    public void testAddUserProfile() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);
        DatstatParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class)
                .getParticipantUser().getDatstatparticipantdata();

        when(mockDataLoader.addUserProfile(
                any(UserDto.class),
                any(DatstatParticipantData.class),
                any(JdbiLanguageCode.class),
                any(JdbiProfile.class)
        )).thenCallRealMethod();

        JdbiLanguageCode mockJdbiLanguageCode = mock(JdbiLanguageCode.class);
        when(mockJdbiLanguageCode.getLanguageCodeId(anyString())).thenReturn(pretendLanguageCodeId);

        JdbiProfile mockJdbiProfile = mock(JdbiProfile.class);
        when(mockJdbiProfile.insert(any(UserProfileDto.class))).thenReturn(1);

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null, null, now, now);

        mockDataLoader.addUserProfile(
                userDto,
                participantData,
                mockJdbiLanguageCode,
                mockJdbiProfile);

        verify(mockJdbiLanguageCode, times(1)).getLanguageCodeId(anyString());

        ArgumentCaptor<UserProfileDto> userProfileDtoCaptor = ArgumentCaptor.forClass(UserProfileDto.class);
        verify(mockJdbiProfile, times(1)).insert(userProfileDtoCaptor.capture());

        assertEquals(pretendUserId, userProfileDtoCaptor.getValue().getUserId());

        assertEquals("First1539381231204", userProfileDtoCaptor.getValue().getFirstName());
        assertEquals("Last1539381231204", userProfileDtoCaptor.getValue().getLastName());
        assertEquals(pretendLanguageCodeId, userProfileDtoCaptor.getValue().getPreferredLanguageId());
        assertEquals(null, userProfileDtoCaptor.getValue().getDoNotContact());
    }

    @Test
    public void testCreateLegacyPepperUser() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);
        DatstatParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class)
                .getParticipantUser().getDatstatparticipantdata();

        when(mockMgmtClient.getToken()).thenReturn(pretendMgmtToken);

        when(mockAuth0Util.createAuth0User(anyString(), anyString(), anyString())).thenReturn(mockAuth0User);

        when(mockAuth0User.getId()).thenReturn(pretendAuth0UserId);

        when(mockJdbiClient.getClientIdByAuth0ClientAndDomain(anyString(), anyString())).thenReturn(Optional.of(pretendPepperClientId));

        when(mockAuthAPI.login(anyString(), anyString())).thenReturn(mockAuthRequest);

        when(mockAuthRequest.setRealm(anyString())).thenReturn(mockAuthRequest);

        when(mockDataLoader.createLegacyPepperUser(
                anyString(),
                anyString(),
                any(JdbiUser.class),
                any(JdbiClient.class),
                any(Auth0Util.class),
                any(Auth0ManagementClient.class),
                any(DatstatParticipantData.class),
                anyString(),
                anyString()
        )).thenCallRealMethod();

        mockDataLoader.createLegacyPepperUser(
                pretendDomain,
                pretendAuth0ClientId,
                mockJdbiUser,
                mockJdbiClient,
                mockAuth0Util,
                mockMgmtClient,
                participantData,
                pretendUserGuid,
                pretendUserHruid
        );

        ArgumentCaptor<String> creationEmail = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> creationPass = ArgumentCaptor.forClass(String.class);
        verify(mockAuth0Util, times(1)).createAuth0User(creationEmail.capture(),
                creationPass.capture(), anyString());
        assertEquals("migrationemail8@datadonationplatform.com", creationEmail.getValue());

        ArgumentCaptor<Long> pepperClientIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> legacyAltPidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> legacyShortIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> createdAtDateCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> lastModifiedDateCaptor = ArgumentCaptor.forClass(Long.class);

        verify(mockJdbiUser, times(1)).insertMigrationUser(
                anyString(),
                anyString(),
                pepperClientIdCaptor.capture(),
                anyString(),
                legacyAltPidCaptor.capture(),
                legacyShortIdCaptor.capture(),
                createdAtDateCaptor.capture(),
                lastModifiedDateCaptor.capture());

        assertEquals(pretendPepperClientId, pepperClientIdCaptor.getValue());
        assertEquals("1163789259.99760ec9-4fe9-4143-8b4c-af12334f456a", legacyAltPidCaptor.getValue());
        assertEquals("15096", legacyShortIdCaptor.getValue());
        assertEquals(1539381231000L, (long) createdAtDateCaptor.getValue());
        assertEquals(1539366840337L, (long) lastModifiedDateCaptor.getValue());

        verify(mockAuth0Util, times(1)).setDDPUserGuidForAuth0User(
                anyString(),
                anyString(),
                anyString(),
                anyString());

    }

    @Test
    public void testRelationToNotOther() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);

        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);
        participantData.getParticipantUser().getDatstatsurveydata().getLovedOneSurvey()
                .setRelationTo(LovedOneRelationTo.CHILD.getDatStatEnumValue());

        doCallRealMethod().when(mockDataLoader).loadLovedOneSurveyData(
                any(Handle.class),
                any(ParticipantData.class),
                anyString(),
                anyString(),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        mockDataLoader.loadLovedOneSurveyData(mockHandle,
                participantData,
                pretendUserGuid,
                pretendInstanceGuid,
                mockAnswerDao);

        ArgumentCaptor<String> pickListQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SelectedPicklistOption>> pickListQAnswerValue = ArgumentCaptor.forClass(List.class);
        verify(mockDataLoader, times(6)).answerPickListQuestion(
                any(Handle.class),
                pickListQPepperStableId.capture(),
                anyString(),
                anyString(),
                pickListQAnswerValue.capture(),
                any(AnswerDao.class));

        Map<String, List<SelectedPicklistOption>> pickListAnswers = IntStream.range(0, pickListQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> pickListQPepperStableId.getAllValues().get(i),
                        i -> pickListQAnswerValue.getAllValues().get(i)));

        List<SelectedPicklistOption> relatedToPicklist = pickListAnswers.get("LOVEDONE_RELATION_TO");
        assertEquals(1, relatedToPicklist.size());
        assertEquals("CHILD", relatedToPicklist.get(0).getStableId());
        assertEquals(null, relatedToPicklist.get(0).getDetailText());
    }

    @Test
    public void testLoadFollowUpConsent() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);

        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);

        doCallRealMethod().when(mockDataLoader).loadFollowupConsentSurveyData(
                any(Handle.class),
                any(ParticipantData.class),
                anyString(),
                anyString(),
                any(AnswerDao.class),
                any(Long.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        mockDataLoader.loadFollowupConsentSurveyData(mockHandle,
                participantData,
                pretendUserGuid,
                pretendInstanceGuid,
                mockAnswerDao,
                pretendUserId);

        ArgumentCaptor<String> dateQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateQAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(1)).answerDateQuestion(
                any(Handle.class),
                dateQPepperStableId.capture(),
                anyString(),
                anyString(),
                dateQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(1)).answerTextQuestion(
                any(Handle.class),
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> boolQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> boolQAnswerValue = ArgumentCaptor.forClass(Integer.class);
        verify(mockDataLoader, times(2)).answerBooleanQuestion(
                any(Handle.class),
                boolQPepperStableId.capture(),
                anyString(),
                anyString(),
                boolQAnswerValue.capture(),
                any(AnswerDao.class));

        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        Map<String, DateValue> localDateTimeAnswers = IntStream.range(0, dateQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> dateQPepperStableId.getAllValues().get(i), i -> dateQAnswerValue.getAllValues().get(i)));

        Map<String, Integer> booleanAnswers = IntStream.range(0, boolQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> boolQPepperStableId.getAllValues().get(i),
                        i -> boolQAnswerValue.getAllValues().get(i)));

        assertEquals((Integer) 1, booleanAnswers.get("FOLLOWUPCONSENT_BLOOD"));
        assertEquals((Integer) 1, booleanAnswers.get("FOLLOWUPCONSENT_TISSUE"));
        assertEquals("Participant 8", textAnswers.get("FOLLOWUPCONSENT_FULLNAME"));
        assertEquals((Integer) 1990, localDateTimeAnswers.get("FOLLOWUPCONSENT_DOB").getYear());
        assertEquals((Integer) 12, localDateTimeAnswers.get("FOLLOWUPCONSENT_DOB").getMonth());
        assertEquals((Integer) 2, localDateTimeAnswers.get("FOLLOWUPCONSENT_DOB").getDay());
    }

    @Test
    public void testLoadConsent() throws Exception {
        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);

        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);

        doCallRealMethod().when(mockDataLoader).loadConsentSurveyData(
                any(Handle.class),
                any(ParticipantData.class),
                anyString(),
                anyString(),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        mockDataLoader.loadConsentSurveyData(mockHandle,
                participantData,
                pretendUserGuid,
                pretendInstanceGuid,
                mockAnswerDao);

        ArgumentCaptor<String> dateValueStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateValueAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(2)).answerDateQuestion(
                any(Handle.class),
                dateValueStableId.capture(),
                anyString(),
                anyString(),
                dateValueAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(3)).answerTextQuestion(
                any(Handle.class),
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> boolQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> boolQAnswerValue = ArgumentCaptor.forClass(Integer.class);
        verify(mockDataLoader, times(2)).answerBooleanQuestion(
                any(Handle.class),
                boolQPepperStableId.capture(),
                anyString(),
                anyString(),
                boolQAnswerValue.capture(),
                any(AnswerDao.class));

        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        Map<String, DateValue> dateValueAnswers = IntStream.range(0, dateValueStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> dateValueStableId.getAllValues().get(i), i -> dateValueAnswerValue.getAllValues().get(i)));


        Map<String, Integer> booleanAnswers = IntStream.range(0, boolQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> boolQPepperStableId.getAllValues().get(i),
                        i -> boolQAnswerValue.getAllValues().get(i)));

        assertEquals((Integer) 1, booleanAnswers.get("CONSENT_BLOOD"));
        assertEquals((Integer) 1, booleanAnswers.get("CONSENT_TISSUE"));
        assertEquals("Ack", textAnswers.get("TREATMENT_NOW_TEXT"));
        assertEquals((Integer) 3, dateValueAnswers.get("TREATMENT_NOW_START").getMonth());
        assertEquals((Integer) 1999, dateValueAnswers.get("TREATMENT_NOW_START").getYear());
        assertEquals("AckAck", textAnswers.get("TREATMENT_PAST_TEXT"));
        assertEquals("Participant 8", textAnswers.get("CONSENT_FULLNAME"));
        assertEquals((Integer) 1990, dateValueAnswers.get("CONSENT_DOB").getYear());
        assertEquals((Integer) 12, dateValueAnswers.get("CONSENT_DOB").getMonth());
        assertEquals((Integer) 2, dateValueAnswers.get("CONSENT_DOB").getDay());
    }

    @Test
    public void testLovedOneHappyPath() throws Exception {

        File file = new File(PARTICIPANT_DATA_TEST_DATA_LOCATION);

        ParticipantData participantData = gson.fromJson(new FileReader(file), ParticipantData.class);

        doCallRealMethod().when(mockDataLoader).loadLovedOneSurveyData(
                any(Handle.class),
                any(ParticipantData.class),
                anyString(),
                anyString(),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        mockDataLoader.loadLovedOneSurveyData(mockHandle,
                participantData,
                pretendUserGuid,
                pretendInstanceGuid,
                mockAnswerDao);

        ArgumentCaptor<String> pickListQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SelectedPicklistOption>> pickListQAnswerValue = ArgumentCaptor.forClass(List.class);
        verify(mockDataLoader, times(6)).answerPickListQuestion(
                any(Handle.class),
                pickListQPepperStableId.capture(),
                anyString(),
                anyString(),
                pickListQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> dateQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> dateQAnswerValue = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mockDataLoader, times(1)).answerDateQuestion(
                any(Handle.class),
                dateQPepperStableId.capture(),
                anyString(),
                anyString(),
                dateQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(6)).answerTextQuestion(
                any(Handle.class),
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));


        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        Map<String, LocalDateTime> dateAnswers = IntStream.range(0, dateQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> dateQPepperStableId.getAllValues().get(i), i -> dateQAnswerValue.getAllValues().get(i)));

        Map<String, List<SelectedPicklistOption>> pickListAnswers = IntStream.range(0, pickListQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> pickListQPepperStableId.getAllValues().get(i),
                        i -> pickListQAnswerValue.getAllValues().get(i)));

        List<SelectedPicklistOption> relatedToPicklist = pickListAnswers.get("LOVEDONE_RELATION_TO");
        assertEquals(1, relatedToPicklist.size());
        assertEquals("OTHER", relatedToPicklist.get(0).getStableId());
        assertEquals("Paternal Cousin Twice Removed", relatedToPicklist.get(0).getDetailText());

        assertEquals(textAnswers.get("LOVEDONE_FIRST_NAME"), "Bob");
        assertEquals(textAnswers.get("LOVEDONE_LAST_NAME"), "Dylan");
        assertEquals(textAnswers.get("LOVEDONE_EXPERIENCE"), "terrible experience");
        assertEquals(textAnswers.get("LOVEDONE_OTHER_CANCER_TEXT"), "yeah");
        assertEquals(textAnswers.get("LOVEDONE_DIAGNOSIS_POSTAL_CODE"), "02144");
        assertEquals(textAnswers.get("LOVEDONE_PASSED_POSTAL_CODE"), "02145");

        assertEquals(dateAnswers.get("LOVEDONE_DOB").getMonthValue(), 5);
        assertEquals(dateAnswers.get("LOVEDONE_DOB").getDayOfMonth(), 24);
        assertEquals(dateAnswers.get("LOVEDONE_DOB").getYear(), 1941);


        List<SelectedPicklistOption> diagnosisPrimaryLoc = pickListAnswers.get("LOVEDONE_DIAGNOSIS_PRIMARY_LOC");
        assertEquals(1, diagnosisPrimaryLoc.size());
        assertEquals("HEADFACENECK", diagnosisPrimaryLoc.get(0).getStableId());
        assertEquals(null, diagnosisPrimaryLoc.get(0).getDetailText());

        List<SelectedPicklistOption> diagnosisSpreadLoc = pickListAnswers.get("LOVEDONE_DIAGNOSIS_SPREAD_LOC");
        assertEquals(12, diagnosisSpreadLoc.size());

        Set<String> spreadStableId = diagnosisSpreadLoc.stream()
                .map(o -> o.getStableId())
                .collect(Collectors.toSet());

        Set<String> expectedSpreadLocations = new HashSet<>(Arrays.asList(HEADFACENECK, SCALP, BREAST, HEART, LIVER, SPLEEN, LUNG, BRAIN,
                LYMPH, BONELIMB, ABDOMINAL, OTHER));

        assertTrue(spreadStableId.containsAll(expectedSpreadLocations));
        assertEquals("boz",
                diagnosisSpreadLoc.stream().filter(o -> o.getStableId().equals(BONELIMB))
                        .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("booz",
                diagnosisSpreadLoc.stream().filter(o -> o.getStableId().equals(ABDOMINAL))
                        .map(SelectedPicklistOption::getDetailText).findFirst().get());

        assertEquals("boooz",
                diagnosisSpreadLoc.stream().filter(o -> o.getStableId().equals(OTHER))
                        .map(SelectedPicklistOption::getDetailText).findFirst().get());


        List<SelectedPicklistOption> otherCancer = pickListAnswers.get("LOVEDONE_OTHER_CANCER");
        assertEquals(1, otherCancer.size());
        assertEquals("YES", otherCancer.get(0).getStableId());

        List<SelectedPicklistOption> otherCancerRadiation = pickListAnswers.get("LOVEDONE_OTHER_CANCER_RADIATION");
        assertEquals(1, otherCancerRadiation.size());
        assertEquals("YES", otherCancerRadiation.get(0).getStableId());

        List<SelectedPicklistOption> futureContact = pickListAnswers.get("LOVEDONE_FUTURE_CONTACT");
        assertEquals(1, futureContact.size());
        assertEquals("YES", futureContact.get(0).getStableId());
    }
}
