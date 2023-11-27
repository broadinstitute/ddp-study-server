package org.broadinstitute.ddp.cf;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.cf.json.IncomingRequest;
import org.broadinstitute.ddp.cf.json.OutgoingRequest;

public class CircadiaSleeplogConnector implements HttpFunction {

    private static final Logger logger = Logger.getLogger(CircadiaSleeplogConnector.class.getName());
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private final String baseUrl;
    private final String auth0Domain;
    private final String auth0ClientId;
    private final String sleeplogCohort;
    private final String authHeader;
    private final java.net.http.HttpClient client;

    public CircadiaSleeplogConnector() {
        client = HttpClient.newHttpClient();
        baseUrl = getEnvOrThrow("BASE_URL");
        auth0Domain = getEnvOrThrow("AUTH0_DOMAIN");
        auth0ClientId = getEnvOrThrow("AUTH0_CLIENT_ID");
        sleeplogCohort = getEnvOrThrow("SLEEPLOG_COHORT");
        String username = getEnvOrThrow("SLEEPLOG_USERNAME");
        String password = getEnvOrThrow("SLEEPLOG_PASSWORD");
        authHeader = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    private String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) {
        logger.info("Received request to perform sleeplog interaction");

        response.appendHeader("Access-Control-Allow-Origin", "*");
        if ("OPTIONS".equals(request.getMethod())) {
            response.appendHeader("Access-Control-Allow-Methods", "POST");
            response.appendHeader("Access-Control-Allow-Headers", "Content-Type");
            response.appendHeader("Access-Control-Max-Age", "3600");
            response.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT); // 204
            return;
        }

        if (!"POST".equals(request.getMethod())) {
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
            return;
        }

        IncomingRequest incReq;
        try {
            incReq = gson.fromJson(request.getReader(), IncomingRequest.class);
            logger.info(auth0Domain + " " + auth0ClientId + " " + gson.toJson(incReq));
        } catch (Exception e) {
            throw new RuntimeException("Error parsing request payload", e);
        }

        if (incReq == null) {
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
            return;
        }

        if (!auth0Domain.equals(incReq.getAuth0Domain()) || !auth0ClientId.equals(incReq.getAuth0ClientId())) {
            logger.severe(String.format(
                    "Request has unauthorized auth0 identifier: domain=%s clientId=%s",
                    incReq.getAuth0Domain(), incReq.getAuth0ClientId()));
            response.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED); // 401
            return;
        }

        java.net.http.HttpResponse<String> incomingResponse;
        try {
            OutgoingRequest outReq = new OutgoingRequest(incReq.getEmail(), incReq.getIsActive(), sleeplogCohort,
                    incReq.getUrl().contains("api/user/") && "POST".equals(incReq.getMethod()));
            String outReqString = gson.toJson(outReq);
            boolean usePathParams = "GET".equals(incReq.getMethod());
            var outgoingRequestBuilder= java.net.http.HttpRequest.newBuilder(new URI(baseUrl + incReq.getUrl()
                    + (usePathParams ? outReq.toString() : "")))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader);
            if (!usePathParams) {
                outgoingRequestBuilder = outgoingRequestBuilder.method(incReq.getMethod(),
                        java.net.http.HttpRequest.BodyPublishers.ofString(outReqString, StandardCharsets.UTF_8));
            } else {
                outgoingRequestBuilder = outgoingRequestBuilder.GET();
            }
            var outgoingRequest = outgoingRequestBuilder
                    .build();
            incomingResponse = client.send(outgoingRequest, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error building URL", e);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error sending request", e);
        }
        try {
            response.getWriter().write(incomingResponse.body());
            response.appendHeader("Content-Type", "application/json");
        } catch (IOException e) {
            throw new RuntimeException("Error writing response", e);
        }
        response.setStatusCode(incomingResponse.statusCode());
    }
}
