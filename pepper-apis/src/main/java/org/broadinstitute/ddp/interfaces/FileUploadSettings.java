package org.broadinstitute.ddp.interfaces;

import java.util.Collection;

/**
 * Defines settings for file upload logic
 */
public interface FileUploadSettings {

    long getMaxFileSize();

    Collection<String> getMimeTypes();
}
