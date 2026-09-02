package com.torqmind.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.application.voice.VoiceIntent;
import com.torqmind.ops.application.voice.VoiceProperties;
import com.torqmind.ops.application.voice.VoicePhraseLearningService;
import com.torqmind.ops.domain.voice.VoicePhraseLearning;
import com.torqmind.ops.infrastructure.persistence.VoicePhraseLearningRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

class VoicePhraseLearningServiceTest {

    @Test
    void doesNotLearnDestructiveActions() {
        VoiceProperties properties = new VoiceProperties();
        properties.setPhraseLearningEnabled(true);
        VoicePhraseLearningRepository repo = Mockito.mock(VoicePhraseLearningRepository.class);
        VoicePhraseLearningService service = new VoicePhraseLearningService(properties, repo, new ObjectMapper());
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "m", "MANAGER", 1L, 2L);
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("DELETE_TASK");
        service.recordSuccess(me, intent, "excluir rotina extintores", null);
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void doesNotLearnWhenDisabled() {
        VoiceProperties properties = new VoiceProperties();
        properties.setPhraseLearningEnabled(false);
        VoicePhraseLearningRepository repo = Mockito.mock(VoicePhraseLearningRepository.class);
        VoicePhraseLearningService service = new VoicePhraseLearningService(properties, repo, new ObjectMapper());
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "m", "MANAGER", 1L, 2L);
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("LIST_TASKS");
        service.recordSuccess(me, intent, "o que está atrasado", null);
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void applyLearnedMergesIntentSnapshot() {
        VoiceProperties properties = new VoiceProperties();
        properties.setPhraseLearningEnabled(true);
        VoicePhraseLearningRepository repo = Mockito.mock(VoicePhraseLearningRepository.class);
        VoicePhraseLearning row = new VoicePhraseLearning();
        row.setLearningType("INTENT");
        row.setIntentSnapshot("{\"action\":\"LIST_MY_TASKS\",\"requestedStatus\":\"HOJE\"}");
        Mockito.when(repo.findTop20ByCompanyIdAndPhraseNormalizedOrderByHitCountDescLastUsedAtDesc(1L, "minhas coisas de hoje"))
                .thenReturn(java.util.List.of(row));
        VoicePhraseLearningService service = new VoicePhraseLearningService(properties, repo, new ObjectMapper());
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "m", "MANAGER", 1L, 2L);
        VoiceIntent intent = new VoiceIntent();
        intent.setTranscript("minhas coisas de hoje");
        service.applyLearned(me, intent, "minhas coisas de hoje");
        Assertions.assertEquals("LIST_MY_TASKS", intent.getAction());
        Assertions.assertEquals("HOJE", intent.getRequestedStatus());
    }

    @Test
    void recordsIntentLearningAfterSuccess() {
        VoiceProperties properties = new VoiceProperties();
        properties.setPhraseLearningEnabled(true);
        VoicePhraseLearningRepository repo = Mockito.mock(VoicePhraseLearningRepository.class);
        Mockito.when(repo.findByCompanyIdAndPhraseNormalizedAndLearningTypeAndFieldNameAndAction(
                Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(repo.countByCompanyId(1L)).thenReturn(0L);
        VoicePhraseLearningService service = new VoicePhraseLearningService(properties, repo, new ObjectMapper());
        AppUserPrincipal me = new AppUserPrincipal(UUID.randomUUID(), "m", "MANAGER", 1L, 2L);
        VoiceIntent intent = new VoiceIntent();
        intent.setAction("LIST_TASKS");
        intent.setRequestedStatus("ATRASADA");
        service.recordSuccess(me, intent, "o que está atrasado na operação", null);
        ArgumentCaptor<VoicePhraseLearning> captor = ArgumentCaptor.forClass(VoicePhraseLearning.class);
        Mockito.verify(repo).save(captor.capture());
        Assertions.assertEquals("INTENT", captor.getValue().getLearningType());
    }
}
