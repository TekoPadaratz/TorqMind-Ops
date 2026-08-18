package com.torqmind.ops;

import com.torqmind.ops.application.task.RoutineRunPdfRenderer;
import com.torqmind.ops.application.task.TaskDetailService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class RoutineRunPdfRendererTest {

    @Test
    void rendersNonEmptyPdf() {
        TaskDetailService.RoutineSummary s = new TaskDetailService.RoutineSummary(
                1L, "Afericao de bomba", "Conferir lacres", "CONCLUIDA",
                Instant.now(), Instant.now(), Instant.now(), Instant.now(),
                2L, new TaskDetailService.UserRef("u", "Alfredo"), true, true, "Tudo certo");

        byte[] pdf = RoutineRunPdfRenderer.render(s, List.of(), List.of(), List.of(), "Filial Centro");

        Assertions.assertTrue(pdf.length > 200);
        // assinatura de PDF: %PDF
        Assertions.assertEquals('%', (char) pdf[0]);
        Assertions.assertEquals('P', (char) pdf[1]);
        Assertions.assertEquals('D', (char) pdf[2]);
        Assertions.assertEquals('F', (char) pdf[3]);
    }
}
