package com.torqmind.ops.application.storage;

import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.infrastructure.persistence.BranchRepository;
import com.torqmind.ops.infrastructure.persistence.CompanyRepository;
import com.torqmind.ops.infrastructure.storage.GoogleDriveStorageProvider;
import com.torqmind.ops.infrastructure.storage.StoragePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante pastas no Drive: empresa → filial → rotinas/ocorrencias.
 * No-op se o provider gdrive não estiver ativo.
 */
@Service
public class DriveFolderService {

    private static final Logger log = LoggerFactory.getLogger(DriveFolderService.class);

    private final ObjectProvider<GoogleDriveStorageProvider> driveProvider;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;

    public DriveFolderService(
            ObjectProvider<GoogleDriveStorageProvider> driveProvider,
            CompanyRepository companyRepository,
            BranchRepository branchRepository
    ) {
        this.driveProvider = driveProvider;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
    }

    public boolean isDriveActive() {
        GoogleDriveStorageProvider drive = driveProvider.getIfAvailable();
        return drive != null && drive.isEnabled();
    }

    @Transactional
    public Company ensureCompanyFolder(Company company) {
        GoogleDriveStorageProvider drive = driveProvider.getIfAvailable();
        if (drive == null || !drive.isEnabled() || company == null) {
            return company;
        }
        try {
            if (company.getDriveFolderId() != null && !company.getDriveFolderId().isBlank()) {
                return company;
            }
            String label = StoragePaths.folderLabel(company.getId(), company.getName());
            String folderId = drive.ensureChildFolder(drive.getRootFolderId(), label);
            company.setDriveFolderId(folderId);
            return companyRepository.save(company);
        } catch (Exception ex) {
            log.warn("Não foi possível criar pasta Drive da empresa {}: {}", company.getId(), ex.getMessage());
            return company;
        }
    }

    @Transactional
    public Branch ensureBranchFolder(Company company, Branch branch) {
        GoogleDriveStorageProvider drive = driveProvider.getIfAvailable();
        if (drive == null || !drive.isEnabled() || branch == null) {
            return branch;
        }
        try {
            if (branch.getDriveFolderId() != null && !branch.getDriveFolderId().isBlank()) {
                return branch;
            }
            Company c = company;
            if (c == null) {
                c = companyRepository.findById(branch.getCompanyId()).orElse(null);
            }
            if (c == null) {
                return branch;
            }
            c = ensureCompanyFolder(c);
            if (c.getDriveFolderId() == null) {
                return branch;
            }
            String label = StoragePaths.folderLabel(branch.getId(), branch.getName());
            String folderId = drive.ensureChildFolder(c.getDriveFolderId(), label);
            // subpastas padrão
            drive.ensureChildFolder(folderId, "rotinas");
            drive.ensureChildFolder(folderId, "ocorrencias");
            branch.setDriveFolderId(folderId);
            return branchRepository.save(branch);
        } catch (Exception ex) {
            log.warn("Não foi possível criar pasta Drive da filial {}: {}", branch.getId(), ex.getMessage());
            return branch;
        }
    }

    /** Caminho lógico relativo à raiz Ops: {emp}/{filial}/{rotinas|ocorrencias} */
    public String logicalFolder(Company company, Branch branch, String taskType) {
        String emp = StoragePaths.folderLabel(company.getId(), company.getName());
        String fil = branch != null
                ? StoragePaths.folderLabel(branch.getId(), branch.getName())
                : "sem-filial";
        return emp + "/" + fil + "/" + StoragePaths.taskKindFolder(taskType);
    }
}
