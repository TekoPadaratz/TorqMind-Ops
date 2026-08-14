package com.torqmind.ops.application.voice;

public class VoiceContext {
    private String currentTaskType;
    private Long currentTaskId;
    private String currentTaskTitle;

    public String getCurrentTaskType() { return currentTaskType; }
    public void setCurrentTaskType(String currentTaskType) { this.currentTaskType = currentTaskType; }
    public Long getCurrentTaskId() { return currentTaskId; }
    public void setCurrentTaskId(Long currentTaskId) { this.currentTaskId = currentTaskId; }
    public String getCurrentTaskTitle() { return currentTaskTitle; }
    public void setCurrentTaskTitle(String currentTaskTitle) { this.currentTaskTitle = currentTaskTitle; }
}
