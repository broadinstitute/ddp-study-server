package org.broadinstitute.ddp.datstat;

import com.google.api.client.http.*;
import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import lombok.NonNull;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

/**
 * Created to add x_auth parameters to Google library code.
 */
public class AccessTokenUtil extends OAuthGetAccessToken
{
    public AccessTokenUtil(String authorizationServerUrl) {
        super(authorizationServerUrl);
    }

    public final HttpResponse executeXAuth(@NonNull String username, @NonNull String password) throws IOException {
        //add form fields for x_auth values
        Map<String, String> parameters = new HashMap<>();
        parameters.put("x_auth_mode", "client_auth");
        parameters.put("x_auth_password", password);
        parameters.put("x_auth_username", username);
        UrlEncodedContent content = new UrlEncodedContent(parameters);
        HttpRequestFactory requestFactory = this.transport.createRequestFactory();
        HttpRequest request = requestFactory.buildPostRequest(this, content);

        AuthParams params = new AuthParams();
        params.consumerKey = this.consumerKey;
        params.signer = this.signer;
        params.version = "1.0";
        params.xAuthMode = "client_auth";
        params.xAuthPassword = password;
        params.xAuthUsername = username;

        params.intercept(request);
        HttpResponse response = request.execute();

        response.setContentLoggingLimit(0);

        return response;
    }
}
