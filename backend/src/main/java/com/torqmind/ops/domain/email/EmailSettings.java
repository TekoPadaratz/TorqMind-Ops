package com.torqmind.ops.domain.email;

import com.torqmind.ops.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Perfil singleton (id=1) de SMTP/remetente, editável pelo MASTER. Senha cifrada em repouso. */
@Entity
@Table(name = "email_settings")
public class EmailSettings {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "host")
    private String host;

    @Column(name = "port", nullable = false)
    private int port = 587;

    @Column(name = "username")
    private String username;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "password_encrypted")
    private String password;

    @Column(name = "use_tls", nullable = false)
    private boolean useTls = true;

    @Column(name = "use_ssl", nullable = false)
    private boolean useSsl = false;

    @Column(name = "from_email")
    private String fromEmail;

    @Column(name = "from_name", nullable = false)
    private String fromName = "TorqMind Ops";

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
