package com.torqmind.ops;

import com.torqmind.ops.application.voice.VoiceAmbiguity;
import com.torqmind.ops.application.voice.VoiceConversationResolver;
import com.torqmind.ops.application.voice.VoiceIntent;
import com.torqmind.ops.application.voice.VoiceOption;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class VoiceConversationResolverTest {

    @Test
    void confirmationIntentDetectsYesAndNo() {
        Assertions.assertEquals(VoiceConversationResolver.SpeechIntent.CONFIRM,
                VoiceConversationResolver.confirmationIntent("sim, pode fazer"));
        Assertions.assertEquals(VoiceConversationResolver.SpeechIntent.DENY,
                VoiceConversationResolver.confirmationIntent("não, cancela"));
    }

    @Test
    void applySpokenAnswerResolvesAmbiguityByLabel() {
        VoiceIntent intent = new VoiceIntent();
        VoiceAmbiguity amb = new VoiceAmbiguity();
        amb.setField("branchReference");
        amb.setQuery("posto");
        amb.setOptions(List.of(
                new VoiceOption("b:1", "Posto Centro"),
                new VoiceOption("b:2", "Posto Norte")
        ));
        intent.setAmbiguities(new ArrayList<>(List.of(amb)));
        boolean applied = VoiceConversationResolver.applySpokenAnswer(intent, "posto centro");
        Assertions.assertTrue(applied);
        Assertions.assertEquals("b:1", intent.getBranchReference());
        Assertions.assertTrue(intent.getAmbiguities().isEmpty());
    }

    @Test
    void applySpokenAnswerFillsMissingTitleAndTime() {
        VoiceIntent intent = new VoiceIntent();
        intent.setMissingFields(new ArrayList<>(List.of("title", "startTime")));
        boolean applied = VoiceConversationResolver.applySpokenAnswer(intent, "Conferir extintores às 8");
        Assertions.assertTrue(applied);
        Assertions.assertEquals("Conferir extintores às 8", intent.getTitle());
        Assertions.assertEquals("08:00", intent.getStartTime());
    }
}
