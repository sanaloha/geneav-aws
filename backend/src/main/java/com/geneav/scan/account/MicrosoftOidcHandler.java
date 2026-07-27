package com.geneav.scan.account;

import com.geneav.scan.web.ScanException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Completes a Microsoft sign-in: maps the Entra identity to a geneav account,
 * replaces Spring's OAuth2 authentication with the dashboard's own session
 * shape (principal = account id, via {@link SessionService}), and sends the
 * browser back to the frontend.
 *
 * <p>Return location: the frontend may set a short-lived {@code geneav_next}
 * cookie holding a <b>relative</b> path (e.g. the marketplace landing page)
 * before starting the sign-in; anything absolute or protocol-relative is
 * ignored so the cookie can never turn this into an open redirect.
 */
@Component
@ConditionalOnProperty(prefix = "geneav.auth.microsoft", name = "enabled", havingValue = "true")
public class MicrosoftOidcHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    static final String NEXT_COOKIE = "geneav_next";

    private static final Logger log = LoggerFactory.getLogger(MicrosoftOidcHandler.class);

    private final FederatedSignInService federatedSignIn;
    private final SessionService sessions;
    private final MicrosoftAuthProperties props;

    public MicrosoftOidcHandler(FederatedSignInService federatedSignIn, SessionService sessions,
                                MicrosoftAuthProperties props) {
        this.federatedSignIn = federatedSignIn;
        this.sessions = sessions;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            redirectWithError(request, response, "Microsoft sign-in returned an unexpected principal.");
            return;
        }
        String tid = user.getClaimAsString("tid");
        String oid = user.getClaimAsString("oid");
        String email = user.getEmail() != null ? user.getEmail() : user.getClaimAsString("preferred_username");

        Account account;
        try {
            account = federatedSignIn.signIn(tid, oid, email);
        } catch (ScanException e) {
            redirectWithError(request, response, e.getMessage());
            return;
        }

        // Swap the OAuth2 security context for the dashboard's own session shape.
        sessions.establish(account, request, response);
        response.sendRedirect(destination(request, response));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Microsoft sign-in failed: {}", exception.getMessage());
        redirectWithError(request, response, "Microsoft sign-in failed. Please try again.");
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        clearNextCookie(request, response);
        response.sendRedirect(props.getPostLoginUrl() + "?msError="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private String destination(HttpServletRequest request, HttpServletResponse response) {
        String next = readNextCookie(request);
        clearNextCookie(request, response);
        if (next != null && next.startsWith("/") && !next.startsWith("//")) {
            URI base = URI.create(props.getPostLoginUrl());
            return base.getScheme() + "://" + base.getRawAuthority() + next;
        }
        return props.getPostLoginUrl();
    }

    private String readNextCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (NEXT_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clearNextCookie(HttpServletRequest request, HttpServletResponse response) {
        if (readNextCookie(request) == null) {
            return;
        }
        Cookie expired = new Cookie(NEXT_COOKIE, "");
        expired.setPath("/");
        expired.setMaxAge(0);
        response.addCookie(expired);
    }
}
