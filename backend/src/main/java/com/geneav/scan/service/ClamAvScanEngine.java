package com.geneav.scan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * {@link ScanEngine} backed by a ClamAV {@code clamd} daemon, spoken over TCP
 * using the native INSTREAM protocol (no third-party client library needed).
 *
 * <p>INSTREAM works as follows: send {@code zINSTREAM\0}, then a sequence of chunks
 * where each chunk is a 4-byte big-endian length followed by that many bytes; a
 * zero-length chunk terminates the stream. clamd then replies with either
 * {@code stream: OK} or {@code stream: <Threat> FOUND}.
 */
@Service
public class ClamAvScanEngine implements ScanEngine {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanEngine.class);

    private static final int CHUNK_SIZE = 8192;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvScanEngine(
            @Value("${geneav.clamav.host:localhost}") String host,
            @Value("${geneav.clamav.port:3310}") int port,
            @Value("${geneav.clamav.timeout-ms:30000}") int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ScanResult scan(InputStream data) throws IOException {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();

            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = data.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                out.write(intToBigEndian(read));
                out.write(buffer, 0, read);
            }
            // zero-length chunk signals end of stream
            out.write(intToBigEndian(0));
            out.flush();

            String reply = readReply(socket.getInputStream());
            return parseReply(reply);
        }
    }

    @Override
    public boolean isHealthy() {
        try (Socket socket = openSocket()) {
            OutputStream out = socket.getOutputStream();
            out.write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String reply = readReply(socket.getInputStream());
            return reply.startsWith("PONG");
        } catch (IOException e) {
            log.warn("ClamAV health check failed for {}:{} - {}", host, port, e.getMessage());
            return false;
        }
    }

    private Socket openSocket() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    private static byte[] intToBigEndian(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private static String readReply(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        // clamd terminates replies with a NUL byte in the "z" command variants
        while ((b = in.read()) != -1) {
            if (b == 0) {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString().trim();
    }

    private ScanResult parseReply(String reply) throws IOException {
        // Examples:
        //   "stream: OK"
        //   "stream: Eicar-Test-Signature FOUND"
        //   "INSTREAM size limit exceeded. ERROR"
        if (reply.endsWith("OK")) {
            return ScanResult.clean();
        }
        if (reply.endsWith("FOUND")) {
            // "stream: <Threat> FOUND" -> extract <Threat>
            String body = reply.substring(reply.indexOf(':') + 1, reply.lastIndexOf("FOUND")).trim();
            return ScanResult.infected(body);
        }
        throw new IOException("Unexpected response from ClamAV: " + reply);
    }
}
