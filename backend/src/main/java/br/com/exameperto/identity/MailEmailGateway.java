package br.com.exameperto.identity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class MailEmailGateway implements EmailGateway {
    private static final Logger log = LoggerFactory.getLogger(MailEmailGateway.class);
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
        log.info("email_gateway_config enabled={} senderConfigured={} allowlistEntries={} senderAllowlisted={} transportBeanAvailable={}",
            enabled, !from.isBlank(), this.allowedRecipients.size(), this.allowedRecipients.contains(normalize(from)), provider.getIfAvailable() != null);
    }
    @Override public boolean configured() {
        return enabled && !from.isBlank() && !allowedRecipients.isEmpty() && provider.getIfAvailable() != null;
    }
    @Override public void send(String recipient, String subject, String text) {
        try {
            recovery.requireExternalEffectsAllowed();
        } catch (RuntimeException ex) {
            log.warn("email_delivery_blocked category=EXTERNAL_EFFECTS_BLOCKED exceptionType={} rootType={}", type(ex), type(root(ex)));
            throw new EmailDeliveryFailure("EXTERNAL_EFFECTS_BLOCKED", ex);
        }
        if (!configured()) throw new EmailDeliveryFailure("EMAIL_CONFIGURATION_MISSING", null);
        if (!allowedRecipients.contains(normalize(recipient)))
            throw new IllegalArgumentException("Destinatário não autorizado para envio.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(text);
        try {
            provider.getObject().send(message);
        } catch (RuntimeException ex) {
            String category = classify(ex);
            log.warn("email_delivery_failed category={} exceptionType={} rootType={}", category, type(ex), type(root(ex)));
            throw new EmailDeliveryFailure(category, ex);
        }
    }
    private static String classify(Throwable ex) {
        String names = type(ex) + ":" + type(root(ex));
        if (names.contains("Authentication")) return "SMTP_AUTHENTICATION";
        if (names.contains("Connect") || names.contains("SocketTimeout") || names.contains("UnknownHost")) return "SMTP_CONNECTION";
        if (names.contains("MailSend")) return "SMTP_SEND";
        return "SMTP_TRANSPORT";
    }
    private static Throwable root(Throwable ex) { Throwable current = ex; while (current.getCause() != null && current.getCause() != current) current = current.getCause(); return current; }
    private static String type(Throwable ex) { return ex == null ? "none" : ex.getClass().getSimpleName(); }
    private static String normalize(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }
    static final class EmailDeliveryFailure extends RuntimeException {
        private final String category;
        EmailDeliveryFailure(String category, Throwable cause) { super(category, cause); this.category = category; }
        String category() { return category; }
    }
}
