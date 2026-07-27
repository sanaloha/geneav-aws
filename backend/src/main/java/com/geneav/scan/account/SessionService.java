package com.geneav.scan.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Establishes the authenticated dashboard session (principal = account id).
 * Shared by password login and federated (Microsoft) login so the
 * session-fixation rotation and the {@code AUTHENTICATED_AT} stamp — which
 * lets a later password change invalidate old sessions — exist in one place.
 */
@Component
public class SessionService {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /** Starts (or replaces) a session authenticated as {@code account}. */
    public void establish(Account account, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                account.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        request.changeSessionId(); // rotate to avoid session fixation
        // Lets a later password change invalidate sessions issued before it.
        session.setAttribute(CurrentAccount.AUTHENTICATED_AT, Instant.now());
        securityContextRepository.saveContext(context, request, response);
    }
}
