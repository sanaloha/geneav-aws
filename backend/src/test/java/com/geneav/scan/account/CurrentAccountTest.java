package com.geneav.scan.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentAccountTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CurrentAccount currentAccount = new CurrentAccount(accounts);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID accountId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(accountId.toString(), null, List.of()));
    }

    @Test
    void resolvesFromSession() {
        UUID id = UUID.randomUUID();
        Account account = new Account(id, "a@b.com", "free", "active", Instant.now());
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        authenticateAs(id);

        assertThat(currentAccount.resolve(new MockHttpServletRequest())).contains(account);
    }

    @Test
    void fallsBackToApiKeyWhenNoSession() {
        UUID id = UUID.randomUUID();
        Account account = new Account(id, "a@b.com", "free", "active", Instant.now());
        ApiKey key = new ApiKey(UUID.randomUUID(), id, "k", "h", "gav_live_abcd", "wxyz", "active", Instant.now());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthenticatedClient.ATTRIBUTE, new AuthenticatedClient(account, key));

        assertThat(currentAccount.resolve(request)).contains(account);
    }

    @Test
    void emptyWhenNeitherPresent() {
        assertThat(currentAccount.resolve(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void sessionForInactiveAccountFallsThroughToEmpty() {
        UUID id = UUID.randomUUID();
        Account suspended = new Account(id, "a@b.com", "free", "suspended", Instant.now());
        when(accounts.findById(id)).thenReturn(Optional.of(suspended));
        authenticateAs(id);

        assertThat(currentAccount.resolve(new MockHttpServletRequest())).isEmpty();
    }
}
