package com.torqmind.ops.application.occurrence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.company.PostalAddress;
import com.torqmind.ops.domain.occurrence.FuelKind;
import com.torqmind.ops.domain.occurrence.FuelQualityAnalysis;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.occurrence.OccurrenceKind;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.ops.StatusRules;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.FuelQualityAnalysisRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.documents.DocumentFormats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FuelQualityAnalysisService {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final OccurrenceService occurrenceService;
    private final OccurrenceRepository occurrenceRepository;
    private final FuelQualityAnalysisRepository analysisRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final TenantResolver tenantResolver;
    private final TenantAccessService tenantAccessService;
    private final TaskDetailService taskDetailService;
    private final ActivityService activityService;
    private final ObjectMapper objectMapper;

    public FuelQualityAnalysisService(
            OccurrenceService occurrenceService,
            OccurrenceRepository occurrenceRepository,
            FuelQualityAnalysisRepository analysisRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            UserRepository userRepository,
            TenantResolver tenantResolver,
            TenantAccessService tenantAccessService,
            TaskDetailService taskDetailService,
            ActivityService activityService,
            ObjectMapper objectMapper
    ) {
        this.occurrenceService = occurrenceService;
        this.occurrenceRepository = occurrenceRepository;
        this.analysisRepository = analysisRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.tenantResolver = tenantResolver;
        this.tenantAccessService = tenantAccessService;
        this.taskDetailService = taskDetailService;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    public QualityReceiptView defaults(AppUserPrincipal me, Long requestedCompanyId, Long requestedBranchId) {
        Long companyId = tenantResolver.resolveCompanyForCreate(me, requestedCompanyId);
        Long branchId = tenantResolver.resolveBranchForCreate(me, requestedBranchId);
        if (branchId == null) {
            throw new IllegalArgumentException("Informe o posto.");
        }
        tenantAccessService.requireBranchInCompany(companyId, branchId);
        tenantResolver.assertCanAccess(me, companyId, branchId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa inválida."));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Posto inválido."));
        FuelQualityAnalysis analysis = new FuelQualityAnalysis();
        StationSnapshot.copyInto(analysis, company, branch);
        String actorName = displayName(me);
        analysis.setAnalystName(actorName);
        analysis.setFilledByName(actorName);
        analysis.setFilledByUserId(me.userId());
        analysis.setCollectionDate(LocalDate.now(ZONE));
        return toView(null, analysis, OccurrenceStatus.ABERTA, null, null, false);
    }

    public QualityReceiptView get(AppUserPrincipal me, Long occurrenceId) {
        Occurrence occurrence = tenantAccessService.requireOccurrenceAccess(me, occurrenceId);
        requireQualityKind(occurrence);
        FuelQualityAnalysis analysis = analysisRepository.findById(occurrenceId)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada."));
        return toView(occurrence, analysis);
    }

    @Transactional
    public QualityReceiptView save(AppUserPrincipal me, Long occurrenceId, QualityReceiptRequest request) {
        Long companyId;
        Long branchId;
        Occurrence occurrence;
        if (occurrenceId == null) {
            companyId = tenantResolver.resolveCompanyForCreate(me, request.companyId());
            branchId = tenantResolver.resolveBranchForCreate(me, request.branchId());
            if (branchId == null) {
                throw new IllegalArgumentException("Informe o posto.");
            }
            tenantAccessService.requireBranchInCompany(companyId, branchId);
            tenantResolver.assertCanAccess(me, companyId, branchId);
            occurrence = new Occurrence();
            occurrence.setCompanyId(companyId);
            occurrence.setBranchId(branchId);
            occurrence.setKind(OccurrenceKind.FUEL_QUALITY_RECEIPT);
            occurrence.setPriority("MEDIA");
            occurrence.setAssigneeUserId(me.userId());
            applyTitle(occurrence, request, me);
            occurrence = occurrenceService.open(occurrence, me);
            occurrence.setKind(OccurrenceKind.FUEL_QUALITY_RECEIPT);
            occurrence = occurrenceRepository.save(occurrence);
        } else {
            occurrence = tenantAccessService.requireOccurrenceAccess(me, occurrenceId);
            requireQualityKind(occurrence);
            if (!StatusRules.canEditQualityReceipt(occurrence.getStatus())) {
                throw new IllegalArgumentException("Ocorrência finalizada. Edição comum não é permitida.");
            }
            companyId = occurrence.getCompanyId();
            branchId = occurrence.getBranchId();
        }

        FuelKind fuel = FuelKind.parse(request.fuel());
        Long savedOccurrenceId = occurrence.getId();
        FuelQualityAnalysis analysis = analysisRepository.findById(savedOccurrenceId).orElseGet(() -> {
            FuelQualityAnalysis created = new FuelQualityAnalysis();
            created.setOccurrenceId(savedOccurrenceId);
            return created;
        });
        analysis.setFuel(fuel);
        applyForm(analysis, request, me);
        Company company = companyRepository.findById(companyId).orElse(null);
        Branch branch = branchId == null ? null : branchRepository.findById(branchId).orElse(null);
        if (analysis.getStationLegalName() == null || analysis.getStationLegalName().isBlank()) {
            StationSnapshot.copyInto(analysis, company, branch);
        } else if (occurrenceId == null) {
            StationSnapshot.copyInto(analysis, company, branch);
        }
        analysisRepository.save(analysis);
        applyTitle(occurrence, request, me);
        occurrence.setUpdatedAt(Instant.now());

        boolean finalize = Boolean.TRUE.equals(request.finalizeOnSave());
        if (finalize) {
            if (!StatusRules.canTransitionOccurrence(occurrence.getStatus(), OccurrenceStatus.ENCERRADA, OccurrenceKind.FUEL_QUALITY_RECEIPT)) {
                throw new IllegalArgumentException("Transição de status de ocorrência inválida.");
            }
            OccurrenceStatus previous = occurrence.getStatus();
            Instant now = Instant.now();
            occurrence.setStatus(OccurrenceStatus.ENCERRADA);
            occurrence.setFinalizedAt(now);
            occurrence.setFinalizedBy(me.userId());
            occurrenceRepository.save(occurrence);
            activityService.record(TaskType.OCCURRENCE, occurrence.getId(), me.userId(), "STATUS_CHANGED",
                    previous.name(), OccurrenceStatus.ENCERRADA.name(), "Análise de qualidade finalizada.");
            byte[] pdf = FuelQualityPdfRenderer.render(occurrence, analysis, parseWitnesses(analysis.getWitnessesJson()), displayName(me));
            TaskDetailService.AttachmentView document = taskDetailService.addAttachment(
                    TaskType.OCCURRENCE, occurrence.getId(), me, "analise-qualidade-recebimento.pdf", pdf);
            occurrence.setDocumentAttachmentId(document.id());
        } else {
            activityService.record(TaskType.OCCURRENCE, occurrence.getId(), me.userId(), "UPDATED",
                    null, null, "Análise de qualidade salva como rascunho.");
        }
        Occurrence saved = occurrenceRepository.save(occurrence);
        return toView(saved, analysis);
    }

    public List<OccurrenceListItem> listItems(List<Occurrence> rows) {
        List<Long> ids = rows.stream()
                .filter(o -> o.getKind() == OccurrenceKind.FUEL_QUALITY_RECEIPT)
                .map(Occurrence::getId)
                .toList();
        Map<Long, FuelQualityAnalysis> analyses = ids.isEmpty()
                ? Map.of()
                : analysisRepository.findByOccurrenceIdIn(ids).stream()
                .collect(Collectors.toMap(FuelQualityAnalysis::getOccurrenceId, a -> a));
        List<OccurrenceListItem> out = new ArrayList<>();
        for (Occurrence occurrence : rows) {
            FuelQualityAnalysis analysis = analyses.get(occurrence.getId());
            out.add(new OccurrenceListItem(
                    occurrence.getId(),
                    occurrence.getTitle(),
                    occurrence.getDescription(),
                    occurrence.getStatus() == null ? null : occurrence.getStatus().name(),
                    occurrence.getPriority(),
                    occurrence.getKind() == null ? OccurrenceKind.GENERIC.name() : occurrence.getKind().name(),
                    occurrence.getCreatedAt(),
                    analysis == null || analysis.getFuel() == null ? null : analysis.getFuel().name(),
                    analysis == null || analysis.getFuel() == null ? null : analysis.getFuel().label(),
                    analysis == null ? null : analysis.getStationName(),
                    analysis == null || analysis.getCollectionDate() == null ? null : analysis.getCollectionDate().toString(),
                    analysis == null ? null : analysis.getFilledByName(),
                    occurrence.getDocumentAttachmentId() == null ? null : "/api/attachments/" + occurrence.getDocumentAttachmentId()
            ));
        }
        return out;
    }

    private void applyForm(FuelQualityAnalysis analysis, QualityReceiptRequest request, AppUserPrincipal me) {
        if (analysis.getFilledByUserId() == null) {
            analysis.setFilledByUserId(me.userId());
        }
        if (blank(analysis.getFilledByName())) {
            analysis.setFilledByName(displayName(me));
        }
        if (request.collectionDate() != null && !request.collectionDate().isBlank()) {
            analysis.setCollectionDate(LocalDate.parse(request.collectionDate()));
        } else if (analysis.getCollectionDate() == null) {
            analysis.setCollectionDate(LocalDate.now(ZONE));
        }
        analysis.setReceivedVolume(DocumentFormats.blankToNull(request.receivedVolume()));
        analysis.setDistributorName(DocumentFormats.blankToNull(request.distributorName()));
        analysis.setDistributorCnpj(DocumentFormats.cnpj(request.distributorCnpj()));
        analysis.setTransporter(DocumentFormats.blankToNull(request.transporter()));
        analysis.setProductNfe(DocumentFormats.blankToNull(request.productNfe()));
        analysis.setTruckPlate(DocumentFormats.plate(request.truckPlate()));
        analysis.setTrailerPlate(DocumentFormats.plate(request.trailerPlate()));
        analysis.setDriverName(DocumentFormats.blankToNull(request.driverName()));
        analysis.setDriverDocument(DocumentFormats.personDocument(request.driverDocument()));
        analysis.setAnalystName(blank(request.analystName()) ? displayName(me) : request.analystName().trim());
        analysis.setAppearance(DocumentFormats.blankToNull(request.appearance()));
        analysis.setColor(DocumentFormats.blankToNull(request.color()));
        analysis.setSpecificMass20c(DocumentFormats.blankToNull(request.specificMass20c()));
        if (request.gasolineAlcoholContent() != null) {
            analysis.setGasolineAlcoholContent(DocumentFormats.blankToNull(request.gasolineAlcoholContent()));
        }
        if (request.aehcAlcoholContent() != null) {
            analysis.setAehcAlcoholContent(DocumentFormats.blankToNull(request.aehcAlcoholContent()));
        }
        analysis.setResponsibleSignatureAttachmentId(request.responsibleSignatureAttachmentId());
        if (request.witnesses() != null) {
            analysis.setWitnessesJson(writeWitnesses(request.witnesses()));
        }
        if (!blank(request.filledByName())) {
            analysis.setFilledByName(request.filledByName().trim());
        }
    }

    private void applyTitle(Occurrence occurrence, QualityReceiptRequest request, AppUserPrincipal me) {
        FuelKind fuel = request.fuel() == null ? null : FuelKind.parse(request.fuel());
        String fuelLabel = fuel == null ? "combustível" : fuel.label();
        occurrence.setTitle("Análise de qualidade no recebimento — " + fuelLabel);
        String date = request.collectionDate() == null || request.collectionDate().isBlank()
                ? LocalDate.now(ZONE).toString()
                : request.collectionDate();
        occurrence.setDescription(fuelLabel + " · " + date + " · " + displayName(me));
    }

    private QualityReceiptView toView(Occurrence occurrence, FuelQualityAnalysis analysis) {
        return toView(
                occurrence == null ? null : occurrence.getId(),
                analysis,
                occurrence == null ? OccurrenceStatus.ABERTA : occurrence.getStatus(),
                occurrence == null ? null : occurrence.getFinalizedAt(),
                occurrenceDocumentUrl(occurrence),
                occurrence != null && !StatusRules.canEditQualityReceipt(occurrence.getStatus())
        );
    }

    private String occurrenceDocumentUrl(Occurrence occurrence) {
        if (occurrence == null || occurrence.getDocumentAttachmentId() == null) {
            return null;
        }
        return "/api/attachments/" + occurrence.getDocumentAttachmentId();
    }

    private QualityReceiptView toView(
            Long id,
            FuelQualityAnalysis analysis,
            OccurrenceStatus status,
            Instant finalizedAt,
            String documentUrl,
            boolean readOnly
    ) {
        FuelKind fuel = analysis.getFuel();
        PostalAddress addr = analysis.getStationAddress();
        return new QualityReceiptView(
                id,
                OccurrenceKind.FUEL_QUALITY_RECEIPT.name(),
                status == null ? OccurrenceStatus.ABERTA.name() : status.name(),
                fuel == null ? null : fuel.name(),
                fuel == null ? null : fuel.label(),
                fuel != null && fuel.showsGasolineAlcohol(),
                fuel != null && fuel.showsAehcAlcohol(),
                analysis.getStationName(),
                analysis.getStationLegalName(),
                analysis.getStationCnpj(),
                new AddressView(
                        addr.getStreet(), addr.getNumber(), addr.getComplement(),
                        addr.getNeighborhood(), addr.getCity(), addr.getState(), addr.getPostalCode(),
                        addr.formatted()
                ),
                analysis.getCollectionDate() == null ? null : analysis.getCollectionDate().toString(),
                analysis.getReceivedVolume(),
                analysis.getDistributorName(),
                analysis.getDistributorCnpj(),
                analysis.getTransporter(),
                analysis.getProductNfe(),
                analysis.getTruckPlate(),
                analysis.getTrailerPlate(),
                analysis.getDriverName(),
                analysis.getDriverDocument(),
                analysis.getAnalystName(),
                analysis.getAppearance(),
                analysis.getColor(),
                analysis.getSpecificMass20c(),
                analysis.getGasolineAlcoholContent(),
                analysis.getAehcAlcoholContent(),
                analysis.getFilledByName(),
                analysis.getFilledByUserId() == null ? null : analysis.getFilledByUserId().toString(),
                analysis.getResponsibleSignatureAttachmentId(),
                parseWitnesses(analysis.getWitnessesJson()),
                finalizedAt,
                documentUrl,
                readOnly
        );
    }

    List<WitnessView> parseWitnesses(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<WitnessView> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeWitnesses(List<WitnessView> witnesses) {
        try {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (WitnessView witness : witnesses) {
                if (witness == null || (blank(witness.name()) && blank(witness.document()) && witness.signatureAttachmentId() == null)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", DocumentFormats.blankToNull(witness.name()));
                row.put("document", DocumentFormats.personDocument(witness.document()));
                row.put("signedAt", witness.signedAt());
                row.put("signatureAttachmentId", witness.signatureAttachmentId());
                payload.add(row);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Testemunhas inválidas.");
        }
    }

    private String displayName(AppUserPrincipal me) {
        return userRepository.findById(me.userId())
                .map(User::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(me.username());
    }

    private static void requireQualityKind(Occurrence occurrence) {
        if (occurrence.getKind() != OccurrenceKind.FUEL_QUALITY_RECEIPT) {
            throw new IllegalArgumentException("Esta ocorrência não é uma análise de qualidade.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record AddressView(
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode,
            String formatted
    ) {}

    public record WitnessView(
            String name,
            String document,
            Instant signedAt,
            Long signatureAttachmentId
    ) {}

    public record QualityReceiptView(
            Long id,
            String kind,
            String status,
            String fuel,
            String fuelLabel,
            boolean showGasolineAlcohol,
            boolean showAehcAlcohol,
            String stationName,
            String stationLegalName,
            String stationCnpj,
            AddressView stationAddress,
            String collectionDate,
            String receivedVolume,
            String distributorName,
            String distributorCnpj,
            String transporter,
            String productNfe,
            String truckPlate,
            String trailerPlate,
            String driverName,
            String driverDocument,
            String analystName,
            String appearance,
            String color,
            String specificMass20c,
            String gasolineAlcoholContent,
            String aehcAlcoholContent,
            String filledByName,
            String filledByUserId,
            Long responsibleSignatureAttachmentId,
            List<WitnessView> witnesses,
            Instant finalizedAt,
            String documentUrl,
            boolean readOnly
    ) {}

    public record QualityReceiptRequest(
            Long companyId,
            Long branchId,
            String fuel,
            Boolean finalizeOnSave,
            String collectionDate,
            String receivedVolume,
            String distributorName,
            String distributorCnpj,
            String transporter,
            String productNfe,
            String truckPlate,
            String trailerPlate,
            String driverName,
            String driverDocument,
            String analystName,
            String appearance,
            String color,
            String specificMass20c,
            String gasolineAlcoholContent,
            String aehcAlcoholContent,
            String filledByName,
            Long responsibleSignatureAttachmentId,
            List<WitnessView> witnesses
    ) {}

    public record OccurrenceListItem(
            Long id,
            String title,
            String description,
            String status,
            String priority,
            String kind,
            Instant createdAt,
            String fuel,
            String fuelLabel,
            String stationName,
            String collectionDate,
            String filledByName,
            String documentUrl
    ) {}
}
