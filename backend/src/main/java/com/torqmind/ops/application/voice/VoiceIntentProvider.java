package com.torqmind.ops.application.voice;

public interface VoiceIntentProvider {
    String name();
    VoiceIntent interpret(String transcript, VoiceContext context);
}
