package com.torqmind.ops.infrastructure.scheduler;

import com.torqmind.ops.application.routine.RoutineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RoutineScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoutineScheduler.class);

    private final RoutineService routineService;

    public RoutineScheduler(RoutineService routineService) {
        this.routineService = routineService;
    }

    // A cada minuto: gera execuções cujo horário de início chegou e processa lembretes/atrasos.
    @Scheduled(fixedDelay = 60000, initialDelay = 20000)
    public void tick() {
        try {
            int created = routineService.generateDueRuns();
            if (created > 0) {
                log.info("Agendador gerou {} tarefa(s) e notificou responsáveis.", created);
            }
        } catch (Exception ex) {
            log.warn("Falha ao gerar tarefas agendadas: {}", ex.getMessage());
        }
        try {
            int reminders = routineService.processDueReminders();
            if (reminders > 0) {
                log.info("Agendador processou {} lembrete(s)/atraso(s) de tarefa.", reminders);
            }
        } catch (Exception ex) {
            log.warn("Falha ao processar lembretes de tarefas: {}", ex.getMessage());
        }
    }
}
