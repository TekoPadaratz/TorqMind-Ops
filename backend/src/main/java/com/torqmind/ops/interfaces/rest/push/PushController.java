package com.torqmind.ops.interfaces.rest.push;

import com.torqmind.ops.application.notification.WebPushService;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final WebPushService webPushService;

    public PushController(WebPushService webPushService) {
        this.webPushService = webPushService;
    }

    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", webPushService.publicKey());
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestBody SubscribeRequest request,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        String p256dh = request.keys() == null ? null : request.keys().p256dh();
        String auth = request.keys() == null ? null : request.keys().auth();
        webPushService.subscribe(me.userId(), request.endpoint(), p256dh, auth);
        return Map.of("ok", true);
    }

    @PostMapping("/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestBody UnsubscribeRequest request,
                                           @AuthenticationPrincipal AppUserPrincipal me) {
        webPushService.unsubscribe(request.endpoint());
        return Map.of("ok", true);
    }

    public record Keys(String p256dh, String auth) {
    }

    public record SubscribeRequest(String endpoint, Keys keys) {
    }

    public record UnsubscribeRequest(String endpoint) {
    }
}
