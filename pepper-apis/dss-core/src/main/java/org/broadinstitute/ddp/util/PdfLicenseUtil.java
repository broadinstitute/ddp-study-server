package org.broadinstitute.ddp.util;

import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.itextpdf.licensekey.LicenseKey;

import com.itextpdf.licensekey.LicenseKeyException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.secrets.SecretManager;

@Slf4j
public final class PdfLicenseUtil {

    public static void loadITextLicense() {
        final var licenseName = PropertyManager.getProperty(ConfigFile.ITEXT_FILE_ENV_VAR);

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

        log.debug("license name does not appear to be a local path, treating property {} as a Google Secret resource id.",
                ConfigFile.ITEXT_FILE_ENV_VAR);

        final var licenseKeySecret = SecretManager.get(SecretVersionName.parse(licenseNameValue))
                .map((secret) -> IOUtils.toInputStream(secret, StandardCharsets.UTF_8))
                .orElseThrow(() -> {
                    return new DDPException(String.format("failed to load iText licence key from resource '%s'", licenseNameValue));
                });

        LicenseKey.loadLicenseFile(licenseKeySecret);
        log.info("successfully loaded the iText license from secret manager resource '{}'", licenseNameValue);
    }

    private static boolean loadITextLicense(Path licensePath) {
        final Path resolvedPath;

        try {
            resolvedPath = licensePath.toRealPath();
        } catch (IOException ioe) {
            throw new DDPException(String.format("failed to load license file at %s", licensePath), ioe);
        }

        LicenseKey.loadLicenseFile(resolvedPath.toString());
        return true;
    }
}
