package com.geneav.scan.marketplace;

import com.geneav.scan.web.ScanException;
import org.springframework.http.HttpStatus;

/**
 * A marketplace-flow failure, carried as the same HTTP-status exception shape
 * the rest of the API uses so {@code ApiExceptionHandler} renders it uniformly.
 */
public class MarketplaceException extends ScanException {

    public MarketplaceException(HttpStatus status, String message) {
        super(status, message);
    }
}
