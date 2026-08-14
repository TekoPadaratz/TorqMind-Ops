package com.torqmind.ops.application.voice;

public class VoiceOption {
    private String key;
    private String label;

    public VoiceOption() {}

    public VoiceOption(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
