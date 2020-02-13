package org.broadinstitute.ddp.client;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for DSM services, effectively just a wrapper around the HTTP calls. API paths use SparkJava's syntax for
 * path parameters, the same syntax used in {@link RouteConstants.API}.
 */
public class DsmClient {

    public static final String API_CANCERS = "/app/cancers";
    public static final String API_DRUGS = "/app/drugs";
    public static final String API_PARTICIPANT_STATUS = String.format(
            "/info/participantstatus/%s/%s", PathParam.STUDY_GUID, PathParam.USER_GUID);
    public static final int DEFAULT_TIMEOUT_SECS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(DsmClient.class);
    private static final Gson gson = new Gson();

    private final String baseUrl;
    private final String jwtSecret;
    private final String jwtSigner;
    private final HttpClient client;

    public DsmClient(Config cfg) {
        this(cfg.getString(ConfigFile.DSM_BASE_URL),
                cfg.getString(ConfigFile.DSM_JWT_SECRET),
                cfg.getString(ConfigFile.DSM_JWT_SIGNER));
    }

    public DsmClient(String baseUrl, String jwtSecret, String jwtSigner) {
        this.baseUrl = baseUrl;
        this.jwtSecret = jwtSecret;
        this.jwtSigner = jwtSigner;
        this.client = HttpClient.newHttpClient();
    }

    private String generateToken() {
        try {
            return Auth0Util.generateShortLivedJwtToken(jwtSecret, jwtSigner);
        } catch (Exception e) {
            throw new DDPException("Failed to generate DSM JWT token", e);
        }
    }

    /**
     * Fetches list of cancer names in DSM.
     *
     * @return cancer names
     */
    public ClientResponse<List<String>> listCancers() {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + API_CANCERS))
                .header(RouteConstants.AUTHORIZATION, RouteUtil.makeAuthBearerHeader(generateToken()))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Type type = new TypeToken<List<String>>() {}.getType();
                List<String> names = gson.fromJson(response.body(), type);
                return new ClientResponse<>(response.statusCode(), names);
            } else {
                return new ClientResponse<>(response.statusCode(), null);
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch DSM cancers", e);
            return new ClientResponse<>(500, null);
        }
    }

    /**
     * Fetches list of drug names in DSM.
     *
     * @return drug names
     */
    public ClientResponse<List<String>> listDrugs() {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + API_DRUGS))
                .header(RouteConstants.AUTHORIZATION, RouteUtil.makeAuthBearerHeader(generateToken()))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Type type = new TypeToken<List<String>>() {}.getType();
                List<String> names = new Gson().fromJson(response.body(), type);
                return new ClientResponse<>(response.statusCode(), names);
            } else {
                return new ClientResponse<>(response.statusCode(), null);
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch DSM drugs", e);
            return new ClientResponse<>(500, null);
        }
    }

    /**
     * Fetches a participant's tracking info for medical records and sample kits in given study. This does not check
     * whether user/study exists or if user is in study, which should be done by the caller.
     *
     * @param studyGuid the study guid
     * @param userGuid  the user guid or alt-pid
     * @param token     the user's JWT token
     * @return status info
     */
    public ClientResponse<ParticipantStatus> getParticipantStatus(String studyGuid, String userGuid, String token) {
        String path = API_PARTICIPANT_STATUS
                .replace(PathParam.STUDY_GUID, studyGuid)
                .replace(PathParam.USER_GUID, userGuid);
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(RouteConstants.AUTHORIZATION, RouteUtil.makeAuthBearerHeader(token))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ParticipantStatus result = new Gson().fromJson(response.body(), ParticipantStatus.class);
                return new ClientResponse<>(response.statusCode(), result);
            } else {
                return new ClientResponse<>(response.statusCode(), null);
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch participant status from DSM", e);
            return new ClientResponse<>(500, null);
        }
    }
}
