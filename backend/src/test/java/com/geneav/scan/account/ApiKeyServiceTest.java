package com.geneav.scan.account;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private final ApiKeyRepository apiKeys = mock(ApiKeyRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ApiKeyService service = new ApiKeyService(apiKeys, accounts);

    @Test
    void issueReturnsPrefixedPlaintextAndStoresOnlyItsHash() {
        UUID accountId = UUID.randomUUID();

        ApiKeyService.IssuedKey issued = service.issue(accountId, "default");

        assertThat(issued.plaintext()).startsWith("gav_live_");
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeys).save(saved.capture());
        ApiKey key = saved.getValue();
        assertThat(key.getKeyHash()).isEqualTo(ApiKeyService.sha256Hex(issued.plaintext()));
        assertThat(key.getKeyHash()).doesNotContain(issued.plaintext());
        assertThat(key.getKeyPrefix()).startsWith("gav_live_");
        assertThat(key.getLastFour()).hasSize(4);
    }

    @Test
    void authenticateResolvesActiveKeyToActiveAccount() {
        String plaintext = "gav_live_abcdefghijklmnopqrstuvwx";
        UUID accountId = UUID.randomUUID();
        ApiKey key = new ApiKey(UUID.randomUUID(), accountId, "k", ApiKeyService.sha256Hex(plaintext),
                "gav_live_abcd", "uvwx", "active", Instant.now());
        Account account = new Account(accountId, "a@b.com", "free", "active", Instant.now());
        when(apiKeys.findByKeyHash(ApiKeyService.sha256Hex(plaintext))).thenReturn(Optional.of(key));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        Optional<AuthenticatedClient> client = service.authenticate(plaintext);

        assertThat(client).isPresent();
        assertThat(client.get().account().getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void authenticateRejectsMissingOrWrongPrefixWithoutHittingTheDb() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("Bearer nope")).isEmpty();
        assertThat(service.authenticate("sk-someoneelse")).isEmpty();
        verify(apiKeys, org.mockito.Mockito.never()).findByKeyHash(any());
    }

    @Test
    void authenticateRejectsRevokedKey() {
        String plaintext = "gav_live_revoked000000000000000000";
        ApiKey key = new ApiKey(UUID.randomUUID(), UUID.randomUUID(), "k", ApiKeyService.sha256Hex(plaintext),
                "gav_live_revo", "0000", "active", Instant.now());
        key.revoke(Instant.now());
        when(apiKeys.findByKeyHash(any())).thenReturn(Optional.of(key));

        assertThat(service.authenticate(plaintext)).isEmpty();
    }

    @Test
    void authenticateRejectsWhenAccountInactive() {
        String plaintext = "gav_live_suspended0000000000000000";
        UUID accountId = UUID.randomUUID();
        ApiKey key = new ApiKey(UUID.randomUUID(), accountId, "k", ApiKeyService.sha256Hex(plaintext),
                "gav_live_susp", "0000", "active", Instant.now());
        Account account = new Account(accountId, "a@b.com", "free", "suspended", Instant.now());
        when(apiKeys.findByKeyHash(any())).thenReturn(Optional.of(key));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        assertThat(service.authenticate(plaintext)).isEmpty();
    }
}
