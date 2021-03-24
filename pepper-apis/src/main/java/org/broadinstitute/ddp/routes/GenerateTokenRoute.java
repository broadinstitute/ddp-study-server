package org.broadinstitute.ddp.routes;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.NonNull;
import org.broadinstitute.ddp.exception.TokenGenerationException;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.broadinstitute.ddp.user.Account;
import org.broadinstitute.ddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Arrays;
import java.util.Collection;

/**
 * Generates necessary application token using Google authentication.
 */
public class GenerateTokenRoute implements Route
{
    private static final Logger logger = LoggerFactory.getLogger(GenerateTokenRoute.class);

    private long tokenDurationInSeconds;
    private String requiredRole = null;
    private String tokenSecret = "";
    private String googleAuthKey = "";

    public GenerateTokenRoute(long tokenDurationInSeconds, @NonNull String tokenSecret, @NonNull String googleAuthKey)
    {
        this.tokenDurationInSeconds = tokenDurationInSeconds;
        this.tokenSecret = tokenSecret;
        this.googleAuthKey = googleAuthKey;
    }

    public GenerateTokenRoute(@NonNull String requiredRole, long tokenDurationInSeconds, @NonNull String tokenSecret, @NonNull String googleAuthKey)
    {
        this(tokenDurationInSeconds, tokenSecret, googleAuthKey);
        this.requiredRole = requiredRole;
    }

    @Override
    public Object handle(@NonNull Request request, @NonNull Response response)
    {
        response.status(401);
        String responseBody = SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString();

        NetHttpTransport transport = new NetHttpTransport();
        GsonFactory gsonFactory = new GsonFactory();

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, gsonFactory)
                .setAudience(Arrays.asList(googleAuthKey))
                .setIssuer("accounts.google.com")
                .build();
        try
        {
            String headerToken = Utility.getTokenFromHeader(request);

            if (headerToken.isEmpty())
            {
                throw new TokenGenerationException("Google token missing from header.");
            }
            else
            {
                //validate the Google token
                GoogleIdToken idToken = verifier.verify(headerToken);

                if (idToken == null)
                {
                    throw new TokenGenerationException("Google token invalid.");
                }
                else
                {
                    response.status(403);
                    responseBody = SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString();

                    GoogleIdToken.Payload payload = idToken.getPayload();

                    String email = payload.getEmail();

                    Collection<String> roles = Account.getRolesFromDB(email);

                    //at least one role is required for a user to get a token
                    if (roles.isEmpty())
                    {
                        throw new TokenGenerationException("User (" + email + ") does not have any roles in the system.");
                    }
                    else
                    {
                        if ((requiredRole != null) && (!roles.contains(requiredRole)))
                        {
                            throw new TokenGenerationException("User (" + email + ") does not have the required role.");
                        }

                        long tokenExpirationInSeconds = (System.currentTimeMillis() / 1000) + tokenDurationInSeconds;
                        String token = SecurityHelper.createTokenWithRoles(tokenSecret, tokenExpirationInSeconds, email, roles);

                        logger.info("TOKEN GENERATION REQUEST - success");
                        response.status(200);
                        return token;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            logger.error("TOKEN GENERATION REQUEST - " + responseBody.toLowerCase() + ": ", ex);
        }

        return responseBody;
    }
}

