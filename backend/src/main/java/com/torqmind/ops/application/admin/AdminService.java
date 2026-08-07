package com.torqmind.ops.application.admin;

import com.torqmind.ops.application.storage.DriveFolderService;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.user.Role;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AdminService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,40}$");

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final DriveFolderService driveFolderService;

    public AdminService(
            UserRepository userRepository,
            SectorRepository sectorRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            PasswordEncoder passwordEncoder,
            DriveFolderService driveFolderService
    ) {
        this.userRepository = userRepository;
        this.sectorRepository = sectorRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.driveFolderService = driveFolderService;
    }

    @Transactional
    public User createUser(
            String actorRole,
            String username,
            String fullName,
            String role,
            String password,
            Long companyId,
            Long branchId,
            Long sectorId
    ) {
        if (!"MASTER".equals(actorRole)) {
            throw new ForbiddenException("Somente administrador pode cadastrar usuários.");
        }
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException("Usuário inválido: use 3 a 40 caracteres (a-z, 0-9, . _ -).");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Nome completo é obrigatório.");
        }

        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        Role parsedRole;
        try {
            parsedRole = Role.valueOf(normalizedRole);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Função inválida.");
        }

        if (parsedRole != Role.MASTER) {
            if (companyId == null) {
                throw new IllegalArgumentException("Selecione a empresa do usuário.");
            }
            if ((parsedRole == Role.MANAGER || parsedRole == Role.OPERATOR) && branchId == null) {
                throw new IllegalArgumentException("Gerente e funcionário precisam de uma filial.");
            }
        }

        PasswordPolicy.validate(password);

        userRepository.findByUsernameIgnoreCase(normalizedUsername).ifPresent(u -> {
            throw new IllegalArgumentException("Já existe um usuário com esse nome.");
        });

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(normalizedUsername);
        user.setFullName(fullName.trim());
        user.setRole(parsedRole.name());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setCompanyId(companyId);
        user.setBranchId(branchId);
        user.setSectorId(sectorId);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public Sector createSector(String name, Long companyId, Long branchId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do setor é obrigatório.");
        }
        if (companyId == null || companyRepository.findById(companyId).isEmpty()) {
            throw new IllegalArgumentException("Empresa inválida.");
        }
        Sector sector = new Sector();
        sector.setName(name.trim());
        sector.setCompanyId(companyId);
        sector.setBranchId(branchId);
        sector.setCreatedAt(Instant.now());
        return sectorRepository.save(sector);
    }

    public List<Sector> listSectors(Long companyId) {
        return sectorRepository.findByCompanyIdOrderByName(companyId);
    }

    @Transactional
    public Company createCompany(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da empresa é obrigatório.");
        }
        Company company = new Company();
        company.setName(name.trim());
        Company saved = companyRepository.save(company);
        return driveFolderService.ensureCompanyFolder(saved);
    }

    @Transactional
    public Branch createBranch(Long companyId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da filial é obrigatório.");
        }
        Company company = companyRepository.findById(companyId == null ? -1L : companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa inválida."));
        Branch branch = new Branch();
        branch.setCompanyId(company.getId());
        branch.setName(name.trim());
        Branch saved = branchRepository.save(branch);
        return driveFolderService.ensureBranchFolder(company, saved);
    }
}
