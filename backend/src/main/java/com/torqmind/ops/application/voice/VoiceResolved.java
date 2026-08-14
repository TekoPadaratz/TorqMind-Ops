package com.torqmind.ops.application.voice;

import java.util.UUID;

public class VoiceResolved {
    private Long companyId;
    private Long branchId;
    private UUID userId;
    private Long sectorId;
    private Long runId;
    private Long occurrenceId;
    private String taskType;
    private String userName;
    private String branchName;
    private String sectorName;
    private String companyName;
    private String taskTitle;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Long getSectorId() { return sectorId; }
    public void setSectorId(Long sectorId) { this.sectorId = sectorId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getOccurrenceId() { return occurrenceId; }
    public void setOccurrenceId(Long occurrenceId) { this.occurrenceId = occurrenceId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
}
