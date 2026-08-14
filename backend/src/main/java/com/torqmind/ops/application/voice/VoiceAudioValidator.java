package com.torqmind.ops.application.voice;

import com.torqmind.ops.shared.media.MediaSignatures;

public final class VoiceAudioValidator {
    private VoiceAudioValidator() {}

    public static String validate(byte[] audio, String declaredMime, int maxBytes) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Áudio vazio.");
        }
        if (audio.length > maxBytes) {
            throw new IllegalArgumentException("Áudio acima do limite permitido.");
        }
        String detected = MediaSignatures.detect(audio);
        if (detected == null || !MediaSignatures.isAudio(detected)) {
            throw new IllegalArgumentException("Arquivo de áudio inválido.");
        }
        if (!MediaSignatures.matchesDeclared(declaredMime, detected)) {
            throw new IllegalArgumentException("O conteúdo do áudio não corresponde ao tipo informado.");
        }
        return detected;
    }
}
