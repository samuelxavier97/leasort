package com.resort.platform.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.access.AccessResult;
import com.resort.platform.access.DenialReason;
import com.resort.platform.common.Emails;
import com.resort.platform.demo.DemoDataPlan.DemoAccess;
import com.resort.platform.demo.DemoDataPlan.DemoCompanion;
import com.resort.platform.demo.DemoDataPlan.DemoData;
import com.resort.platform.demo.DemoDataPlan.DemoInvitation;
import com.resort.platform.demo.DemoDataPlan.DemoLead;
import com.resort.platform.demo.DemoDataPlan.DemoUser;
import com.resort.platform.demo.DemoDataPlan.DemoVisit;
import com.resort.platform.invitations.InvitationStatus;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import com.resort.platform.users.dto.PhoneFormat;
import com.resort.platform.visits.VisitCancelReason;
import com.resort.platform.visits.VisitStatus;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Conteúdo da carga de demonstração (D-125), sem banco: estados coerentes entre si, nada no futuro, nenhum
 * dado que possa coincidir com o de uma pessoa real e o volume combinado. Roda com "agora" em dias
 * diferentes da semana e perto da meia-noite, no fuso da operação.
 */
class DemoDataPlanTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String ADMIN_NAME = "Nome Fictício do Administrador";

    private static DemoData generate(ZonedDateTime now) {
        return DemoDataPlan.generate(now.toLocalDate(), now.toInstant(), ZONE, DemoDataPlan.SEED, new SecureRandom(), ADMIN_NAME);
    }

    private static final ZonedDateTime WEDNESDAY_AFTERNOON = ZonedDateTime.of(2026, 9, 30, 15, 0, 0, 0, ZONE);

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-30T15:00", "2026-10-03T08:30", "2026-10-04T23:50", "2026-10-05T00:20"})
    void everyStateIsConsistentAndNothingIsInTheFuture(String localNow) {
        ZonedDateTime now = java.time.LocalDateTime.parse(localNow).atZone(ZONE);
        LocalDate today = now.toLocalDate();
        DemoData data = generate(now);
        Instant instant = now.toInstant();
        Map<UUID, DemoInvitation> invitationOfVisit = data.invitations().stream()
                .collect(Collectors.toMap(DemoInvitation::visitId, Function.identity()));
        Map<UUID, DemoInvitation> invitationById = data.invitations().stream()
                .collect(Collectors.toMap(DemoInvitation::id, Function.identity()));
        Map<UUID, DemoVisit> visitById = data.visits().stream().collect(Collectors.toMap(DemoVisit::id, Function.identity()));

        // Nada no futuro.
        data.leads().forEach(l -> assertThat(l.createdAt()).isBefore(instant).isBeforeOrEqualTo(l.updatedAt()));
        data.visits().forEach(v -> assertThat(v.createdAt()).isBefore(instant).isBeforeOrEqualTo(v.updatedAt()));
        data.visits().forEach(v -> assertThat(v.updatedAt()).isBefore(instant));
        data.invitations().forEach(i -> assertThat(i.updatedAt()).isBefore(instant));
        data.accesses().forEach(a -> assertThat(a.createdAt()).isBefore(instant));

        // Visita, convite e acesso coerentes (D-076, D-085, D-098, §12).
        for (DemoVisit visit : data.visits()) {
            DemoInvitation invitation = invitationOfVisit.get(visit.id());
            assertThat(invitation).as("um convite por visita").isNotNull();
            assertThat(invitation.expiresAt()).isEqualTo(visit.date().plusDays(1).atStartOfDay(ZONE).toInstant());
            assertThat(invitation.createdAt()).isEqualTo(visit.createdAt());
            assertThat(visit.cancelReason() != null).isEqualTo(visit.status() == VisitStatus.CANCELLED);
            assertThat(visit.cancelledAt() != null).isEqualTo(visit.status() == VisitStatus.CANCELLED);
            InvitationStatus expected = switch (visit.status()) {
                case SCHEDULED -> InvitationStatus.ACTIVE;
                case COMPLETED -> InvitationStatus.USED;
                case NO_SHOW -> InvitationStatus.EXPIRED;
                case CANCELLED -> InvitationStatus.CANCELLED;
            };
            assertThat(invitation.status()).as("convite da visita %s", visit.status()).isEqualTo(expected);
            assertThat(invitation.cancelledAt() != null).isEqualTo(expected == InvitationStatus.CANCELLED);
            switch (visit.status()) {
                case SCHEDULED -> assertThat(visit.date()).isAfterOrEqualTo(today);
                // A de hoje pode já ter entrado (D-128); sem comparecimento só no passado.
                case COMPLETED -> assertThat(visit.date()).isBeforeOrEqualTo(today);
                case NO_SHOW -> assertThat(visit.date()).isBefore(today);
                case CANCELLED -> {
                    if (visit.cancelReason() != VisitCancelReason.RESCHEDULED) {
                        assertThat(visit.date()).isBefore(today);
                    }
                    assertThat(visit.cancelledAt()).isAfterOrEqualTo(visit.createdAt());
                }
            }
        }
        List<DemoAccess> entries = data.accesses().stream().filter(a -> a.result() == AccessResult.AUTHORIZED).toList();
        assertThat(entries).hasSize((int) data.visits().stream().filter(v -> v.status() == VisitStatus.COMPLETED).count());
        for (DemoAccess entry : entries) {
            DemoInvitation invitation = invitationById.get(entry.invitationId());
            DemoVisit visit = visitById.get(invitation.visitId());
            assertThat(visit.status()).isEqualTo(VisitStatus.COMPLETED);
            assertThat(LocalDate.ofInstant(entry.entryAt(), ZONE)).isEqualTo(visit.date());
            assertThat(invitation.usedAt()).isEqualTo(entry.entryAt());
            assertThat(entry.attemptedCode()).isEqualTo(invitation.code());
        }
        for (DemoAccess denial : data.accesses().stream().filter(a -> a.result() == AccessResult.DENIED).toList()) {
            if (denial.denialReason() == DenialReason.INVALID_CODE) {
                assertThat(denial.invitationId()).isNull();
                continue;
            }
            DemoInvitation invitation = invitationById.get(denial.invitationId());
            DemoVisit visit = visitById.get(invitation.visitId());
            assertThat(denial.createdAt()).isAfterOrEqualTo(invitation.createdAt());
            LocalDate day = LocalDate.ofInstant(denial.createdAt(), ZONE);
            switch (denial.denialReason()) {
                case ALREADY_USED -> {
                    assertThat(visit.status()).isEqualTo(VisitStatus.COMPLETED);
                    assertThat(denial.createdAt()).isAfter(invitation.usedAt());
                }
                case WRONG_DATE -> assertThat(day).isBefore(visit.date());
                case CANCELLED -> {
                    assertThat(visit.status()).isEqualTo(VisitStatus.CANCELLED);
                    assertThat(denial.createdAt()).isAfter(visit.cancelledAt());
                }
                case EXPIRED -> {
                    assertThat(visit.status()).isEqualTo(VisitStatus.NO_SHOW);
                    assertThat(denial.createdAt()).isAfterOrEqualTo(invitation.expiresAt());
                }
                case INVALID_CODE -> throw new AssertionError("já tratado");
            }
        }
        Set<UUID> companionIds = data.companions().stream().map(c -> c.id()).collect(Collectors.toSet());
        data.presences().forEach(p -> assertThat(companionIds).contains(p.companionId()));

        // O Lead segue a última visita, como as regras do sistema fariam (VisitService, AccessService, job noturno).
        Map<UUID, List<DemoVisit>> visitsOfLead = data.visits().stream().collect(Collectors.groupingBy(DemoVisit::leadId));
        for (DemoLead lead : data.leads()) {
            List<DemoVisit> own = visitsOfLead.getOrDefault(lead.id(), List.of());
            if (own.isEmpty()) {
                assertThat(lead.status()).isIn(LeadStatus.NEW, LeadStatus.CONTACTED);
                continue;
            }
            assertThat(own.stream().filter(v -> v.status() == VisitStatus.SCHEDULED)).hasSizeLessThanOrEqualTo(1);
            DemoVisit last = own.stream().max(Comparator.comparing(DemoVisit::createdAt)).orElseThrow();
            LeadStatus expected = switch (last.status()) {
                case SCHEDULED -> LeadStatus.VISIT_SCHEDULED;
                case COMPLETED -> LeadStatus.VISITED;
                case NO_SHOW -> LeadStatus.CONTACTED;
                case CANCELLED -> last.cancelReason() == VisitCancelReason.LEAD_DISCARDED ? LeadStatus.CANCELLED : LeadStatus.CONTACTED;
            };
            assertThat(lead.status()).isEqualTo(expected);
            assertThat(lead.prospectorId()).isEqualTo(last.prospectorId());
            assertThat(lead.createdAt()).isBefore(own.stream().map(DemoVisit::createdAt).min(Comparator.naturalOrder()).orElseThrow());
            // Uma visita remarcada nunca fica como a última.
            assertThat(last.cancelReason()).isNotEqualTo(VisitCancelReason.RESCHEDULED);
        }
        // Códigos únicos, também os tentados em "código inválido".
        Set<String> codes = data.invitations().stream().map(DemoInvitation::code).collect(Collectors.toSet());
        assertThat(codes).hasSize(data.invitations().size());
        data.accesses().stream().filter(a -> a.denialReason() == DenialReason.INVALID_CODE)
                .forEach(a -> assertThat(codes).doesNotContain(a.attemptedCode()));
    }

    @Test
    void noDataThatCouldBelongToARealPerson() {
        DemoData data = generate(WEDNESDAY_AFTERNOON);
        // Sem CPF: o gerador nem tem o campo; a carga grava NULL (DemoLoader).
        for (DemoLead lead : data.leads()) {
            assertThat(lead.phone()).matches("\\(00\\) 9\\d{4}-\\d{4}").matches(PhoneFormat.REGEX);
            assertThat(lead.email()).endsWith("@example.com");
            assertThat(Emails.isValid(lead.email())).isTrue();
            // Primeiro nome (às vezes composto) e dois sobrenomes.
            assertThat(lead.name().split(" ")).hasSizeBetween(3, 4);
        }
        assertThat(data.leads().stream().map(DemoLead::email).distinct()).hasSize(data.leads().size());
        // Nenhum nome completo de Lead se repete; na mesma visita, nenhum acompanhante repete outro nem o Lead.
        assertThat(data.leads().stream().map(DemoLead::name).distinct()).hasSize(data.leads().size());
        Map<UUID, String> leadOfVisit = data.visits().stream().collect(Collectors.toMap(DemoVisit::id,
                visit -> data.leads().stream().filter(l -> l.id().equals(visit.leadId())).findFirst().orElseThrow().name()));
        data.companions().stream().collect(Collectors.groupingBy(DemoCompanion::visitId)).forEach((visitId, family) -> {
            List<String> names = family.stream().map(DemoCompanion::name).toList();
            assertThat(names).doesNotHaveDuplicates().doesNotContain(leadOfVisit.get(visitId));
        });
        data.prospectors().forEach(p -> assertThat(p.phone()).matches("\\(00\\) 9\\d{4}-\\d{4}"));
        data.users().forEach(u -> assertThat(u.email()).endsWith("@example.com"));
        assertThat(data.users().stream().map(u -> u.email()).collect(Collectors.toSet())).isEqualTo(DemoDataPlan.USER_EMAILS);
    }

    @Test
    void usersProspectorsAndVolume() {
        DemoData data = generate(WEDNESDAY_AFTERNOON);
        LocalDate today = WEDNESDAY_AFTERNOON.toLocalDate();
        assertThat(data.prospectors()).hasSize(8);
        assertThat(data.users().stream().filter(u -> u.login()).map(u -> u.role()))
                .containsExactlyInAnyOrder(Role.ADMIN, Role.PROSPECTOR, Role.GATE, Role.HOST);

        List<DemoVisit> finals = data.visits().stream().filter(v -> v.cancelReason() != VisitCancelReason.RESCHEDULED).toList();
        Map<Boolean, List<DemoVisit>> history = finals.stream().filter(v -> v.date().isBefore(today))
                .collect(Collectors.partitioningBy(v -> v.date().getDayOfWeek() == DayOfWeek.SATURDAY
                        || v.date().getDayOfWeek() == DayOfWeek.SUNDAY));
        // 180 dias de histórico: em média 3 visitas nos dias úteis e 11 nos fins de semana.
        long weekendDays = today.minusDays(DemoDataPlan.HISTORY_DAYS).datesUntil(today)
                .filter(d -> d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY).count();
        assertThat(DemoDataPlan.HISTORY_DAYS).isEqualTo(180);
        assertThat(history.get(false).size() / (double) (DemoDataPlan.HISTORY_DAYS - weekendDays)).isBetween(2.7, 3.3);
        assertThat(history.get(true).size() / (double) weekendDays).isBetween(10.5, 11.5);
        // Hoje: 5 ou 6, perto da média; 3 ou 4 já entraram, e 2 esperam a Portaria, com acompanhantes.
        List<DemoVisit> todays = finals.stream().filter(v -> v.date().equals(today)).toList();
        assertThat(todays.size()).isBetween(5, 6);
        assertThat(todays.stream().filter(v -> v.status() == VisitStatus.SCHEDULED)).hasSize(DemoDataPlan.PENDING_TODAY);
        assertThat(todays.stream().filter(v -> v.status() == VisitStatus.COMPLETED).count()).isBetween(3L, 4L);
        Set<UUID> pending = todays.stream().filter(v -> v.status() == VisitStatus.SCHEDULED).map(DemoVisit::id).collect(Collectors.toSet());
        assertThat(data.companions().stream().filter(c -> pending.contains(c.visitId())).map(DemoCompanion::visitId).distinct())
                .hasSize(DemoDataPlan.PENDING_TODAY);
        // As entradas de hoje se espalham das 8h até antes da carga (15h).
        assertThat(data.accesses().stream().filter(a -> a.entryAt() != null && LocalDate.ofInstant(a.entryAt(), ZONE).equals(today))
                .map(a -> a.entryAt().atZone(ZONE).toLocalTime()))
                .hasSizeBetween(3, 4)
                .allMatch(time -> !time.isBefore(java.time.LocalTime.of(8, 0)) && time.isBefore(java.time.LocalTime.of(14, 50)));
        // O ADMIN da demonstração tem o nome informado na carga.
        assertThat(data.users().stream().filter(u -> u.email().equals(DemoDataPlan.ADMIN_EMAIL)).map(DemoUser::name))
                .containsExactly(ADMIN_NAME);
        assertThat(finals.stream().filter(v -> v.date().isAfter(today))).isNotEmpty();
        // Uma das visitas de hoje tem acompanhantes, para a Portaria marcar a presença ao vivo.
        Set<UUID> todayIds = finals.stream().filter(v -> v.date().equals(today)).map(DemoVisit::id).collect(Collectors.toSet());
        assertThat(data.companions().stream().filter(c -> todayIds.contains(c.visitId()))).isNotEmpty();
    }

    @Test
    void outcomesInPlausibleProportionsWithDifferentProspectors() {
        DemoData data = generate(WEDNESDAY_AFTERNOON);
        LocalDate today = WEDNESDAY_AFTERNOON.toLocalDate();
        List<DemoVisit> past = data.visits().stream()
                .filter(v -> v.date().isBefore(today) && v.cancelReason() != VisitCancelReason.RESCHEDULED).toList();
        Map<VisitStatus, Long> byStatus = past.stream().collect(Collectors.groupingBy(DemoVisit::status, () -> new EnumMap<>(VisitStatus.class),
                Collectors.counting()));
        double completed = byStatus.get(VisitStatus.COMPLETED) / (double) past.size();
        assertThat(completed).isBetween(0.55, 0.75);
        assertThat(byStatus.get(VisitStatus.NO_SHOW)).isPositive();
        assertThat(byStatus.get(VisitStatus.CANCELLED)).isPositive();
        assertThat(data.visits().stream().map(DemoVisit::cancelReason).filter(r -> r != null).distinct())
                .containsExactlyInAnyOrder(VisitCancelReason.RESCHEDULED, VisitCancelReason.CANCELLED_BY_USER, VisitCancelReason.LEAD_DISCARDED);

        // O melhor e o pior Prospector ficam bem separados na taxa de visitas realizadas.
        Map<UUID, List<DemoVisit>> byProspector = past.stream().collect(Collectors.groupingBy(DemoVisit::prospectorId));
        List<Double> rates = byProspector.values().stream()
                .map(visits -> visits.stream().filter(v -> v.status() == VisitStatus.COMPLETED).count() / (double) visits.size())
                .sorted().toList();
        assertThat(byProspector).hasSize(8);
        assertThat(rates.getLast() - rates.getFirst()).isGreaterThan(0.15);

        // Presença parcial dos acompanhantes nas visitas realizadas.
        Set<UUID> completedIds = past.stream().filter(v -> v.status() == VisitStatus.COMPLETED).map(DemoVisit::id).collect(Collectors.toSet());
        long expected = data.companions().stream().filter(c -> completedIds.contains(c.visitId())).count();
        assertThat(data.presences().size()).isPositive().isLessThan((int) expected);
    }

    @Test
    void theFiveDenialReasonsWithCancelledAndExpiredInSmallNumber() {
        DemoData data = generate(WEDNESDAY_AFTERNOON);
        Map<DenialReason, Long> byReason = data.accesses().stream().filter(a -> a.result() == AccessResult.DENIED)
                .collect(Collectors.groupingBy(DemoAccess::denialReason, () -> new EnumMap<>(DenialReason.class), Collectors.counting()));
        assertThat(byReason.keySet()).containsExactlyInAnyOrder(DenialReason.values());
        assertThat(byReason.get(DenialReason.CANCELLED)).isLessThan(byReason.get(DenialReason.ALREADY_USED));
        assertThat(byReason.get(DenialReason.EXPIRED)).isLessThan(byReason.get(DenialReason.ALREADY_USED));
        assertThat(byReason.get(DenialReason.INVALID_CODE)).isGreaterThanOrEqualTo(byReason.get(DenialReason.WRONG_DATE));
    }

    @Test
    void theSameShapeAtEveryLoad() {
        DemoData first = generate(WEDNESDAY_AFTERNOON);
        DemoData second = generate(WEDNESDAY_AFTERNOON);
        assertThat(DemoLoader.totals(second)).isEqualTo(DemoLoader.totals(first));
        assertThat(second.leads().stream().map(DemoLead::name).toList()).isEqualTo(first.leads().stream().map(DemoLead::name).toList());
    }

    @Test
    void beforeEightInTheMorningNobodyHasArrivedYet() {
        ZonedDateTime early = ZonedDateTime.of(2026, 9, 30, 7, 30, 0, 0, ZONE);
        DemoData data = generate(early);
        assertThat(data.visits().stream().filter(v -> v.date().equals(early.toLocalDate())))
                .hasSizeBetween(5, 6)
                .allMatch(v -> v.status() == VisitStatus.SCHEDULED);
    }
}
