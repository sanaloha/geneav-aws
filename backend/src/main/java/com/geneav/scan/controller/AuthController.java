package com.geneav.scan.controller;

import com.geneav.scan.account.Account;
import com.geneav.scan.account.AccountService;
import com.geneav.scan.account.CurrentAccount;
import com.geneav.scan.account.PasswordResetService;
import com.geneav.scan.account.SessionService;
import com.geneav.scan.account.SignupAttribution;
import com.geneav.scan.dto.AccountDtos.AttributionPayload;
import com.geneav.scan.dto.AccountDtos.ForgotPasswordRequest;
import com.geneav.scan.dto.AccountDtos.LoginRequest;
import com.geneav.scan.dto.AccountDtos.MeResponse;
import com.geneav.scan.dto.AccountDtos.ResetPasswordRequest;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * Dashboard authentication: email/password signup, login, logout, and the current
 * session. On success a Spring Security context is persisted to an HttpOnly cookie
 * session; the dashboard then calls the management endpoints with that cookie.
 * "Sign in with Microsoft" (Entra ID) runs through Spring's OAuth2 login and
 * lands in the same session shape — see {@link com.geneav.scan.account.MicrosoftOidcConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Dashboard sign up / sign in")
public class AuthController {

    private final AccountService accounts;
    private final CurrentAccount currentAccount;
    private final MailService mail;
    private final PasswordResetService passwordResets;
    private final SessionService sessions;

    public AuthController(AccountService accounts, CurrentAccount currentAccount, MailService mail,
                          PasswordResetService passwordResets, SessionService sessions) {
        this.accounts = accounts;
        this.currentAccount = currentAccount;
        this.mail = mail;
        this.passwordResets = passwordResets;
        this.sessions = sessions;
    }

    @Operation(summary = "Sign up with a password", description = "Creates a password account and starts a session.")
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SignupPasswordRequest req,
                             HttpServletRequest request, HttpServletResponse response) {
        Account account = accounts.signupWithPassword(req.email(), req.password(), toAttribution(req.attribution()));
        sessions.establish(account, request, response);
        // Best-effort and asynchronous — a mail failure must not fail the signup.
        mail.sendSignupAcknowledgement(account.getEmail());
        return toMe(account);
    }

    @Operation(summary = "Sign in", description = "Verifies email + password and starts a session.")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MeResponse login(@Valid @RequestBody LoginRequest req,
                            HttpServletRequest request, HttpServletResponse response) {
        Account account = accounts.authenticatePassword(req.email(), req.password());
        sessions.establish(account, request, response);
        return toMe(account);
    }

    @Operation(summary = "Request a password reset",
            description = "Emails a single-use reset link that expires in 10 minutes. Always returns 202, "
                    + "whether or not the address has an account, so it cannot be used to discover customers.")
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResets.request(req.email()).ifPresent(issued -> mail.sendPasswordReset(
                issued.account().getEmail(), issued.token(), passwordResets.tokenTtl().toMinutes()));
    }

    @Operation(summary = "Set a new password",
            description = "Redeems a reset token and replaces the password. The token is single-use.")
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest request) {
        Account account = passwordResets.reset(req.token(), req.password());
        mail.sendPasswordChanged(account.getEmail());
        // Drop any session on this browser so the user signs in with the new password.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
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

    /**
     * Maps the optional attribution block off the request body. Null in means
     * null out — a signup that carried no campaign tags is stored as having none
     * rather than as a row of empty strings.
     */
    private SignupAttribution toAttribution(AttributionPayload p) {
        if (p == null) {
            return null;
        }
        return SignupAttribution.of(p.utmSource(), p.utmMedium(), p.utmCampaign(),
                p.utmTerm(), p.utmContent(), p.referrer(), p.landingPath());
    }

    private static MeResponse toMe(Account account) {
        return new MeResponse(account.getEmail(), account.getPlan(), account.getAuthProvider());
    }
}
