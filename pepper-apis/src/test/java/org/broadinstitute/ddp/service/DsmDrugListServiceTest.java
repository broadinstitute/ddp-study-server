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

public class DsmDrugListServiceTest extends ConfigAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(DsmDrugListServiceTest.class);
    private static DsmDrugListService service;
    private static URL dsmBaseUrl;

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, false,  dsmBaseUrl.getPort());

    @BeforeClass
    public static void setupService() {
        try {
            dsmBaseUrl = new URL(ConfigManager.getInstance().getConfig().getString(ConfigFile.DSM_BASE_URL));
        } catch (MalformedURLException e) {
            throw new DDPException("Invalid base dsm url: " + dsmBaseUrl);
        }
        service = new DsmDrugListService(dsmBaseUrl);
    }

    @Test
    public void testService() {
        String[] sampleDrugList = {"Drug1", "Drug2", "Drug3", "Drug4"};
        TestUtil.stubMockServerForRequest(
                mockServerRule.getPort(),
                "/" + RouteConstants.API.DSM_DRUGS,
                200,
                new Gson().toJson(sampleDrugList)
        );

        Assert.assertNotNull(service);
        Assert.assertNotNull(dsmBaseUrl);
        List<String> dsmDrugs = service.fetchDrugs(cfg.getString(ConfigFile.DSM_JWT_SECRET));
        Assert.assertNotNull(dsmDrugs);
        Assert.assertEquals(4, dsmDrugs.size());
        Assert.assertTrue(dsmDrugs.contains(sampleDrugList[0]));

    }

}
