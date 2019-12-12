package org.broadinstitute.ddp.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import com.google.gson.Gson;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.apache.http.client.utils.URIBuilder;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.DsmCallResponse;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service calls a DSM endpoint and returns a participant status if the call succeeds
 */
public class DsmParticipantStatusService {

    private final URL baseUrl;

    private static final Logger LOG = LoggerFactory.getLogger(DsmParticipantStatusService.class);

    public DsmParticipantStatusService(URL baseUrl) {
        this.baseUrl = baseUrl;
    }


    /**
     * Given the study and user GUIDs, returns the participantStatus produced by DSM
     * @param studyGuid GUID of the study to fetch information for
     * @param userGuid GUID of the user to fetch information for
     * @param token DDP token which is honored by DSM
     * @return The DSM call response
     */
    public DsmCallResponse fetchParticipantStatus(String studyGuid, String userGuid, String token) {
        UserDto user = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUser.class).findByUserGuid(userGuid));
        if (user == null) {
            LOG.warn("User {} doesn't exist in Pepper", userGuid);
            return new DsmCallResponse(null, 404);
        }
        Optional<EnrollmentStatusType> userEnrollmentStatus = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).getEnrollmentStatusByUserAndStudyGuids(
                        userGuid, studyGuid
                )
        );
        // User must have a relation to the study, it should be at least REGISTERED
        if (!userEnrollmentStatus.isPresent()) {
            String errMsg = "The user " + userGuid + " must be enrolled into study " + studyGuid
                    + ", but the record for this user doesn't exist";
            LOG.error(errMsg);
            return new DsmCallResponse(null, 500);
        }

        URIBuilder uriBuilder = null;
        try {
            uriBuilder = new URIBuilder(baseUrl.toString());
        } catch (URISyntaxException e) {
            throw new DDPException("DSM baseUrl " + baseUrl.toString() + " is invalid", e);
        }

        HttpUrl url = new HttpUrl.Builder()
                .scheme(uriBuilder.getScheme())
                .host(uriBuilder.getHost())
                .port(uriBuilder.getPort())
                .addPathSegment(RouteConstants.API.DSM.PathSegments.BASE)
                .addPathSegment(RouteConstants.API.DSM.PathSegments.PARTICIPANT_STATUS)
                .addPathSegment(studyGuid)
                .addPathSegment(userGuid)
                .build();

        LOG.info("Connecting to DSM, url = " + url.toString());

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .build();
        Response response = null;

        try {
            response = new OkHttpClient().newCall(request).execute();
        } catch (IOException e) {
            LOG.error("A problem occurred while trying to call DSM: {}", e);
            return new DsmCallResponse(null, 500);
        }

        if (response.code() == 404) {
            LOG.warn("User {} doesn't exist in DSM", userGuid);
            return new DsmCallResponse(null, response.code());
        } else if (response.code() != 200) {
            LOG.warn("A problem occurred while calling DSM, it returned code {}", response.code());
            return new DsmCallResponse(null, response.code());
        }

        LOG.info("DSM call was successful, extracting the response JSON...");
        ParticipantStatus dsmParticipantStatus = null;
        String responseBody = null;
        try {
            responseBody = response.body().string();
            dsmParticipantStatus = new Gson().fromJson(responseBody, ParticipantStatus.class);
        } catch (IOException e) {
            LOG.error("A problem occurred while trying to deserialize the response JSON: {}", e);
            return new DsmCallResponse(null, 500);
        }
        LOG.info("Successfuly deserialized the response JSON {}", responseBody);
        return new DsmCallResponse(
                new ParticipantStatusTrackingInfo(
                        dsmParticipantStatus,
                        userEnrollmentStatus.get(),
                        userGuid
                ),
                200
        );
    }

}
