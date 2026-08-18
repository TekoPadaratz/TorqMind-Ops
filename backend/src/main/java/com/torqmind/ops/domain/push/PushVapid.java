package com.torqmind.ops.domain.push;

import com.torqmind.ops.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Singleton (id=1) com o par de chaves VAPID; a chave privada e cifrada em repouso. */
@Entity
@Table(name = "push_vapid")
public class PushVapid {

    @Id
    private Integer id = 1;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "private_key", nullable = false)
    private String privateKey;

    @Column(nullable = false)
    private String subject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
