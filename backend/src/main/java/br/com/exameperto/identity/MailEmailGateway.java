package br.com.exameperto.identity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
final class MailEmailGateway implements EmailGateway {
    private final ObjectProvider<JavaMailSender> provider;
    private final String from;
    private final boolean enabled;
    private final Set<String> allowedRecipients;
    private final RecoveryGuard recovery;
    MailEmailGateway(ObjectProvider<JavaMailSender> provider,
                     @Value("${email.from:}") String from,
                     @Value("${email.enabled:false}") boolean enabled,
                     @Value("${email.allowed-recipients:}") String allowedRecipients,
                     RecoveryGuard recovery) {
        this.provider = provider; this.from = from; this.enabled = enabled; this.recovery=recovery;
        this.allowedRecipients = Arrays.stream(allowedRecipients.split(","))
            .map(MailEmailGateway::normalize)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
    @Override public boolean configured() {
        return enabled && !from.isBlank() && !allowedRecipients.isEmpty() && provider.getIfAvailable() != null;
    }
    @Override public void send(String recipient, String subject, String text) {
        recovery.requireExternalEffectsAllowed();
        if (!configured()) throw new IllegalStateException("SMTP indisponível.");
        if (!allowedRecipients.contains(normalize(recipient)))
            throw new IllegalArgumentException("Destinatário não autorizado para envio.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(text);
        provider.getObject().send(message);
    }
    private static String normalize(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }
}
