package com.torqmind.ops.domain.voice;

public enum VoiceDraftStatus {
    PROCESSING,
    NEEDS_INPUT,
    READY_FOR_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    FAILED
}
