package com.torqmind.ops.application.admin;

import com.torqmind.ops.application.auth.CredentialService;
import com.torqmind.ops.application.storage.DriveFolderService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.user.PasswordChangeEvent;
import com.torqmind.ops.domain.user.Role;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.PasswordChangeEventRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.shared.api.ForbiddenException;
import com.torqmind.ops.shared.documents.DocumentFormats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,40}$");

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final CredentialService credentialService;
    private final PasswordChangeEventRepository passwordChangeEventRepository;
    private final DriveFolderService driveFolderService;
    private final TenantAccessService tenantAccessService;

    public AdminService(
            UserRepository userRepository,
            SectorRepository sectorRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            CredentialService credentialService,
            PasswordChangeEventRepository passwordChangeEventRepository,
            DriveFolderService driveFolderService,
            TenantAccessService tenantAccessService
    ) {
        this.userRepository = userRepository;
        this.sectorRepository = sectorRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.credentialService = credentialService;
        this.passwordChangeEventRepository = passwordChangeEventRepository;
        this.driveFolderService = driveFolderService;
        this.tenantAccessService = tenantAccessService;
    }

    @Transactional
    public User createUser(
            String actorRole,
            UUID actorId,
            String username,
            String fullName,
            String role,
            String password,
            Long companyId,
            Long branchId,
            Long sectorId,
            String email
    ) {
        requireMaster(actorRole);
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException("Usuário inválido: use 3 a 40 caracteres (a-z, 0-9, . _ -).");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Nome completo é obrigatório.");
        }

        userRepository.findByUsernameIgnoreCase(normalizedUsername).ifPresent(u -> {
            throw new IllegalArgumentException("Já existe um usuário com esse nome.");
        });

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(normalizedUsername);
        user.setFullName(fullName.trim());
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        applyAssignment(user, parseRole(role), companyId, branchId, sectorId);
        user.setEmail(uniqueEmail(normalizeEmail(email), user.getId()));
        credentialService.assignPassword(user, actorId, password, CredentialService.ACTION_CREATED, false);
        return user;
    }

    @Transactional
    public User updateUser(
            String actorRole,
            UUID actorId,
            UUID userId,
            String fullName,
            String role,
            Long companyId,
            Long branchId,
            Long sectorId,
            Boolean active,
            String email
    ) {
        requireMaster(actorRole);
        User user = requireUser(userId);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Nome completo é obrigatório.");
        }
        Role parsedRole = parseRole(role);
        boolean newActive = active == null ? user.isActive() : active;
        ensureAccountContinuity(user, actorId, parsedRole, newActive);
        user.setFullName(fullName.trim());
        user.setActive(newActive);
        if (email != null) {
            user.setEmail(uniqueEmail(normalizeEmail(email), user.getId()));
        }
        applyAssignment(user, parsedRole, companyId, branchId, sectorId);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public User resetPassword(String actorRole, UUID actorId, UUID userId, String newPassword) {
        requireMaster(actorRole);
        User user = requireUser(userId);
        credentialService.assignPassword(user, actorId, newPassword, CredentialService.ACTION_ADMIN_RESET, true);
        return user;
    }

    @Transactional
    public User unlockUser(String actorRole, UUID userId) {
        requireMaster(actorRole);
        User user = requireUser(userId);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String e = email.trim().toLowerCase();
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("E-mail invalido.");
        }
        return e;
    }

    private String uniqueEmail(String email, UUID selfId) {
        if (email == null) {
            return null;
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(other -> {
            if (!other.getId().equals(selfId)) {
                throw new IllegalArgumentException("Ja existe um usuario com esse e-mail.");
            }
        });
        return email;
    }

    public List<User> listUsers() {
        return userRepository.findAllByOrderByFullNameAscUsernameAsc();
    }

    public List<PasswordEventView> listPasswordEvents(String actorRole, UUID userId) {
        requireMaster(actorRole);
        requireUser(userId);
        List<PasswordChangeEvent> events = passwordChangeEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<UUID> actorIds = events.stream()
                .map(PasswordChangeEvent::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return events.stream().map(event -> {
            User actor = event.getActorUserId() == null ? null : actors.get(event.getActorUserId());
            String actorName = actor == null ? null : actor.getFullName();
            String actorUsername = actor == null ? null : actor.getUsername();
            return new PasswordEventView(
                    event.getId(),
                    event.getAction(),
                    passwordActionLabel(event.getAction()),
                    event.getActorUserId(),
                    actorName,
                    actorUsername,
                    event.getCreatedAt()
            );
        }).toList();
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId == null ? new UUID(0, 0) : userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário inválido."));
    }

    private static void requireMaster(String actorRole) {
        if (!"MASTER".equals(actorRole)) {
            throw new ForbiddenException("Somente administrador pode gerenciar usuários.");
        }
    }

    private Role parseRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        try {
            return Role.valueOf(normalizedRole);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Função inválida.");
        }
    }

    private void applyAssignment(User user, Role parsedRole, Long companyId, Long branchId, Long sectorId) {
        Long resolvedCompany = parsedRole == Role.MASTER ? null : companyId;
        Long resolvedBranch = parsedRole == Role.MASTER ? null : branchId;
        Long resolvedSector = parsedRole == Role.MASTER ? null : sectorId;
        if (parsedRole != Role.MASTER) {
            if (resolvedCompany == null) {
                throw new IllegalArgumentException("Selecione a empresa do usuário.");
            }
            if ((parsedRole == Role.MANAGER || parsedRole == Role.OPERATOR) && resolvedBranch == null) {
                throw new IllegalArgumentException("Gerente e funcionário precisam de uma filial.");
            }
        }
        if (resolvedCompany != null && companyRepository.findById(resolvedCompany).isEmpty()) {
            throw new IllegalArgumentException("Empresa inválida.");
        }
        tenantAccessService.requireBranchInCompany(resolvedCompany, resolvedBranch);
        if (resolvedSector != null) {
            if (resolvedCompany == null) {
                throw new IllegalArgumentException("Selecione a empresa do setor.");
            }
            tenantAccessService.requireTargetSector(resolvedCompany, resolvedBranch, resolvedSector);
        }
        user.setRole(parsedRole.name());
        user.setCompanyId(resolvedCompany);
        user.setBranchId(resolvedBranch);
        user.setSectorId(resolvedSector);
    }

    private void ensureAccountContinuity(User user, UUID actorId, Role newRole, boolean newActive) {
        if (!newActive && user.getId().equals(actorId)) {
            throw new IllegalArgumentException("Você não pode desativar a própria conta.");
        }
        boolean wasActiveMaster = user.isActive() && "MASTER".equalsIgnoreCase(user.getRole());
        boolean remainsActiveMaster = newActive && newRole == Role.MASTER;
        if (wasActiveMaster && !remainsActiveMaster) {
            long activeMasters = userRepository.countByRoleIgnoreCaseAndActiveTrue("MASTER");
            if (activeMasters <= 1) {
                throw new IllegalArgumentException("Não é possível desativar ou alterar o último administrador.");
            }
        }
    }

    private static String passwordActionLabel(String action) {
        if ("CREATED".equals(action)) {
            return "Cadastro";
        }
        if ("SELF_CHANGE".equals(action)) {
            return "Troca pelo usuário";
        }
        if ("ADMIN_RESET".equals(action)) {
            return "Redefinição pelo administrador";
        }
        return action;
    }

    public record PasswordEventView(
            Long id,
            String action,
            String actionLabel,
            UUID actorUserId,
            String actorName,
            String actorUsername,
            Instant createdAt
    ) {}

    @Transactional
    public Sector createSector(String name, Long companyId, Long branchId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do setor é obrigatório.");
        }
        if (companyId == null || companyRepository.findById(companyId).isEmpty()) {
            throw new IllegalArgumentException("Empresa inválida.");
        }
        tenantAccessService.requireBranchInCompany(companyId, branchId);
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
    public Company createCompany(
            String name,
            String legalName,
            String cnpj,
            PostalAddressRequest address
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da empresa é obrigatório.");
        }
        Company company = new Company();
        applyLegal(company, name, legalName, cnpj, address);
        Company saved = companyRepository.save(company);
        return driveFolderService.ensureCompanyFolder(saved);
    }

    @Transactional
    public Company updateCompany(
            Long id,
            String name,
            String legalName,
            String cnpj,
            PostalAddressRequest address
    ) {
        Company company = companyRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa inválida."));
        applyLegal(company, name, legalName, cnpj, address);
        return companyRepository.save(company);
    }

    @Transactional
    public Branch createBranch(Long companyId, String name, String legalName, String cnpj, PostalAddressRequest address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da filial é obrigatório.");
        }
        Company company = companyRepository.findById(companyId == null ? -1L : companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa inválida."));
        Branch branch = new Branch();
        branch.setCompanyId(company.getId());
        applyLegal(branch, name, legalName, cnpj, address);
        Branch saved = branchRepository.save(branch);
        return driveFolderService.ensureBranchFolder(company, saved);
    }

    @Transactional
    public Branch updateBranch(Long id, String name, String legalName, String cnpj, PostalAddressRequest address) {
        Branch branch = branchRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> new IllegalArgumentException("Filial inválida."));
        applyLegal(branch, name, legalName, cnpj, address);
        return branchRepository.save(branch);
    }

    private void applyLegal(Company company, String name, String legalName, String cnpj, PostalAddressRequest address) {
        if (name != null && !name.isBlank()) {
            company.setName(name.trim());
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da empresa é obrigatório.");
        }
        company.setLegalName(blankToNull(legalName));
        company.setCnpj(DocumentFormats.cnpj(cnpj));
        applyAddress(company.getAddress(), address);
    }

    private void applyLegal(Branch branch, String name, String legalName, String cnpj, PostalAddressRequest address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da filial é obrigatório.");
        }
        branch.setName(name.trim());
        branch.setLegalName(blankToNull(legalName));
        branch.setCnpj(DocumentFormats.cnpj(cnpj));
        applyAddress(branch.getAddress(), address);
    }

    private void applyAddress(com.torqmind.ops.domain.company.PostalAddress target, PostalAddressRequest address) {
        if (target == null || address == null) {
            return;
        }
        target.setStreet(blankToNull(address.street()));
        target.setNumber(blankToNull(address.number()));
        target.setComplement(blankToNull(address.complement()));
        target.setNeighborhood(blankToNull(address.neighborhood()));
        target.setCity(blankToNull(address.city()));
        target.setState(DocumentFormats.uf(address.state()));
        target.setPostalCode(DocumentFormats.postalCode(address.postalCode()));
    }

    private static String blankToNull(String value) {
        return DocumentFormats.blankToNull(value);
    }

    public record PostalAddressRequest(
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode
    ) {}
}
