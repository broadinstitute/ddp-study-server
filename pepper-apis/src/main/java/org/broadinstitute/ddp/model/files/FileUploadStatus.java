package org.broadinstitute.ddp.model.files;

public enum FileUploadStatus {
    /**
     * File upload has been authorized. This is the initial state.
     */
    AUTHORIZED,

    /**
     * File has been uploaded. File may not be safe to be operated on, and a scan is required/pending.
     */
    UPLOADED,

    /**
     * File upload has been scanned and found to be malicious. File has been moved to quarantine and should not be used.
     */
    QUARANTINED,

    /**
     * File upload may not be totally clean but is sufficiently safe. File is moved to safe storage and can be used.
     */
    SCANNED,
}
