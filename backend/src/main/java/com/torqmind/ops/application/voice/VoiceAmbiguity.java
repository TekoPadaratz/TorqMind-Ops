package com.torqmind.ops.application.voice;

import java.util.ArrayList;
import java.util.List;

public class VoiceAmbiguity {
    private String field;
    private String query;
    private List<VoiceOption> options = new ArrayList<>();

    public VoiceAmbiguity() {}

    public VoiceAmbiguity(String field, String query, List<VoiceOption> options) {
        this.field = field;
        this.query = query;
        this.options = options;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<VoiceOption> getOptions() { return options; }
    public void setOptions(List<VoiceOption> options) { this.options = options == null ? new ArrayList<>() : options; }
}
