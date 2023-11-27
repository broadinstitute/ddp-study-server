package org.broadinstitute.ddp.interfaces;

import java.util.Set;

/**
 * Defines settings for file upload logic
 */
public interface FileUploadSettings {

    long getMaxFileSize();

    Set<String> getMimeTypes();
}
