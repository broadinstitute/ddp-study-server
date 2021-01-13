package org.broadinstitute.ddp.util;

import static io.restassured.RestAssured.config;
import static io.restassured.config.RedirectConfig.redirectConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.StringBody;
import org.mockserver.verify.VerificationTimes;

public class TestUtil {

    /**
     * Helper to wrap each question definition in a block.
     *
     * @param questions array of questions
     * @return list of blocks
     */
    public static List<FormBlockDef> wrapQuestions(QuestionDef... questions) {
        return Stream.of(questions).map(QuestionBlockDef::new).collect(Collectors.toList());
    }

    /**
     * Instantiates a non-following request specification for RestAssured calls
     * The non-following specification doesn't react the redirects (301 and alike),
     * it takes them as-is. In most cases this is the desired behavior because
     * following redirects in tests yields 404 oftentimes. Feed the spec to given()
     */
    public static class RestAssured {
        public static RequestSpecification nonFollowingRequestSpec() {
            return new RequestSpecBuilder().setConfig(config()
                    .redirect(redirectConfig().followRedirects(false))).build();
        }
    }

    public static void stubMockServerForRequest(int mockserverPort, String urlRegex, int httpStatus, String responseJson) {
        stubMockServerForRequest("GET", mockserverPort, urlRegex, httpStatus, responseJson);
    }

    public static void stubMockServerForRequest(
            String method, int mockserverPort, String urlRegex, int httpStatus, String responseJson
    ) {
        new MockServerClient("localhost", mockserverPort)
                .when(HttpRequest.request().withMethod(method).withPath(urlRegex)).respond(
                        HttpResponse.response().withStatusCode(httpStatus).withBody(responseJson)
        );
    }

    public static void verifyUrlAndHeader(int mockserverPort, String header, String headerContents) {
        new MockServerClient("localhost", mockserverPort).verify(
                HttpRequest.request().withHeaders(
                        new Header(header, headerContents)
                ),
                VerificationTimes.atLeast(1)
        );
    }

    public static void verifyRequestBody(int mockserverPort, String body) {
        new MockServerClient("localhost", mockserverPort).verify(
                HttpRequest.request().withBody(StringBody.exact(body))
        );
    }

    public static JsonElement readJSONFromFile(String fileName) throws FileNotFoundException {
        return new Gson().fromJson(new FileReader(new File(fileName)), JsonObject.class);
    }
}
