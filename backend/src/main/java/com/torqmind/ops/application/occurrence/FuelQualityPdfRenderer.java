package com.torqmind.ops.application.occurrence;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.torqmind.ops.domain.occurrence.FuelKind;
import com.torqmind.ops.domain.occurrence.FuelQualityAnalysis;
import com.torqmind.ops.domain.occurrence.Occurrence;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** PDF de análise de qualidade inspirado na planilha, sem fórmulas ou limites. */
public final class FuelQualityPdfRenderer {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Color ACCENT = new Color(11, 122, 82);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);

    private FuelQualityPdfRenderer() {}

    public static byte[] render(
            Occurrence occurrence,
            FuelQualityAnalysis analysis,
            List<FuelQualityAnalysisService.WitnessView> witnesses,
            String finalizedByName
    ) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 42, 42, 36, 42);
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, ACCENT);
            Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ACCENT);
            Font label = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            Font value = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable header = new PdfPTable(new float[] {18, 82});
            header.setWidthPercentage(100);
            PdfPCell mark = cell("TM", brand);
            mark.setBackgroundColor(ACCENT);
            mark.setHorizontalAlignment(Element.ALIGN_CENTER);
            mark.setVerticalAlignment(Element.ALIGN_MIDDLE);
            mark.setPadding(10);
            header.addCell(mark);
            PdfPCell heading = new PdfPCell();
            heading.setBorder(Rectangle.NO_BORDER);
            heading.setPaddingLeft(10);
            heading.addElement(new Paragraph("TorqMind Ops", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, ACCENT)));
            heading.addElement(new Paragraph("Análise de qualidade no recebimento de combustível", title));
            header.addCell(heading);
            document.add(header);
            document.add(gap());

            FuelKind fuel = analysis.getFuel();
            addSection(document, "Identificação do posto", section);
            document.add(kvTable(label, value,
                    "Razão social", nvl(analysis.getStationLegalName()),
                    "Nome do posto", nvl(analysis.getStationName()),
                    "CNPJ", nvl(analysis.getStationCnpj()),
                    "Endereço", nvl(analysis.getStationAddress().formatted())
            ));

            addSection(document, "Combustível", section);
            document.add(kvTable(label, value, "Produto", fuel == null ? "—" : fuel.label()));

            addSection(document, "Dados do recebimento", section);
            document.add(kvTable(label, value,
                    "Data da coleta", analysis.getCollectionDate() == null ? "—" : analysis.getCollectionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    "Volume recebido", nvl(analysis.getReceivedVolume()),
                    "Distribuidora", nvl(analysis.getDistributorName()),
                    "CNPJ da distribuidora", nvl(analysis.getDistributorCnpj()),
                    "Transportador", nvl(analysis.getTransporter()),
                    "NF-e do produto", nvl(analysis.getProductNfe()),
                    "Placa do caminhão", nvl(analysis.getTruckPlate()),
                    "Placa do reboque", nvl(analysis.getTrailerPlate()),
                    "Motorista", nvl(analysis.getDriverName()),
                    "RG/CPF do motorista", nvl(analysis.getDriverDocument()),
                    "Analista", nvl(analysis.getAnalystName())
            ));

            addSection(document, "Resultados da análise", section);
            List<String> resultPairs = new java.util.ArrayList<>(List.of(
                    "Aspecto", nvl(analysis.getAppearance()),
                    "Cor", nvl(analysis.getColor()),
                    "Massa específica a 20 °C", nvl(analysis.getSpecificMass20c())
            ));
            if (fuel != null && fuel.showsGasolineAlcohol()) {
                resultPairs.add("Teor de álcool na gasolina");
                resultPairs.add(nvl(analysis.getGasolineAlcoholContent()));
            } else if (fuel != null && fuel.showsAehcAlcohol()) {
                resultPairs.add("Teor alcoólico no AEHC");
                resultPairs.add(nvl(analysis.getAehcAlcoholContent()));
            } else {
                resultPairs.add("Medições de álcool");
                resultPairs.add("Não aplicável");
            }
            document.add(kvTable(label, value, resultPairs.toArray(String[]::new)));

            addSection(document, "Responsável, testemunhas e fechamento", section);
            document.add(kvTable(label, value,
                    "Responsável pelo preenchimento", nvl(analysis.getFilledByName()),
                    "Finalizado por", nvl(finalizedByName),
                    "Finalizado em", occurrence.getFinalizedAt() == null ? "—" : DT.format(occurrence.getFinalizedAt()),
                    "Assinatura do responsável", analysis.getResponsibleSignatureAttachmentId() == null ? "Não anexada" : "Anexada"
            ));
            if (witnesses != null && !witnesses.isEmpty()) {
                for (int i = 0; i < witnesses.size(); i++) {
                    FuelQualityAnalysisService.WitnessView w = witnesses.get(i);
                    document.add(kvTable(label, value,
                            "Testemunha " + (i + 1), nvl(w.name()),
                            "Documento", nvl(w.document()),
                            "Data/hora", w.signedAt() == null ? "—" : DT.format(w.signedAt()),
                            "Assinatura", w.signatureAttachmentId() == null ? "Não anexada" : "Anexada"
                    ));
                }
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar o documento da análise.", ex);
        }
    }

    private static void addSection(Document document, String title, Font font) throws Exception {
        Paragraph p = new Paragraph(title, font);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        document.add(p);
    }

    private static PdfPTable kvTable(Font label, Font value, String... pairs) {
        PdfPTable table = new PdfPTable(new float[] {36, 64});
        table.setWidthPercentage(100);
        table.setSpacingAfter(4);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            table.addCell(cell(pairs[i], label));
            table.addCell(cell(pairs[i + 1], value));
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "—" : text, font));
        cell.setPadding(5);
        cell.setBorderColor(new Color(210, 220, 214));
        return cell;
    }

    private static Paragraph gap() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(6);
        return p;
    }

    private static String nvl(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
