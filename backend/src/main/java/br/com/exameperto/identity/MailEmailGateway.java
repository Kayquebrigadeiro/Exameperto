package br.com.exameperto.identity;

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
    MailEmailGateway(ObjectProvider<JavaMailSender> provider,
                     @Value("${email.from:}") String from,
                     @Value("${email.enabled:false}") boolean enabled) {
        this.provider = provider; this.from = from; this.enabled = enabled;
    }
    @Override public boolean configured() { return enabled && !from.isBlank() && provider.getIfAvailable() != null; }
    @Override public void send(String recipient, String subject, String text) {
        if (!configured()) throw new IllegalStateException("SMTP indisponível.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(text);
        provider.getObject().send(message);
    }
}
