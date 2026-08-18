package com.torqmind.ops.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Envio de e-mail best-effort: se o SMTP nao estiver configurado, apenas registra e ignora. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:nao-responder@torqmind.com.br}") String from
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    public boolean isEnabled() {
        return mailSenderProvider.getIfAvailable() != null;
    }

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.info("SMTP nao configurado; e-mail para {} ignorado (assunto: {}).", to, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception ex) {
            log.warn("Falha ao enviar e-mail para {}: {}", to, ex.getMessage());
        }
    }
}
