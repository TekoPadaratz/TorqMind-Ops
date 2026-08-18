package com.torqmind.ops.application.task;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.torqmind.ops.application.task.TaskDetailService.ActivityView;
import com.torqmind.ops.application.task.TaskDetailService.AttachmentView;
import com.torqmind.ops.application.task.TaskDetailService.CommentView;
import com.torqmind.ops.application.task.TaskDetailService.RoutineSummary;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Comprovante de execucao de rotina: dados, evidencias com carimbo, comentarios e historico. */
public final class RoutineRunPdfRenderer {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private static final Color ACCENT = new Color(11, 122, 82);

    private RoutineRunPdfRenderer() {}

    public static byte[] render(RoutineSummary s, List<CommentView> comments,
                                List<AttachmentView> attachments, List<ActivityView> activities, String branchName) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 44, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, ACCENT);
            Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            doc.add(new Paragraph("Comprovante de execucao", title));
            Paragraph sub = new Paragraph(nvl(s.title(), "Rotina"), head);
            sub.setSpacingAfter(10);
            doc.add(sub);

            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            metaRow(meta, "Status", nvl(s.status(), "-"), head, normal);
            metaRow(meta, "Responsavel", s.assignee() == null ? "-" : nvl(s.assignee().name(), "-"), head, normal);
            metaRow(meta, "Filial", nvl(branchName, s.branchId() == null ? "-" : String.valueOf(s.branchId())), head, normal);
            metaRow(meta, "Agendado", fmt(s.scheduledFor()), head, normal);
            metaRow(meta, "Vencimento", fmt(s.dueAt()), head, normal);
            metaRow(meta, "Iniciado", fmt(s.startedAt()), head, normal);
            metaRow(meta, "Concluido", fmt(s.completedAt()), head, normal);
            meta.setSpacingAfter(12);
            doc.add(meta);

            if (s.executionComment() != null && !s.executionComment().isBlank()) {
                doc.add(sectionTitle("Observacao da execucao", head));
                doc.add(new Paragraph(s.executionComment(), normal));
            }

            doc.add(sectionTitle("Evidencias (fotos/anexos)", head));
            if (attachments == null || attachments.isEmpty()) {
                doc.add(new Paragraph("Sem anexos.", small));
            } else {
                PdfPTable t = new PdfPTable(new float[]{3, 2, 2});
                t.setWidthPercentage(100);
                headerCell(t, "Arquivo", head);
                headerCell(t, "Tipo", head);
                headerCell(t, "Carimbo (data/hora)", head);
                for (AttachmentView a : attachments) {
                    cell(t, nvl(a.fileName(), "-"), normal);
                    cell(t, nvl(a.mimeType(), "-"), normal);
                    cell(t, fmt(a.createdAt()), normal);
                }
                doc.add(t);
            }

            doc.add(sectionTitle("Comentarios", head));
            if (comments == null || comments.isEmpty()) {
                doc.add(new Paragraph("Sem comentarios.", small));
            } else {
                for (CommentView c : comments) {
                    String who = c.author() == null ? "-" : nvl(c.author().name(), "-");
                    doc.add(new Paragraph(who + " - " + fmt(c.createdAt()), small));
                    doc.add(new Paragraph(nvl(c.body(), ""), normal));
                }
            }

            doc.add(sectionTitle("Historico", head));
            if (activities == null || activities.isEmpty()) {
                doc.add(new Paragraph("Sem historico.", small));
            } else {
                for (ActivityView a : activities) {
                    String who = a.actor() == null ? "-" : nvl(a.actor().name(), "-");
                    String change = a.fromStatus() != null && a.toStatus() != null
                            ? " (" + a.fromStatus() + " -> " + a.toStatus() + ")" : "";
                    String msg = a.message() != null && !a.message().isBlank() ? ": " + a.message() : "";
                    doc.add(new Paragraph(fmt(a.createdAt()) + " - " + who + " - " + nvl(a.type(), "") + change + msg, small));
                }
            }

            Paragraph footer = new Paragraph("Gerado em " + DT.format(Instant.now()) + " - TorqMind Ops", small);
            footer.setSpacingBefore(16);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar o comprovante PDF.", ex);
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
