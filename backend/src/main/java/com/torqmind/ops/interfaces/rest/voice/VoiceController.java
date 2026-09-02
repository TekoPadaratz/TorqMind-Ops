package com.torqmind.ops.interfaces.rest.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.application.voice.VoiceContext;
import com.torqmind.ops.application.voice.VoiceDraftService;
import com.torqmind.ops.application.voice.VoiceIntent;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceDraftService voiceDraftService;
    private final ObjectMapper objectMapper;

    public VoiceController(VoiceDraftService voiceDraftService, ObjectMapper objectMapper) {
        this.voiceDraftService = voiceDraftService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return voiceDraftService.status();
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createMultipart(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "transcript", required = false) String transcript,
            @RequestParam(value = "contextJson", required = false) String contextJson
    ) {
        byte[] audio = null;
        String filename = null;
        String mime = null;
        if (file != null && !file.isEmpty()) {
            try {
                audio = file.getBytes();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Falha ao ler o áudio.");
            }
            filename = file.getOriginalFilename();
            mime = file.getContentType();
        }
        return voiceDraftService.createDraft(me, audio, filename, mime, transcript, parseContext(contextJson));
    }

    @PostMapping(value = "/drafts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createJson(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestBody CreateDraftRequest request
    ) {
        VoiceContext ctx = new VoiceContext();
        if (request != null && request.currentTaskId() != null) {
            ctx.setCurrentTaskId(request.currentTaskId());
            ctx.setCurrentTaskType(request.currentTaskType());
            ctx.setCurrentTaskTitle(request.currentTaskTitle());
        }
        String transcript = request == null ? null : request.transcript();
        return voiceDraftService.createDraft(me, null, null, null, transcript, ctx);
    }

    @GetMapping("/drafts/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return voiceDraftService.get(me, id);
    }

    @PatchMapping("/drafts/{id}")
    public Map<String, Object> patch(
            @AuthenticationPrincipal AppUserPrincipal me,
            @PathVariable UUID id,
            @RequestBody PatchRequest request
    ) {
        VoiceIntent fields = new VoiceIntent();
        if (request != null && request.fields() != null) {
            fields = request.fields();
        }
        return voiceDraftService.patch(
                me,
                id,
                request == null ? null : request.selectedOptions(),
                fields,
                request == null ? null : request.transcript()
        );
    }

    @PostMapping("/drafts/{id}/confirm")
    public Map<String, Object> confirm(
            @AuthenticationPrincipal AppUserPrincipal me,
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ConfirmRequest body
    ) {
        String key = idempotencyKey;
        if ((key == null || key.isBlank()) && body != null) {
            key = body.idempotencyKey();
        }
        return voiceDraftService.confirm(me, id, key);
    }

    @DeleteMapping("/drafts/{id}")
    public Map<String, Object> cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return voiceDraftService.cancel(me, id);
    }

    private VoiceContext parseContext(String json) {
        VoiceContext ctx = new VoiceContext();
        if (json == null || json.isBlank()) {
            return ctx;
        }
        try {
            return objectMapper.readValue(json, VoiceContext.class);
        } catch (Exception ex) {
            return ctx;
        }
    }

    public record CreateDraftRequest(String transcript, String currentTaskType, Long currentTaskId, String currentTaskTitle) {}

    public record PatchRequest(Map<String, String> selectedOptions, VoiceIntent fields, String transcript) {}

    public record ConfirmRequest(String idempotencyKey) {}
}
