package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.StudyDataLoader.YES;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
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
import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiCountrySubnationalDivision;
import org.broadinstitute.ddp.db.dao.JdbiInstitutionType;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyLegacyData;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.OLCService;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyDataLoaderTest {
    private static final Logger LOG = LoggerFactory.getLogger(StudyDataLoaderTest.class);
    private static String PARTICIPANT_DATA_TEST_DATA_LOCATION = "src/test/resources/dm-survey-mbc-testdata-8148.json";
    public static final String QUESTION_STABLE_MAP_FILE = "src/test/resources/question_stableid_map.json";
    private static OLCService olcService;
    private static AddressService addressService;
    private final String pretendAuth0UserId = "i_am_pretend";
    private final String pretendInstanceGuid = "51678L";
    private final String pretendMgmtToken = "pretendMgmtToken";
    private final String pretendUserGuid = "MBCguid";
    private final String pretendUserHruid = "IM a Hruid";
    private final String pretendDomain = "fake_domain";
    //private final String pretendClientName = "fake_client_name";
    private final String pretendAuth0ClientId = "fake_client_id";
    private final long pretendUserId = 51678L;
    private final Long pretendStudyId = 998877L;
    private final Long pretendPepperClientId = 12345L;
    private final Long pretendLanguageCodeId = 67890L;
    private final String pretendMailAddressGuid = "778899";
    private final Long pretendMailAddressId = 99887766L;
    //private final String pretendKitGuid = "334455";
    //private final Long pretendKitTypeId = 556677L;
    private final String pretendStudyGuid = "cmi-mbc";
    private final String pretendPhoneNumber = "12345567890";
    private final KitType pretendSalivaKitTypeId = new KitType(778866L, "SALIVA");
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private StudyDataLoader mockDataLoader;
    private Handle mockHandle;
    private JdbiUser mockJdbiUser;
    private JdbiClient mockJdbiClient;
    private Auth0Util mockAuth0Util;
    private AuthRequest mockAuthRequest;
    private AuthAPI mockAuthAPI;
    private Auth0ManagementClient mockMgmtClient;
    private User mockAuth0User;
    private ClientDto mockClientDto;
    private static String sourceData;
    private static Map<String, JsonElement> sourceDataMap;
    private static Map<String, JsonElement> mappingData;

    @BeforeClass
    public static void beforeClass() {
        Config cfg = ConfigManager.getInstance().getConfig();
        olcService = new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY));
        addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));
        try {
            sourceData = new String(Files.readAllBytes(Paths.get(PARTICIPANT_DATA_TEST_DATA_LOCATION)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        StudyDataLoaderMain dataLoaderMain = new StudyDataLoaderMain();
        sourceDataMap = dataLoaderMain.loadSourceDataFile(sourceData);
        mappingData = dataLoaderMain.loadDataMapping(QUESTION_STABLE_MAP_FILE);

    }

    @Before
    public void setupMocks() throws Exception {
        mockDataLoader = mock(StudyDataLoader.class);

        mockHandle = mock(Handle.class);
        mockJdbiUser = mock(JdbiUser.class);
        mockJdbiClient = mock(JdbiClient.class);
        mockAuthAPI = mock(AuthAPI.class);
        mockAuthRequest = mock(AuthRequest.class);
        mockAuth0Util = mock(Auth0Util.class);
        mockMgmtClient = mock(Auth0ManagementClient.class);
        mockAuth0User = mock(User.class);
        mockClientDto = mock(ClientDto.class);
        initMockStudyDataLoader();

        ActivityInstanceDto mockInstanceDto = mock(ActivityInstanceDto.class);

        when(mockDataLoader.answerTextQuestion(
                anyString(),
                eq(pretendInstanceGuid),
                eq(pretendUserGuid),
                anyString(),
                any(AnswerDao.class))).thenReturn(null);

        when(mockDataLoader.answerDateQuestion(
                anyString(),
                eq(pretendInstanceGuid),
                eq(pretendUserGuid),
                any(DateValue.class),
                any(AnswerDao.class))).thenReturn(null);

        when(mockDataLoader.answerPickListQuestion(
                anyString(),
                eq(pretendInstanceGuid),
                eq(pretendUserGuid),
                anyList(),
                any(AnswerDao.class))).thenReturn(null);

        when(mockDataLoader.createActivityInstance(any(JsonElement.class),
                anyString(), anyLong(), anyString(), anyString(), any(JdbiActivity.class),
                any(ActivityInstanceDao.class),
                any(ActivityInstanceStatusDao.class))).thenReturn(mockInstanceDto);

        JdbiActivityInstance mockActivityDao = mock(JdbiActivityInstance.class);
        JdbiUserStudyLegacyData mockLegacyDataDao = mock(JdbiUserStudyLegacyData.class);
        when(mockHandle.attach(JdbiActivityInstance.class)).thenReturn(mockActivityDao);
        when(mockHandle.attach(JdbiUserStudyLegacyData.class)).thenReturn(mockLegacyDataDao);

        when(mockActivityDao.getActivityInstanceId(any())).thenReturn(0L);

    }

    private void initMockStudyDataLoader() {
        //todo .. try Mockito.spy and avoid this constructor init stuff

        mockDataLoader.sourceDataSurveyQs = new HashMap<>();

        //some lookup codes/values
        mockDataLoader.yesNoDkLookup = new HashMap<>();
        mockDataLoader.yesNoDkLookup.put(0, "NO");
        mockDataLoader.yesNoDkLookup.put(1, "YES");
        mockDataLoader.yesNoDkLookup.put(2, "DK");

        mockDataLoader.booleanValueLookup = new HashMap<>();
        mockDataLoader.booleanValueLookup.put(0, false);
        mockDataLoader.booleanValueLookup.put(1, true);

        mockDataLoader.dkAltNames = new HashMap<>();
        mockDataLoader.dkAltNames.put("dk", "Don't know");

        mockDataLoader.altNames = new HashMap<>();
        mockDataLoader.altNames.put("AMERICAN_INDIAN", "American Indian or Native American");
        mockDataLoader.altNames.put("AXILLARY_LYMPH_NODES", "aux_lymph_node");
        mockDataLoader.altNames.put("OTHER_LYMPH_NODES", "other_lymph_node");

        mockDataLoader.altNames.put("drugstart_year", "drugstartyear");
        mockDataLoader.altNames.put("drugstart_month", "drugstartmonth");
        mockDataLoader.altNames.put("drugend_year", "drugendyear");
        mockDataLoader.altNames.put("drugend_month", "drugendmonth");

        mockDataLoader.auth0Util = mockAuth0Util;
        mockDataLoader.auth0Domain = pretendDomain;
        mockDataLoader.mgmtToken = pretendMgmtToken;
    }

    @Test
    public void testLoadRelease() throws Exception {

        doCallRealMethod().when(mockDataLoader).loadReleaseSurveyData(
                any(Handle.class),
                any(JsonElement.class),
                any(JsonElement.class),
                any(StudyDto.class),
                any(UserDto.class),
                any(ActivityInstanceDto.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        JdbiUserStudyLegacyData mockJdbiUserStudyLegacyData = mock(JdbiUserStudyLegacyData.class);
        MedicalProviderDao mockMedicalProviderDao = mock(MedicalProviderDao.class);
        JdbiUserStudyEnrollment mockJdbiUserStudyEnrollment = mock(JdbiUserStudyEnrollment.class);
        when(mockHandle.attach(MedicalProviderDao.class)).thenReturn(mockMedicalProviderDao);
        when(mockHandle.attach(JdbiUserStudyLegacyData.class)).thenReturn(mockJdbiUserStudyLegacyData);
        when(mockHandle.attach(JdbiUserStudyEnrollment.class)).thenReturn(mockJdbiUserStudyEnrollment);
        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null,
                null, now, now);
        StudyDto studyDto = new StudyDto(pretendStudyId, pretendStudyGuid, "MBC", null, null,
                1L, 2L, null, false, null, false);

        ActivityInstanceDto instanceDto = new ActivityInstanceDto(1L, pretendInstanceGuid, 1L, 1L, 1L,
                1L, 1L, true, false, null, null, null, true);

        mockDataLoader.loadReleaseSurveyData(
                mockHandle,
                sourceDataMap.get("releasesurvey"),
                mappingData.get("releasesurvey"),
                studyDto,
                userDto,
                instanceDto,
                mockAnswerDao);

        ArgumentCaptor<String> legacyDataCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockJdbiUserStudyLegacyData, times(1)).insert(anyLong(), anyLong(), anyLong(),
                anyString(), legacyDataCaptor.capture()
        );

        //release address and physician.streetaddress are saved into legacy_data
        String expectedAddress = "{\"fullName\":null,\"street1\":\"415 Main Street\",\"street2\":null,\"city\":\"Cambridge\","
                + "\"state\":\"MA\",\"country\":null,\"postalCode\":\"02144\",\"phone\":\"6173336666\"}";
        legacyDataCaptor.getAllValues().stream().forEach(value -> {
            if (!(value.equalsIgnoreCase(expectedAddress) || value.equals("Dr Love.320 charles st"))) {
                fail("Unexpected item:" + legacyDataCaptor.getValue() + "  :: value: " + value);
            }
        });


        ArgumentCaptor<MedicalProviderDto> medicalProviderDtoArgumentCaptor = ArgumentCaptor.forClass(MedicalProviderDto.class);
        verify(mockMedicalProviderDao, times(3)).insert(medicalProviderDtoArgumentCaptor.capture());

        medicalProviderDtoArgumentCaptor.getAllValues().stream()
                .forEach(dto -> {
                    if (dto.getInstitutionName().equals("MIT")) {
                        assertEquals("Dr Love",
                                dto.getPhysicianName());
                        assertEquals("Cambridge", dto.getCity());
                        assertEquals("MA", dto.getState());
                        assertEquals("02141", dto.getPostalCode());
                        assertEquals("888-888-7878", dto.getPhone());
                        assertEquals("GUID_93", dto.getLegacyGuid());
                        assertEquals(InstitutionType.PHYSICIAN, dto.getInstitutionType());
                    } else if (dto.getInstitutionName().equals("Mass General")) {
                        assertEquals("MA", dto.getState());
                        assertEquals("Boston", dto.getCity());
                        assertEquals("GUID_110", dto.getLegacyGuid());
                        assertEquals(InstitutionType.INITIAL_BIOPSY, dto.getInstitutionType());
                    } else if (dto.getInstitutionName().equals("BW")) {
                        assertEquals("MA", dto.getState());
                        assertEquals("Boston", dto.getCity());
                        assertEquals("109", dto.getLegacyGuid());
                        assertEquals(InstitutionType.INSTITUTION, dto.getInstitutionType());
                    } else {
                        fail("Unexpected item in bagging area");
                    }
                });

        assertTrue(medicalProviderDtoArgumentCaptor.getAllValues().stream()
                .map(dto -> dto.getInstitutionName())
                .collect(Collectors.toSet())
                .containsAll(Arrays.asList("MIT", "Mass General", "BW")));

        verify(mockJdbiUserStudyEnrollment, times(1))
                .changeUserStudyEnrollmentStatus(pretendUserGuid, pretendStudyGuid, EnrollmentStatusType.ENROLLED, 1552405260000L);
    }

    @Test
    public void testLoadBloodRelease() throws Exception {
        //JsonElement releaseData = sourceDataMap.get("bdreleasesurvey");
        doCallRealMethod().when(mockDataLoader).loadBloodReleaseSurveyData(
                any(Handle.class),
                any(JsonElement.class),
                any(JsonElement.class),
                any(StudyDto.class),
                any(UserDto.class),
                any(ActivityInstanceDto.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        JdbiUserStudyLegacyData mockJdbiUserStudyLegacyData = mock(JdbiUserStudyLegacyData.class);
        MedicalProviderDao mockMedicalProviderDao = mock(MedicalProviderDao.class);
        JdbiUserStudyEnrollment mockJdbiUserStudyEnrollment = mock(JdbiUserStudyEnrollment.class);
        JdbiMedicalProvider mockJdbiMedicalProvider = mock(JdbiMedicalProvider.class);
        JdbiInstitutionType mockInstitutionType = mock(JdbiInstitutionType.class);
        when(mockHandle.attach(MedicalProviderDao.class)).thenReturn(mockMedicalProviderDao);
        when(mockHandle.attach(JdbiUserStudyLegacyData.class)).thenReturn(mockJdbiUserStudyLegacyData);
        when(mockHandle.attach(JdbiUserStudyEnrollment.class)).thenReturn(mockJdbiUserStudyEnrollment);
        when(mockHandle.attach(JdbiMedicalProvider.class)).thenReturn(mockJdbiMedicalProvider);
        when(mockHandle.attach(JdbiInstitutionType.class)).thenReturn(mockInstitutionType);
        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null,
                null, now, now);
        StudyDto studyDto = new StudyDto(pretendStudyId, pretendStudyGuid, "MBC", null, null,
                1L, 2L, null, false, null, false);
        ActivityInstanceDto instanceDto = new ActivityInstanceDto(1L, pretendInstanceGuid, 1L, 1L, 1L,
                1L, 1L, true, false, null, null, null, true);
        mockDataLoader.loadBloodReleaseSurveyData(
                mockHandle,
                sourceDataMap.get("bdreleasesurvey"),
                mappingData.get("bdreleasesurvey"),
                studyDto,
                userDto,
                instanceDto,
                mockAnswerDao);

        ArgumentCaptor<MedicalProviderDto> medicalProviderDtoArgumentCaptor = ArgumentCaptor.forClass(MedicalProviderDto.class);
        verify(mockMedicalProviderDao, times(1)).insert(medicalProviderDtoArgumentCaptor.capture());

        medicalProviderDtoArgumentCaptor.getAllValues().stream()
                .forEach(dto -> {
                    if (dto.getInstitutionName().equals("Inst#1")) {
                        assertEquals("Dr Physician",
                                dto.getPhysicianName());
                        assertEquals("Boston", dto.getCity());
                        assertEquals("MA", dto.getState());
                        assertEquals("02111", dto.getPostalCode());
                        assertEquals(null, dto.getPhone());
                        assertEquals("119", dto.getLegacyGuid());
                        assertEquals(InstitutionType.PHYSICIAN, dto.getInstitutionType());
                    } else {
                        fail("Unexpected item in bagging area");
                    }
                });

        assertTrue(medicalProviderDtoArgumentCaptor.getAllValues().stream()
                .map(dto -> dto.getInstitutionName())
                .collect(Collectors.toSet())
                .containsAll(Arrays.asList("Inst#1")));

        verify(mockJdbiUserStudyEnrollment, times(0))
                .changeUserStudyEnrollmentStatus(pretendUserGuid, pretendStudyGuid, EnrollmentStatusType.ENROLLED, 1552405260000L);
    }

    @Test
    public void testLoadAboutYou() throws Exception {

        doCallRealMethod().when(mockDataLoader).loadAboutYouSurveyData(
                any(Handle.class),
                any(JsonElement.class),
                any(JsonElement.class),
                any(StudyDto.class),
                any(UserDto.class),
                any(ActivityInstanceDto.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null,
                null, now, now);
        StudyDto studyDto = new StudyDto(pretendStudyId, pretendStudyGuid, "MBC", null, null,
                1L, 2L, null, false, null, false);

        ActivityInstanceDto instanceDto = new ActivityInstanceDto(1L, pretendInstanceGuid, 1L, 1L, 1L,
                1L, 1L, true, false, null, null, null, true);

        mockDataLoader.loadAboutYouSurveyData(
                mockHandle,
                sourceDataMap.get("aboutyousurvey"),
                mappingData.get("aboutyousurvey"),
                studyDto,
                userDto,
                instanceDto,
                mockAnswerDao);

        ArgumentCaptor<String> pickListQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SelectedPicklistOption>> pickListQAnswerValue = ArgumentCaptor.forClass(List.class);
        verify(mockDataLoader, times(9)).answerPickListQuestion(
                pickListQPepperStableId.capture(),
                anyString(),
                anyString(),
                pickListQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> dateQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateQAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(4)).answerDateQuestion(
                dateQPepperStableId.capture(),
                anyString(),
                anyString(),
                dateQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(6)).answerTextQuestion(
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


        assertEquals((int) dateAnswers.get("DIAGNOSIS_DATE").getMonth(), 3);
        assertEquals((int) dateAnswers.get("DIAGNOSIS_DATE").getYear(), 2018);
        assertEquals(dateAnswers.get("DIAGNOSIS_DATE").getDay(), null);

        assertEquals(dateAnswers.get("BIRTH_YEAR").getMonth(), null);
        assertEquals((int) dateAnswers.get("BIRTH_YEAR").getYear(), 1978);
        assertEquals(dateAnswers.get("BIRTH_YEAR").getDay(), null);

        List<SelectedPicklistOption> race = pickListAnswers.get("RACE");
        assertEquals(3, race.size());
        assertEquals("AMERICAN_INDIAN", race.get(0).getStableId());
        assertEquals(null, race.get(0).getDetailText());
        assertEquals("OTHER", race.get(2).getStableId());
        assertEquals("something else not on the list", race.get(2).getDetailText());

        Set<String> yesNoDkQuestions = new HashSet<>(Arrays.asList(
                "HER2_POSITIVE",
                //"TRIPLE_NEGATIVE",
                "HISPANIC",
                "THERAPIES",
                "WORKED_THERAPIES",
                "INFLAMMATORY",
                "HR_POSITIVE"
        ));

        pickListAnswers.entrySet().stream()
                .filter(entry -> yesNoDkQuestions.contains(entry.getKey()))
                .forEach(entry -> {
                    assertEquals("We expected a YES answer on: " + entry.getKey(), 1, entry.getValue().stream()
                            .filter(answer -> answer.getStableId().equals(YES))
                            .count());
                });

        assertEquals(1, pickListAnswers.get("RACE").stream()
                .filter(entry -> entry.getStableId().equals("OTHER"))
                .filter(entry -> entry.getDetailText().equals("something else not on the list"))
                .count());

        assertEquals(1, pickListAnswers.get("COUNTRY").stream()
                .filter(entry -> entry.getStableId().equals("US"))
                .count());


        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        assertEquals(textAnswers.get("WORKED_THERAPIES_NOTE"), "tamoxifen (Nolvadex) worked very well for me");
        assertEquals(textAnswers.get("OTHER_COMMENTS"), "it's terrible");
        assertEquals(textAnswers.get("HEARD_FROM"), "From an existing study participant");
        assertEquals(textAnswers.get("POSTAL_CODE"), "02144");
    }

    @Test
    public void testAddKitDetails() {
        JsonElement datstatParticipantData = sourceDataMap.get("datstatparticipantdata");
        String kitGuid = datstatParticipantData.getAsJsonObject().get("ddp_spit_kit_request_id").getAsString();

        KitTypeDao mockKitTypeDao = mock(KitTypeDao.class);
        when(mockKitTypeDao.getSalivaKitType()).thenReturn(pretendSalivaKitTypeId);

        DsmKitRequestDao mockDsmKitRequestDao = mock(DsmKitRequestDao.class);
        when(mockDsmKitRequestDao.createKitRequest(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong())).thenReturn(0L);

        when(mockDataLoader.addKitDetails(any(DsmKitRequestDao.class),
                any(KitTypeDao.class),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString())).thenCallRealMethod();

        mockDataLoader.addKitDetails(mockDsmKitRequestDao,
                mockKitTypeDao,
                pretendUserId,
                pretendMailAddressId,
                kitGuid,
                pretendStudyGuid,
                "07/01/2015");

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

        assertEquals("V6YUYSY07RP8CQAAHBRA_8148_24", kitGuidCaptor.getValue());
        assertEquals(pretendStudyGuid, studyGuidCaptor.getValue());
        assertEquals((Long) pretendSalivaKitTypeId.getId(), kitIdCaptor.getValue());
        assertEquals((Long) pretendUserId, pepperUserIdCaptor.getValue());
        assertEquals((Long) 1435708800L, secondsSinceEpochCaptor.getValue());
    }

    @Test
    public void testAddUserAddress() {
        JsonElement participantData = sourceDataMap.get("datstatparticipantdata");

        JdbiMailAddress mockJdbiMailAddress = mock(JdbiMailAddress.class);
        MailAddress mailAddress = new MailAddress();
        mailAddress.setGuid(pretendMailAddressGuid);
        when(mockJdbiMailAddress.insertLegacyAddress(
                any(MailAddress.class),
                anyString(),
                anyString(),
                anyLong())).thenReturn(mailAddress);

        when(mockDataLoader.getUserAddress(any(Handle.class),
                any(JsonElement.class),
                anyString(),
                eq(olcService), eq(addressService))).thenCallRealMethod();

        when(mockDataLoader.addUserAddress(any(Handle.class),
                any(UserDto.class),
                any(JsonElement.class),
                anyString(), any(MailAddress.class),
                any(JdbiMailAddress.class),
                eq(olcService), eq(addressService))).thenCallRealMethod();

        JdbiCountrySubnationalDivision mockDao = mock(JdbiCountrySubnationalDivision.class);
        when(mockHandle.attach(any())).thenReturn(mockDao);

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null, null, now, now);
        mailAddress = mockDataLoader.getUserAddress(mockHandle,
                participantData,
                pretendPhoneNumber, olcService, addressService);

        mockDataLoader.addUserAddress(mockHandle, userDto,
                participantData,
                pretendPhoneNumber, mailAddress,
                mockJdbiMailAddress, olcService, addressService);

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
        assertEquals("US", mailAddressArgumentCaptor.getValue().getCountry());
        assertEquals("02144", mailAddressArgumentCaptor.getValue().getZip());

        verify(mockJdbiMailAddress, times(1)).setDefaultAddressForParticipant(anyString());
    }

    @Test
    public void testAddUserProfile() {

        JsonElement participantData = sourceDataMap.get("datstatparticipantdata");
        when(mockDataLoader.addUserProfile(
                any(UserDto.class),
                any(JsonElement.class),
                any(JdbiLanguageCode.class),
                any(UserProfileDao.class)
        )).thenCallRealMethod();

        JdbiLanguageCode mockJdbiLanguageCode = mock(JdbiLanguageCode.class);
        when(mockJdbiLanguageCode.getLanguageCodeId(anyString())).thenReturn(pretendLanguageCodeId);

        UserProfileDao mockProfileDao = mock(UserProfileDao.class);
        doNothing().when(mockProfileDao).createProfile(any(UserProfile.class));

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null, null, now, now);

        mockDataLoader.addUserProfile(
                userDto,
                participantData,
                mockJdbiLanguageCode,
                mockProfileDao);

        verify(mockJdbiLanguageCode, times(1)).getLanguageCodeId(anyString());

        ArgumentCaptor<UserProfile> userProfileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(mockProfileDao, times(1)).createProfile(userProfileCaptor.capture());

        assertEquals(pretendUserId, userProfileCaptor.getValue().getUserId());

        assertEquals("First1539381231204", userProfileCaptor.getValue().getFirstName());
        assertEquals("Last1539381231204", userProfileCaptor.getValue().getLastName());
        assertEquals(pretendLanguageCodeId, userProfileCaptor.getValue().getPreferredLangId());
    }

    @Test
    public void testCreateLegacyPepperUser() throws Exception {
        JsonElement participantData = sourceDataMap.get("datstatparticipantdata");

        when(mockMgmtClient.getToken()).thenReturn(pretendMgmtToken);

        when(mockAuth0Util.createAuth0User(anyString(), anyString(), anyString())).thenReturn(mockAuth0User);

        when(mockAuth0User.getId()).thenReturn(pretendAuth0UserId);

        when(mockJdbiClient.getClientIdByAuth0ClientAndDomain(anyString(), anyString())).thenReturn(Optional.of(pretendPepperClientId));

        when(mockAuthAPI.login(anyString(), anyString())).thenReturn(mockAuthRequest);

        when(mockAuthRequest.setRealm(anyString())).thenReturn(mockAuthRequest);

        when(mockClientDto.getId()).thenReturn(pretendPepperClientId);
        when(mockClientDto.getAuth0ClientId()).thenReturn(pretendAuth0ClientId);


        when(mockDataLoader.createLegacyPepperUser(
                any(JdbiUser.class),
                any(JdbiClient.class),
                any(JsonElement.class),
                anyString(),
                anyString(),
                any(ClientDto.class)
        )).thenCallRealMethod();

        mockDataLoader.createLegacyPepperUser(
                mockJdbiUser,
                mockJdbiClient,
                participantData,
                pretendUserGuid,
                pretendUserHruid,
                mockClientDto
        );

        ArgumentCaptor<String> creationEmail = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> creationPass = ArgumentCaptor.forClass(String.class);
        verify(mockAuth0Util, times(1)).createAuth0User(creationEmail.capture(),
                creationPass.capture(), anyString());
        assertEquals("mbcmigration+999+9@broadinstitute.org", creationEmail.getValue());

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
        assertEquals("814824", legacyAltPidCaptor.getValue());
        assertEquals("814824", legacyShortIdCaptor.getValue());
        assertEquals(1496934180000L, (long) createdAtDateCaptor.getValue());
        assertEquals(1496934180000L, (long) lastModifiedDateCaptor.getValue());

        verify(mockAuth0Util, times(1)).setDDPUserGuidForAuth0User(
                anyString(),
                anyString(),
                anyString(),
                anyString());
    }


    @Test
    public void testLoadFollowUpConsent() throws Exception {

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null,
                null, now, now);
        StudyDto studyDto = new StudyDto(pretendStudyId, pretendStudyGuid, "MBC", null, null,
                1L, 2L, null, false, null, false);

        ActivityInstanceDto instanceDto = new ActivityInstanceDto(1L, pretendInstanceGuid, 1L, 1L, 1L,
                1L, 1L, true, false, null, null, null, true);

        doCallRealMethod().when(mockDataLoader).loadFollowupSurveyData(
                any(Handle.class),
                any(JsonElement.class),
                any(JsonElement.class),
                any(StudyDto.class),
                any(UserDto.class),
                any(ActivityInstanceDto.class),
                any(JdbiActivityInstance.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        JdbiActivityInstance mockActivityInstanceDao = mock(JdbiActivityInstance.class);
        mockDataLoader.loadFollowupSurveyData(mockHandle,
                sourceDataMap.get("followupsurvey"),
                mappingData.get("followupsurvey"),
                studyDto,
                userDto,
                instanceDto,
                mockActivityInstanceDao,
                mockAnswerDao);

        ArgumentCaptor<String> pickListQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SelectedPicklistOption>> pickListQAnswerValue = ArgumentCaptor.forClass(List.class);
        verify(mockDataLoader, times(11)).answerPickListQuestion(
                pickListQPepperStableId.capture(),
                anyString(),
                anyString(),
                pickListQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> dateQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateQAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(3)).answerDateQuestion(
                dateQPepperStableId.capture(),
                anyString(),
                anyString(),
                dateQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(2)).answerTextQuestion(
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> boolQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> boolQAnswerValue = ArgumentCaptor.forClass(Boolean.class);
        verify(mockDataLoader, times(0)).answerBooleanQuestion(
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

        Map<String, List<SelectedPicklistOption>> pickListAnswers = IntStream.range(0, pickListQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> pickListQPepperStableId.getAllValues().get(i),
                        i -> pickListQAnswerValue.getAllValues().get(i)));

        Set<String> yesNoDkQuestions = new HashSet<>(Arrays.asList(
                "DIAGNOSIS_SPREAD",
                "POST_DIAGNOSIS_SPREAD"
        ));

        pickListAnswers.entrySet().stream()
                .filter(entry -> yesNoDkQuestions.contains(entry.getKey()))
                .forEach(entry -> {
                    assertEquals("We expected a YES answer on: " + entry.getKey(), 1, entry.getValue().stream()
                            .filter(answer -> answer.getStableId().equals(DataLoader.YES))
                            .count());
                });

        Set<String> spreadLocQuestions = new HashSet<>(Arrays.asList(
                "CURRENT_CANCER_LOC",
                "DIAGNOSIS_CANCER_LOC",
                "ANYTIME_CANCER_LOC"
        ));

        Set<String> expectedSpreadLocations = new HashSet<>(Arrays.asList("BREAST", "AXILLARY_LYMPH_NODES", "OTHER_LYMPH_NODES"));

        pickListAnswers.entrySet().stream()
                .filter(entry -> spreadLocQuestions.contains(entry.getKey()))
                .forEach(entry -> {
                    Set<String> presentStableIds = entry.getValue().stream()
                            .map(answer -> answer.getStableId())
                            .collect(Collectors.toSet());

                    assertTrue(presentStableIds.containsAll(expectedSpreadLocations));
                });

        assertEquals(1, pickListAnswers.get("CURRENT_CANCER_LOC").stream()
                .filter(entry -> entry.getStableId().equals("BREAST"))
                .count());

        assertEquals(1, pickListAnswers.get("DIAGNOSIS_CANCER_LOC").stream()
                .filter(entry -> entry.getStableId().equals("BONE"))
                .count());

        assertEquals(1, pickListAnswers.get("ANYTIME_CANCER_LOC").stream()
                .filter(entry -> entry.getStableId().equals("AXILLARY_LYMPH_NODES"))
                .count());
    }

    @Test
    public void testLoadTissueConsent() throws Exception {

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null,
                null, now, now);
        StudyDto studyDto = new StudyDto(pretendStudyId, pretendStudyGuid, "MBC", null, null,
                1L, 2L, null, false, null, false);

        ActivityInstanceDto instanceDto = new ActivityInstanceDto(1L, pretendInstanceGuid, 1L, 1L, 1L,
                1L, 1L, true, false, null, null, null, true);

        doCallRealMethod().when(mockDataLoader).loadTissueConsentSurveyData(
                any(Handle.class),
                any(JsonElement.class),
                any(JsonElement.class),
                any(StudyDto.class),
                any(UserDto.class),
                any(ActivityInstanceDto.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        mockDataLoader.loadTissueConsentSurveyData(mockHandle,
                sourceDataMap.get("consentsurvey"),
                mappingData.get("tissueconsentsurvey"),
                studyDto,
                userDto,
                instanceDto,
                mockAnswerDao);

        ArgumentCaptor<String> dateValueStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateValueAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(1)).answerDateQuestion(
                dateValueStableId.capture(),
                anyString(),
                anyString(),
                dateValueAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(1)).answerTextQuestion(
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));

        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        Map<String, DateValue> dateValueAnswers = IntStream.range(0, dateValueStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> dateValueStableId.getAllValues().get(i), i -> dateValueAnswerValue.getAllValues().get(i)));

        assertEquals("Participant #1", textAnswers.get("TISSUECONSENT_FULLNAME"));
        assertEquals((Integer) 1976, dateValueAnswers.get("TISSUECONSENT_DOB").getYear());
        assertEquals((Integer) 12, dateValueAnswers.get("TISSUECONSENT_DOB").getMonth());
        assertEquals((Integer) 11, dateValueAnswers.get("TISSUECONSENT_DOB").getDay());
    }

    @Test
    public void testLoadBloodConsent() throws Exception {

        long now = Instant.now().toEpochMilli();
        UserDto userDto = new UserDto(pretendUserId, pretendAuth0UserId, pretendUserGuid, pretendUserGuid, null,
                null, now, now);
        StudyDto studyDto = new StudyDto(pretendStudyId, pretendStudyGuid, "MBC", null, null,
                1L, 2L, null, false, null, false);

        ActivityInstanceDto instanceDto = new ActivityInstanceDto(1L, pretendInstanceGuid, 1L, 1L, 1L,
                1L, 1L, true, false, null, null, null, true);

        doCallRealMethod().when(mockDataLoader).loadConsentSurveyData(
                any(Handle.class),
                any(JsonElement.class),
                any(JsonElement.class),
                any(StudyDto.class),
                any(UserDto.class),
                any(ActivityInstanceDto.class),
                any(AnswerDao.class));

        AnswerDao mockAnswerDao = mock(AnswerDao.class);
        mockDataLoader.loadConsentSurveyData(mockHandle,
                sourceDataMap.get("bdconsentsurvey"),
                mappingData.get("bdconsentsurvey"),
                studyDto,
                userDto,
                instanceDto,
                mockAnswerDao);

        ArgumentCaptor<String> dateValueStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DateValue> dateValueAnswerValue = ArgumentCaptor.forClass(DateValue.class);
        verify(mockDataLoader, times(2)).answerDateQuestion(
                dateValueStableId.capture(),
                anyString(),
                anyString(),
                dateValueAnswerValue.capture(),
                any(AnswerDao.class));

        ArgumentCaptor<String> textQPepperStableId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textQAnswerValue = ArgumentCaptor.forClass(String.class);
        verify(mockDataLoader, times(3)).answerTextQuestion(
                textQPepperStableId.capture(),
                anyString(),
                anyString(),
                textQAnswerValue.capture(),
                any(AnswerDao.class));

        Map<String, String> textAnswers = IntStream.range(0, textQPepperStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> textQPepperStableId.getAllValues().get(i), i -> textQAnswerValue.getAllValues().get(i)));

        Map<String, DateValue> dateValueAnswers = IntStream.range(0, dateValueStableId.getAllValues().size())
                .boxed()
                .collect(Collectors.toMap(i -> dateValueStableId.getAllValues().get(i), i -> dateValueAnswerValue.getAllValues().get(i)));


        assertEquals("Blood Consent Participant", textAnswers.get("BLOODCONSENT_FULLNAME"));
        assertEquals((Integer) 1989, dateValueAnswers.get("BLOODCONSENT_DOB").getYear());
        assertEquals((Integer) 12, dateValueAnswers.get("BLOODCONSENT_DOB").getMonth());
        assertEquals((Integer) 11, dateValueAnswers.get("BLOODCONSENT_DOB").getDay());
        assertEquals((Integer) 4, dateValueAnswers.get("BLOODCONSENT_TREATMENT_START").getMonth());
        assertEquals((Integer) 2018, dateValueAnswers.get("BLOODCONSENT_TREATMENT_START").getYear());

        assertEquals("BD consent treatment text...", textAnswers.get("BLOODCONSENT_TREATMENT_NOW"));
        assertEquals("BD consent treatment past text...", textAnswers.get("BLOODCONSENT_TREATMENT_PAST"));
    }

}
