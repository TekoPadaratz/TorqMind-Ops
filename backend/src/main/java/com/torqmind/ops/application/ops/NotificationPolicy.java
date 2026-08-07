package com.torqmind.ops.application.ops;

import java.util.UUID;

public final class NotificationPolicy {
    private NotificationPolicy() {}

    public static boolean shouldNotify(UUID actorUserId, UUID recipientUserId) {
        if (actorUserId == null || recipientUserId == null) {
            return false;
        }
        return !actorUserId.equals(recipientUserId);
    }
}
