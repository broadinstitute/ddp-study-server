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
        given().header("Host", password)
                .when().get(url).then().assertThat()
                .statusCode(200);
    }

    @Test
    public void testBadPassword() {
        given().header(new Header("Host", null))
                .when().get(url).then().assertThat()
                .statusCode(401);
        given().header("Host", "bad password")
                .when().get(url).then().assertThat()
                .statusCode(401);
    }
}
