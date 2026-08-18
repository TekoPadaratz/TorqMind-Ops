package com.torqmind.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.torqmind.ops.application.occurrence.FuelQualityAnalysisService;
import com.torqmind.ops.application.occurrence.OccurrenceService;
import com.torqmind.ops.application.task.ActivityService;
import com.torqmind.ops.application.task.TaskDetailService;
import com.torqmind.ops.application.tenant.TenantAccessService;
import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.occurrence.FuelQualityAnalysis;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.occurrence.OccurrenceKind;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import com.torqmind.ops.domain.task.TaskType;
import com.torqmind.ops.domain.user.User;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.persistence.FuelQualityAnalysisRepository;
import com.torqmind.ops.infrastructure.persistence.OccurrenceRepository;
import com.torqmind.ops.infrastructure.persistence.UserRepository;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FuelQualityAnalysisServiceTest {

    private OccurrenceService occurrenceService;
    private OccurrenceRepository occurrenceRepository;
    private FuelQualityAnalysisRepository analysisRepository;
    private CompanyRepository companyRepository;
    private BranchRepository branchRepository;
    private UserRepository userRepository;
    private TenantResolver tenantResolver;
    private TenantAccessService tenantAccessService;
    private TaskDetailService taskDetailService;
    private ActivityService activityService;
    private FuelQualityAnalysisService service;
    private AppUserPrincipal me;
    private UUID userId;

    @BeforeEach
    void setup() {
        occurrenceService = Mockito.mock(OccurrenceService.class);
        occurrenceRepository = Mockito.mock(OccurrenceRepository.class);
        analysisRepository = Mockito.mock(FuelQualityAnalysisRepository.class);
        companyRepository = Mockito.mock(CompanyRepository.class);
        branchRepository = Mockito.mock(BranchRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        tenantResolver = Mockito.mock(TenantResolver.class);
        tenantAccessService = Mockito.mock(TenantAccessService.class);
        taskDetailService = Mockito.mock(TaskDetailService.class);
        activityService = Mockito.mock(ActivityService.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        service = new FuelQualityAnalysisService(
                occurrenceService, occurrenceRepository, analysisRepository,
                companyRepository, branchRepository, userRepository,
                tenantResolver, tenantAccessService, taskDetailService, activityService, mapper
        );
        userId = UUID.randomUUID();
        me = new AppUserPrincipal(userId, "op", "OPERATOR", 1L, 2L);
        Mockito.when(tenantResolver.resolveCompanyForCreate(Mockito.eq(me), Mockito.any())).thenReturn(1L);
        Mockito.when(tenantResolver.resolveBranchForCreate(Mockito.eq(me), Mockito.any())).thenReturn(2L);
        Mockito.when(occurrenceRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(analysisRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        User user = new User();
        user.setId(userId);
        user.setFullName("Ana Operadora");
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Company company = new Company();
        company.setName("Rede");
        company.setLegalName("Rede Combustiveis LTDA");
        company.setCnpj("11222333000181");
        company.getAddress().setCity("Curitiba");
        company.getAddress().setState("PR");
        Branch branch = new Branch();
        branch.setCompanyId(1L);
        branch.setName("Posto Centro");
        branch.setLegalName("Posto Centro LTDA");
        branch.setCnpj("99888777000166");
        branch.getAddress().setStreet("Rua das Bombas");
        branch.getAddress().setNumber("100");
        branch.getAddress().setCity("Curitiba");
        branch.getAddress().setState("PR");
        Mockito.when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Mockito.when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
    }

    @Test
    void defaultsFillStationFromAuthenticatedBranch() {
        FuelQualityAnalysisService.QualityReceiptView view = service.defaults(me, null, 2L);
        Assertions.assertEquals("Posto Centro LTDA", view.stationLegalName());
        Assertions.assertEquals("99.888.777/0001-66", view.stationCnpj());
        Assertions.assertEquals("Ana Operadora", view.analystName());
        Assertions.assertEquals("Ana Operadora", view.filledByName());
        Assertions.assertNotNull(view.collectionDate());
        Assertions.assertTrue(view.stationAddress().formatted().contains("Rua das Bombas"));
    }

    @Test
    void isolationBlocksOtherCompany() {
        Mockito.when(tenantAccessService.requireOccurrenceAccess(me, 99L))
                .thenThrow(new ForbiddenException("Este recurso pertence a outra empresa."));
        Assertions.assertThrows(ForbiddenException.class, () -> service.get(me, 99L));
    }

    @Test
    void draftSaveDoesNotFinalizeOrGeneratePdf() {
        mockCreateOpen();
        FuelQualityAnalysisService.QualityReceiptView saved = service.save(me, null, request(false));
        Assertions.assertEquals("ABERTA", saved.status());
        Mockito.verify(taskDetailService, Mockito.never()).addAttachment(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        ArgumentCaptor<FuelQualityAnalysis> captor = ArgumentCaptor.forClass(FuelQualityAnalysis.class);
        Mockito.verify(analysisRepository).save(captor.capture());
        Assertions.assertEquals("Posto Centro LTDA", captor.getValue().getStationLegalName());
    }

    @Test
    void finalizeCheckboxGeneratesDocumentAndLocksEdit() {
        mockCreateOpen();
        Mockito.when(taskDetailService.addAttachment(
                Mockito.eq(TaskType.OCCURRENCE), Mockito.eq(10L), Mockito.eq(me),
                Mockito.anyString(), Mockito.any()
        )).thenReturn(new TaskDetailService.AttachmentView(
                77L, "analise.pdf", "application/pdf", 200, "/api/attachments/77", null, Instant.now(), null, null));

        FuelQualityAnalysisService.QualityReceiptView saved = service.save(me, null, request(true));
        Assertions.assertEquals("ENCERRADA", saved.status());
        Assertions.assertEquals("/api/attachments/77", saved.documentUrl());
        Assertions.assertTrue(saved.readOnly());
        Mockito.verify(taskDetailService).addAttachment(
                Mockito.eq(TaskType.OCCURRENCE), Mockito.eq(10L), Mockito.eq(me),
                Mockito.eq("analise-qualidade-recebimento.pdf"), Mockito.any(byte[].class));
    }

    @Test
    void snapshotPersistsIndependentlyFromLaterCadastro() {
        mockCreateOpen();
        service.save(me, null, request(false));
        ArgumentCaptor<FuelQualityAnalysis> captor = ArgumentCaptor.forClass(FuelQualityAnalysis.class);
        Mockito.verify(analysisRepository).save(captor.capture());
        String snap = captor.getValue().getStationCnpj();
        Company company = companyRepository.findById(1L).orElseThrow();
        company.setCnpj("00000000000000");
        Assertions.assertEquals("99.888.777/0001-66", snap);
        Assertions.assertNotEquals(snap, company.getCnpj());
    }

    @Test
    void alcoholFieldsRemainStoredWhenHiddenForDiesel() {
        Occurrence occurrence = qualityOccurrence(OccurrenceStatus.ABERTA);
        Mockito.when(tenantAccessService.requireOccurrenceAccess(me, 10L)).thenReturn(occurrence);
        FuelQualityAnalysis existing = new FuelQualityAnalysis();
        existing.setOccurrenceId(10L);
        existing.setFuel(com.torqmind.ops.domain.occurrence.FuelKind.GASOLINA_COMUM);
        existing.setGasolineAlcoholContent("27");
        existing.setStationLegalName("Posto Centro LTDA");
        Mockito.when(analysisRepository.findById(10L)).thenReturn(Optional.of(existing));

        FuelQualityAnalysisService.QualityReceiptRequest diesel = new FuelQualityAnalysisService.QualityReceiptRequest(
                1L, 2L, "DIESEL_S10", false, "2026-08-17", null, null, null, null, null,
                null, null, null, null, "Ana Operadora", null, null, null, null, "27",
                "Ana Operadora", null, List.of()
        );
        FuelQualityAnalysisService.QualityReceiptView view = service.save(me, 10L, diesel);
        Assertions.assertFalse(view.showGasolineAlcohol());
        Assertions.assertEquals("27", view.gasolineAlcoholContent());
    }

    @Test
    void finalizedOccurrenceRejectsCommonEdit() {
        Occurrence occurrence = qualityOccurrence(OccurrenceStatus.ENCERRADA);
        Mockito.when(tenantAccessService.requireOccurrenceAccess(me, 10L)).thenReturn(occurrence);
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.save(me, 10L, request(false)));
    }

    private void mockCreateOpen() {
        Mockito.when(occurrenceService.open(Mockito.any(), Mockito.eq(me))).thenAnswer(inv -> {
            Occurrence o = inv.getArgument(0);
            setId(o, 10L);
            o.setStatus(OccurrenceStatus.ABERTA);
            o.setKind(OccurrenceKind.FUEL_QUALITY_RECEIPT);
            return o;
        });
        Mockito.when(analysisRepository.findById(10L)).thenReturn(Optional.empty());
    }

    private Occurrence qualityOccurrence(OccurrenceStatus status) {
        Occurrence occurrence = new Occurrence();
        setId(occurrence, 10L);
        occurrence.setCompanyId(1L);
        occurrence.setBranchId(2L);
        occurrence.setKind(OccurrenceKind.FUEL_QUALITY_RECEIPT);
        occurrence.setStatus(status);
        occurrence.setTitle("Análise");
        occurrence.setDescription("x");
        return occurrence;
    }

    private FuelQualityAnalysisService.QualityReceiptRequest request(boolean finalize) {
        return new FuelQualityAnalysisService.QualityReceiptRequest(
                1L, 2L, "GASOLINA_COMUM", finalize, "2026-08-17", "10000",
                "Distribuidora", "11222333000181", null, null, "ABC1D23", null,
                "Motorista", "MG123", "Ana Operadora", "Límpido", "Amarela",
                "0,74", "27", null, "Ana Operadora", null, List.of()
        );
    }

    private static void setId(Occurrence occurrence, Long id) {
        try {
            var field = Occurrence.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(occurrence, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
