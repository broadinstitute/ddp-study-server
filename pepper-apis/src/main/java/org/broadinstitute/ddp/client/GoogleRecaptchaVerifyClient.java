package org.broadinstitute.ddp.client;

import java.io.IOException;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client that will verify user reCaptcha responses against Google's reCaptcha verify service
 * Supports reCaptcha V2 and V3. Which version is being used is based on configuration associated with
 * site secret key provided.
 * More info @see <a href="https://developers.google.com/recaptcha">Google reCaptcha</a>
 */
public class GoogleRecaptchaVerifyClient {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleRecaptchaVerifyClient.class);
    private static final String VERIFY_SERVICE_URL = "https://www.google.com/recaptcha/api/siteverify";
    private final String secret;

    public GoogleRecaptchaVerifyClient(String secret) {
        this.secret = secret;
    }


    public GoogleRecaptchaVerifyResponse verifyRecaptchaResponse(String userCaptchaResponse, String clientIpAddress) {
        String responseJsonString = callVerifyService(userCaptchaResponse, clientIpAddress);

        GoogleRecaptchaVerifyResponse verifyResponse = new Gson().fromJson(responseJsonString, GoogleRecaptchaVerifyResponse.class);
        if (verifyResponse == null) {
            LOG.error("Response from Google Recaptcha verify could not be converted from JSON body: {}", responseJsonString);
            throw new DDPException("Error processing Google ReCaptcha verify response");
        }
        if (!verifyResponse.isSuccess()) {
            LOG.warn("Google Recaptcha verify failed. user response: {}, remoteip: {}, verification response: {}",
                    userCaptchaResponse, clientIpAddress, responseJsonString);
        }
        return verifyResponse;

    }

    public GoogleRecaptchaVerifyResponse verifyRecaptchaResponse(String userCaptchaResponse) {
        return verifyRecaptchaResponse(userCaptchaResponse, null);
    }

    private String callVerifyService(String userCaptchaResponse, String clientIpAddress) {
        var requestBody = Form.form()
                .add("secret", this.secret)
                .add("response", userCaptchaResponse);
        if (clientIpAddress != null) {
            requestBody.add("remoteip", clientIpAddress);
        }
        try {
            Response execResult = Request.Post(VERIFY_SERVICE_URL).bodyForm(requestBody.build()).execute();
            HttpResponse httpResponse = execResult.returnResponse();
            String responseJson = EntityUtils.toString(httpResponse.getEntity());
            int responseStatusCode = httpResponse.getStatusLine().getStatusCode();
            LOG.debug("Received response from Google Recaptcha verify with http status code: {} and body: {}",
                    responseStatusCode, responseJson);
            return responseJson;
        } catch (IOException e) {
            throw new DDPException("Problem calling recaptcha verify", e);
        }
    }

}
