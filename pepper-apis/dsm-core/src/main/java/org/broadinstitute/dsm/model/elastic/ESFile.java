package org.broadinstitute.dsm.model.elastic;

import org.apache.commons.lang3.StringUtils;

public class ESFile {
    public String guid;
    public String scannedAt;
    public String scanResult;
    public String fileName;
    public String bucket;
    public String fileSize;
    public String mimeType;
    public String blobName;

    public boolean isFileClean() {
        return StringUtils.isNotBlank(this.scannedAt) && "CLEAN".equals(this.scanResult);
    }

}
