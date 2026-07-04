package com.geneav.scan.web;

import org.springframework.http.HttpStatus;

/**
 * Carries an HTTP status alongside a human-readable message for expected,
 * client-facing scan errors (bad input, unsupported type, engine down).
 */
public class ScanException extends RuntimeException {

    private final HttpStatus status;

    public ScanException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
