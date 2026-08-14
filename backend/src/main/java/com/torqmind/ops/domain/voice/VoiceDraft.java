package com.torqmind.ops.domain.voice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_drafts")
public class VoiceDraft {

    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "branch_id")
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private VoiceDraftStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 40)
    private VoiceAction action;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "1";

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "intent_json", nullable = false, columnDefinition = "TEXT")
    private String intentJson;

    @Column(name = "resolved_json", columnDefinition = "TEXT")
    private String resolvedJson;

    @Column(name = "preview_text", columnDefinition = "TEXT")
    private String previewText;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "result_entity_type", length = 40)
    private String resultEntityType;

    @Column(name = "result_entity_id")
    private Long resultEntityId;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public VoiceDraftStatus getStatus() { return status; }
    public void setStatus(VoiceDraftStatus status) { this.status = status; }
    public VoiceAction getAction() { return action; }
    public void setAction(VoiceAction action) { this.action = action; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getIntentJson() { return intentJson; }
    public void setIntentJson(String intentJson) { this.intentJson = intentJson; }
    public String getResolvedJson() { return resolvedJson; }
    public void setResolvedJson(String resolvedJson) { this.resolvedJson = resolvedJson; }
    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getResultEntityType() { return resultEntityType; }
    public void setResultEntityType(String resultEntityType) { this.resultEntityType = resultEntityType; }
    public Long getResultEntityId() { return resultEntityId; }
    public void setResultEntityId(Long resultEntityId) { this.resultEntityId = resultEntityId; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
