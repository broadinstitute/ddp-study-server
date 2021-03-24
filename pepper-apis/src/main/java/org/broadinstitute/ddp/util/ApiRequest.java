package org.broadinstitute.ddp.util;
import com.auth0.jwt.interfaces.Claim;
import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Response;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiRequest {

    private static final Logger logger = LoggerFactory.getLogger(ApiRequest.class);

    public static String performGet(String requestUrl, String requestName, Map<String, String> headers) {
        logger.info("Making request: " + requestName + "  using:  " + requestUrl);

        String content;

        try {
            org.apache.http.client.fluent.Request request = org.apache.http.client.fluent.Request.Get(requestUrl);

            if (headers != null) {
                for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                    request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
                }
            }

            HttpResponse httpResponse = request.execute().returnResponse();

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Status code returned =" + httpResponse.getStatusLine().getStatusCode());
            }

            content = Utility.getHttpResponseAsString(httpResponse);
        }
        catch (Exception ex) {
            throw new RuntimeException("An error occurred attempting the get request.", ex);
        }

        return content;
    }

    // @TODO: porting this over from DSM to get it working for drug list, then will look at flexibility
    // updates, and having DSM call it from here instead so the code only lives in one place
    public static Map<String,String> buildHeaders(String secret) {
        int tokenDurationInSeconds = 60 * 5; //5 minutes
        Map<String, String> claims = new HashMap<>();
        String jwtToken = new SecurityHelper().createToken(secret, tokenDurationInSeconds + (System.currentTimeMillis() / 1000), claims);
        Map<String,String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + jwtToken);
        return authHeaders;
    }
}
