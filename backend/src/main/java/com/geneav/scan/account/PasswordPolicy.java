package com.geneav.scan.account;

import com.geneav.scan.web.ScanException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Strength rules for dashboard passwords. Length does most of the work; the
 * character-class rule is deliberately "3 of 4" rather than "all 4" so a long
 * passphrase stays acceptable without inviting the {@code Password1!} pattern
 * that strict composition rules tend to produce.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;

    /**
     * BCrypt hashes only the first 72 bytes, so anything beyond that is silently
     * ignored when verifying. Rejecting it outright beats accepting a password
     * whose tail does not actually count.
     */
    static final int MAX_LENGTH = 72;

    /** Rejected outright regardless of the rules below — the first thing an attacker tries. */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012", "password1234", "passwordpassword", "qwertyqwerty",
            "111111111111", "123123123123", "letmeinletmein", "iloveyouiloveyou",
            "administrator", "qwerty123456", "1qaz2wsx3edc", "welcome123456");

    /**
     * @throws ScanException 400 listing every rule the password fails
     */
    public void validate(String password, String email) {
        List<String> problems = new ArrayList<>();

        if (password == null || password.isBlank()) {
            throw new ScanException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("be at most " + MAX_LENGTH + " characters");
        }
        if (characterClasses(password) < 3) {
            problems.add("mix at least three of: lowercase, uppercase, digits, symbols");
        }

        String normalized = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalized)) {
            problems.add("not be a commonly used password");
        }
        if (containsEmailLocalPart(normalized, email)) {
            problems.add("not contain your email address");
        }

        if (!problems.isEmpty()) {
            throw new ScanException(HttpStatus.BAD_REQUEST, "Password must " + String.join(", ", problems) + ".");
        }
    }

    private static int characterClasses(String password) {
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
    }

    /** Blocks {@code alice@corp.com} / {@code Alice12345!} — trivially guessable from the login. */
    private static boolean containsEmailLocalPart(String normalizedPassword, String email) {
        if (email == null) {
            return false;
        }
        String local = email.toLowerCase(Locale.ROOT).split("@", 2)[0].trim();
        return local.length() >= 3 && normalizedPassword.contains(local);
    }
}
