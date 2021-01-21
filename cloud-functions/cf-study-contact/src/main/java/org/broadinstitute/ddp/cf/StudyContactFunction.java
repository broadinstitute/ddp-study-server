package org.broadinstitute.ddp.cf;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.storage.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cf.json.FileUploadUrl;
import org.broadinstitute.ddp.cf.json.StudyContactRequest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class StudyContactFunction implements HttpFunction {
    private static final Logger logger = Logger.getLogger(StudyContactFunction.class.getName());
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private static final String TYPESAFE_CONFIG_SYSTEM_VAR = "config.file";
    private static final char[] ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();

    private final String captchaKey;
    private final String bucketName;
    private final String bucketFolder;
    private final String auth0Domain;
    private final String auth0ClientId;

    private final Storage storage;

    public StudyContactFunction() {
        String configFileName = System.getenv(TYPESAFE_CONFIG_SYSTEM_VAR.replace('.', '_'));
        if (configFileName == null) {
            configFileName = System.getProperty(TYPESAFE_CONFIG_SYSTEM_VAR);
        }
        Config cfg = ConfigFactory.parseFile(new File(configFileName));
        captchaKey = cfg.getString("captchaKey");
        String gcpProject = cfg.getString("gcpProject");
        bucketName = cfg.getString("bucketName");
        bucketFolder = cfg.getString("bucketFolder");
        auth0Domain = cfg.getString("auth0Domain");
        auth0ClientId = cfg.getString("auth0ClientId");

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
    }

    @Override
    public void service(com.google.cloud.functions.HttpRequest request, com.google.cloud.functions.HttpResponse response)
            throws IOException, URISyntaxException, InterruptedException {
        try {
            response.appendHeader("Access-Control-Allow-Origin", "*");

            if ("OPTIONS".equals(request.getMethod())) {
                response.appendHeader("Access-Control-Allow-Methods", "POST");
                response.appendHeader("Access-Control-Allow-Headers", "Content-Type");
                response.appendHeader("Access-Control-Max-Age", "3600");
                response.setStatusCode(204);
                return;
            }

            StudyContactRequest req = gson.fromJson(request.getReader(), StudyContactRequest.class);
            if (!isCaptchaPassed(req.getCaptchaToken())
                    || !auth0ClientId.equals(req.getClientId())
                    || !auth0Domain.equals(req.getDomain())) {
                response.setStatusCode(401, "Not authorized");
            } else {
                String guid = generateGuid();
                String filename = req.getAttachment().getName();
                String metadataPath = String.format("%s/%s.metadata", bucketFolder, guid);
                String filePath = String.format("%s/%s.pdf", bucketFolder, guid);

                Map<String, String> metadata = req.getData();
                if (metadata.size() > 50) {
                    response.setStatusCode(400, "This form does not accept more than 50 values");
                }
                long fileSize = req.getAttachment().getSize();
                if (fileSize > 2097152) {
                    response.setStatusCode(400, "Files larger than 2MB are not allowed");
                }
                metadata.put("meta_filesize", String.valueOf(fileSize));
                metadata.put("meta_filename", filename);
                BlobInfo dataFile = BlobInfo.newBuilder(BlobId.of(bucketName, metadataPath))
                        .setMetadata(metadata)
                        .build();
                storage.create(dataFile);

                BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, filePath)).build();
                Map<String, String> extensionHeaders = new HashMap<>();
                extensionHeaders.put("Content-Type", "application/pdf");

                URL url = storage.signUrl(blobInfo, 5, TimeUnit.MINUTES,
                        Storage.SignUrlOption.withV4Signature(),
                        Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                        Storage.SignUrlOption.withExtHeaders(extensionHeaders));

                FileUploadUrl resp = new FileUploadUrl(url.toString());
                gson.toJson(resp, FileUploadUrl.class, response.getWriter());
                response.setStatusCode(200);
            }
        } catch (JsonParseException e) {
            logger.severe("Error parsing JSON: " + e.getMessage());
        }
    }

    private boolean isCaptchaPassed(String token) throws URISyntaxException, InterruptedException, IOException {
        logger.info(token);
        String url = "https://www.google.com/recaptcha/api/siteverify";
        String params = "secret=" + captchaKey + "&response=" + token;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(new URI(url))
                .headers("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(params, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonObject response = gson.fromJson(res.body(), JsonObject.class);
        logger.info(response.toString());
        return response.has("success") && response.get("success").getAsBoolean();
    }

    private String generateGuid() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ALLOWED_CHARS, 10);
    }
}