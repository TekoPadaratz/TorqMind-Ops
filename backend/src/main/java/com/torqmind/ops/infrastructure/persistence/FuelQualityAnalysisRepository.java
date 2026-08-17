package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.occurrence.FuelQualityAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FuelQualityAnalysisRepository extends JpaRepository<FuelQualityAnalysis, Long> {
    List<FuelQualityAnalysis> findByOccurrenceIdIn(Collection<Long> occurrenceIds);
}
