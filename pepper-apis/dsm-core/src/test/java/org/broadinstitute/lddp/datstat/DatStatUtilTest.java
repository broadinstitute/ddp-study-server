package org.broadinstitute.lddp.datstat;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.TestHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

//todo EOB - refactor.. there need to be way more tests here!!!

/**
 * These tests require DatStat. In some cases very specific configuration steps must be followed before the tests can be run.
 * Please read through the notes for the tests below to see what configuration changes need to be made.
 */
public class DatStatUtilTest
{
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private Config ddpConfig;
    private DatStatUtil datStatUtil;

    @Before
    public void setUp() throws Exception  {
        ddpConfig = TestHelper.resetAuthSingletonAndGetDDPProperties();
        //datStatUtil = new DatStatUtil(ddpConfig,null);
    }

    @Test
    public void testGetAuthSingleton() throws Exception
    {
        throw new Exception("TESTS NOT REFACTORED!!!!!!!");
        /*Config cfg = TestHelper.loadDDPTestProperties();

        AuthSingleton singleton = AuthSingleton.getInstance(true, cfg.getString(ConfigProperties.DATSTAT_KEY),
                cfg.getString(ConfigProperties.DATSTAT_SECRET),
                cfg.getString(ConfigProperties.DATSTAT_USERNAME),
                cfg.getString(ConfigProperties.DATSTAT_PASSWORD),
                cfg.getString(ConfigProperties.DATSTAT_URL));*/
    }

    /*@Test
    public void testGetAuthSingletonMultipleTimes() throws Exception
    {
        Config cfg = TestHelper.loadDDPTestProperties();

        AuthSingleton singleton1 = AuthSingleton.getInstance(true, cfg.getString(ConfigProperties.DATSTAT_KEY),
                cfg.getString(ConfigProperties.DATSTAT_SECRET),
                cfg.getString(ConfigProperties.DATSTAT_USERNAME),
                cfg.getString(ConfigProperties.DATSTAT_PASSWORD),
                cfg.getString(ConfigProperties.DATSTAT_URL));
        int firstHash = singleton1.getToken().hashCode();
        AuthSingleton singleton2 = AuthSingleton.getInstance(false, cfg.getString(ConfigProperties.DATSTAT_KEY),
                cfg.getString(ConfigProperties.DATSTAT_SECRET),
                cfg.getString(ConfigProperties.DATSTAT_USERNAME),
                cfg.getString(ConfigProperties.DATSTAT_PASSWORD),
                cfg.getString(ConfigProperties.DATSTAT_URL));
        int secondHash = singleton2.getToken().hashCode();

        assertEquals(System.identityHashCode(singleton1), System.identityHashCode(singleton2));
        assertEquals(singleton1.getToken(), singleton2.getToken());
        assertEquals(firstHash, secondHash);

        AuthSingleton singleton3 = AuthSingleton.getInstance(true, cfg.getString(ConfigProperties.DATSTAT_KEY),
                cfg.getString(ConfigProperties.DATSTAT_SECRET),
                cfg.getString(ConfigProperties.DATSTAT_USERNAME),
                cfg.getString(ConfigProperties.DATSTAT_PASSWORD),
                cfg.getString(ConfigProperties.DATSTAT_URL));

        assertEquals(System.identityHashCode(singleton2), System.identityHashCode(singleton3));
        assertNotEquals(secondHash, singleton3.getToken().hashCode());
    }

    @Test
    public void testGetAuthSingletonBadUserCredentials()
    {
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(com.google.api.client.http.HttpResponseException.class));
        thrown.expect(DatStatTokenException.class);
        AuthSingleton singleton = AuthSingleton.getInstance(true, ddpConfig.getString(ConfigProperties.DATSTAT_KEY),
                ddpConfig.getString(ConfigProperties.DATSTAT_SECRET),
                ddpConfig.getString(ConfigProperties.DATSTAT_USERNAME),
                "not the right password",
                ddpConfig.getString(ConfigProperties.DATSTAT_URL));
    }

    @Test
    public void testSurveyServiceCommunication() {
        SurveyService surveyService = new SurveyService();
        Collection<SurveyDefinition> surveyDefinitions = surveyService.fetchSurveys(datStatUtil);
        Assert.assertFalse(surveyDefinitions.isEmpty());
    }

    @Test
    public void testGetAuthSingletonBadApiCredentials()
    {
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(com.google.api.client.http.HttpResponseException.class));
        thrown.expect(DatStatTokenException.class);
        AuthSingleton singleton = AuthSingleton.getInstance(true, "bad api key",
                ddpConfig.getString(ConfigProperties.DATSTAT_SECRET),
                ddpConfig.getString(ConfigProperties.DATSTAT_USERNAME),
                ddpConfig.getString(ConfigProperties.DATSTAT_PASSWORD),
                ddpConfig.getString(ConfigProperties.DATSTAT_URL));
    }

    @Test
    public void testGetAuthSingletonBadUrl()
    {
        thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(java.lang.IllegalArgumentException.class));
        thrown.expect(DatStatTokenException.class);

        AuthSingleton singleton = AuthSingleton.getInstance(true, "bad api key",
                ddpConfig.getString(ConfigProperties.DATSTAT_SECRET),
                ddpConfig.getString(ConfigProperties.DATSTAT_USERNAME),
                ddpConfig.getString(ConfigProperties.DATSTAT_PASSWORD),
                "bad url");
    }

    @Test
    public void testSendRequestRetriesGeneratesNewToken() throws Exception
    {
        AuthSingleton singleton1 = AuthSingleton.getInstance(false, ddpConfig.getString(ConfigProperties.DATSTAT_KEY),
                ddpConfig.getString(ConfigProperties.DATSTAT_SECRET),
                ddpConfig.getString(ConfigProperties.DATSTAT_USERNAME),
                ddpConfig.getString(ConfigProperties.DATSTAT_PASSWORD),
                ddpConfig.getString(ConfigProperties.DATSTAT_URL));
        int firstHash = singleton1.getToken().hashCode();

        try
        {
            HttpResponse response = datStatUtil.sendRequest(DatStatUtil.MethodType.GET, "badpath", null);
        }
        catch (DatStatRestApiException ex)
        {

        }

        AuthSingleton singleton2 = AuthSingleton.getInstance(false, ddpConfig.getString(ConfigProperties.DATSTAT_KEY),
                ddpConfig.getString(ConfigProperties.DATSTAT_SECRET),
                ddpConfig.getString(ConfigProperties.DATSTAT_USERNAME),
                ddpConfig.getString(ConfigProperties.DATSTAT_PASSWORD),
                ddpConfig.getString(ConfigProperties.DATSTAT_URL));
        int secondHash = singleton2.getToken().hashCode();

        assertEquals(System.identityHashCode(singleton1), System.identityHashCode(singleton2));
        assertNotEquals(firstHash, secondHash);
    }

    @Test
    public void testGetParticipantListId()  {

        assertNotNull(datStatUtil.getDerivedParticipantListId());
    }


    @Test
    public void testGetAllParticipants()  {
        JsonArray participantsArray = datStatUtil.getAllParticipants(ParticipantFields.values());
        assertNotNull(participantsArray);
    }
*/
}
