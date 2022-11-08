package org.broadinstitute.ddp.secrets;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.Optional;

@Slf4j
public final class SecretManager implements AutoCloseable {
    private static final Cleaner cleaner = Cleaner.create();
    private static SecretManager sharedInstance;

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class State implements Runnable {
        SecretManagerServiceClient secretClient;

        public void run() {
            if (secretClient != null) {
                secretClient.close();
            }
        }
    }

    public static synchronized SecretManager getInstance() {
        if (sharedInstance == null) {
            try {
                sharedInstance = new SecretManager();
            } catch (IOException error) {
                log.error("failed to initialize Google Secret Manager client.", error);
            }
        }

        return sharedInstance;
    }

    public static Optional<String> get(final String projectName, final String secretName, final String secretVersion) {
        return get(SecretVersionName.of(projectName, secretName, secretVersion));
    }

    public static Optional<String> get(final SecretVersionName secret) {
        final var client = getInstance().state.secretClient;
        return Optional.of(secret)
                .map(client::accessSecretVersion)
                .map(AccessSecretVersionResponse::getPayload)
                .map(SecretPayload::getData)
                .map(ByteString::toStringUtf8);
    }

    private final State state;
    private final Cleaner.Cleanable cleanerToken;

    private SecretManager() throws IOException {
        final var credentialsProvider = FixedCredentialsProvider.create(GoogleCredentialUtil.initCredentials(false));
        final var secretSettings = SecretManagerServiceSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .build();
        final var secretManager = SecretManagerServiceClient.create(secretSettings);

        this.state = new State(secretManager);
        this.cleanerToken = SecretManager.cleaner.register(this, this.state);
    }

    @Override
    public void close() throws Exception {
        cleanerToken.clean();
    }
}
