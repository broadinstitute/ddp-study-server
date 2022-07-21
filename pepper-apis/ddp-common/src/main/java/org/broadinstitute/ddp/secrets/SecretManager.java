package org.broadinstitute.ddp.secrets;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.protobuf.ByteString;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecretManager {
    public static Optional<String> get(final String projectName, final String secretName) {
        try (final var client = SecretManagerServiceClient.create()) {
            return Optional.of(getLatestSecretVersion(projectName, secretName).getName())
                    .map(client::accessSecretVersion)
                    .map(AccessSecretVersionResponse::getPayload)
                    .map(SecretPayload::getData)
                    .map(ByteString::toStringUtf8);
        } catch (final Exception e) {
            log.warn("The secret " + secretName + " wasn't found", e);
        }

        return Optional.empty();
    }

    private static SecretVersion getLatestSecretVersion(final String projectName, final String secretName) throws Exception {
        try (final var client = SecretManagerServiceClient.create()) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(client.listSecretVersions(SecretName.of(projectName, secretName)).iterateAll().iterator(), 0), false)
                    .max(SecretManager::compare)
                    .orElseThrow();
        }
    }

    private static int compare(final SecretVersion a, final SecretVersion b) {
        return Long.compare(a.getCreateTime().getSeconds(), b.getCreateTime().getSeconds());
    }
}
