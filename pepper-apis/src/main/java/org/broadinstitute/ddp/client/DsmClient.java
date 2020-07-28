package org.broadinstitute.ddp.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import org.apache.commons.collections4.ListUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.model.dsm.ParticipantKits;
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

    public static final String PATH_CANCERS = "/app/cancers";
    public static final String PATH_DRUGS = "/app/drugs";
    public static final String PATH_PARTICIPANT_STATUS = String.format(
            "/info/participantstatus/%s/%s", PathParam.STUDY_GUID, PathParam.USER_GUID);
    public static final String PATH_BATCH_KITS_STATUS = String.format(
            "/app/batchKitsStatus/%s", PathParam.STUDY_GUID);
    public static final int DEFAULT_TIMEOUT_SECS = 10;
    public static final int DEFAULT_PAGE_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(DsmClient.class);
    private static final Gson gson = new Gson();

    private final URI baseUrl;
    private final Algorithm algorithm;
    private final String jwtSigner;
    private final HttpClient client;

    public DsmClient(Config cfg) {
        this(cfg, HttpClient.newHttpClient());
    }

    public DsmClient(Config cfg, HttpClient client) {
        this(cfg.getString(ConfigFile.DSM_BASE_URL),
                cfg.getString(ConfigFile.DSM_JWT_SECRET),
                cfg.getString(ConfigFile.DSM_JWT_SIGNER),
                client);
    }

    public DsmClient(String baseUrl, String jwtSecret, String jwtSigner) {
        this(baseUrl, jwtSecret, jwtSigner, HttpClient.newHttpClient());
    }

    /**
     * Instantiate a new DSM client.
     *
     * @param baseUrl   the base url
     * @param jwtSecret the secret for creating auth token
     * @param jwtSigner the signer for creating auth token
     * @param client    the http client
     * @throws IllegalArgumentException if url or secret is invalid
     */
    public DsmClient(String baseUrl, String jwtSecret, String jwtSigner, HttpClient client) {
        try {
            this.baseUrl = new URL(baseUrl).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("Invalid DSM base url", e);
        }

        try {
            this.algorithm = Algorithm.HMAC256(jwtSecret);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Invalid DSM JWT secret", e);
        }

        this.jwtSigner = jwtSigner;
        this.client = client;
    }

    private String generateToken() {
        return Auth0Util.generateShortLivedJwtToken(algorithm, jwtSigner);
    }

    /**
     * Fetches list of cancer names in DSM.
     *
     * @return result with cancer names
     */
    public ApiResult<List<String>, Void> listCancers() {
        try {
            String auth = RouteUtil.makeAuthBearerHeader(generateToken());
            var request = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve(PATH_CANCERS))
                    .header(RouteConstants.Header.AUTHORIZATION, auth)
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                Type type = new TypeToken<List<String>>() {}.getType();
                List<String> names = gson.fromJson(response.body(), type);
                return ApiResult.ok(statusCode, names);
            } else {
                LOG.error("Trouble getting cancer list from {}", baseUrl);
                return ApiResult.err(statusCode, null);
            }
        } catch (JWTCreationException | IOException | InterruptedException | JsonSyntaxException e) {
            LOG.error("Trouble getting cancer list from {}", baseUrl, e);
            return ApiResult.thrown(e);
        }
    }

    /**
     * Fetches list of drug names in DSM.
     *
     * @return result with drug names
     */
    public ApiResult<List<String>, Void> listDrugs() {
        try {
            String auth = RouteUtil.makeAuthBearerHeader(generateToken());
            var request = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve(PATH_DRUGS))
                    .header(RouteConstants.Header.AUTHORIZATION, auth)
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                Type type = new TypeToken<List<String>>() {}.getType();
                List<String> names = gson.fromJson(response.body(), type);
                return ApiResult.ok(statusCode, names);
            } else {
                return ApiResult.err(statusCode, null);
            }
        } catch (JWTCreationException | IOException | InterruptedException | JsonSyntaxException e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Fetches kit statuses for a list of participants.
     *
     * @param studyGuid the study guid
     * @param userGuids list of participant guids
     * @return result with list of kit statuses
     */
    public ApiResult<List<ParticipantKits>, Void> listParticipantKits(String studyGuid, List<String> userGuids) {
        String path = PATH_BATCH_KITS_STATUS.replace(PathParam.STUDY_GUID, studyGuid);
        try {
            String auth = RouteUtil.makeAuthBearerHeader(generateToken());
            String payload = gson.toJson(new ListParticipantKitsPayload(userGuids));
            var request = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve(path))
                    .header(RouteConstants.Header.AUTHORIZATION, auth)
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                Type type = new TypeToken<List<ParticipantKits>>() {}.getType();
                List<ParticipantKits> statuses = gson.fromJson(response.body(), type);
                return ApiResult.ok(statusCode, statuses);
            } else {
                return ApiResult.err(statusCode, null);
            }
        } catch (JWTCreationException | IOException | InterruptedException | JsonSyntaxException e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Partition list of guids into batches and fetch each batch. Pagination may be stopped early by the callback.
     *
     * @param studyGuid the study guid
     * @param userGuids the list of participant guids
     * @param callback  handler for processing each batch results
     * @return total number processed
     */
    public int paginateParticipantKits(String studyGuid, List<String> userGuids,
                                       PageCallback<String, List<ParticipantKits>, Void> callback) {
        return paginateParticipantKits(DEFAULT_PAGE_SIZE, studyGuid, userGuids, callback);
    }

    /**
     * Partition list of guids into batches and fetch each batch. Pagination may be stopped early by the callback.
     *
     * @param pageSize  size of each batch
     * @param studyGuid the study guid
     * @param userGuids the list of participant guids
     * @param callback  handler for processing each batch results
     * @return total number processed
     */
    public int paginateParticipantKits(int pageSize, String studyGuid, List<String> userGuids,
                                       PageCallback<String, List<ParticipantKits>, Void> callback) {
        List<List<String>> partitions = ListUtils.partition(userGuids, pageSize);
        int numProcessed = 0;
        for (var batch : partitions) {
            var result = listParticipantKits(studyGuid, batch);
            boolean shouldContinue = callback.handlePage(batch, result);
            numProcessed += batch.size();
            if (!shouldContinue) {
                break;
            }
        }
        return numProcessed;
    }

    /**
     * Fetches a participant's tracking info for medical records and sample kits in given study. This does not check
     * whether user/study exists or if user is in study, which should be done by the caller.
     *
     * @param studyGuid the study guid
     * @param userGuid  the user guid or alt-pid
     * @param token     the user's JWT token
     * @return result with status info
     */
    public ApiResult<ParticipantStatus, Void> getParticipantStatus(String studyGuid, String userGuid, String token) {
        String path = PATH_PARTICIPANT_STATUS
                .replace(PathParam.STUDY_GUID, studyGuid)
                .replace(PathParam.USER_GUID, userGuid);
        String responseBody = null;
        try {
            String auth = RouteUtil.makeAuthBearerHeader(token);
            var request = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve(path))
                    .header(RouteConstants.Header.AUTHORIZATION, auth)
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                responseBody = response.body();
                ParticipantStatus status = gson.fromJson(responseBody, ParticipantStatus.class);
                return ApiResult.ok(statusCode, status);
            } else {
                return ApiResult.err(statusCode, null);
            }
        } catch (JWTCreationException | IOException | InterruptedException | JsonSyntaxException e) {
            LOG.error("Trouble looking up status for participant {} in study {}. Response was {}", userGuid, studyGuid, responseBody);
            return ApiResult.thrown(e);
        }
    }

    /**
     * A callback used during pagination.
     *
     * @param <I> the batch item type
     * @param <B> the result body type
     * @param <E> the result error type
     */
    @FunctionalInterface
    public interface PageCallback<I, B, E> {
        /**
         * Consume the page result and respond whether to continue pagination or not.
         *
         * @param batch the batch that resulted in the page
         * @param page  a page result
         * @return true to continue pagination, false to stop
         */
        boolean handlePage(List<I> batch, ApiResult<B, E> page);
    }

    public static class ListParticipantKitsPayload {
        private List<String> participantIds;

        public ListParticipantKitsPayload(List<String> participantIds) {
            this.participantIds = participantIds;
        }

        public List<String> getParticipantIds() {
            return participantIds;
        }
    }
}
