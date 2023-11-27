package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.API.ADDRESS_COUNTRIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.address.CountryAddressInfo;
import org.broadinstitute.ddp.model.address.CountryAddressInfoSummary;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetMailAddressInfoRouteTest extends IntegrationTestSuite.TestCase {
    private static final Gson gson = new Gson();

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        token = testData.getTestingUser().getToken();
        url = RouteTestUtil.getTestingBaseUrl() + ADDRESS_COUNTRIES;
    }

    @Test
    public void getAllCountrySummaryInfos() throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, url).execute();
        HttpResponse httpResponse = res.returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        CountryAddressInfoSummary[] addressinfoSummaries = gson.fromJson(EntityUtils.toString(httpResponse.getEntity()),
                CountryAddressInfoSummary[].class);
        assertTrue(addressinfoSummaries.length > 1);

        //check if US is included
        Optional<CountryAddressInfoSummary> usInfo = Arrays.stream(addressinfoSummaries)
                .filter((country) -> country.getCode().equals("US")).findFirst();
        assertTrue(usInfo.isPresent());
        assertTrue(usInfo.get().getName().startsWith("United States"));
        //making sure id is not set
        assertEquals(0, usInfo.get().getId());
    }

    @Test
    public void getUsCountryInfo() throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, url + "/US").execute();
        HttpResponse httpResponse = res.returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        CountryAddressInfo usAddressInfo = gson.fromJson(EntityUtils.toString(httpResponse.getEntity()),
                CountryAddressInfo.class);
        assertNull(usAddressInfo.getStateCode());

        assertEquals("US", usAddressInfo.getCode());
        assertTrue(usAddressInfo.getName().startsWith("United States"));
        assertEquals("Zip Code", usAddressInfo.getPostalCodeLabel());
        assertEquals("State", usAddressInfo.getSubnationalDivisionTypeName());
        assertNotNull(usAddressInfo.getPostalCodeRegex());
        Pattern regexPattern = Pattern.compile(usAddressInfo.getPostalCodeRegex());
        assertTrue(regexPattern.matcher("11111").matches());
        assertFalse(regexPattern.matcher("b1111").matches());

        assertEquals(51, usAddressInfo.getSubnationalDivisions().size());

        assertEquals(51, usAddressInfo.getSubnationalDivisions().stream()
                .filter((state) -> state.getCode().length() == 2
                        && state.getName().length() > 3 && state.getId() == 0).count());

    }
}
