package com.geneav.scan.service;

/**
 * Low-level outcome from a scan engine, independent of the HTTP layer.
 *
 * @param infected true if a threat was found
 * @param threat   the signature/threat name when infected, otherwise null
 */
public record ScanResult(boolean infected, String threat) {

    public static ScanResult clean() {
        return new ScanResult(false, null);
    }

    public static ScanResult infected(String threat) {
        return new ScanResult(true, threat);
    }
}
