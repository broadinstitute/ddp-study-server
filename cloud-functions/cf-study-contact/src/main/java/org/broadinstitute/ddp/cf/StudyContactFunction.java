package org.broadinstitute.ddp.cf;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.cf.json.FileUploadUrl;
import org.broadinstitute.ddp.cf.json.StudyContactRequest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class StudyContactFunction implements HttpFunction {
    private static final Logger logger = Logger.getLogger(StudyContactFunction.class.getName());
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private static final String CAPTCHA_KEY = "CAPTCHA_KEY";
    private static final String GCP_PROJECT = "broad-ddp-dev";
    private static final String BUCKET_NAME = "dar-form";

    private static final String BUCKET_FOLDER = "files";
    private static final char[] allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();

    private final Storage storage;

    public StudyContactFunction() {
        GoogleCredentials googleCredentials;
        try {
            googleCredentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot obtain GoogleCredentials", e);
        }

        storage = StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(GCP_PROJECT)
                .build()
                .getService();
    }

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {
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
            if (!isCaptchaPassed(req.getCaptchaToken())) {
                response.setStatusCode(401, "Captcha verification failed");
            } else {
                String guid = generateGuid();
                String filename = req.getAttachment().getName();
                String metadataPath = String.format("%s/%s.metadata", BUCKET_FOLDER, guid);
                String filePath = String.format("%s/%s.pdf", BUCKET_FOLDER, guid);

                Map<String, String> metadata = req.getData();
                metadata.put("meta_filesize", String.valueOf(req.getAttachment().getSize()));
                metadata.put("meta_filename", filename);
                BlobInfo dataFile = BlobInfo.newBuilder(BlobId.of(BUCKET_NAME, metadataPath))
                        .setMetadata(metadata)
                        .build();
                storage.create(dataFile);

                BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(BUCKET_NAME, filePath)).build();
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

    private boolean isCaptchaPassed(String token) throws IOException {
        logger.info(token);
        String url = "https://www.google.com/recaptcha/api/siteverify";
        String params = "secret=" + CAPTCHA_KEY + "&response=" + token;

        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setDoOutput(true);
        http.setRequestMethod("POST");
        http.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8");
        OutputStream out = http.getOutputStream();
        out.write(params.getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.close();

        InputStream res = http.getInputStream();
        JsonObject response = gson.fromJson(new InputStreamReader(res, StandardCharsets.UTF_8), JsonObject.class);
        res.close();
        logger.info(response.toString());
        return response.has("success") && response.get("success").getAsBoolean();
    }


    private String generateGuid() {
        Random random = new Random();

        char[] randomChars = new char[10];
        for (int i = 0; i < 10; i++) {
            int randomInt = random.nextInt(allowedChars.length);
            randomChars[i] = (allowedChars[randomInt]);
        }
        return new String(randomChars);
    }
}