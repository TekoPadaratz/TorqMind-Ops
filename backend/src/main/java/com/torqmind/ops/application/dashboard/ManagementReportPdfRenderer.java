package com.torqmind.ops.application.dashboard;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.torqmind.ops.application.dashboard.DashboardService.BranchLoad;
import com.torqmind.ops.application.dashboard.DashboardService.OverdueLine;
import com.torqmind.ops.application.dashboard.DashboardService.ReportData;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Relatorio operacional gerencial por periodo: KPIs, envelhecimento, ranking, atrasos e ocorrencias. */
public final class ManagementReportPdfRenderer {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);
    private static final Color ACCENT = new Color(11, 122, 82);

    private ManagementReportPdfRenderer() {}

    public static byte[] render(ReportData r) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 44, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, ACCENT);
            Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            doc.add(new Paragraph("Relatorio operacional", title));
            Paragraph sub = new Paragraph(
                    (r.branchName() == null ? "Todas as filiais" : r.branchName())
                            + "  -  " + D.format(r.from()) + " a " + D.format(r.to().minusSeconds(1)), head);
            sub.setSpacingAfter(10);
            doc.add(sub);

            PdfPTable kpi = new PdfPTable(2);
            kpi.setWidthPercentage(100);
            metaRow(kpi, "Rotinas no periodo", String.valueOf(r.total()), head, normal);
            metaRow(kpi, "Concluidas", String.valueOf(r.completed()), head, normal);
            metaRow(kpi, "Concluidas no prazo", r.onTime() + " (" + r.onTimeRate() + "%)", head, normal);
            metaRow(kpi, "Pendentes", String.valueOf(r.pending()), head, normal);
            metaRow(kpi, "Em andamento", String.valueOf(r.inProgress()), head, normal);
            metaRow(kpi, "Atrasadas", String.valueOf(r.late()), head, normal);
            metaRow(kpi, "Rejeitadas", String.valueOf(r.rejected()), head, normal);
            kpi.setSpacingAfter(12);
            doc.add(kpi);

            doc.add(sectionTitle("Atrasos por tempo (situacao atual)", head));
            PdfPTable aging = new PdfPTable(4);
            aging.setWidthPercentage(100);
            headerCell(aging, "ate 1 dia", head);
            headerCell(aging, "1 a 3 dias", head);
            headerCell(aging, "3 a 7 dias", head);
            headerCell(aging, "+ de 7 dias", head);
            cell(aging, String.valueOf(r.aging().upTo1d()), normal);
            cell(aging, String.valueOf(r.aging().upTo3d()), normal);
            cell(aging, String.valueOf(r.aging().upTo7d()), normal);
            cell(aging, String.valueOf(r.aging().over7d()), normal);
            aging.setSpacingAfter(12);
            doc.add(aging);

            if (r.branchName() == null && !r.branchRanking().isEmpty()) {
                doc.add(sectionTitle("Ranking por filial (em aberto / atrasadas)", head));
                PdfPTable t = new PdfPTable(new float[]{4, 2, 2});
                t.setWidthPercentage(100);
                headerCell(t, "Filial", head);
                headerCell(t, "Em aberto", head);
                headerCell(t, "Atrasadas", head);
                for (BranchLoad b : r.branchRanking()) {
                    cell(t, nvl(b.branchName(), "-"), normal);
                    cell(t, String.valueOf(b.openCount()), normal);
                    cell(t, String.valueOf(b.lateCount()), normal);
                }
                t.setSpacingAfter(12);
                doc.add(t);
            }

            doc.add(sectionTitle("Tarefas atrasadas", head));
            if (r.overdue().isEmpty()) {
                doc.add(new Paragraph("Nenhuma tarefa atrasada no periodo.", small));
            } else {
                PdfPTable t = new PdfPTable(new float[]{1, 4, 3, 3, 3});
                t.setWidthPercentage(100);
                headerCell(t, "#", head);
                headerCell(t, "Tarefa", head);
                headerCell(t, "Filial", head);
                headerCell(t, "Responsavel", head);
                headerCell(t, "Vencimento", head);
                for (OverdueLine o : r.overdue()) {
                    cell(t, String.valueOf(o.id()), normal);
                    cell(t, nvl(o.title(), "-"), normal);
                    cell(t, nvl(o.branchName(), "-"), normal);
                    cell(t, nvl(o.assignee(), "-"), normal);
                    cell(t, fmt(o.dueAt()), normal);
                }
                doc.add(t);
            }

            doc.add(sectionTitle("Ocorrencias abertas no periodo", head));
            PdfPTable occ = new PdfPTable(2);
            occ.setWidthPercentage(100);
            metaRow(occ, "Total", String.valueOf(r.occTotal()), head, normal);
            metaRow(occ, "Abertas", String.valueOf(r.occOpen()), head, normal);
            metaRow(occ, "Em atendimento", String.valueOf(r.occInAttendance()), head, normal);
            metaRow(occ, "Aguardando validacao", String.valueOf(r.occAwaiting()), head, normal);
            metaRow(occ, "Encerradas", String.valueOf(r.occClosed()), head, normal);
            metaRow(occ, "Rejeitadas", String.valueOf(r.occRejected()), head, normal);
            doc.add(occ);

            Paragraph footer = new Paragraph("Gerado em " + DT.format(Instant.now()) + " - TorqMind Ops", small);
            footer.setSpacingBefore(16);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar o relatorio PDF.", ex);
        }
    }

    private static Paragraph sectionTitle(String text, Font head) {
        Paragraph p = new Paragraph(text, head);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    private static void metaRow(PdfPTable t, String label, String value, Font head, Font normal) {
        PdfPCell l = new PdfPCell(new Phrase(label, head));
        l.setBorder(0);
        l.setPaddingBottom(3);
        PdfPCell v = new PdfPCell(new Phrase(value, normal));
        v.setBorder(0);
        v.setPaddingBottom(3);
        t.addCell(l);
        t.addCell(v);
    }

    private static void headerCell(PdfPTable t, String text, Font head) {
        PdfPCell c = new PdfPCell(new Phrase(text, head));
        c.setBackgroundColor(new Color(240, 240, 240));
        c.setPadding(4);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(4);
        t.addCell(c);
    }

    private static String fmt(Instant i) {
        return i == null ? "-" : DT.format(i);
    }

    private static String nvl(String s, String d) {
        return s == null || s.isBlank() ? d : s;
    }
}
