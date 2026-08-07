package com.torqmind.ops.domain.routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "routine_templates")
public class RoutineTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "recurrence_rule", nullable = false)
    private String recurrenceRule;

    @Column(name = "requires_photo", nullable = false)
    private boolean requiresPhoto;

    @Column(name = "requires_comment", nullable = false)
    private boolean requiresComment;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "target_type", nullable = false)
    private String targetType = "USER";

    @Column(name = "target_role")
    private String targetRole;

    @Column(name = "target_sector_id")
    private Long targetSectorId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "notify_time")
    private LocalTime notifyTime;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "weekday")
    private Integer weekday;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /** Dias do mês para CUSTOM, ex: "1,15,28". */
    @Column(name = "custom_days")
    private String customDays;

    @Column(name = "business_days_only", nullable = false)
    private boolean businessDaysOnly;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "reminder_before_minutes", nullable = false)
    private Integer reminderBeforeMinutes = 30;

    @Column(name = "last_generated_on")
    private LocalDate lastGeneratedOn;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public void setRecurrenceRule(String recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
    }

    public boolean isRequiresPhoto() {
        return requiresPhoto;
    }

    public void setRequiresPhoto(boolean requiresPhoto) {
        this.requiresPhoto = requiresPhoto;
    }

    public boolean isRequiresComment() {
        return requiresComment;
    }

    public void setRequiresComment(boolean requiresComment) {
        this.requiresComment = requiresComment;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public Long getTargetSectorId() {
        return targetSectorId;
    }

    public void setTargetSectorId(Long targetSectorId) {
        this.targetSectorId = targetSectorId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }

    public LocalTime getNotifyTime() {
        return notifyTime;
    }

    public void setNotifyTime(LocalTime notifyTime) {
        this.notifyTime = notifyTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
    }

    public Integer getWeekday() {
        return weekday;
    }

    public void setWeekday(Integer weekday) {
        this.weekday = weekday;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public String getCustomDays() {
        return customDays;
    }

    public void setCustomDays(String customDays) {
        this.customDays = customDays;
    }

    public boolean isBusinessDaysOnly() {
        return businessDaysOnly;
    }

    public void setBusinessDaysOnly(boolean businessDaysOnly) {
        this.businessDaysOnly = businessDaysOnly;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public Integer getReminderBeforeMinutes() {
        return reminderBeforeMinutes;
    }

    public void setReminderBeforeMinutes(Integer reminderBeforeMinutes) {
        this.reminderBeforeMinutes = reminderBeforeMinutes;
    }

    public LocalDate getLastGeneratedOn() {
        return lastGeneratedOn;
    }

    public void setLastGeneratedOn(LocalDate lastGeneratedOn) {
        this.lastGeneratedOn = lastGeneratedOn;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
