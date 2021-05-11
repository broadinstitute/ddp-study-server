package org.broadinstitute.ddp.util;

import java.io.IOException;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.exception.DDPException;

public class SecretUtil {
    public static Config getConfigFromSecret(String googleProjectId, String secretName) {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName versionName = SecretVersionName.of(googleProjectId, secretName, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(versionName);
            String secret = response.getPayload().getData().toStringUtf8();
            JsonElement elem = new JsonParser().parse(secret);
            return ConfigFactory.parseString(new Gson().toJson(elem));

        } catch (IOException e) {
            throw new DDPException("Error getting config from secret " + secretName, e);
        }
    }
}
