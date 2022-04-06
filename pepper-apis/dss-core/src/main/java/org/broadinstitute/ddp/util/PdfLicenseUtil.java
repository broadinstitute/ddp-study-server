package org.broadinstitute.ddp.util;

import com.itextpdf.licensekey.LicenseKey;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;

public class PdfLicenseUtil {

    public static void loadITextLicense() {
        String licenseFile = System.getProperty(ConfigFile.ITEXT_FILE_ENV_VAR);

        if (StringUtils.isEmpty(licenseFile)) {
            throw new DDPException("No itext license file found.");
        }
        LicenseKey.loadLicenseFile(licenseFile);
    }
}
