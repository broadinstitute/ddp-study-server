package org.broadinstitute.ddp.model.files;

public enum FileScanResult {
    /**
     * File upload should be sufficiently clean and safe. File is moved to safe storage and can be used.
     */
    CLEAN,
    /**
     * File upload was found to be malicious. File has been moved to quarantine and should not be used.
     */
    INFECTED,
}
