package com.geneav.scan.account;

import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.plan.PlanProperties;
import com.geneav.scan.web.ScanException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ApiKeyRepository apiKeys = mock(ApiKeyRepository.class);
    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PlanCatalog plans = catalog();
    private final AccountService service =
            new AccountService(accounts, apiKeys, apiKeyService, plans, encoder, new PasswordPolicy());

    private PlanCatalog catalog() {
        PlanProperties props = new PlanProperties();
        props.setDefaultPlan("free");
        props.getDefinitions().put("free", new PlanProperties.Plan());
        return new PlanCatalog(props);
    }

    @Test
    void signupHashesPasswordAndNeverStoresPlaintext() {
        when(accounts.existsByEmail("a@b.com")).thenReturn(false);
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));

        service.signupWithPassword("A@B.com", "Sup3rsecret!pass");

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        Account account = saved.getValue();
        assertThat(account.getEmail()).isEqualTo("a@b.com"); // normalized
        assertThat(account.getAuthProvider()).isEqualTo("password");
        assertThat(account.getPasswordHash()).isNotNull().isNotEqualTo("Sup3rsecret!pass");
        assertThat(encoder.matches("Sup3rsecret!pass", account.getPasswordHash())).isTrue();
    }

    @Test
    void signupRejectsShortPassword() {
        assertThatThrownBy(() -> service.signupWithPassword("a@b.com", "short"))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(accounts.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> service.signupWithPassword("a@b.com", "Sup3rsecret!pass"))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void authenticateSucceedsWithCorrectPassword() {
        Account account = new Account(UUID.randomUUID(), "a@b.com", "free", "active", Instant.now());
        account.setPasswordHash(encoder.encode("Sup3rsecret!pass"));
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        assertThat(service.authenticatePassword("a@b.com", "Sup3rsecret!pass")).isSameAs(account);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        Account account = new Account(UUID.randomUUID(), "a@b.com", "free", "active", Instant.now());
        account.setPasswordHash(encoder.encode("Sup3rsecret!pass"));
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.authenticatePassword("a@b.com", "wrongpass"))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticateRejectsUnknownEmailWithSameError() {
        when(accounts.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticatePassword("nobody@b.com", "Whatever1!pass"))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticateRejectsPasswordlessAccount() {
        Account oauthOnly = new Account(UUID.randomUUID(), "a@b.com", "free", "active", Instant.now());
        // no password hash set (e.g. an API-only or future OAuth account)
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(oauthOnly));

        assertThatThrownBy(() -> service.authenticatePassword("a@b.com", "Whatever1!pass"))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
