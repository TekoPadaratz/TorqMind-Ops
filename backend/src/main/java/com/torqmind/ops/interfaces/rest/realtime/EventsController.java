package com.torqmind.ops.interfaces.rest.realtime;

import com.torqmind.ops.application.realtime.RealtimeService;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventsController {

    private final RealtimeService realtimeService;

    public EventsController(RealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AppUserPrincipal me) {
        return realtimeService.register(me.userId());
    }
}
