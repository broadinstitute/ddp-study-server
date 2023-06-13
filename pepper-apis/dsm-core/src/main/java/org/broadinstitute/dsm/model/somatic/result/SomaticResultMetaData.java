package org.broadinstitute.dsm.model.somatic.result;

import lombok.NonNull;

/**
 * Class to encapsulate the information the DSM UI will pass to the DSM service so that a decision to authorize file
 * upload can be made.
 */
public class SomaticResultMetaData {
    private final String fileName;
    private final String mimeType; //media type, but the front end already calls it mime type.
    private final Long fileSize;

    public SomaticResultMetaData(@NonNull String fileName,
                                 @NonNull String mimeType, @NonNull Long fileSize) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    public Long getFileSize() {
        return this.fileSize;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public String getFileName() {
        return this.fileName;
    }
}
