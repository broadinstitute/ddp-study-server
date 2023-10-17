package org.broadinstitute.ddp.route;

import java.util.Arrays;
import java.util.Collections;

import com.google.gson.Gson;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.hamcrest.Matchers;

import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ListCancersRouteStandaloneTest extends IntegrationTestSuite.TestCase {
    private static final String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.LIST_CANCERS;

    private static final String EXPECTED_CANCERS = "[\n"
            + "\t{\n"
            + "\t\t\"name\":\"Cancer in English\",\n"
            + "\t\t\"language\":\"en\"\n"
            + "\t},\n"
            + "\t{\n"
            + "\t\t\"name\":\"Cancer in Spanish\",\n"
            + "\t\t\"language\":\"es\"\n"
            + "\t}\n"
            + "]";

    @BeforeClass
    public static void setupClass() {
        TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void givenCancerListExists_whenRouteIsCalled_thenValidListOfCancersIsReturned() {
        CancerItem[] cancers = new Gson().fromJson(EXPECTED_CANCERS, CancerItem[].class);

        CancerStore.getInstance().populate(Arrays.asList(cancers));
        log.info("Calling the route, url = " + url);
        RestAssured.given().when().get(url).then().assertThat().statusCode(200).contentType(ContentType.JSON)
                .body("$", Matchers.hasSize(2))
                .body("[0].name", Matchers.is("Cancer in English"))
                .body("[0].language", Matchers.is("en"))
                .body("[1].name", Matchers.is("Cancer in Spanish"))
                .body("[1].language", Matchers.is("es"));
        CancerStore.getInstance().populate(Collections.emptyList());
    }
}
