package com.torqmind.ops.domain.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "company_settings")
public class CompanySettings {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "require_photo_on_complete", nullable = false)
    private boolean requirePhotoOnComplete = true;

    @Column(name = "require_comment_on_complete", nullable = false)
    private boolean requireCommentOnComplete = true;

    @Column(name = "default_reminder_minutes", nullable = false)
    private Integer defaultReminderMinutes = 15;

    @Column(name = "checklists_enabled", nullable = false)
    private boolean checklistsEnabled = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public boolean isRequirePhotoOnComplete() {
        return requirePhotoOnComplete;
    }

    public void setRequirePhotoOnComplete(boolean requirePhotoOnComplete) {
        this.requirePhotoOnComplete = requirePhotoOnComplete;
    }

    public boolean isRequireCommentOnComplete() {
        return requireCommentOnComplete;
    }

    public void setRequireCommentOnComplete(boolean requireCommentOnComplete) {
        this.requireCommentOnComplete = requireCommentOnComplete;
    }

    public int getDefaultReminderMinutes() {
        return defaultReminderMinutes == null ? 15 : defaultReminderMinutes;
    }

    public void setDefaultReminderMinutes(int defaultReminderMinutes) {
        this.defaultReminderMinutes = defaultReminderMinutes;
    }

    public boolean isChecklistsEnabled() {
        return checklistsEnabled;
    }

    public void setChecklistsEnabled(boolean checklistsEnabled) {
        this.checklistsEnabled = checklistsEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
