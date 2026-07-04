package com.geneav.scan.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction over a malware-scanning backend. v1 is backed by {@link ClamAvScanEngine},
 * but the interface lets us swap or add engines later without touching the controller.
 */
public interface ScanEngine {

    /**
     * Scan a stream of bytes for malware.
     *
     * @param data the document content
     * @return the scan result
     * @throws IOException if the engine could not be reached or the scan failed
     */
    ScanResult scan(InputStream data) throws IOException;

    /**
     * @return true if the engine is reachable and ready to scan
     */
    boolean isHealthy();
}
