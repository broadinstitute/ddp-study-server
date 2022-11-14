package org.broadinstitute.ddp.secrets;

import java.io.IOException;
import java.util.Optional;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;

@Slf4j
public final class SecretManager {
    public static final String LATEST = "latest";

    private static SecretManagerServiceClient createSecretManagerClient() throws IOException {
        final var credentialsProvider = FixedCredentialsProvider.create(GoogleCredentialUtil.initCredentials(false));
        final var secretSettings = SecretManagerServiceSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .build();

        return SecretManagerServiceClient.create(secretSettings);
    }

    public static Optional<String> get(final String projectName, final String secretName, final String secretVersion) {
        return get(SecretVersionName.of(projectName, secretName, secretVersion));
    }

    public static Optional<String> get(final SecretVersionName secret) {
        final Optional<String> value;
        try (final var client = createSecretManagerClient()) {
            value = Optional.of(secret)
                    .map(client::accessSecretVersion)
                    .map(AccessSecretVersionResponse::getPayload)
                    .map(SecretPayload::getData)
                    .map(ByteString::toStringUtf8);
        } catch (IOException error) {
            throw new DDPException(String.format("failed to load [resource:%s] from Google Secret Manager.", secret), error);
        }

        return value;
    }
}
