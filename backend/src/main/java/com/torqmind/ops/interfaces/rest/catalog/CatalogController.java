package com.torqmind.ops.interfaces.rest.catalog;

import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.user.RoleLabels;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final SectorRepository sectorRepository;
    private final UserRepository userRepository;
    private final TenantResolver tenantResolver;

    public CatalogController(
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            SectorRepository sectorRepository,
            UserRepository userRepository,
            TenantResolver tenantResolver
    ) {
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.sectorRepository = sectorRepository;
        this.userRepository = userRepository;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/companies")
    public List<Company> companies(@AuthenticationPrincipal AppUserPrincipal me) {
        if (tenantResolver.isMaster(me)) {
            return companyRepository.findAll();
        }
        Long cid = tenantResolver.requireCompanyId(me);
        return companyRepository.findById(cid).stream().toList();
    }

    @GetMapping("/branches")
    public List<Branch> branches(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        List<Branch> all = branchRepository.findByCompanyIdOrderById(cid);
        Long branchFilter = tenantResolver.branchFilterOrNull(me);
        if (branchFilter == null) {
            return all;
        }
        return all.stream().filter(b -> branchFilter.equals(b.getId())).toList();
    }

    @GetMapping("/sectors")
    public List<Sector> sectors(
            @AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(required = false) Long companyId
    ) {
        Long cid = tenantResolver.resolveListCompanyId(me, companyId);
        List<Sector> all = sectorRepository.findByCompanyIdOrderByName(cid);
        Long branchFilter = tenantResolver.branchFilterOrNull(me);
        if (branchFilter == null) {
            return all;
        }
        return all.stream()
                .filter(s -> s.getBranchId() == null || branchFilter.equals(s.getBranchId()))
                .toList();
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@AuthenticationPrincipal AppUserPrincipal me) {
        List<User> list;
        if (tenantResolver.isMaster(me) && me.companyId() == null) {
            list = userRepository.findAll();
        } else {
            Long cid = tenantResolver.resolveListCompanyId(me, null);
            Long branchFilter = tenantResolver.branchFilterOrNull(me);
            list = branchFilter != null
                    ? userRepository.findByCompanyIdAndBranchIdAndActiveTrue(cid, branchFilter)
                    : userRepository.findByCompanyIdAndActiveTrue(cid);
        }
        return list.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId().toString());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("role", u.getRole());
        m.put("roleLabel", RoleLabels.pt(u.getRole()));
        m.put("companyId", u.getCompanyId());
        m.put("branchId", u.getBranchId());
        return m;
    }
}
