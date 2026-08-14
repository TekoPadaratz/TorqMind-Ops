package com.torqmind.ops.application.voice;

import java.util.ArrayList;
import java.util.List;

/** Contrato versionado da intenção. Campos extras são descartados na desserialização. */
public class VoiceIntent {

    public static final String SCHEMA_VERSION = "1";

    private String schemaVersion = SCHEMA_VERSION;
    private String action;
    private String transcript;
    private String taskReference;
    private String title;
    private String description;
    private String companyReference;
    private String branchReference;
    private String cityReference;
    private String targetType;
    private String targetUserReference;
    private String targetSectorReference;
    private String recurrence;
    private String scheduledDate;
    private String startTime;
    private String dueTime;
    private Integer reminderBeforeMinutes;
    private Boolean requiresPhoto;
    private Boolean requiresComment;
    private String comment;
    private String occurrencePriority;
    private String requestedStatus;
    private List<String> missingFields = new ArrayList<>();
    private List<VoiceAmbiguity> ambiguities = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Double confidence;
    private Boolean requiresConfirmation = Boolean.TRUE;

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getTaskReference() { return taskReference; }
    public void setTaskReference(String taskReference) { this.taskReference = taskReference; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCompanyReference() { return companyReference; }
    public void setCompanyReference(String companyReference) { this.companyReference = companyReference; }
    public String getBranchReference() { return branchReference; }
    public void setBranchReference(String branchReference) { this.branchReference = branchReference; }
    public String getCityReference() { return cityReference; }
    public void setCityReference(String cityReference) { this.cityReference = cityReference; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetUserReference() { return targetUserReference; }
    public void setTargetUserReference(String targetUserReference) { this.targetUserReference = targetUserReference; }
    public String getTargetSectorReference() { return targetSectorReference; }
    public void setTargetSectorReference(String targetSectorReference) { this.targetSectorReference = targetSectorReference; }
    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getDueTime() { return dueTime; }
    public void setDueTime(String dueTime) { this.dueTime = dueTime; }
    public Integer getReminderBeforeMinutes() { return reminderBeforeMinutes; }
    public void setReminderBeforeMinutes(Integer reminderBeforeMinutes) { this.reminderBeforeMinutes = reminderBeforeMinutes; }
    public Boolean getRequiresPhoto() { return requiresPhoto; }
    public void setRequiresPhoto(Boolean requiresPhoto) { this.requiresPhoto = requiresPhoto; }
    public Boolean getRequiresComment() { return requiresComment; }
    public void setRequiresComment(Boolean requiresComment) { this.requiresComment = requiresComment; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getOccurrencePriority() { return occurrencePriority; }
    public void setOccurrencePriority(String occurrencePriority) { this.occurrencePriority = occurrencePriority; }
    public String getRequestedStatus() { return requestedStatus; }
    public void setRequestedStatus(String requestedStatus) { this.requestedStatus = requestedStatus; }
    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields == null ? new ArrayList<>() : new ArrayList<>(missingFields);
    }
    public List<VoiceAmbiguity> getAmbiguities() { return ambiguities; }
    public void setAmbiguities(List<VoiceAmbiguity> ambiguities) {
        this.ambiguities = ambiguities == null ? new ArrayList<>() : new ArrayList<>(ambiguities);
    }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Boolean getRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(Boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
}
