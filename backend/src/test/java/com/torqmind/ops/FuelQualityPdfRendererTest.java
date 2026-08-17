package com.torqmind.ops;

import com.torqmind.ops.application.occurrence.FuelQualityAnalysisService;
import com.torqmind.ops.application.occurrence.FuelQualityPdfRenderer;
import com.torqmind.ops.domain.company.PostalAddress;
import com.torqmind.ops.domain.occurrence.FuelKind;
import com.torqmind.ops.domain.occurrence.FuelQualityAnalysis;
import com.torqmind.ops.domain.occurrence.Occurrence;
import com.torqmind.ops.domain.ops.OccurrenceStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

class FuelQualityPdfRendererTest {

    @Test
    void rendersReadablePdfWithStationSnapshot() {
        Occurrence occurrence = new Occurrence();
        occurrence.setStatus(OccurrenceStatus.ENCERRADA);
        occurrence.setFinalizedAt(Instant.parse("2026-08-17T13:00:00Z"));
        FuelQualityAnalysis analysis = new FuelQualityAnalysis();
        analysis.setFuel(FuelKind.GASOLINA_COMUM);
        analysis.setStationLegalName("Posto Exemplo Ltda");
        analysis.setStationCnpj("12.345.678/0001-90");
        analysis.setCollectionDate(LocalDate.of(2026, 8, 17));
        analysis.setAppearance("Límpido");
        PostalAddress address = new PostalAddress();
        address.setCity("Curitiba");
        address.setState("PR");
        analysis.setStationAddress(address);

        byte[] pdf = FuelQualityPdfRenderer.render(
                occurrence,
                analysis,
                List.of(new FuelQualityAnalysisService.WitnessView("Maria", "MG-1", Instant.parse("2026-08-17T13:05:00Z"), 1L)),
                "João Analista"
        );
        Assertions.assertTrue(pdf.length > 200);
        String head = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        Assertions.assertEquals("%PDF-", head);
    }
}
