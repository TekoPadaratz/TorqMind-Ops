package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.sector.Sector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectorRepository extends JpaRepository<Sector, Long> {
    List<Sector> findByCompanyIdOrderByName(Long companyId);
}
