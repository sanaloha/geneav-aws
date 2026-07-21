package com.geneav.scan.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceTest {

    private final JavaMailSender sender = spy(new JavaMailSenderImpl());

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender value) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private MailProperties props(boolean enabled) {
        MailProperties props = new MailProperties();
        props.setEnabled(enabled);
        return props;
    }

    @Test
    void doesNotSendWhenDisabled() {
        new MailService(props(false), provider(sender)).sendSignupAcknowledgement("a@b.com");
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void doesNotSendWhenNoSenderIsConfigured() {
        assertThatCode(() -> new MailService(props(true), provider(null)).sendSignupAcknowledgement("a@b.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendsWhenEnabled() {
        doThrow(new MailSendException("no smtp in tests")).when(sender).send(any(MimeMessage.class));

        // The throw above proves we reached the transport; swallowing it is the point.
        assertThatCode(() -> new MailService(props(true), provider(sender)).sendSignupAcknowledgement("a@b.com"))
                .doesNotThrowAnyException();
        verify(sender).send(any(MimeMessage.class));
    }
}
