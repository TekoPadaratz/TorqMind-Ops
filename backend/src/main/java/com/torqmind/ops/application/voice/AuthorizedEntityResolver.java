package com.torqmind.ops.application.voice;

import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.RoutineStatus;
import com.torqmind.ops.domain.routine.RoutineRun;
import com.torqmind.ops.domain.routine.RoutineTemplate;
import com.torqmind.ops.domain.sector.Sector;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineRunRepository;
import com.torqmind.ops.infrastructure.persistence.RoutineTemplateRepository;
import com.torqmind.ops.infrastructure.persistence.SectorRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class AuthorizedEntityResolver {

    private final TenantResolver tenantResolver;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final SectorRepository sectorRepository;
    private final UserRepository userRepository;
    private final RoutineRunRepository runRepository;
    private final RoutineTemplateRepository templateRepository;
    private final OccurrenceRepository occurrenceRepository;

    public AuthorizedEntityResolver(
            TenantResolver tenantResolver,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            SectorRepository sectorRepository,
            UserRepository userRepository,
            RoutineRunRepository runRepository,
            RoutineTemplateRepository templateRepository,
            OccurrenceRepository occurrenceRepository
    ) {
        this.tenantResolver = tenantResolver;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.sectorRepository = sectorRepository;
        this.userRepository = userRepository;
        this.runRepository = runRepository;
        this.templateRepository = templateRepository;
        this.occurrenceRepository = occurrenceRepository;
    }

    public VoiceResolved resolve(AppUserPrincipal me, VoiceIntent intent, VoiceContext context) {
        VoiceResolved resolved = new VoiceResolved();
        Long companyId = tenantResolver.resolveListCompanyId(me, null);
        resolved.setCompanyId(companyId);
        companyRepository.findById(companyId).ifPresent(c -> resolved.setCompanyName(c.getName()));

        resolveCompany(me, intent, resolved);
        resolveBranch(me, intent, resolved);
        resolveUser(me, intent, resolved);
        resolveSector(me, intent, resolved);
        resolveTask(me, intent, context, resolved);
        return resolved;
    }

    public void applySelection(VoiceIntent intent, String field, String key) {
        if (field == null || key == null) {
            return;
        }
        switch (field) {
            case "companyReference" -> intent.setCompanyReference(key);
            case "branchReference", "cityReference" -> intent.setBranchReference(key);
            case "targetUserReference" -> intent.setTargetUserReference(key);
            case "targetSectorReference" -> intent.setTargetSectorReference(key);
            case "taskReference" -> intent.setTaskReference(key);
            default -> { }
        }
    }

    private void resolveCompany(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        String ref = intent.getCompanyReference();
        if (blank(ref)) {
            return;
        }
        if (ref.startsWith("c:")) {
            Long id = parseLong(ref.substring(2));
            Company c = companyRepository.findById(id).orElse(null);
            if (c != null) {
                tenantResolver.assertCanAccess(me, c.getId(), null);
                resolved.setCompanyId(c.getId());
                resolved.setCompanyName(c.getName());
            }
            return;
        }
        List<Company> all = tenantResolver.isMaster(me) ? companyRepository.findAll()
                : companyRepository.findById(resolved.getCompanyId()).stream().toList();
        List<Company> hits = all.stream().filter(c -> matches(c.getName(), ref)).toList();
        putAmbiguity(intent, "companyReference", ref, hits.stream()
                .map(c -> new VoiceOption("c:" + c.getId(), c.getName())).toList());
        if (hits.size() == 1) {
            resolved.setCompanyId(hits.get(0).getId());
            resolved.setCompanyName(hits.get(0).getName());
        }
    }

    private void resolveBranch(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        String ref = firstNonBlank(intent.getBranchReference(), intent.getCityReference());
        Long cid = resolved.getCompanyId();
        List<Branch> all = branchRepository.findByCompanyIdOrderById(cid);
        Long branchFilter = tenantResolver.branchFilterOrNull(me);
        if (branchFilter != null) {
            all = all.stream().filter(b -> branchFilter.equals(b.getId())).toList();
            if (all.size() == 1) {
                resolved.setBranchId(all.get(0).getId());
                resolved.setBranchName(all.get(0).getName());
            }
        }
        if (blank(ref)) {
            if (resolved.getBranchId() == null && all.size() == 1) {
                resolved.setBranchId(all.get(0).getId());
                resolved.setBranchName(all.get(0).getName());
            }
            return;
        }
        if (ref.startsWith("b:")) {
            Long id = parseLong(ref.substring(2));
            Branch b = all.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (b == null) {
                intent.getMissingFields().add("branchReference");
                return;
            }
            resolved.setBranchId(b.getId());
            resolved.setBranchName(b.getName());
            return;
        }
        List<Branch> hits = all.stream().filter(b -> matches(b.getName(), ref)).toList();
        putAmbiguity(intent, "branchReference", ref, hits.stream()
                .map(b -> new VoiceOption("b:" + b.getId(), b.getName())).toList());
        if (hits.size() == 1) {
            resolved.setBranchId(hits.get(0).getId());
            resolved.setBranchName(hits.get(0).getName());
        }
    }

    private void resolveUser(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        if (!"USER".equalsIgnoreCase(nvl(intent.getTargetType())) && blank(intent.getTargetUserReference())) {
            return;
        }
        String ref = intent.getTargetUserReference();
        Long cid = resolved.getCompanyId();
        Long bid = resolved.getBranchId() != null ? resolved.getBranchId() : tenantResolver.branchFilterOrNull(me);
        List<User> all = bid != null
                ? userRepository.findByCompanyIdAndBranchIdAndActiveTrue(cid, bid)
                : userRepository.findByCompanyIdAndActiveTrue(cid);
        all = all.stream().filter(u -> u.getRole() == null || !"MASTER".equalsIgnoreCase(u.getRole())).toList();
        if (blank(ref)) {
            intent.getMissingFields().add("targetUserReference");
            return;
        }
        if (ref.startsWith("u:")) {
            UUID id = UUID.fromString(ref.substring(2));
            User u = all.stream().filter(x -> id.equals(x.getId())).findFirst().orElse(null);
            if (u == null) {
                intent.getMissingFields().add("targetUserReference");
                return;
            }
            resolved.setUserId(u.getId());
            resolved.setUserName(u.getFullName());
            return;
        }
        List<User> hits = all.stream().filter(u -> matches(u.getFullName(), ref) || matches(u.getUsername(), ref)).toList();
        putAmbiguity(intent, "targetUserReference", ref, hits.stream()
                .map(u -> new VoiceOption("u:" + u.getId(), u.getFullName())).toList());
        if (hits.size() == 1) {
            resolved.setUserId(hits.get(0).getId());
            resolved.setUserName(hits.get(0).getFullName());
        }
    }

    private void resolveSector(AppUserPrincipal me, VoiceIntent intent, VoiceResolved resolved) {
        if (!"SECTOR".equalsIgnoreCase(nvl(intent.getTargetType())) && blank(intent.getTargetSectorReference())) {
            return;
        }
        String ref = intent.getTargetSectorReference();
        Long cid = resolved.getCompanyId();
        List<Sector> all = sectorRepository.findByCompanyIdOrderByName(cid);
        Long branchFilter = resolved.getBranchId() != null ? resolved.getBranchId() : tenantResolver.branchFilterOrNull(me);
        if (branchFilter != null) {
            all = all.stream().filter(s -> s.getBranchId() == null || branchFilter.equals(s.getBranchId())).toList();
        }
        if (blank(ref)) {
            intent.getMissingFields().add("targetSectorReference");
            return;
        }
        if (ref.startsWith("s:")) {
            Long id = parseLong(ref.substring(2));
            Sector s = all.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (s == null) {
                intent.getMissingFields().add("targetSectorReference");
                return;
            }
            resolved.setSectorId(s.getId());
            resolved.setSectorName(s.getName());
            return;
        }
        List<Sector> hits = all.stream().filter(s -> matches(s.getName(), ref)).toList();
        putAmbiguity(intent, "targetSectorReference", ref, hits.stream()
                .map(s -> new VoiceOption("s:" + s.getId(), s.getName())).toList());
        if (hits.size() == 1) {
            resolved.setSectorId(hits.get(0).getId());
            resolved.setSectorName(hits.get(0).getName());
        }
    }

    private void resolveTask(AppUserPrincipal me, VoiceIntent intent, VoiceContext context, VoiceResolved resolved) {
        String action = nvl(intent.getAction());
        boolean needsTask = List.of("START_TASK", "COMPLETE_TASK", "REJECT_TASK", "ADD_COMMENT", "OPEN_TASK").contains(action);
        if (!needsTask) {
            return;
        }
        if (context != null && context.getCurrentTaskId() != null
                && (blank(intent.getTaskReference()) || "current".equalsIgnoreCase(intent.getTaskReference()))) {
            bindCurrent(me, context, resolved, intent);
            return;
        }
        String ref = intent.getTaskReference();
        Long cid = resolved.getCompanyId();
        Long bid = tenantResolver.branchFilterOrNull(me);
        List<RoutineRun> runs = bid != null
                ? runRepository.findByCompanyIdAndBranchIdOrderByDueAtAsc(cid, bid)
                : runRepository.findByCompanyIdOrderByDueAtAsc(cid);
        List<Hit> hits = new ArrayList<>();
        for (RoutineRun run : runs) {
            if (run.getStatus() == RoutineStatus.CONCLUIDA || run.getStatus() == RoutineStatus.REJEITADA) {
                if (!"OPEN_TASK".equals(action)) {
                    continue;
                }
            }
            RoutineTemplate tpl = templateRepository.findById(run.getTemplateId()).orElse(null);
            String title = tpl == null ? "" : tpl.getTitle();
            if (blank(ref) || matches(title, ref) || ("current".equalsIgnoreCase(ref))) {
                if ("LIST_TASKS".equals(action)) {
                    continue;
                }
                if (intent.getRequestedStatus() == null || intent.getRequestedStatus().equals(run.getStatus().name())
                        || ("HOJE".equals(intent.getRequestedStatus()) && dueToday(run))) {
                    hits.add(new Hit(run.getId(), title, run.getStatus().name()));
                }
            }
        }
        if (!blank(ref) && ref.startsWith("r:")) {
            Long id = parseLong(ref.substring(2));
            RoutineRun run = runs.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
            if (run == null) {
                intent.getMissingFields().add("taskReference");
                return;
            }
            resolved.setRunId(run.getId());
            resolved.setTaskType("ROUTINE_RUN");
            templateRepository.findById(run.getTemplateId()).ifPresent(t -> resolved.setTaskTitle(t.getTitle()));
            return;
        }
        if (hits.isEmpty() && "OPEN_TASK".equals(action)) {
            List<Occurrence> occs = bid != null
                    ? occurrenceRepository.findByCompanyIdAndBranchIdOrderByCreatedAtDesc(cid, bid)
                    : occurrenceRepository.findByCompanyIdOrderByCreatedAtDesc(cid);
            List<Occurrence> ohits = occs.stream()
                    .filter(o -> blank(ref) || matches(o.getTitle(), ref) || matches(o.getDescription(), ref))
                    .limit(8)
                    .toList();
            putAmbiguity(intent, "taskReference", ref, ohits.stream()
                    .map(o -> new VoiceOption("o:" + o.getId(), o.getTitle())).toList());
            if (ohits.size() == 1) {
                resolved.setOccurrenceId(ohits.get(0).getId());
                resolved.setTaskType("OCCURRENCE");
                resolved.setTaskTitle(ohits.get(0).getTitle());
            }
            return;
        }
        putAmbiguity(intent, "taskReference", ref, hits.stream()
                .map(h -> new VoiceOption("r:" + h.id, h.title + " (" + h.status + ")")).toList());
        if (hits.size() == 1) {
            resolved.setRunId(hits.get(0).id);
            resolved.setTaskType("ROUTINE_RUN");
            resolved.setTaskTitle(hits.get(0).title);
        }
    }

    private void bindCurrent(AppUserPrincipal me, VoiceContext context, VoiceResolved resolved, VoiceIntent intent) {
        if ("OCCURRENCE".equalsIgnoreCase(context.getCurrentTaskType())) {
            Occurrence occ = occurrenceRepository.findById(context.getCurrentTaskId()).orElse(null);
            if (occ == null) {
                intent.getMissingFields().add("taskReference");
                return;
            }
            tenantResolver.assertCanAccess(me, occ.getCompanyId(), occ.getBranchId());
            resolved.setOccurrenceId(occ.getId());
            resolved.setTaskType("OCCURRENCE");
            resolved.setTaskTitle(occ.getTitle());
            return;
        }
        RoutineRun run = runRepository.findById(context.getCurrentTaskId()).orElse(null);
        if (run == null) {
            intent.getMissingFields().add("taskReference");
            return;
        }
        tenantResolver.assertCanAccess(me, run.getCompanyId(), run.getBranchId());
        resolved.setRunId(run.getId());
        resolved.setTaskType("ROUTINE_RUN");
        resolved.setTaskTitle(context.getCurrentTaskTitle());
    }

    private static void putAmbiguity(VoiceIntent intent, String field, String query, List<VoiceOption> options) {
        if (options.size() == 1) {
            return;
        }
        if (options.isEmpty()) {
            if (!intent.getMissingFields().contains(field)) {
                intent.getMissingFields().add(field);
            }
            return;
        }
        intent.getAmbiguities().removeIf(a -> field.equals(a.getField()));
        intent.getAmbiguities().add(new VoiceAmbiguity(field, query, options));
    }

    private static boolean dueToday(RoutineRun run) {
        if (run.getDueAt() == null) {
            return false;
        }
        LocalDate due = run.getDueAt().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
        return due.equals(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }

    public static boolean matches(String name, String query) {
        if (blank(name) || blank(query)) {
            return false;
        }
        String n = fold(name);
        String q = fold(query);
        return n.equals(q) || n.contains(q) || q.contains(n);
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (!blank(a)) return a;
        return b;
    }

    private static Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private record Hit(Long id, String title, String status) {}
}
