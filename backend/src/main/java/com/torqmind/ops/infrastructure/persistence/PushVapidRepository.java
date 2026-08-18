package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.push.PushVapid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushVapidRepository extends JpaRepository<PushVapid, Integer> {
}
