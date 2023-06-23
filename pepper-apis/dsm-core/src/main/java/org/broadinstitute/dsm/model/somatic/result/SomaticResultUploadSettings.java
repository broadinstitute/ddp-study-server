package org.broadinstitute.dsm.model.somatic.result;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;

public class SomaticResultUploadSettings {
    private long maxFileSize = 30000000L; // 1 MB = 1,000,000 bytes.  30 MB.
    private Set<String> supportedSomaticFileTypes = Collections.singleton("application/pdf");

    private List<String> allowedFileExtensions = List.of("pdf");

    /**
     * Object to encapsulate file upload constraints to the UI so that only the files of a certain
     * size and type are used.  Default configuration can be overridden using a configuration file.
     * @param cfg Config or null
     */
    public SomaticResultUploadSettings(Config cfg) {
        if (cfg != null) {
            if (cfg.hasPath(ConfigFile.SomaticUploads.MAX_FILES_CONFIG_PATH)) {
                this.maxFileSize = cfg.getLong(ConfigFile.SomaticUploads.MAX_FILES_CONFIG_PATH);
            }

            if (cfg.hasPath(ConfigFile.SomaticUploads.MEDIA_TYPES_CONFIG_PATH)) {
                this.supportedSomaticFileTypes = new HashSet<>(Arrays
                        .asList(cfg.getString(ConfigFile.SomaticUploads.MEDIA_TYPES_CONFIG_PATH).split(",")));
            }

            if (cfg.hasPath(ConfigFile.SomaticUploads.ALLOWED_FILE_EXTENSIONS)) {
                this.allowedFileExtensions = List.of(cfg.getString(ConfigFile.SomaticUploads.ALLOWED_FILE_EXTENSIONS).split(","));
            }
        }
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public Set<String> getMimeTypes() {
        return supportedSomaticFileTypes;
    }

    public List<String> getAllowedFileExtensions() {
        return allowedFileExtensions;
    }
}
