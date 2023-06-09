package org.broadinstitute.dsm.model.somatic.result;

import lombok.NonNull;

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
