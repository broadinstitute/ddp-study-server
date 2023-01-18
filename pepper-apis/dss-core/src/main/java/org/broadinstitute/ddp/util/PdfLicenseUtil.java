package org.broadinstitute.ddp.util;

import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.itextpdf.licensekey.LicenseKey;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;

@Slf4j
public class PdfLicenseUtil {
    private static final String GOOGLE_SECRET_PROJECT = "google.secret.project";
    private static final String GOOGLE_SECRET_NAME = "google.secret.name";

    public static void loadITextLicense() {
        final var licenseFile = System.getProperty(ConfigFile.ITEXT_FILE_ENV_VAR);

        if (StringUtils.isBlank(licenseFile)) {
            throw new DDPException("No itext license file found.");
        }

        if (loadITextLicense(licenseFile)) {
            log.info("successfully loaded the iText license key.");
            return;
        }

        log.info("license key failed to be loaded from a file, trying as a Google Secret");

        final var projectId = System.getProperty(GOOGLE_SECRET_PROJECT);
        final var latest = "latest";

        final var googleSecretNameInvalid = StringUtils.isBlank(projectId)
                && StringUtils.isBlank(latest);

        if (googleSecretNameInvalid) {
            log.info
        }
        
        final var secretName = SecretVersionName.of(projectId, licenseFile, version);

    }

    private static boolean loadITextLicenseSecret(SecretVersionName secretName) {
        
    }

    private static boolean loadITextLicense(String path) {
        final Path resolvedPath;

        try {
            resolvedPath = Paths.get(path).toRealPath();
        } catch (IOException ioe) {
            throw new DDPException(String.format("failed to load license file at %s", path));
        }

        if (Files.exists(resolvedPath)) {
            LicenseKey.loadLicenseFile(resolvedPath.toString());
            return true;
        }

        return false;
    }
}
