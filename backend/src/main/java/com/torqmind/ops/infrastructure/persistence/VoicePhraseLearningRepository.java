package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.voice.VoicePhraseLearning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoicePhraseLearningRepository extends JpaRepository<VoicePhraseLearning, UUID> {

    Optional<VoicePhraseLearning> findByCompanyIdAndPhraseNormalizedAndLearningTypeAndFieldNameAndAction(
            Long companyId,
            String phraseNormalized,
            String learningType,
            String fieldName,
            String action
    );

    List<VoicePhraseLearning> findTop20ByCompanyIdAndPhraseNormalizedOrderByHitCountDescLastUsedAtDesc(
            Long companyId,
            String phraseNormalized
    );

    long countByCompanyId(Long companyId);

    @Query("SELECT l FROM VoicePhraseLearning l WHERE l.companyId = :companyId ORDER BY l.lastUsedAt ASC")
    List<VoicePhraseLearning> findOldestByCompany(@Param("companyId") Long companyId);
}
