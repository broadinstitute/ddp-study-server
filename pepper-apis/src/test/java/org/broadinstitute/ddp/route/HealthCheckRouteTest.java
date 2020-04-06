package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;

import io.restassured.http.Header;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.junit.BeforeClass;
import org.junit.Test;

public class HealthCheckRouteTest extends IntegrationTestSuite.TestCase {

    private static String url;
    private static String password;

    @BeforeClass
    public static void setup() {
        url = RouteTestUtil.getTestingBaseUrl() + API.HEALTH_CHECK;
        password = RouteTestUtil.getConfig().getString(ConfigFile.HEALTHCHECK_PASSWORD);
    }

    @Test
    public void testGoodPassword() {
        given().when().get(url).then().assertThat()
                .statusCode(200);
    }

}
