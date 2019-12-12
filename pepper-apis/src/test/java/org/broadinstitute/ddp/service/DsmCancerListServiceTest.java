package org.broadinstitute.ddp.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import com.google.gson.Gson;

import org.broadinstitute.ddp.ConfigAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import org.mockserver.junit.MockServerRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DsmCancerListServiceTest extends ConfigAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(DsmCancerListServiceTest.class);
    private static DsmCancerListService service;
    private static URL dsmBaseUrl;
    private static final String SIGNER = "org.broadinstitute.kdux";

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, false,  dsmBaseUrl.getPort());

    @BeforeClass
    public static void setupService() {
        try {
            dsmBaseUrl = new URL(ConfigManager.getInstance().getConfig().getString(ConfigFile.DSM_BASE_URL));
        } catch (MalformedURLException e) {
            throw new DDPException("Invalid base dsm url: " + dsmBaseUrl.toString());
        }
        service = new DsmCancerListService(dsmBaseUrl.toString());
    }

    @Test
    public void test_GivenCancersExistInDsm_whenServiceIsCalled_thenValidListOfThemIsReturned() {
        String[] sampleCancerList = {"Cancer1", "Cancer2", "Cancer3", "Cancer4"};
        TestUtil.stubMockServerForRequest(
                mockServerRule.getPort(),
                "/" + RouteConstants.API.DSM_CANCERS,
                200,
                new Gson().toJson(sampleCancerList)
        );

        Assert.assertNotNull(dsmBaseUrl);
        List<String> dsmCancers = service.fetchCancerList(cfg.getString(ConfigFile.DSM_JWT_SECRET));
        Assert.assertNotNull(dsmCancers);
        Assert.assertEquals(4, dsmCancers.size());
        Assert.assertTrue(dsmCancers.contains(sampleCancerList[0]));

    }

}
