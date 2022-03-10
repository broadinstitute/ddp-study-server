package org.broadinstitute.ddp.clamav;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private class Defaults {
        static final int PORT = 3310;
        static final int SO_TIMEOUT = 30000; // 30 seconds
        static final int MAX_CHUNK_SIZE = 1000000; // 1MB
    }

    private enum Delimiter {
        NULL,
        NEWLINE;

        public byte[] modeFlag() {
            switch (this) {
                case NULL:
                    return new byte[] { 'z' };
                case NEWLINE:
                    return new byte[] { 'n' };
                default:
                    return new byte[] {};
            }
        }

        public byte[] lineTerminator() {
            switch (this) {
                case NULL:
                    return new byte[] { 0x00 };
                case NEWLINE:
                    return new byte[] { 0x0A };
                default:
                    return new byte[] {};
            }
        }
    }

    private enum Command {
        PING("PING"),
        VERSION("VERSION"),
        INSTREAM("INSTREAM");

        private final String command;

        private Command(final String command) {
            this.command = command;
        }

        public byte[] getBytes() {
            return this.command.getBytes(StandardCharsets.US_ASCII);
        }
    }

    public enum MalwareResult {
        /** One or more pieces of malware were identified during a scan */
        POSITIVE,

        /** No malware was identified during a scan*/
        NEGATIVE;
    }

    public class ScanResult {
        public final MalwareResult result;
        public final Optional<String> message;

        private ScanResult(MalwareResult result) {
            this.result = result;
            this.message = Optional.empty();
        }

        private ScanResult(MalwareResult result, String message) {
            this.result = result;
            this.message = Optional.ofNullable(message);
        }
    }

    private InetSocketAddress clamdAddress;
    private int socketTimeout = Defaults.SO_TIMEOUT;

    public Client(String host) {
        this(host, Defaults.PORT);
    }
    
    public Client(String host, int port) {
        assert host != null;
        assert host.isEmpty() == false;

        clamdAddress = new InetSocketAddress(host, port);
    }

    private Socket open() throws IOException {
        Socket socket = new Socket(clamdAddress.getAddress(), clamdAddress.getPort());
        socket.setSoTimeout(socketTimeout);
        return socket;
    }

    public boolean ping() throws IOException {
        final var delimiter = Delimiter.NULL;
        final var command = Command.PING;

        // BufferedInputStream defaults to an 8k buffer which should be more than enough
        try (Socket socket = open();
                var clamdOutputStream = new BufferedOutputStream(socket.getOutputStream());
                var clamdResponseStream = new BufferedInputStream(socket.getInputStream())) {
            logger.fine("socket successfully opened");

            // The full command for a NUL-delimited PING is "zPING\0".
            // Upon receipt, clamd should write back "PONG\0"
            clamdOutputStream.write(delimiter.modeFlag());
            clamdOutputStream.write(command.getBytes());
            clamdOutputStream.write(delimiter.lineTerminator());
            clamdOutputStream.flush();

            logger.fine("Ping sent. Awaiting PONG...");

            String expected = "PONG";
            String response;

            // Read in the response from clamd
            // Keep in mind that Scanner::close _also_ closes the underlying stream, so
            // clamdResponseStream is closed at the end of the try scope, 
            // before the end of the parent scope.
            try (var scanner = new Scanner(clamdResponseStream)) {
                scanner.useDelimiter(new String(delimiter.lineTerminator(), StandardCharsets.US_ASCII));
                if (scanner.hasNext()) {
                    // Take a look into nextLine's behavior if the socket is closed.
                    // This is an easy method but we might need to fall back to more 
                    // traditional socket methods.
                    try {
                        response = scanner.next();
                    } catch (NoSuchElementException nsee) {
                        // This exception will be thrown by Scanner if a token delimiter has not been
                        // read before the connection time or the underlying socket has been closed
                        throw new IOException("unexpected response from clamd", nsee);
                    }
                } else {
                    throw new IOException("no response from clamd");
                }
            }

            if (expected.equals(response)) {
                return true;
            } else {
                logger.warning("unexpected response from clamd PING: " + response);
                return false;
            }
        }
    }

    public ScanResult scan(InputStream inputStream) throws IOException {
        return this.scan(inputStream, Defaults.MAX_CHUNK_SIZE);
    }

    /**
     * Transmits the input stream to the ClamAV host for scanning.
     * 
     * <p>This method does not call InputStream::reset on the passed stream.
     * @param inputStream the data to scan.
     * @param chunkSize the maximum size of the chunks sent to clamd.
     * @return true if the malware scan was negative, false otherwise
     * @throws IOException if there is a communication error with clamd
     */
    public ScanResult scan(InputStream inputStream, int chunkSize) throws IOException {
        final var delimiter = Delimiter.NULL;
        final var command = Command.INSTREAM;

        try (Socket socket = open()) {
            try (var clamdOutputStream = new BufferedOutputStream(socket.getOutputStream());
                    var clamdInputStream = new BufferedInputStream(socket.getInputStream())) {

                // clamd will not respond until the terminating chunk has been sent.
                clamdOutputStream.write(delimiter.modeFlag());
                clamdOutputStream.write(command.getBytes());
                clamdOutputStream.write(delimiter.lineTerminator());

                var dataBuffer = ByteBuffer.allocate(chunkSize);

                int read = inputStream.read(dataBuffer.array(), 0, chunkSize);

                while (read > 0) {
                    logger.fine(String.format("read %d bytes from is", read));

                    // The chunk format is: <size - 4 bytes><data>
                    // The stream is terminated by sending a zero-length chunk
                    // {0x00, 0x00, 0x00, 0x00}
                    clamdOutputStream.write(ByteBuffer.allocate(4).putInt(read).array());
                    clamdOutputStream.write(dataBuffer.array(), 0, read);

                    // If clamd writes to the socket before we've finished
                    // sending out data, something abnormal happened and
                    // we should stop what we're doing immediately. There shouldn't
                    // be any data for us as a client to read until after the
                    // terminating zero-size chunk is written.
                    if (clamdInputStream.available() > 0) {
                        throw new IOException("unexpected response from clamd");
                    }

                    read = inputStream.read(dataBuffer.array(), 0, chunkSize);
                }

                logger.fine("data chunks sent, preparting termination chunk");
                clamdOutputStream.write(new byte[]{0, 0, 0, 0});
                clamdOutputStream.flush();
                logger.fine("terminated final chunk");

                String response;

                try (var scanner = new Scanner(clamdInputStream, StandardCharsets.US_ASCII)) {
                    scanner.useDelimiter(new String(delimiter.lineTerminator(), StandardCharsets.US_ASCII));
                    response = scanner.next().trim();
                } catch (NoSuchElementException nsee) {
                    // This exception will be thrown by Scanner if a token delimiter has not been
                    // read before the connection time or the underlying socket has been closed
                    throw new IOException("unexpected response from clamd", nsee);
                }

                logger.info(String.format("received response from clamd: '%s'", response));

                /*
                _if a NEWLINE delimiter is being used, replace the \0 delimiter as necessary_

                The expected response format for a negative scan is:
                    stream: OK\0
                
                The expected response for a positive scan is:
                    stream: <malware details>\0
                
                ClamAV may also response with the below message if the size of the file being scanned is too large:
                  INSTREAM size limit exceeded. ERROR\0
                
                If [IDSESSION/END] is used, the format used by clamd will change, and this will need to be updated.

                [IDSESSION/END]: https://manpages.debian.org/testing/clamav-daemon/clamd.8.en.html#IDSESSION,
                */

                final var sizeLimitError = "INSTREAM size limit exceeded";
                final var streamHeader = "stream:";
                final var negativeResponse = "OK";

                if (response.startsWith(sizeLimitError)) {
                    throw new IOException("clamav file size limit exceeded");
                } else if (response.startsWith(streamHeader) == false) {
                    var message = String.format("unrecognized response from clamav: %s", response);
                    throw new IOException(message);
                }

                response = response.replaceFirst(streamHeader, "").trim();
                
                if (response.startsWith(negativeResponse)) {
                    return new ScanResult(MalwareResult.NEGATIVE);
                } else {
                    logger.fine(String.format("malware detected: %s", response));
                    return new ScanResult(MalwareResult.POSITIVE, response);
                }
            }
        }
    }
}
