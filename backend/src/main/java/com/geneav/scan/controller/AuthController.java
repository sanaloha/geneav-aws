package com.geneav.scan.controller;

import com.geneav.scan.account.Account;
import com.geneav.scan.account.AccountService;
import com.geneav.scan.account.CurrentAccount;
import com.geneav.scan.dto.AccountDtos.LoginRequest;
import com.geneav.scan.dto.AccountDtos.MeResponse;
import com.geneav.scan.dto.AccountDtos.SignupPasswordRequest;
import com.geneav.scan.mail.MailService;
import com.geneav.scan.web.ScanException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard authentication: email/password signup, login, logout, and the current
 * session. On success a Spring Security context is persisted to an HttpOnly cookie
 * session; the dashboard then calls the management endpoints with that cookie.
 * Google OAuth2 login is added in a later slice.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Dashboard sign up / sign in")
public class AuthController {

    private final AccountService accounts;
    private final CurrentAccount currentAccount;
    private final MailService mail;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AccountService accounts, CurrentAccount currentAccount, MailService mail) {
        this.accounts = accounts;
        this.currentAccount = currentAccount;
        this.mail = mail;
    }

    @Operation(summary = "Sign up with a password", description = "Creates a password account and starts a session.")
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SignupPasswordRequest req,
                             HttpServletRequest request, HttpServletResponse response) {
        Account account = accounts.signupWithPassword(req.email(), req.password());
        establishSession(account, request, response);
        // Best-effort and asynchronous — a mail failure must not fail the signup.
        mail.sendSignupAcknowledgement(account.getEmail());
        return toMe(account);
    }

    @Operation(summary = "Sign in", description = "Verifies email + password and starts a session.")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MeResponse login(@Valid @RequestBody LoginRequest req,
                            HttpServletRequest request, HttpServletResponse response) {
        Account account = accounts.authenticatePassword(req.email(), req.password());
        establishSession(account, request, response);
        return toMe(account);
    }

    @Operation(summary = "Sign out", description = "Ends the current dashboard session.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Operation(summary = "Current session", description = "Returns the signed-in account, or 401 if not signed in.")
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public MeResponse me(HttpServletRequest request) {
        Account account = currentAccount.resolve(request)
                .orElseThrow(() -> new ScanException(HttpStatus.UNAUTHORIZED, "Not signed in."));
        return toMe(account);
    }

    /** Establishes an authenticated session with the account id as the principal. */
    private void establishSession(Account account, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                account.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId(); // rotate to avoid session fixation
        securityContextRepository.saveContext(context, request, response);
    }

    private static MeResponse toMe(Account account) {
        return new MeResponse(account.getEmail(), account.getPlan(), account.getAuthProvider());
    }
}
