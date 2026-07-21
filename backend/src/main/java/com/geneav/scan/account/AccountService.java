package com.geneav.scan.account;

import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.web.ScanException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Self-serve account provisioning, password auth, and API-key management. */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final ApiKeyRepository apiKeys;
    private final ApiKeyService apiKeyService;
    private final PlanCatalog plans;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public AccountService(AccountRepository accounts, ApiKeyRepository apiKeys,
                          ApiKeyService apiKeyService, PlanCatalog plans, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy) {
        this.accounts = accounts;
        this.apiKeys = apiKeys;
        this.apiKeyService = apiKeyService;
        this.plans = plans;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /** Creates a password-backed account for the dashboard (no API key issued yet). */
    @Transactional
    public Account signupWithPassword(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        passwordPolicy.validate(rawPassword, email);
        if (accounts.existsByEmail(email)) {
            throw new ScanException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        Account account = new Account(UUID.randomUUID(), email, plans.defaultPlanKey(), "active", Instant.now());
        account.setAuthProvider("password");
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        return accounts.save(account);
    }

    /** Verifies email + password, returning the account or throwing a generic 401. */
    @Transactional(readOnly = true)
    public Account authenticatePassword(String rawEmail, String rawPassword) {
        ScanException invalid = new ScanException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        if (rawEmail == null || rawPassword == null) {
            throw invalid;
        }
        Account account = accounts.findByEmail(normalizeEmail(rawEmail)).orElseThrow(() -> invalid);
        if (account.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw invalid;
        }
        if (!account.isActive()) {
            throw new ScanException(HttpStatus.FORBIDDEN, "This account is not active.");
        }
        return account;
    }

    public record SignupResult(Account account, ApiKeyService.IssuedKey firstKey) {
    }

    /**
     * Creates an account on the default plan and issues its first API key. No
     * password is set: the account is identified by its key until the holder
     * claims a password through the reset flow.
     */
    @Transactional
    public SignupResult signup(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (accounts.existsByEmail(email)) {
            throw new ScanException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        Account account = new Account(UUID.randomUUID(), email, plans.defaultPlanKey(), "active", Instant.now());
        // Not "password" (the field default): that would claim a password this
        // path never sets, leaving a row that can neither sign in nor be told
        // apart from a real password account.
        account.setAuthProvider("apikey");
        accounts.save(account);
        ApiKeyService.IssuedKey key = apiKeyService.issue(account.getId(), "default");
        return new SignupResult(account, key);
    }

    @Transactional
    public ApiKeyService.IssuedKey createKey(UUID accountId, String name) {
        return apiKeyService.issue(accountId, name == null || name.isBlank() ? "key" : name.trim());
    }

    @Transactional(readOnly = true)
    public List<ApiKey> listKeys(UUID accountId) {
        return apiKeys.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /** Revokes a key, refusing if it belongs to a different account. */
    @Transactional
    public void revokeKey(UUID accountId, UUID keyId) {
        ApiKey key = apiKeys.findById(keyId)
                .filter(k -> k.getAccountId().equals(accountId))
                .orElseThrow(() -> new ScanException(HttpStatus.NOT_FOUND, "API key not found."));
        if (key.isActive()) {
            key.revoke(Instant.now());
            apiKeys.save(key);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@") || email.length() > 320) {
            throw new ScanException(HttpStatus.BAD_REQUEST, "A valid email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
