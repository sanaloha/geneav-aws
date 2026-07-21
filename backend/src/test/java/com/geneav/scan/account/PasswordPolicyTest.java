package com.geneav.scan.account;

import com.geneav.scan.web.ScanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "Sup3rsecret!pass",         // all four classes
            "Correct horse battery 9",  // long passphrase: upper + lower + digit + space
            "Tr0ub4dour&3xtra",
            "MixedCaseAndDigits1234"    // no symbol, but three classes is enough
    })
    void acceptsStrongPasswords(String password) {
        assertThatCode(() -> policy.validate(password, "user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> policy.validate("Ab1!short", "user@example.com"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    void rejectsLongerThanBcryptWillHash() {
        assertThatThrownBy(() -> policy.validate("Aa1!".repeat(20), "user@example.com"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("at most 72 characters");
    }

    @Test
    void rejectsTooFewCharacterClasses() {
        assertThatThrownBy(() -> policy.validate("alllowercaseletters", "user@example.com"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("three of");
    }

    @Test
    void rejectsCommonPassword() {
        assertThatThrownBy(() -> policy.validate("Password1234", "user@example.com"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("commonly used");
    }

    @Test
    void rejectsPasswordContainingEmailLocalPart() {
        assertThatThrownBy(() -> policy.validate("Alice12345!xy", "alice@example.com"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("email address");
    }

    @Test
    void shortEmailLocalPartDoesNotBlockEverything() {
        // A two-character local part would otherwise match almost any password.
        assertThatCode(() -> policy.validate("Ab1!strongpass", "ab@example.com")).doesNotThrowAnyException();
    }

    @Test
    void reportsEveryViolationAtOnce() {
        assertThatThrownBy(() -> policy.validate("abc", "user@example.com"))
                .isInstanceOf(ScanException.class)
                .satisfies(e -> {
                    assertThat(((ScanException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("at least 12 characters").contains("three of");
                });
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> policy.validate("   ", "user@example.com"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("required");
    }
}
