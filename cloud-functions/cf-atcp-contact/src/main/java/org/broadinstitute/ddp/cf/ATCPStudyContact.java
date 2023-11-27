package org.broadinstitute.ddp.cf;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.cf.json.FileUploadUrl;
import org.broadinstitute.ddp.cf.json.StudyContactRequest;

public class ATCPStudyContact implements HttpFunction {

    private static final Logger logger = Logger.getLogger(ATCPStudyContact.class.getName());
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private static final char[] GUID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();
    private static final int GUID_LENGTH = 10;
    private static final int MAX_METADATA_PAIRS = 50;
    private static final int MAX_FILE_SIZE_BYTES = 5242880; // 5 MB
    private static final int MAX_SIGNED_URL_MINS = 5;

    private final String bucketName;
    private final String auth0Domain;
    private final String auth0ClientId;

    private final Storage storage;
    private final RecaptchaClient recaptcha;

    public ATCPStudyContact() {
        String captchaKey = getEnvOrThrow("CAPTCHA_KEY");
        String gcpProject = getEnvOrThrow("GCP_PROJECT");
        bucketName = getEnvOrThrow("BUCKET");
        auth0Domain = getEnvOrThrow("AUTH0_DOMAIN");
        auth0ClientId = getEnvOrThrow("AUTH0_CLIENT_ID");

        GoogleCredentials googleCredentials;
        try {
            googleCredentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot obtain GoogleCredentials", e);
        }

        storage = StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(gcpProject)
                .build()
                .getService();
        recaptcha = new RecaptchaClient(gson, captchaKey);
    }

    private String getEnvOrThrow(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) {
        logger.info("Received request to perform ATCP DAR study contact");

        response.appendHeader("Access-Control-Allow-Origin", "*");
        if ("OPTIONS".equals(request.getMethod())) {
            response.appendHeader("Access-Control-Allow-Methods", "POST");
            response.appendHeader("Access-Control-Allow-Headers", "Content-Type");
            response.appendHeader("Access-Control-Max-Age", "3600");
            response.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT); // 204
            return;
        }

        if (!"POST".equals(request.getMethod())) {
            logger.severe("Received request with unsupported http method: " + request.getMethod());
            response.setStatusCode(HttpURLConnection.HTTP_BAD_METHOD); // 405
            return;
        }

        StudyContactRequest payload;
        try {
            payload = gson.fromJson(request.getReader(), StudyContactRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing request payload", e);
        }
        if (payload == null) {
            logger.severe("No payload was provided with request");
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST); // 404
            return;
        }

        if (!auth0Domain.equals(payload.getDomain()) || !auth0ClientId.equals(payload.getClientId())) {
            logger.severe(String.format(
                    "Request has unauthorized auth0 identifier: domain=%s clientId=%s",
                    payload.getDomain(), payload.getClientId()));
            response.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED); // 401
            return;
        } else if (!isCaptchaPassed(payload.getCaptchaToken())) {
            logger.severe("Request did not pass recaptcha challenge");
            response.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED); // 401
            return;
        }

        Map<String, String> metadata = payload.getData();
        if (metadata.size() > MAX_METADATA_PAIRS) {
            String msg = "This form does not accept more than " + MAX_METADATA_PAIRS + " values";
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST, msg); // 400
            return;
        }

        long fileSize = payload.getAttachment().getSize();
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            String msg = "Files larger than 2MB are not allowed";
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST, msg); // 400
            return;
        }

        String guid = generateGuid();
        String filename = payload.getAttachment().getName();
        String metadataPath = String.format("%s.metadata", guid);
        String filePath = String.format("%s.pdf", guid);

        metadata.put("meta_filesize", String.valueOf(fileSize));
        metadata.put("meta_filename", filename);
        BlobInfo dataFile = BlobInfo.newBuilder(BlobId.of(bucketName, metadataPath))
                .setMetadata(metadata)
                .build();
        Blob metadataBlob = storage.create(dataFile);
        logger.info("Created metadata file: " + metadataBlob.getBlobId().toString());

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, filePath)).build();
        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", "application/pdf");

        URL url = storage.signUrl(blobInfo, MAX_SIGNED_URL_MINS, TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders));
        logger.info("Generated signed url for file: " + blobInfo.getBlobId().toString());

        FileUploadUrl resp = new FileUploadUrl(url.toString());
        try {
            gson.toJson(resp, FileUploadUrl.class, response.getWriter());
        } catch (IOException e) {
            throw new RuntimeException("Error writing response", e);
        }
        response.setStatusCode(HttpURLConnection.HTTP_OK); // 200
    }

    private boolean isCaptchaPassed(String token) {
        RecaptchaClient.Response response = recaptcha.verify(token);
        return response != null && response.isSuccess();
    }

    private String generateGuid() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, GUID_CHARS, GUID_LENGTH);
    }
}
