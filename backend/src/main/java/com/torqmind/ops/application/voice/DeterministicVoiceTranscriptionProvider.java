package com.torqmind.ops.application.voice;

public class DeterministicVoiceTranscriptionProvider implements VoiceTranscriptionProvider {
    @Override
    public String name() {
        return "deterministic";
    }

    @Override
    public String transcribe(byte[] audio, String filename, String declaredMime) {
        throw new VoiceUnavailableException(
                "Transcrição automática indisponível neste ambiente. Digite o comando ou configure VOICE_OPENAI_API_KEY.");
    }
}
