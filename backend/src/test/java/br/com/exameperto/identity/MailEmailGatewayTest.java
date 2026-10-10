package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailEmailGatewayTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final RecoveryGuard recovery = mock(RecoveryGuard.class);

    @Test void refusesToEnableWithoutAnExplicitRecipientAllowlist() {
        when(provider.getIfAvailable()).thenReturn(sender);

        var gateway = new MailEmailGateway(provider, "sender@example.test", true, "", recovery);

        assertThat(gateway.configured()).isFalse();
    }

    @Test void sendsOnlyToAnAllowlistedRecipient() {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        var gateway = new MailEmailGateway(provider, "sender@example.test", true,
            " allowed@example.test ", recovery);

        gateway.send("Allowed@Example.Test", "Assunto", "Conteúdo");

        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test void blocksEveryOtherRecipientBeforeCallingTheSmtpTransport() {
        when(provider.getIfAvailable()).thenReturn(sender);
        var gateway = new MailEmailGateway(provider, "sender@example.test", true,
            "allowed@example.test", recovery);

        assertThatThrownBy(() -> gateway.send("other@example.test", "Assunto", "Conteúdo"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Destinatário não autorizado para envio.");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }
}
