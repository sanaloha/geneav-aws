package com.geneav.scan.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends transactional mail. Delivery is best-effort and off the request thread:
 * a signup must not fail, or even slow down, because SMTP is down or unconfigured.
 * With {@code geneav.mail.enabled=false} (the default, and the case for local dev)
 * messages are logged rather than sent.
 */
@Service
@EnableConfigurationProperties(MailProperties.class)
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailProperties props;
    private final ObjectProvider<JavaMailSender> senderProvider;

    public MailService(MailProperties props, ObjectProvider<JavaMailSender> senderProvider) {
        this.props = props;
        this.senderProvider = senderProvider;
    }

    /** Confirms to a new user that their account exists, and where to sign in. */
    @Async
    public void sendSignupAcknowledgement(String email) {
        String body = """
                Welcome to geneav.

                Your account (%s) is ready. You can sign in and create an API key here:
                %s

                From the dashboard you can issue and revoke API keys and watch your
                monthly scan usage against your plan quota.

                If you did not create this account, please reply to this email and we
                will remove it.

                — the geneav team
                """.formatted(email, props.getLoginUrl());

        send(email, "Welcome to geneav — your account is ready", body);
    }

    /**
     * Sends the password-reset link. The token is URL-encoded because it is
     * base64url — safe in practice, but the encoding costs nothing and stops a
     * future token format from silently breaking the link.
     */
    @Async
    public void sendPasswordReset(String email, String token, long expiryMinutes) {
        String link = props.getResetUrl() + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String body = """
                We received a request to reset the password for your geneav account (%s).

                Set a new password here:
                %s

                This link expires in %d minutes and can only be used once.

                If you did not request this, you can ignore this email — your
                password has not been changed.

                — the geneav team
                """.formatted(email, link, expiryMinutes);

        send(email, "Reset your geneav password", body);
    }

    /** Confirms a completed password change, so an unexpected one is noticed. */
    @Async
    public void sendPasswordChanged(String email) {
        String body = """
                The password for your geneav account (%s) was just changed.

                You can sign in here:
                %s

                If this wasn't you, reset your password immediately and contact us
                by replying to this email — someone else may have access to your
                account.

                — the geneav team
                """.formatted(email, props.getLoginUrl());

        send(email, "Your geneav password was changed", body);
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (!props.isEnabled() || sender == null) {
            log.info("Mail disabled — not sending \"{}\" to {}", subject, to);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(props.getFrom(), props.getFromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            log.info("Sent \"{}\" to {}", subject, to);
        } catch (UnsupportedEncodingException | RuntimeException | jakarta.mail.MessagingException e) {
            // Deliberately swallowed: the caller's operation already succeeded.
            log.warn("Could not send \"{}\" to {}: {}", subject, to, e.toString());
        }
    }
}
