package org.broadinstitute.ddp.interfaces;

import java.util.List;

/**
 * Defines settings for file upload logic
 */
public interface FileUploadSettings {

    long getMaxFileSize();

    List<String> getMimeTypes();
}
