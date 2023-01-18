package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.itextpdf.licensekey.LicenseKey;
import com.itextpdf.licensekey.LicenseKeyException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ApplicationProperty;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.secrets.SecretManager;

@Slf4j
public final class PdfLicenseUtil {

    public static void loadITextLicense() throws LicenseKeyException {
        final var licenseName = PropertyManager.getProperty(ApplicationProperty.ITEXT_LICENSE);

        if (licenseName.isEmpty() || StringUtils.isWhitespace(licenseName.get())) {
            log.warn("no license key specified for property {}, skipping attempting load of iText license. "
                    + "PDF generation may not function as expected.",
                    ConfigFile.ITEXT_FILE_ENV_VAR);
            return;
        }

        final var licenseNameValue = licenseName.get();
        final var licenseFilePath = FileSystems.getDefault().getPath(licenseNameValue);
        if (Files.exists(licenseFilePath) && loadITextLicense(licenseFilePath)) {
            log.info("successfully loaded the iText license key from {}", licenseFilePath);
            return;
        }

        log.info("license name does not appear to be a local path, treating property {} as a the name of a Google secret",
                ApplicationProperty.ITEXT_LICENSE.getPropertyName());

        final var googleProject = PropertyManager.getProperty(ApplicationProperty.GOOGLE_SECRET_PROJECT)
                .orElseThrow(() -> noGoogleProjectDefined());

        final var secretName = SecretVersionName.of(googleProject, licenseNameValue, "latest");

        final var licenseKeySecret = SecretManager.get(secretName)
                .map((secret) -> IOUtils.toInputStream(secret, StandardCharsets.UTF_8))
                .orElseThrow(() -> failedToLoadLicenseSecret(secretName));

        LicenseKey.loadLicenseFile(licenseKeySecret);
        log.info("successfully loaded the iText license from secret manager resource '{}'", secretName);
    }

    private static boolean loadITextLicense(Path licensePath) throws LicenseKeyException {
        final Path resolvedPath;

        try {
            resolvedPath = licensePath.toRealPath();
        } catch (IOException ioe) {
            throw new DDPException(String.format("failed to load license file at %s", licensePath), ioe);
        }

        LicenseKey.loadLicenseFile(resolvedPath.toString());
        return true;
    }

    private static DDPException noGoogleProjectDefined() {
        return new DDPException(String.format("The application property %s is missing or empty.",
                ApplicationProperty.GOOGLE_SECRET_PROJECT.getPropertyName()));
    }

    private static DDPException failedToLoadLicenseSecret(SecretVersionName name) {
        return new DDPException(String.format("failed to load iText licence key from resource '%s'", name.toString()));
    }
}
