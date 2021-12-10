package org.broadinstitute.lddp.datstat;

import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.UrlEncodedParser;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.broadinstitute.lddp.exception.DatStatTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.NonNull;

/**
 * This class is used so that we don't have to keep getting new DatStat tokens all the time. Supposedly tokens last for 20 days
 * so we will always try to use old ones before we grab a new one.
 */
public class AuthSingleton
{
    private static final Logger logger = LoggerFactory.getLogger(AuthSingleton.class);

    private static final String LOG_PREFIX = "AUTH SINGLETON - ";

    private static final String GET_TOKEN_PATH = "/oauth/access_token";

    private static AuthSingleton instance = null;

    private String token = null;

    private OAuthHmacSigner signer;

    private AuthSingleton()
    {
    }

    public synchronized String getToken()
    {
        return this.token;
    }

    public synchronized OAuthHmacSigner getSigner()
    {
        return this.signer;
    }

    /**
     * Used to create the one and only instance, reset its values, or just retrieve the current instance.
     */
    public synchronized static AuthSingleton getInstance(@NonNull Boolean resetValues, @NonNull String datStatKey, @NonNull String datStatSecret,
                                                         @NonNull String datStatUsername, @NonNull String datStatPassword, @NonNull String datStatUrl)
    {
        //this will happen when the app first starts
        if (instance == null)
        {
            resetValues = true;
            instance = new AuthSingleton();
            logger.info(LOG_PREFIX + "Created new instance: " + System.identityHashCode(instance));
        }
        else
        {
            logger.info(LOG_PREFIX + "Using existing instance: " + System.identityHashCode(instance));
        }

        if (resetValues) setValues(datStatKey, datStatSecret, datStatUsername, datStatPassword, datStatUrl);

        return instance;
    }

    /**
     * Used to reset the signer and token for the instance.
     */
    private static void setValues(@NonNull String datStatKey, @NonNull String datStatSecret,
                                  @NonNull String datStatUsername, @NonNull String datStatPassword, @NonNull String datStatUrl)
    {
        try
        {
            instance.signer = new OAuthHmacSigner();
            instance.signer.clientSharedSecret = datStatSecret;

            AccessTokenUtil tokenUtil = new AccessTokenUtil(DatStatUtil.generateCompleteApiUrl(datStatUrl, GET_TOKEN_PATH));
            tokenUtil.signer = instance.signer;
            tokenUtil.transport = new NetHttpTransport();
            tokenUtil.consumerKey = datStatKey;
            HttpResponse response = tokenUtil.executeXAuth(datStatUsername, datStatPassword);

            DatStatUtil.preprocessDatStatResponse(response);

            OAuthCredentialsResponse oauthResponse = new OAuthCredentialsResponse();
            UrlEncodedParser.parse(response.parseAsString(), oauthResponse);

            instance.token = oauthResponse.token;
            instance.signer.tokenSharedSecret = oauthResponse.tokenSecret;

            logger.info(LOG_PREFIX + "Set new values for instance: " + System.identityHashCode(instance));
        }
        catch (Exception ex)
        {
            throw new DatStatTokenException("Token generation error.", ex);
        }
    }
}

