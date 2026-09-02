package com.torqmind.ops.domain.voice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_phrase_learnings")
public class VoicePhraseLearning {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "phrase_normalized", nullable = false, length = 500)
    private String phraseNormalized;

    @Column(name = "learning_type", nullable = false, length = 40)
    private String learningType;

    @Column(name = "action", length = 40)
    private String action;

    @Column(name = "field_name", length = 80)
    private String fieldName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "intent_snapshot", columnDefinition = "TEXT")
    private String intentSnapshot;

    @Column(name = "hit_count", nullable = false)
    private int hitCount = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public String getPhraseNormalized() { return phraseNormalized; }
    public void setPhraseNormalized(String phraseNormalized) { this.phraseNormalized = phraseNormalized; }
    public String getLearningType() { return learningType; }
    public void setLearningType(String learningType) { this.learningType = learningType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldValue() { return fieldValue; }
    public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }
    public String getIntentSnapshot() { return intentSnapshot; }
    public void setIntentSnapshot(String intentSnapshot) { this.intentSnapshot = intentSnapshot; }
    public int getHitCount() { return hitCount; }
    public void setHitCount(int hitCount) { this.hitCount = hitCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
