package com.geneav.scan.controller;

import com.geneav.scan.account.Account;
import com.geneav.scan.account.ApiKey;
import com.geneav.scan.account.ApiKeyService;
import com.geneav.scan.account.AccountService;
import com.geneav.scan.account.CurrentAccount;
import com.geneav.scan.dto.AccountDtos.CreateKeyRequest;
import com.geneav.scan.dto.AccountDtos.CreatedKeyResponse;
import com.geneav.scan.dto.AccountDtos.KeySummary;
import com.geneav.scan.dto.AccountDtos.SignupRequest;
import com.geneav.scan.dto.AccountDtos.SignupResponse;
import com.geneav.scan.dto.AccountDtos.UsageResponse;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.usage.UsageService;
import com.geneav.scan.web.ScanException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-serve account and API-key management for the commercial API. Signup is
 * public; key and usage endpoints require a valid API key.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Account", description = "Sign up and manage API keys and usage")
public class AccountController {

    private final AccountService accounts;
    private final UsageService usage;
    private final PlanCatalog plans;
    private final CurrentAccount currentAccount;

    public AccountController(AccountService accounts, UsageService usage, PlanCatalog plans,
                            CurrentAccount currentAccount) {
        this.accounts = accounts;
        this.usage = usage;
        this.plans = plans;
        this.currentAccount = currentAccount;
    }

    @Operation(summary = "Sign up", description = "Creates an account and returns its first API key (shown once).")
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        AccountService.SignupResult result = accounts.signup(request.email());
        Account account = result.account();
        ApiKeyService.IssuedKey key = result.firstKey();
        return new SignupResponse(account.getId(), account.getEmail(), account.getPlan(),
                key.plaintext(), key.record().getKeyPrefix());
    }

    @Operation(summary = "List API keys", description = "Lists the caller's API keys (secrets are never returned).")
    @GetMapping(value = "/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KeySummary> listKeys(HttpServletRequest request) {
        UUID accountId = requireAccount(request).getId();
        return accounts.listKeys(accountId).stream().map(AccountController::toSummary).toList();
    }

    @Operation(summary = "Create API key", description = "Issues an additional API key (plaintext shown once).")
    @PostMapping(value = "/keys", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedKeyResponse createKey(@RequestBody(required = false) CreateKeyRequest body, HttpServletRequest request) {
        UUID accountId = requireAccount(request).getId();
        ApiKeyService.IssuedKey key = accounts.createKey(accountId, body == null ? null : body.name());
        return new CreatedKeyResponse(key.record().getId(), key.record().getName(),
                key.plaintext(), key.record().getKeyPrefix());
    }

    @Operation(summary = "Revoke API key", description = "Permanently revokes one of the caller's API keys.")
    @DeleteMapping(value = "/keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(@PathVariable("id") UUID id, HttpServletRequest request) {
        UUID accountId = requireAccount(request).getId();
        accounts.revokeKey(accountId, id);
    }

    @Operation(summary = "Usage this month", description = "Current-month scan usage against the plan quota.")
    @GetMapping(value = "/usage", produces = MediaType.APPLICATION_JSON_VALUE)
    public UsageResponse usage(HttpServletRequest request) {
        Account account = requireAccount(request);
        long quota = plans.resolve(account.getPlan()).getMonthlyScanQuota();
        long used = usage.currentCount(account.getId(), "scan");
        return new UsageResponse(account.getPlan(), usage.currentPeriod(), used, quota, Math.max(0, quota - used));
    }

    private Account requireAccount(HttpServletRequest request) {
        return currentAccount.resolve(request).orElseThrow(() -> new ScanException(HttpStatus.UNAUTHORIZED,
                "Sign in or send an 'Authorization: Bearer gav_live_...' API key."));
    }

    private static KeySummary toSummary(ApiKey key) {
        return new KeySummary(key.getId(), key.getName(), key.getKeyPrefix(), key.getLastFour(),
                key.getStatus(), key.getCreatedAt(), key.getLastUsedAt());
    }
}
