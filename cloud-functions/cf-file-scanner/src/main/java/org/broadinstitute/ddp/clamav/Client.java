package org.broadinstitute.ddp.clamav;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

import lombok.extern.flogger.Flogger;

@Flogger
public class Client {

    private class Defaults {
        static final int PORT = 3310;
        static final int SO_TIMEOUT = 30000; // 30 seconds
        static final int MAX_CHUNK_SIZE = 1000000; // 1MB
    }

    private enum Delimiter {
        NULL((byte)'z', (byte)0x00),
        NEWLINE((byte)'n', (byte)0x0A);

        private final byte[] modeFlag;
        private final byte[] lineTerminator;

        private Delimiter(byte modeFlag, byte lineTerminator) {
            this.modeFlag = new byte[] { modeFlag };
            this.lineTerminator = new byte[] { lineTerminator };
        }

        public byte[] getModeFlag() {
            return Arrays.copyOf(modeFlag, modeFlag.length);
        }

        public byte[] getLineTerminator() {
            return Arrays.copyOf(lineTerminator, lineTerminator.length);
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
        Objects.requireNonNull(host, "host must not be null");

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
            log.atFiner().log("socket successfully opened");

            // The full command for a NUL-delimited PING is "zPING\0".
            // Upon receipt, clamd should write back "PONG\0"
            clamdOutputStream.write(delimiter.getModeFlag());
            clamdOutputStream.write(command.getBytes());
            clamdOutputStream.write(delimiter.getLineTerminator());
            clamdOutputStream.flush();

            log.atFine().log("Ping sent. Awaiting PONG...");

            String expected = "PONG";
            String response;

            // Read in the response from clamd
            // Keep in mind that Scanner::close _also_ closes the underlying stream, so
            // clamdResponseStream is closed at the end of the try scope, 
            // before the end of the parent scope.
            try (var scanner = new Scanner(clamdResponseStream)) {
                scanner.useDelimiter(new String(delimiter.getLineTerminator(), StandardCharsets.US_ASCII));
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
                log.atWarning().log("unexpected response from clamd PING: %s", response);
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
                clamdOutputStream.write(delimiter.getModeFlag());
                clamdOutputStream.write(command.getBytes());
                clamdOutputStream.write(delimiter.getLineTerminator());

                var dataBuffer = ByteBuffer.allocate(chunkSize);

                int read = inputStream.read(dataBuffer.array(), 0, chunkSize);

                while (read > 0) {
                    log.atFiner().log("read %s bytes from stream", read);

                    // The chunk format is: <size - 4 bytes><data>
                    // The stream is terminated by sending a zero-length chunk
                    // { 0x00, 0x00, 0x00, 0x00 }
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

                log.atFiner().log("data chunks sent, preparing termination chunk");
                clamdOutputStream.write(new byte[]{0, 0, 0, 0});
                clamdOutputStream.flush();
                log.atFiner().log("terminated final chunk");

                String response;

                try (var scanner = new Scanner(clamdInputStream, StandardCharsets.US_ASCII)) {
                    scanner.useDelimiter(new String(delimiter.getLineTerminator(), StandardCharsets.US_ASCII));
                    response = scanner.next().trim();
                } catch (NoSuchElementException nsee) {
                    // This exception will be thrown by Scanner if a token delimiter has not been
                    // read before the connection time or the underlying socket has been closed
                    throw new IOException("unexpected response from clamd", nsee);
                }

                log.atFiner().log("received response from clamd: %s", response);

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
                    throw new IOException("unrecognized response from clamav: " + response);
                }

                response = response.replaceFirst(streamHeader, "").trim();
                
                if (response.startsWith(negativeResponse)) {
                    return new ScanResult(MalwareResult.NEGATIVE);
                } else {
                    log.atFine().log("malware detected: %s", response);
                    return new ScanResult(MalwareResult.POSITIVE, response);
                }
            }
        }
    }
}
