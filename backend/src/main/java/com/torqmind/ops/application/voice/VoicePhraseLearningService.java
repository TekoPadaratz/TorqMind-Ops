package com.torqmind.ops.application.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.domain.voice.VoicePhraseLearning;
import com.torqmind.ops.infrastructure.persistence.VoicePhraseLearningRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aprendizado tenant-scoped de frases operacionais.
 * Grava somente apos confirmacao bem-sucedida; nunca aprende acoes destrutivas ou admin.
 */
@Service
public class VoicePhraseLearningService {

    private static final Logger log = LoggerFactory.getLogger(VoicePhraseLearningService.class);
    private static final int MAX_PER_COMPANY = 300;
    private static final int MIN_PHRASE_LEN = 6;
    private static final Set<String> BLOCKED_ACTIONS = Set.of(
            "DELETE_TASK", "REJECT_TASK", "ADMIN_DENIED"
    );

    private final VoicePhraseLearningRepository repository;
    private final ObjectMapper objectMapper;

    public VoicePhraseLearningService(VoicePhraseLearningRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void applyLearned(AppUserPrincipal me, VoiceIntent intent, String transcript) {
        if (me == null || me.companyId() == null || transcript == null || intent == null) {
            return;
        }
        String normalized = VoicePhraseNormalizer.normalize(transcript);
        if (normalized.length() < MIN_PHRASE_LEN) {
            return;
        }
        List<VoicePhraseLearning> hits = repository
                .findTop20ByCompanyIdAndPhraseNormalizedOrderByHitCountDescLastUsedAtDesc(me.companyId(), normalized);
        if (hits.isEmpty()) {
            return;
        }
        VoicePhraseLearning best = hits.get(0);
        if ("INTENT".equals(best.getLearningType()) && best.getIntentSnapshot() != null) {
            mergeSnapshot(intent, best.getIntentSnapshot());
            intent.getWarnings().add("Usei uma expressao aprendida da sua empresa.");
            bump(best);
            return;
        }
        if ("SLOT".equals(best.getLearningType()) && best.getFieldName() != null && best.getFieldValue() != null) {
            applySlot(intent, best.getFieldName(), best.getFieldValue());
            intent.getWarnings().add("Usei um apelido aprendido da sua empresa.");
            bump(best);
        }
    }

    @Transactional
    public void recordSuccess(
            AppUserPrincipal me,
            VoiceIntent intent,
            String originalTranscript,
            String clarificationTranscript
    ) {
        if (me == null || me.companyId() == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null || BLOCKED_ACTIONS.contains(action)) {
            return;
        }
        Instant now = Instant.now();
        if (clarificationTranscript != null && !clarificationTranscript.isBlank()) {
            saveSlotLearning(me, clarificationTranscript, intent, now);
        }
        if (originalTranscript != null && !originalTranscript.isBlank()) {
            saveIntentLearning(me, originalTranscript, intent, now);
        }
        pruneIfNeeded(me.companyId());
    }

    private void saveIntentLearning(AppUserPrincipal me, String phrase, VoiceIntent intent, Instant now) {
        String normalized = VoicePhraseNormalizer.normalize(phrase);
        if (normalized.length() < MIN_PHRASE_LEN) {
            return;
        }
        String snapshot = buildSnapshot(intent);
        if (snapshot == null) {
            return;
        }
        upsert(me, normalized, "INTENT", intent.getAction(), "", null, snapshot, now);
        log.info("voice.learning intent company={} phrase={}", me.companyId(), normalized);
    }

    private void saveSlotLearning(AppUserPrincipal me, String phrase, VoiceIntent intent, Instant now) {
        String normalized = VoicePhraseNormalizer.normalize(phrase);
        if (normalized.length() < 3) {
            return;
        }
        if (!blank(intent.getBranchReference())) {
            upsert(me, normalized, "SLOT", intent.getAction(), "branchReference", intent.getBranchReference(), null, now);
        }
        if (!blank(intent.getTaskReference())) {
            upsert(me, normalized, "SLOT", intent.getAction(), "taskReference", intent.getTaskReference(), null, now);
        }
        if (!blank(intent.getTargetUserReference())) {
            upsert(me, normalized, "SLOT", intent.getAction(), "targetUserReference", intent.getTargetUserReference(), null, now);
        }
        if (!blank(intent.getTitle())) {
            upsert(me, normalized, "SLOT", intent.getAction(), "title", intent.getTitle(), null, now);
        }
    }

    private void upsert(
            AppUserPrincipal me,
            String phrase,
            String type,
            String action,
            String fieldName,
            String fieldValue,
            String snapshot,
            Instant now
    ) {
        String field = fieldName == null ? "" : fieldName;
        String act = action == null ? "" : action;
        VoicePhraseLearning row = repository
                .findByCompanyIdAndPhraseNormalizedAndLearningTypeAndFieldNameAndAction(
                        me.companyId(), phrase, type, field, act)
                .orElseGet(VoicePhraseLearning::new);
        if (row.getId() == null) {
            row.setId(UUID.randomUUID());
            row.setCompanyId(me.companyId());
            row.setBranchId(me.branchId());
            row.setActorUserId(me.userId());
            row.setPhraseNormalized(phrase);
            row.setLearningType(type);
            row.setAction(act);
            row.setFieldName(field);
            row.setCreatedAt(now);
            row.setHitCount(1);
        } else {
            row.setHitCount(row.getHitCount() + 1);
        }
        if (fieldValue != null) {
            row.setFieldValue(fieldValue);
        }
        if (snapshot != null) {
            row.setIntentSnapshot(snapshot);
        }
        row.setLastUsedAt(now);
        repository.save(row);
    }

    private void bump(VoicePhraseLearning row) {
        row.setHitCount(row.getHitCount() + 1);
        row.setLastUsedAt(Instant.now());
        repository.save(row);
    }

    private void pruneIfNeeded(Long companyId) {
        long count = repository.countByCompanyId(companyId);
        if (count <= MAX_PER_COMPANY) {
            return;
        }
        List<VoicePhraseLearning> oldest = repository.findOldestByCompany(companyId);
        int toRemove = (int) (count - MAX_PER_COMPANY);
        for (int i = 0; i < toRemove && i < oldest.size(); i++) {
            repository.delete(oldest.get(i));
        }
    }

    private String buildSnapshot(VoiceIntent intent) {
        try {
            VoiceIntent snap = new VoiceIntent();
            snap.setAction(intent.getAction());
            snap.setTaskReference(intent.getTaskReference());
            snap.setTitle(intent.getTitle());
            snap.setBranchReference(intent.getBranchReference());
            snap.setTargetUserReference(intent.getTargetUserReference());
            snap.setTargetSectorReference(intent.getTargetSectorReference());
            snap.setRequestedStatus(intent.getRequestedStatus());
            snap.setTargetType(intent.getTargetType());
            snap.setRecurrence(intent.getRecurrence());
            if (snap.getAction() == null) {
                return null;
            }
            return objectMapper.writeValueAsString(snap);
        } catch (Exception ex) {
            return null;
        }
    }

    private void mergeSnapshot(VoiceIntent intent, String snapshot) {
        try {
            VoiceIntent learned = objectMapper.readValue(snapshot, VoiceIntent.class);
            if (learned.getAction() != null && !BLOCKED_ACTIONS.contains(learned.getAction())) {
                intent.setAction(learned.getAction());
            }
            if (blank(intent.getTaskReference()) && !blank(learned.getTaskReference())) {
                intent.setTaskReference(learned.getTaskReference());
            }
            if (blank(intent.getTitle()) && !blank(learned.getTitle())) {
                intent.setTitle(learned.getTitle());
            }
            if (blank(intent.getBranchReference()) && !blank(learned.getBranchReference())) {
                intent.setBranchReference(learned.getBranchReference());
            }
            if (blank(intent.getTargetUserReference()) && !blank(learned.getTargetUserReference())) {
                intent.setTargetUserReference(learned.getTargetUserReference());
            }
            if (blank(intent.getTargetSectorReference()) && !blank(learned.getTargetSectorReference())) {
                intent.setTargetSectorReference(learned.getTargetSectorReference());
            }
            if (intent.getRequestedStatus() == null && learned.getRequestedStatus() != null) {
                intent.setRequestedStatus(learned.getRequestedStatus());
            }
            if (intent.getTargetType() == null && learned.getTargetType() != null) {
                intent.setTargetType(learned.getTargetType());
            }
            if (intent.getRecurrence() == null && learned.getRecurrence() != null) {
                intent.setRecurrence(learned.getRecurrence());
            }
            intent.setRequiresConfirmation(Boolean.FALSE);
            intent.setConfidence(0.88);
        } catch (Exception ignored) {
            // snapshot invalido — ignora
        }
    }

    private static void applySlot(VoiceIntent intent, String field, String value) {
        switch (field) {
            case "branchReference" -> intent.setBranchReference(value);
            case "taskReference" -> intent.setTaskReference(value);
            case "targetUserReference" -> intent.setTargetUserReference(value);
            case "title" -> intent.setTitle(value);
            default -> { }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
