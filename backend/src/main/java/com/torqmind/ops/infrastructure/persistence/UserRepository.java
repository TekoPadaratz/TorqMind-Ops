package com.torqmind.ops.infrastructure.persistence;

import com.torqmind.ops.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findFirstByEmailIgnoreCaseAndActiveTrue(String email);
    List<User> findByRoleIgnoreCaseAndActiveTrue(String role);
    List<User> findBySectorIdAndActiveTrue(Long sectorId);
    List<User> findByActiveTrue();
    List<User> findByCompanyIdAndActiveTrue(Long companyId);
    List<User> findByCompanyIdAndBranchIdAndActiveTrue(Long companyId, Long branchId);
    List<User> findByCompanyIdAndRoleIgnoreCaseAndActiveTrue(Long companyId, String role);
    List<User> findByCompanyIdAndBranchIdAndRoleIgnoreCaseAndActiveTrue(Long companyId, Long branchId, String role);
    List<User> findAllByOrderByFullNameAscUsernameAsc();
    long countByRoleIgnoreCaseAndActiveTrue(String role);
}
