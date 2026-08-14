package com.torqmind.ops.application.voice;

public interface VoiceTranscriptionProvider {
    String name();
    String transcribe(byte[] audio, String filename, String declaredMime);
}
