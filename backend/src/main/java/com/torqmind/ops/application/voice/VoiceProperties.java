package com.torqmind.ops.application.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.voice")
public class VoiceProperties {

    private boolean enabled = true;
    private String transcriptionProvider = "deterministic";
    private String intentProvider = "deterministic";
    private String openaiApiKey = "";
    private String openaiBaseUrl = "https://api.openai.com/v1";
    private String transcribeModel = "whisper-1";
    private String intentModel = "gpt-4o-mini";
    private int maxAudioBytes = 8 * 1024 * 1024;
    private int maxAudioSeconds = 60;
    private int draftTtlMinutes = 15;
    private int rateLimitPerWindow = 20;
    private int rateLimitWindowSeconds = 600;
    private int transcribeTimeoutMs = 45000;
    private int intentTimeoutMs = 20000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTranscriptionProvider() { return transcriptionProvider; }
    public void setTranscriptionProvider(String transcriptionProvider) { this.transcriptionProvider = transcriptionProvider; }
    public String getIntentProvider() { return intentProvider; }
    public void setIntentProvider(String intentProvider) { this.intentProvider = intentProvider; }
    public String getOpenaiApiKey() { return openaiApiKey; }
    public void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; }
    public String getOpenaiBaseUrl() { return openaiBaseUrl; }
    public void setOpenaiBaseUrl(String openaiBaseUrl) { this.openaiBaseUrl = openaiBaseUrl; }
    public String getTranscribeModel() { return transcribeModel; }
    public void setTranscribeModel(String transcribeModel) { this.transcribeModel = transcribeModel; }
    public String getIntentModel() { return intentModel; }
    public void setIntentModel(String intentModel) { this.intentModel = intentModel; }
    public int getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(int maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
    public int getMaxAudioSeconds() { return maxAudioSeconds; }
    public void setMaxAudioSeconds(int maxAudioSeconds) { this.maxAudioSeconds = maxAudioSeconds; }
    public int getDraftTtlMinutes() { return draftTtlMinutes; }
    public void setDraftTtlMinutes(int draftTtlMinutes) { this.draftTtlMinutes = draftTtlMinutes; }
    public int getRateLimitPerWindow() { return rateLimitPerWindow; }
    public void setRateLimitPerWindow(int rateLimitPerWindow) { this.rateLimitPerWindow = rateLimitPerWindow; }
    public int getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) { this.rateLimitWindowSeconds = rateLimitWindowSeconds; }
    public int getTranscribeTimeoutMs() { return transcribeTimeoutMs; }
    public void setTranscribeTimeoutMs(int transcribeTimeoutMs) { this.transcribeTimeoutMs = transcribeTimeoutMs; }
    public int getIntentTimeoutMs() { return intentTimeoutMs; }
    public void setIntentTimeoutMs(int intentTimeoutMs) { this.intentTimeoutMs = intentTimeoutMs; }

    public boolean hasOpenaiKey() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
