package org.broadinstitute.ddp.client;

import static spark.Spark.before;
import static spark.Spark.options;
import static spark.Spark.port;
import static spark.Spark.post;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Quick and dirty local server to put ReCaptcha through its paces. Can be removed when done with development.
 */
public class GoogleRecaptchaVerifyServer {
    public static void main(String[] args) {
        GoogleRecaptchaVerifyClient client = new GoogleRecaptchaVerifyClient(System.getProperty("secret"));
        port(8080);
        options("/*",
                (request, response) -> {

                    String accessControlRequestHeaders = request
                            .headers("Access-Control-Request-Headers");
                    if (accessControlRequestHeaders != null) {
                        response.header("Access-Control-Allow-Headers",
                                accessControlRequestHeaders);
                    }

                    String accessControlRequestMethod = request
                            .headers("Access-Control-Request-Method");
                    if (accessControlRequestMethod != null) {
                        response.header("Access-Control-Allow-Methods",
                                accessControlRequestMethod);
                    }

                    return "OK";
                });

        before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));
        post("/verify", (req, res) -> {
            String userCaptchaResponse = req.body();
            JsonObject captchaObject = new Gson().fromJson(userCaptchaResponse, JsonObject.class);
            GoogleRecaptchaVerifyResponse verifyResponse = client.verifyRecaptchaResponse(captchaObject.get("response").getAsString(),
                    req.ip());
            System.out.println(new Gson().toJson(verifyResponse));
            return "";
        });
    }
}
