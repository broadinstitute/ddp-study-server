package org.broadinstitute.ddp.route;

import java.util.Arrays;
import java.util.Collections;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.hamcrest.Matchers;

import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ListCancersRouteStandaloneTest extends IntegrationTestSuite.TestCase {
    private static final String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.LIST_CANCERS;

    @BeforeClass
    public static void setupClass() {
        TestDataSetupUtil.GeneratedTestData testData =
                TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void givenCancerListExists_whenRouteIsCalled_thenValidListOfCancersIsReturned() {
        CancerStore.getInstance().populate(Arrays.asList("Cancer1", "Cancer2"));
        log.info("Calling the route, url = " + url);
        RestAssured.given().when().get(url).then().assertThat().statusCode(200).contentType(ContentType.JSON)
                .body("$", Matchers.hasSize(2))
                .body("[0].name", Matchers.is("Cancer1"));
        CancerStore.getInstance().populate(Collections.emptyList());
    }
}
