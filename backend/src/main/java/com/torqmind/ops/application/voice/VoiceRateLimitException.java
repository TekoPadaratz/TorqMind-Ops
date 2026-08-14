package com.torqmind.ops.application.voice;

public class VoiceRateLimitException extends RuntimeException {
    public VoiceRateLimitException(String message) {
        super(message);
    }
}
