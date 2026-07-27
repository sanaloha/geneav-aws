package com.geneav.scan.account;

import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.web.ScanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds or creates the account behind a completed Microsoft (Entra ID) sign-in.
 *
 * <p>The account-linking rule is deliberate, not defaulted: a Microsoft
 * identity may sign in to an <b>existing</b> geneav account matched by email
 * only when the token comes from a work/school tenant, where the address is
 * tenant-verified. Consumer (MSA) accounts carry an unverified email claim —
 * linking on it would let anyone who registers {@code victim@corp.com} as an
 * MSA alias take over the victim's geneav account — so an email collision from
 * the MSA tenant is refused with a "sign in with your password" message.
 */
@Service
public class FederatedSignInService {

    private static final Logger log = LoggerFactory.getLogger(FederatedSignInService.class);

    static final String PROVIDER_MICROSOFT = "microsoft";

    /** The fixed tenant id every consumer (MSA) account authenticates under. */
    static final String MSA_TENANT_ID = "9188040d-6c67-4c5b-b112-36a304b66dad";

    private final AccountRepository accounts;
    private final PlanCatalog plans;

    public FederatedSignInService(AccountRepository accounts, PlanCatalog plans) {
        this.accounts = accounts;
        this.plans = plans;
    }

    /**
     * Resolves a Microsoft sign-in ({@code tid} + {@code oid} identify the user
     * globally) to an account, creating one on the default plan if needed.
     *
     * @throws ScanException 409 for an MSA email collision, 400 for an unusable
     *                       token (no email), 403 for a deactivated account.
     */
    @Transactional
    public Account signIn(String tenantId, String objectId, String rawEmail) {
        if (tenantId == null || objectId == null) {
            throw new ScanException(HttpStatus.BAD_REQUEST, "Microsoft sign-in returned an incomplete identity.");
        }
        // oid alone is only unique within a tenant; the pair is global.
        String subject = tenantId.toLowerCase(Locale.ROOT) + ":" + objectId.toLowerCase(Locale.ROOT);

        Optional<Account> bySubject = accounts.findByAuthProviderAndProviderSubject(PROVIDER_MICROSOFT, subject);
        if (bySubject.isPresent()) {
            return requireActive(bySubject.get());
        }

        String email = normalizeEmail(rawEmail);
        Optional<Account> byEmail = accounts.findByEmail(email);
        if (byEmail.isEmpty()) {
            Account account = new Account(UUID.randomUUID(), email, plans.defaultPlanKey(), "active", Instant.now());
            account.setAuthProvider(PROVIDER_MICROSOFT);
            account.setProviderSubject(subject);
            log.info("Created account {} via Microsoft sign-in (tenant {})", account.getId(), tenantId);
            return accounts.save(account);
        }

        Account existing = byEmail.get();
        if (MSA_TENANT_ID.equalsIgnoreCase(tenantId)) {
            // Unverified email claim: never link, never take over.
            throw new ScanException(HttpStatus.CONFLICT,
                    "An account with this email already exists. Sign in with your password instead.");
        }
        // Work/school tenant: the email is tenant-verified, so signing this
        // identity into the matching account is safe. A password account keeps
        // its provider (and its password); only a keyless account is converted.
        requireActive(existing);
        if ("apikey".equals(existing.getAuthProvider())) {
            existing.setAuthProvider(PROVIDER_MICROSOFT);
            existing.setProviderSubject(subject);
            accounts.save(existing);
        }
        log.info("Microsoft identity {} signed in to existing account {} by verified email", subject,
                existing.getId());
        return existing;
    }

    private Account requireActive(Account account) {
        if (!account.isActive()) {
            throw new ScanException(HttpStatus.FORBIDDEN, "This account is not active.");
        }
        return account;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@") || email.length() > 320) {
            throw new ScanException(HttpStatus.BAD_REQUEST,
                    "Microsoft sign-in did not provide a usable email address.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
