package org.broadinstitute.ddp.secrets;

import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecretManager {
    public static Optional<String> get(final String projectName, final String secretName, final String secretVersion) {
        try (final var client = SecretManagerServiceClient.create()) {
            return Optional.ofNullable(SecretVersionName.of(projectName, secretName, secretVersion))
                    .map(client::accessSecretVersion)
                    .map(AccessSecretVersionResponse::getPayload)
                    .map(SecretPayload::getData)
                    .map(ByteString::toStringUtf8);
        } catch (final Exception e) {
            log.warn("The secret " + secretName + " wasn't found", e);
        }

        return Optional.empty();
    }
}
