package org.broadinstitute.dsm.util;

import static org.apache.http.client.fluent.Request.Get;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.SurveyNotCreated;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.ddp.PreferredLanguage;
import org.broadinstitute.dsm.model.pdf.MiscPDFDownload;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.ParticipantInstitutionInfo;
import org.broadinstitute.lddp.handlers.util.ParticipantSurveyInfo;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.handlers.util.SimpleFollowUpSurvey;
import org.broadinstitute.lddp.handlers.util.SurveyInfo;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.util.GoogleBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import org.apache.http.client.fluent.Response;

public class DDPRequestUtil {

    private static final Logger logger = LoggerFactory.getLogger(DDPRequestUtil.class);

    public static String getContentAsString(HttpResponse response) throws IOException {
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        return org.apache.commons.io.IOUtils.toString(rd);
    }

    // make a get request
    public static <T> T getResponseObject(Class<T> responseClass, String sendRequest, String name, boolean auth0Token) throws IOException {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request request = SecurityUtil.createGetRequestWithHeader(sendRequest, name, auth0Token);
        T objects = request.execute().handleResponse(res -> getResponse(res, responseClass, sendRequest));
        if (objects != null) {
            logger.info("Got response back");
        }
        return objects;
    }

    // make a get request
    public static <T> T getResponseObjectWithoutHeader(Class<T> responseClass, String sendRequest, String name) throws IOException {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request request = SecurityUtil.createGetRequestNoToken(sendRequest);
        T objects = request.execute().handleResponse(res -> getResponse(res, responseClass, sendRequest));
        if (objects != null) {
            logger.info("Got response back");
        }
        return objects;
    }

