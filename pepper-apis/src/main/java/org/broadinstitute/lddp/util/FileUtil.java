package org.broadinstitute.lddp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    /**
     * Send the contents of the given file to the output stream
     * of the response, using zero copy IO.  Be sure to set response
     * status and headers before calling this when using a servlet
     * stream as the destination.
     * @return number of bytes served
     */
    public long sendFile(File file, OutputStream destination) throws IOException {
        long bytesWritten = 0;
        FileChannel fileChannel = null;
        try {
            WritableByteChannel responseChannel = Channels.newChannel(destination);
            fileChannel = FileChannel.open(file.toPath(), Collections.singleton(StandardOpenOption.READ));
            bytesWritten = fileChannel.transferTo(0, fileChannel.size(), responseChannel);
        }
        finally {
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
        return bytesWritten;

    }

    /**
     * Send partial contents of the given file to the output stream
     * of the response, using zero copy IO.  Be sure to set response
     * status and headers before calling this when using a servlet
     * stream as the destination.
     * @param  file the source file
     * @param startPosition the position at which to start reading the file
     * @param count the total number of bytes to send
     * @return number of bytes served
     */
    public long sendFile(File file, long startPosition, long count,OutputStream destination) throws IOException {
        if (startPosition < 0 || count < 0) {
            throw new IllegalArgumentException("startPosition and count must be >= 0");
        }

        FileChannel fileChannel = null;
        long bytesWritten = 0;

        try {
            WritableByteChannel responseChannel = Channels.newChannel(destination);
            fileChannel = FileChannel.open(file.toPath(), Collections.singleton(StandardOpenOption.READ));
            bytesWritten = fileChannel.transferTo(startPosition, count, responseChannel);
        }
        finally {
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
        return bytesWritten;
    }
}
