package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.apikey.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByPrefixAndActiveTrue(String prefix);
    List<ApiKey> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :ts where k.id = :id")
    void updateLastUsed(@Param("id") Long id, @Param("ts") Instant ts);
}
