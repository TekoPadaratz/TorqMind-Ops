package com.torqmind.ops.interfaces.rest.attachment;

import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final TaskDetailService taskDetailService;

    public AttachmentController(TaskDetailService taskDetailService) {
        this.taskDetailService = taskDetailService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> get(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal me
    ) {
        TaskDetailService.AttachmentContent content = taskDetailService.readAttachment(id, me);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.mimeType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String disposition = ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(content.bytes()));
    }
}
