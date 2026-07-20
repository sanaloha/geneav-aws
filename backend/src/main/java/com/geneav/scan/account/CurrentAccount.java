package com.geneav.scan.account;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
}
