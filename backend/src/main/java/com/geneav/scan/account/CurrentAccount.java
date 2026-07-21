package com.geneav.scan.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the account behind a management request, accepting either credential:
 * a logged-in dashboard <b>session</b> (Spring Security principal = account id) or
 * a programmatic <b>API key</b> (attached by {@link ApiKeyAuthFilter}). The session
 * is preferred when both are present.
 */
@Component
public class CurrentAccount {

    private static final Logger log = LoggerFactory.getLogger(CurrentAccount.class);

    /** Session attribute holding when the session was authenticated. */
    public static final String AUTHENTICATED_AT = "geneav.authenticatedAt";

    private final AccountRepository accounts;

    public CurrentAccount(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<Account> resolve(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            try {
                UUID id = UUID.fromString(auth.getName());
                Optional<Account> bySession = accounts.findById(id).filter(Account::isActive);
                if (bySession.isPresent()) {
                    if (isStale(request, bySession.get())) {
                        // Password changed elsewhere: drop this session rather than
                        // serving it, which signs the other browser out.
                        endSession(request);
                        log.info("Rejected a session for account {} issued before its password change", id);
                        return Optional.empty();
                    }
                    return bySession;
                }
            } catch (IllegalArgumentException ignored) {
                // principal name was not an account id; fall through to API key
            }
        }
        return ApiKeyAuthFilter.current(request)
                .map(AuthenticatedClient::account)
                .filter(Account::isActive);
    }

    private boolean isStale(HttpServletRequest request, Account account) {
        HttpSession session = request.getSession(false);
        Object stamp = session == null ? null : session.getAttribute(AUTHENTICATED_AT);
        return !account.acceptsSessionFrom(stamp instanceof Instant instant ? instant : null);
    }

    private void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
