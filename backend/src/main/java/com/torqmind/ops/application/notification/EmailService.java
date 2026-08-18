package com.torqmind.ops.application.notification;

import com.torqmind.ops.application.notification.EmailSettingsService.SmtpRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/** Envio best-effort usando o perfil resolvido (banco ou ambiente). Sem SMTP, apenas registra. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSettingsService settingsService;

    public EmailService(EmailSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public boolean isEnabled() {
        SmtpRuntime rt = settingsService.resolveRuntime();
        return rt.enabled() && !rt.host().isBlank();
    }

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        SmtpRuntime rt = settingsService.resolveRuntime();
        if (!rt.enabled() || rt.host().isBlank()) {
            log.info("SMTP nao configurado; e-mail para {} ignorado (assunto: {}).", to, subject);
            return;
        }
        try {
            dispatch(rt, to, subject, body);
        } catch (Exception ex) {
            log.warn("Falha ao enviar e-mail para {}: {}", to, ex.getMessage());
        }
    }

    /** Envia um e-mail de teste e propaga o erro (para a tela do admin). */
    public void sendTest(String to) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Informe um destinatario para o teste.");
        }
        SmtpRuntime rt = settingsService.resolveRuntime();
        if (!rt.enabled() || rt.host().isBlank()) {
            throw new IllegalArgumentException("SMTP nao esta habilitado/configurado.");
        }
        try {
            dispatch(rt, to, "Teste - TorqMind Ops",
                    "Este e um e-mail de teste do TorqMind Ops. Se voce recebeu, o envio esta funcionando.");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Falha ao enviar: " + ex.getMessage());
        }
    }

    private void dispatch(SmtpRuntime rt, String to, String subject, String body) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(rt.host());
        sender.setPort(rt.port());
        boolean auth = !rt.username().isBlank();
        if (auth) {
            sender.setUsername(rt.username());
            sender.setPassword(rt.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(rt.useTls()));
        if (rt.useSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender(rt));
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    private static String sender(SmtpRuntime rt) {
        String email = rt.fromEmail() == null || rt.fromEmail().isBlank() ? rt.username() : rt.fromEmail();
        String name = rt.fromName();
        return (name == null || name.isBlank()) ? email : name + " <" + email + ">";
    }
}
