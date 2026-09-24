package com.resort.platform.invitations;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job noturno (§20, D-091): às 00:15 em {@code APP_TIMEZONE}, expira os convites ACTIVE vencidos e marca as
 * visitas como NO_SHOW. Cada convite numa transação própria: uma falha não impede os demais. O agendamento só
 * existe com {@code app.jobs.enabled}; nos testes, {@link #run()} é chamado diretamente.
 */
@Component
public class InvitationExpiryJob {

    static final String CRON = "0 15 0 * * *";
    private static final Logger log = LoggerFactory.getLogger(InvitationExpiryJob.class);

    private final InvitationExpiryService expiry;

    public InvitationExpiryJob(InvitationExpiryService expiry) {
        this.expiry = expiry;
    }

    @Scheduled(cron = CRON, zone = "${app.timezone}")
    public void scheduled() {
        run();
    }

    /** Devolve quantos convites expirou. O log leva só contagens e ids, nunca o código (regra 5). */
    public int run() {
        int expired = 0;
        int failed = 0;
        for (UUID id : expiry.dueIds()) {
            try {
                if (expiry.expire(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Falha ao expirar o convite {}: {}", id, e.getClass().getSimpleName());
            }
        }
        log.info("Job de expiração: {} convite(s) expirado(s), {} falha(s).", expired, failed);
        return expired;
    }
}
