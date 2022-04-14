package org.broadinstitute.ddp.cf;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.Gson;

/**
 * A client to interface with Google reCAPTCHA service. The underlying HttpClient is immutable so
 * this client is thread-safe and can be cached for re-use.
 */
public class RecaptchaClient {

    private static final Logger logger = Logger.getLogger(RecaptchaClient.class.getName());
    private static final String API_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final HttpClient client;
    private final String captchaKey;
    private final Gson gson;
    private final URI url;

    public RecaptchaClient(Gson gson, String captchaKey) {
        this.gson = gson;
        this.captchaKey = captchaKey;
        this.client = HttpClient.newHttpClient();
        try {
            this.url = new URI(API_URL);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to build recaptcha service URL");
        }
    }

    public Response verify(String token) {
        logger.info("Verifying provided recaptcha: " + token);

        String params = "secret=" + captchaKey + "&response=" + token;
        var request = HttpRequest.newBuilder(url)
                .headers("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(params, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error while verifying recaptcha", e);
        }

        String body = response.body();
        logger.info("Received recaptcha response: " + body);

        try {
            return gson.fromJson(body, Response.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing recaptcha response", e);
        }
    }

    public static final class Response {
        // There are other properties but we only care about this one at the moment.
        private boolean success;

        public boolean isSuccess() {
            return success;
        }
    }
}
