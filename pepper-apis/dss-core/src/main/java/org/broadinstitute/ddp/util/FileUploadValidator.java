package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.ConfigFile.FileUploads.MAX_FILE_SIZE_BYTES;

/**
 * Contains static methods used to validate data related
 */
public class FileUploadValidator {

    /**
     * Validate `maxFileSize`: it shoud be in interval [1..Config.MAX_FILE_SIZE_BYTES]
     * @param maxFileSize a value which to validate
     * @throws IllegalArgumentException the exception is thrown in case of invalid value
     */
    public static void validateFileMaxSize(long maxFileSize) throws IllegalArgumentException {
        Long maxFileSizeConf = ConfigManager.getInstance().getConfig().getLong(MAX_FILE_SIZE_BYTES);
        String errorMessage = null;
        final String errorMessagePrefix = "Invalid value of maxFileSize=" + maxFileSize + ". ";
        if (maxFileSize <= 0) {
            errorMessage = errorMessagePrefix + "It should be greater than 0.";
        } else if (maxFileSizeConf != null && maxFileSize > maxFileSizeConf) {
            errorMessage = errorMessagePrefix + "It should not exceed max value=" + maxFileSizeConf + ".";
        }
        if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
