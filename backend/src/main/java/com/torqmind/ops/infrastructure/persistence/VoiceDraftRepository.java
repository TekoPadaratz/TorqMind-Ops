package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.voice.VoiceDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoiceDraftRepository extends JpaRepository<VoiceDraft, UUID> {
    Optional<VoiceDraft> findByIdAndActorUserId(UUID id, UUID actorUserId);
    List<VoiceDraft> findTop20ByActorUserIdOrderByCreatedAtDesc(UUID actorUserId);
    long countByActorUserIdAndCreatedAtAfter(UUID actorUserId, Instant after);
}
