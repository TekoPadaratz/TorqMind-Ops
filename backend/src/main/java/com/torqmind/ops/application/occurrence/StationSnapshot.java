package com.torqmind.ops.application.occurrence;

import com.torqmind.ops.domain.company.Branch;
import com.torqmind.ops.domain.company.Company;
import com.torqmind.ops.domain.company.PostalAddress;
import com.torqmind.ops.domain.occurrence.FuelQualityAnalysis;
import com.torqmind.ops.shared.documents.DocumentFormats;

final class StationSnapshot {

    private StationSnapshot() {}

    static void copyInto(FuelQualityAnalysis analysis, Company company, Branch branch) {
        String stationName = first(branch == null ? null : branch.getName(), company == null ? null : company.getName());
        analysis.setStationName(stationName);
        analysis.setStationLegalName(first(
                branch == null ? null : branch.getLegalName(),
                company == null ? null : company.getLegalName(),
                stationName
        ));
        analysis.setStationCnpj(DocumentFormats.cnpj(first(
                branch == null ? null : branch.getCnpj(),
                company == null ? null : company.getCnpj()
        )));
        PostalAddress source = addressOf(branch);
        if (source == null || source.isBlank()) {
            source = addressOf(company);
        }
        analysis.setStationAddress(source == null ? new PostalAddress() : source.copy());
        PostalAddress addr = analysis.getStationAddress();
        addr.setPostalCode(DocumentFormats.postalCode(addr.getPostalCode()));
        addr.setState(DocumentFormats.uf(addr.getState()));
    }

    private static PostalAddress addressOf(Company company) {
        return company == null ? null : company.getAddress();
    }

    private static PostalAddress addressOf(Branch branch) {
        return branch == null ? null : branch.getAddress();
    }

    private static String first(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
