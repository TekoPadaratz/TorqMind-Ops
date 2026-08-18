package com.torqmind.ops.application.voice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.domain.voice.VoiceAction;
import com.torqmind.ops.domain.voice.VoiceDraft;
import com.torqmind.ops.domain.voice.VoiceDraftStatus;
import com.torqmind.ops.infrastructure.persistence.VoiceDraftRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VoiceDraftService {

    private static final Logger log = LoggerFactory.getLogger(VoiceDraftService.class);

    private final VoiceProperties properties;
    private final VoiceDraftRepository draftRepository;
    private final VoiceTranscriptionProvider transcriptionProvider;
    private final VoiceIntentProvider intentProvider;
    private final AuthorizedEntityResolver resolver;
    private final VoiceCommandExecutor executor;
    private final ObjectMapper objectMapper;
    private final com.torqmind.ops.application.task.TaskDetailService taskDetailService;
    private final com.torqmind.ops.application.company.CompanySettingsService companySettingsService;

    public VoiceDraftService(
            VoiceProperties properties,
            VoiceDraftRepository draftRepository,
            VoiceTranscriptionProvider transcriptionProvider,
            VoiceIntentProvider intentProvider,
            AuthorizedEntityResolver resolver,
            VoiceCommandExecutor executor,
            ObjectMapper objectMapper,
            com.torqmind.ops.application.task.TaskDetailService taskDetailService,
            com.torqmind.ops.application.company.CompanySettingsService companySettingsService
    ) {
        this.properties = properties;
        this.draftRepository = draftRepository;
        this.transcriptionProvider = transcriptionProvider;
        this.intentProvider = intentProvider;
        this.resolver = resolver;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.taskDetailService = taskDetailService;
        this.companySettingsService = companySettingsService;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", properties.isEnabled());
        out.put("transcriptionProvider", transcriptionProvider.name());
        out.put("intentProvider", intentProvider.name());
        out.put("maxSeconds", properties.getMaxAudioSeconds());
        out.put("maxBytes", properties.getMaxAudioBytes());
        out.put("manualTranscriptAllowed", true);
        return out;
    }

    @Transactional
    public Map<String, Object> createDraft(
            AppUserPrincipal me,
            byte[] audio,
            String filename,
            String declaredMime,
            String transcriptInput,
            VoiceContext context
    ) {
        requireEnabled();
        rateLimit(me.userId());
        String correlationId = UUID.randomUUID().toString().substring(0, 12);
        log.info("voice.draft start cid={} actorPresent=true audio={} transcript={}",
                correlationId, audio != null && audio.length > 0, transcriptInput != null && !transcriptInput.isBlank());

        String transcript = transcriptInput == null ? "" : transcriptInput.trim();
        try {
            if (audio != null && audio.length > 0) {
                VoiceAudioValidator.validate(audio, declaredMime, properties.getMaxAudioBytes());
                if (transcript.isBlank()) {
                    transcript = transcriptionProvider.transcribe(audio, filename, declaredMime);
                }
            }
        } finally {
            if (audio != null) {
                java.util.Arrays.fill(audio, (byte) 0);
            }
        }
        if (transcript.isBlank()) {
            throw new IllegalArgumentException("Fale ou digite o comando.");
        }
        transcript = VoiceIntentSanitizer.sanitize(transcript);
        try {
            VoiceIntent intent = intentProvider.interpret(transcript, context);
            intent.setTranscript(transcript);
            return persistInterpreted(me, intent, context, correlationId);
        } catch (IllegalArgumentException | VoiceUnavailableException | VoiceRateLimitException | ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("voice.draft failed cid={}", correlationId, ex);
            throw new IllegalArgumentException("Não foi possível processar o comando.");
        }
    }

    @Transactional
    public Map<String, Object> get(AppUserPrincipal me, UUID id) {
        VoiceDraft draft = loadOwn(me, id);
        expireIfNeeded(draft);
        return toView(draft);
    }

    @Transactional
    public Map<String, Object> patch(AppUserPrincipal me, UUID id, Map<String, String> selectedOptions, VoiceIntent fields) {
        VoiceDraft draft = loadOwn(me, id);
        expireIfNeeded(draft);
        if (draft.getStatus() == VoiceDraftStatus.CONFIRMED || draft.getStatus() == VoiceDraftStatus.CANCELLED) {
            throw new IllegalArgumentException("Este comando já foi encerrado.");
        }
        VoiceIntent intent = readIntent(draft);
        if (selectedOptions != null) {
            selectedOptions.forEach((field, key) -> resolver.applySelection(intent, field, key));
        }
        if (fields != null) {
            mergeFields(intent, fields);
        }
        VoiceContext ctx = new VoiceContext();
        return reprocess(me, draft, intent, ctx);
    }

    @Transactional
    public Map<String, Object> confirm(AppUserPrincipal me, UUID id, String idempotencyKey) {
        VoiceDraft draft = loadOwn(me, id);
        expireIfNeeded(draft);
        if (draft.getStatus() == VoiceDraftStatus.CONFIRMED) {
            if (idempotencyKey != null && idempotencyKey.equals(draft.getIdempotencyKey())) {
                return toView(draft);
            }
            throw new IllegalArgumentException("Este comando já foi confirmado.");
        }
        if (draft.getStatus() != VoiceDraftStatus.READY_FOR_CONFIRMATION
                && draft.getStatus() != VoiceDraftStatus.NEEDS_INPUT) {
            throw new IllegalArgumentException("Este comando ainda não pode ser confirmado.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Informe a chave de idempotência.");
        }
        VoiceIntent intent = readIntent(draft);
        VoiceContext ctx = new VoiceContext();
        if (draft.getResultEntityId() != null && "ROUTINE_RUN".equals(draft.getResultEntityType())) {
            ctx.setCurrentTaskType("ROUTINE_RUN");
            ctx.setCurrentTaskId(draft.getResultEntityId());
        }
        VoiceResolved resolved = resolver.resolve(me, intent, ctx);
        executor.collectMissing(intent, resolved, taskDetailService);
        if (!intent.getAmbiguities().isEmpty() || !intent.getMissingFields().isEmpty()) {
            draft.setIntentJson(write(intent));
            draft.setResolvedJson(write(resolved));
            draft.setStatus(intent.getMissingFields().contains("photo") || intent.getMissingFields().contains("comment")
                    ? VoiceDraftStatus.NEEDS_INPUT : VoiceDraftStatus.NEEDS_INPUT);
            draft.setPreviewText(executor.preview(intent, resolved));
            draft.setUpdatedAt(Instant.now());
            draftRepository.save(draft);
            Map<String, Object> view = toView(draft);
            view.put("message", "Ainda faltam informações para confirmar.");
            return view;
        }
        Map<String, Object> result = executor.execute(me, intent, resolved);
        draft.setStatus(VoiceDraftStatus.CONFIRMED);
        draft.setIdempotencyKey(idempotencyKey);
        draft.setConfirmedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        draft.setResultJson(write(result));
        Object entityType = result.get("entityType");
        Object entityId = result.get("entityId");
        if (entityType != null) {
            draft.setResultEntityType(String.valueOf(entityType));
        }
        if (entityId instanceof Number n) {
            draft.setResultEntityId(n.longValue());
        }
        draftRepository.save(draft);
        log.info("voice.draft confirmed cid={} action={}", draft.getCorrelationId(), draft.getAction());
        return toView(draft);
    }

    @Transactional
    public Map<String, Object> cancel(AppUserPrincipal me, UUID id) {
        VoiceDraft draft = loadOwn(me, id);
        if (draft.getStatus() != VoiceDraftStatus.CONFIRMED) {
            draft.setStatus(VoiceDraftStatus.CANCELLED);
            draft.setUpdatedAt(Instant.now());
            draftRepository.save(draft);
        }
        return toView(draft);
    }

    private Map<String, Object> persistInterpreted(AppUserPrincipal me, VoiceIntent intent, VoiceContext context, String correlationId) {
        VoiceResolved resolved = resolver.resolve(me, intent, context);
        executor.collectMissing(intent, resolved, taskDetailService);
        VoiceDraft draft = new VoiceDraft();
        draft.setId(UUID.randomUUID());
        draft.setActorUserId(me.userId());
        draft.setCompanyId(resolved.getCompanyId());
        draft.setBranchId(resolved.getBranchId());
        draft.setSchemaVersion(VoiceIntent.SCHEMA_VERSION);
        draft.setTranscript(intent.getTranscript());
        draft.setCorrelationId(correlationId);
        Instant now = Instant.now();
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draft.setExpiresAt(now.plus(properties.getDraftTtlMinutes(), ChronoUnit.MINUTES));
        try {
            draft.setAction(VoiceAction.valueOf(intent.getAction()));
        } catch (Exception ex) {
            draft.setStatus(VoiceDraftStatus.FAILED);
            draft.setErrorMessage("Ação não reconhecida.");
            draft.setIntentJson(write(intent));
            draftRepository.save(draft);
            return toView(draft);
        }
        return reprocess(me, draft, intent, context);
    }

    private Map<String, Object> reprocess(AppUserPrincipal me, VoiceDraft draft, VoiceIntent intent, VoiceContext context) {
        intent.setAmbiguities(new java.util.ArrayList<>());
        intent.setMissingFields(new java.util.ArrayList<>());
        VoiceResolved resolved = resolver.resolve(me, intent, context);
        applyCompanyDefaults(intent, resolved);
        executor.collectMissing(intent, resolved, taskDetailService);
        draft.setIntentJson(write(intent));
        draft.setResolvedJson(write(resolved));
        draft.setPreviewText(executor.preview(intent, resolved));
        draft.setUpdatedAt(Instant.now());
        if (resolved.getRunId() != null) {
            draft.setResultEntityType("ROUTINE_RUN");
            draft.setResultEntityId(resolved.getRunId());
        } else if (resolved.getOccurrenceId() != null) {
            draft.setResultEntityType("OCCURRENCE");
            draft.setResultEntityId(resolved.getOccurrenceId());
        }
        if (!intent.getAmbiguities().isEmpty() || !intent.getMissingFields().isEmpty()) {
            draft.setStatus(VoiceDraftStatus.NEEDS_INPUT);
        } else {
            draft.setStatus(VoiceDraftStatus.READY_FOR_CONFIRMATION);
        }
        draftRepository.save(draft);
        return toView(draft);
    }

    private VoiceDraft loadOwn(AppUserPrincipal me, UUID id) {
        VoiceDraft draft = draftRepository.findByIdAndActorUserId(id, me.userId())
                .orElseThrow(() -> new ForbiddenException("Rascunho de voz não encontrado."));
        return draft;
    }

    private void expireIfNeeded(VoiceDraft draft) {
        if (draft.getStatus() == VoiceDraftStatus.CONFIRMED || draft.getStatus() == VoiceDraftStatus.CANCELLED) {
            return;
        }
        if (draft.getExpiresAt() != null && draft.getExpiresAt().isBefore(Instant.now())) {
            draft.setStatus(VoiceDraftStatus.EXPIRED);
            draft.setUpdatedAt(Instant.now());
            draftRepository.save(draft);
            throw new IllegalArgumentException("Este comando expirou. Grave novamente.");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new VoiceUnavailableException("Comandos por voz estão desligados neste ambiente.");
        }
    }

    private void rateLimit(UUID userId) {
        Instant window = Instant.now().minusSeconds(properties.getRateLimitWindowSeconds());
        long count = draftRepository.countByActorUserIdAndCreatedAtAfter(userId, window);
        if (count >= properties.getRateLimitPerWindow()) {
            throw new VoiceRateLimitException("Muitos comandos por voz. Aguarde alguns minutos.");
        }
    }

    private void applyCompanyDefaults(VoiceIntent intent, VoiceResolved resolved) {
        if (!"CREATE_TASK".equals(intent.getAction()) || resolved.getCompanyId() == null) {
            return;
        }
        com.torqmind.ops.domain.company.CompanySettings s = companySettingsService.getOrDefault(resolved.getCompanyId());
        if (intent.getRequiresPhoto() == null) {
            intent.setRequiresPhoto(s.isRequirePhotoOnComplete());
        }
        if (intent.getRequiresComment() == null) {
            intent.setRequiresComment(s.isRequireCommentOnComplete());
        }
        if (intent.getReminderBeforeMinutes() == null) {
            intent.setReminderBeforeMinutes(s.getDefaultReminderMinutes());
        }
    }

    private VoiceIntent readIntent(VoiceDraft draft) {
        try {
            return objectMapper.readValue(draft.getIntentJson(), VoiceIntent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Rascunho corrompido.");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> toView(VoiceDraft draft) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", draft.getId().toString());
        out.put("status", draft.getStatus().name());
        out.put("action", draft.getAction() == null ? null : draft.getAction().name());
        out.put("schemaVersion", draft.getSchemaVersion());
        out.put("transcript", draft.getTranscript());
        out.put("previewText", draft.getPreviewText());
        out.put("errorMessage", draft.getErrorMessage());
        out.put("correlationId", draft.getCorrelationId());
        out.put("expiresAt", draft.getExpiresAt());
        out.put("resultEntityType", draft.getResultEntityType());
        out.put("resultEntityId", draft.getResultEntityId());
        try {
            out.put("intent", objectMapper.readValue(draft.getIntentJson(), VoiceIntent.class));
        } catch (Exception ex) {
            out.put("intent", Map.of());
        }
        if (draft.getResultJson() != null) {
            try {
                out.put("result", objectMapper.readValue(draft.getResultJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ex) {
                out.put("result", Map.of());
            }
        }
        return out;
    }

    private void mergeFields(VoiceIntent target, VoiceIntent src) {
        if (src.getTitle() != null) target.setTitle(src.getTitle());
        if (src.getDescription() != null) target.setDescription(src.getDescription());
        if (src.getComment() != null) target.setComment(src.getComment());
        if (src.getStartTime() != null) target.setStartTime(src.getStartTime());
        if (src.getDueTime() != null) target.setDueTime(src.getDueTime());
        if (src.getScheduledDate() != null) target.setScheduledDate(src.getScheduledDate());
        if (src.getRecurrence() != null) target.setRecurrence(src.getRecurrence());
        if (src.getTargetType() != null) target.setTargetType(src.getTargetType());
        if (src.getRequiresPhoto() != null) target.setRequiresPhoto(src.getRequiresPhoto());
        if (src.getRequiresComment() != null) target.setRequiresComment(src.getRequiresComment());
        if (src.getOccurrencePriority() != null) target.setOccurrencePriority(src.getOccurrencePriority());
        if (src.getBranchReference() != null) target.setBranchReference(src.getBranchReference());
        if (src.getTargetUserReference() != null) target.setTargetUserReference(src.getTargetUserReference());
        if (src.getTargetSectorReference() != null) target.setTargetSectorReference(src.getTargetSectorReference());
        if (src.getTaskReference() != null) target.setTaskReference(src.getTaskReference());
    }

    }