    // make a get request with custom header
    public static <T> T getResponseObjectWithCustomHeader(Class<T> responseClass, String sendRequest, String name,
                                                          Map<String, String> header) throws IOException {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request request = Get(sendRequest);

        if (header != null) {
            for (Map.Entry<String, String> headerEntry : header.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        T objects = request.execute().handleResponse(res -> getResponse(res, responseClass, sendRequest));
        if (objects != null) {
            logger.info("Got response back");
        }
        return objects;
    }

    // make a post request
    public static Integer postRequest(String sendRequest, Object objectToPost, String name, boolean auth0Token)
            throws IOException, RuntimeException {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request request =
                SecurityUtil.createPostRequestWithHeader(sendRequest, name, auth0Token, objectToPost);

        int responseCode = request.execute().handleResponse(res -> getResponseCode(res, sendRequest));
        return responseCode;
    }

    // make a post request
    public static Integer postRequest(String sendRequest, Object objectToPost, String name, boolean auth0Token, Auth0Util auth0Util)
            throws IOException, RuntimeException {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request request =
                SecurityUtil.createPostRequestWithHeader(sendRequest, name, auth0Token, objectToPost, auth0Util);

        int responseCode = request.execute().handleResponse(res -> getResponseCode(res, sendRequest));
        return responseCode;
    }

    private static Integer getResponseCode(HttpResponse res, String sendRequest) {
        int responseCodeInt = res.getStatusLine().getStatusCode();
        if (responseCodeInt != HttpStatusCodes.STATUS_CODE_OK) {
            logger.error("Got " + responseCodeInt + " from " + sendRequest);
            if (responseCodeInt == HttpStatusCodes.STATUS_CODE_SERVER_ERROR) {
                throw new RuntimeException("Got " + responseCodeInt + " from " + sendRequest);
            }
        }
        return responseCodeInt;
    }

    private static <T> T getResponse(HttpResponse res, Class<T> responseClass, String sendRequest) {
        int responseCodeInt = getResponseCode(res, sendRequest);
        if (responseCodeInt == HttpStatusCodes.STATUS_CODE_OK) {
            try {
                logger.info("Got response back");
                String message = EntityUtils.toString(res.getEntity(), "UTF-8");
                return new Gson().fromJson(message, responseClass);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read HttpResponse ", e);
            }
        }
        return null;
    }

    public static byte[] getPDFByteArray(@NonNull String sendRequest, @NonNull String name, boolean auth0Token) throws IOException {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);

        org.apache.http.client.fluent.Request request = SecurityUtil.createGetRequestWithHeader(sendRequest, name, auth0Token);

        byte[] bytes = request.execute().handleResponse(res -> getResponseByteArray(res, sendRequest));
        return bytes;
    }

    private static byte[] getResponseByteArray(HttpResponse res, String sendRequest) {
        int responseCodeInt = getResponseCode(res, sendRequest);
        if (responseCodeInt == HttpStatusCodes.STATUS_CODE_OK) {
            try {
                InputStream is = res.getEntity().getContent();
                return IOUtils.toByteArray(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read HttpResponse ", e);
            }
        }
        return null;
    }

    public static Map<String, DDPParticipant> getDDPParticipant(@NonNull DDPInstance instance) {
        Map<String, DDPParticipant> mapDDPParticipantInstitution = new HashMap<>();
        String sendRequest = instance.getBaseUrl() + RoutePath.DDP_PARTICIPANTINSTITUTIONS_PATH;
        try {
            ParticipantInstitutionInfo[] participantInfo =
                    DDPRequestUtil.getResponseObject(ParticipantInstitutionInfo[].class, sendRequest, instance.getName(),
                            instance.isHasAuth0Token());
            if (participantInfo != null) {
                logger.info("Got " + participantInfo.length + " ParticipantInstitutionInfo ");
                for (ParticipantInstitutionInfo info : participantInfo) {
                    String key = info.getParticipantId();
                    mapDDPParticipantInstitution.put(key,
                            new DDPParticipant(info.getShortId(), info.getLegacyShortId(), info.getFirstName(), info.getLastName(),
                                    info.getAddress()));
                }
            }
        } catch (Exception ioe) {
            throw new RuntimeException("Couldn't get participants from " + sendRequest, ioe);
        }
        return mapDDPParticipantInstitution;
    }

    public static Collection<SurveyInfo> getFollowupSurveys(@NonNull DDPInstance instance) {
        SurveyInfo[] surveyInfos = null;
        String sendRequest = instance.getBaseUrl() + RoutePath.DDP_FOLLOW_UP_SURVEYS_PATH;
        try {
            surveyInfos = DDPRequestUtil.getResponseObject(SurveyInfo[].class, sendRequest, instance.getName(), instance.isHasAuth0Token());
            if (surveyInfos != null) {
                logger.info("Got " + surveyInfos.length + " SurveyInfo ");
            }
        } catch (Exception ioe) {
            throw new RuntimeException("Couldn't get followUp survey list from " + sendRequest, ioe);
        }
        return Arrays.asList(surveyInfos);
    }

    public static Result triggerFollowupSurvey(@NonNull DDPInstance instance, @NonNull SimpleFollowUpSurvey survey,
                                               @NonNull String surveyName) {
        String sendRequest = instance.getBaseUrl() + RoutePath.DDP_FOLLOW_UP_SURVEY_PATH + "/" + surveyName;
        Integer ddpResponse = null;
        try {
            ddpResponse = DDPRequestUtil.postRequest(sendRequest, survey, instance.getName(), instance.isHasAuth0Token());
        } catch (Exception e) {
            logger.error("Couldn't trigger survey for participant " + sendRequest, e);
            throw new SurveyNotCreated("Couldn't trigger survey for participant " + sendRequest);
        }
        if (ddpResponse == HttpStatusCodes.STATUS_CODE_OK) {
            logger.info(
                    "Triggered DDP to create " + surveyName + " survey for participant w/ ddpParticipantId " + survey.getParticipantId());
            return new Result(200);
        }
        return new Result(500, UserErrorMessages.SURVEY_NOT_CREATED);
    }

    public static List<ParticipantSurveyInfo> getFollowupSurveysStatus(@NonNull DDPInstance instance, @NonNull String surveyName) {
        List<ParticipantSurveyInfo> list = new ArrayList<>();
        ParticipantSurveyInfo[] participantSurveyInfos = null;
        String sendRequest = instance.getBaseUrl() + RoutePath.DDP_FOLLOW_UP_SURVEY_PATH + "/" + surveyName;
        try {
            participantSurveyInfos = DDPRequestUtil.getResponseObject(ParticipantSurveyInfo[].class, sendRequest, instance.getName(),
                    instance.isHasAuth0Token());
            if (participantSurveyInfos != null) {
                logger.info("Got " + participantSurveyInfos.length + " ParticipantSurveyInfo ");
                for (ParticipantSurveyInfo info : participantSurveyInfos) {
                    list.add(info);
                }
            }
        } catch (Exception ioe) {
            throw new RuntimeException("Couldn't get followUp survey status list from " + sendRequest, ioe);
        }
        return list;
    }

    public static void savePDFsInBucket(@NonNull String baseURL, @NonNull String instanceName, @NonNull String ddpParticipantId,
                                        @NonNull boolean hasAuth0Token, @NonNull String pdfEndpoint, @NonNull long time,
                                        @NonNull String userId, @NonNull String reason) {
        String fileName = pdfEndpoint.replace("/", "").replace("pdf", "");
        String gcpName = DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
        if (StringUtils.isNotBlank(gcpName)) {
            String bucketName = gcpName + "_dsm_" + instanceName.toLowerCase();
            try {
                if (GoogleBucket.bucketExists(null, gcpName, bucketName)) {
                    String pdfRequest = baseURL + RoutePath.DDP_PARTICIPANTS_PATH + "/" + ddpParticipantId + pdfEndpoint;
                    byte[] bytes = DDPRequestUtil.getPDFByteArray(pdfRequest, instanceName, hasAuth0Token);

                    GoogleBucket.uploadFile(null, gcpName, bucketName,
                            ddpParticipantId + "/readonly/" + ddpParticipantId + "_" + fileName + "_" + userId + "_" + reason + "_" + time
                                    + ".pdf", new ByteArrayInputStream(bytes));
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        "Couldn't save " + fileName + " pdf in google bucket " + bucketName + " for ddpParticipant " + ddpParticipantId, e);
            }
        }
    }

    public static void makeStandardPDF(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId, @NonNull String userId,
                                       @NonNull String reason) {
        DDPInstance instanceRole = DDPInstance.getDDPInstanceWithRole(ddpInstance.getName(), DBConstants.PDF_DOWNLOAD_RELEASE);
        makeStandardPDF(ddpInstance.isHasRole(), instanceRole.isHasRole(), ddpInstance.getBaseUrl(), ddpInstance.getName(),
                ddpParticipantId, ddpInstance.isHasAuth0Token(), userId, reason);
    }

    public static void makeStandardPDF(@NonNull boolean hasConsentEndpoints, @NonNull boolean hasReleaseEndpoints, @NonNull String baseUrl,
                                       @NonNull String instanceName, @NonNull String ddpParticipantId, @NonNull boolean hasAuth0Token,
                                       @NonNull String userId, @NonNull String reason) {
        // save consent in bucket, if ddpInstance has endpoint
        long time = System.currentTimeMillis();
        if (hasConsentEndpoints) {
            try {
                DDPRequestUtil.savePDFsInBucket(baseUrl, instanceName, ddpParticipantId, hasAuth0Token, "/consentpdf", time, userId,
                        reason);
            } catch (RuntimeException e) {
                logger.error("Couldn't download consent pdf ", e);
            }
        }
        // save release in bucket, if ddpInstance has endpoint
        if (hasReleaseEndpoints) {
            try {
                DDPRequestUtil.savePDFsInBucket(baseUrl, instanceName, ddpParticipantId, hasAuth0Token, "/releasepdf", time, userId,
                        reason);
            } catch (RuntimeException e) {
                logger.error("Couldn't download consent pdf ", e);
            }
        }
    }

    public static void makeNonStandardPDF(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId, @NonNull String userId,
                                          @NonNull String reason) {
        Object pdfs = new MiscPDFDownload().returnPDFS(ddpParticipantId, ddpInstance.getName());
        List<Map<String, String>> pdfList = (List<Map<String, String>>) pdfs;
        long time = System.currentTimeMillis();
        for (Map<String, String> pdf : pdfList) {
            DDPRequestUtil.savePDFsInBucket(ddpInstance.getBaseUrl(), ddpInstance.getName(), ddpParticipantId,
                    ddpInstance.isHasAuth0Token(), "/pdfs/" + pdf.get("configName"), time, userId, reason);
        }
    }

    public static List<PreferredLanguage> getPreferredLanguages(@NonNull DDPInstance ddpInstance) {
        PreferredLanguage[] list = null;
        String sendRequest = ddpInstance.getBaseUrl().replace("/dsm", "") + "/languages";
        try {
            list = DDPRequestUtil.getResponseObjectWithoutHeader(PreferredLanguage[].class, sendRequest, ddpInstance.getName());
        } catch (Exception ioe) {
            logger.error("Couldn't get preferred languages from " + sendRequest, ioe);
        }
        if (list != null) {
            return Arrays.asList(list.clone());
        }
        return null;
    }

    public static <T> T postRequestWithResponse(Class<T> responseClass, String sendRequest, Object objectToPost, String name, Map<String, String> header) {
        logger.info("Requesting data from " + name + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request request = SecurityUtil.createPostRequestWithHeaderNoToken(sendRequest, header, objectToPost);

        T objects = null;
        try {
            Response response = request.execute();
            objects = response.handleResponse(res -> getResponse(res, responseClass, sendRequest));
        }
        catch (IOException e) {
            throw new RuntimeException("Post request to "+sendRequest+" was not completed successfully", e);
        }
        return objects;
    }
}
