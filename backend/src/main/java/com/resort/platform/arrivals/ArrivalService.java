package com.resort.platform.arrivals;

import com.resort.platform.access.AccessRecord;
import com.resort.platform.arrivals.dto.ArrivalResponse;
import com.resort.platform.arrivals.dto.ArrivalsResponse;
import com.resort.platform.arrivals.dto.VisitSheetResponse;
import com.resort.platform.arrivals.dto.VisitSheetResponse.AbsentCompanion;
import com.resort.platform.arrivals.dto.VisitSheetResponse.LeadInfo;
import com.resort.platform.arrivals.dto.VisitSheetResponse.PresentCompanion;
import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.visits.Visit;
import com.resort.platform.visits.VisitCompanion;
import com.resort.platform.visits.VisitRepository;
import com.resort.platform.visits.VisitService;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chegadas do dia e ficha da visita (§16.6). Só leitura, sem auditoria. O dia é o de {@code entry_at} do
 * acesso liberado em {@code APP_TIMEZONE}, nunca o do servidor.
 */
@Service
@Transactional(readOnly = true)
public class ArrivalService {

    private final ArrivalRepository arrivals;
    private final VisitRepository visits;
    private final BusinessCalendar calendar;

    public ArrivalService(ArrivalRepository arrivals, VisitRepository visits, BusinessCalendar calendar) {
        this.arrivals = arrivals;
        this.visits = visits;
        this.calendar = calendar;
    }

    /** ADMIN e HOST veem todas; PROSPECTOR, as da D-041. O HOST só consulta hoje (D-095). */
    public ArrivalsResponse list(LocalDate requested, Viewer viewer) {
        LocalDate today = calendar.today();
        LocalDate date = requested == null ? today : requested;
        if (viewer.isHost() && !date.equals(today)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "ARRIVALS_DATE_NOT_ALLOWED", "O anfitrião só consulta as chegadas de hoje.");
        }
        var from = calendar.startOfDay(date);
        var to = calendar.endOfDay(date);
        List<ArrivalResponse> list = viewer.isProspector()
                ? arrivals.findForProspector(from, to, viewer.prospectorId())
                : arrivals.findAll(from, to);
        return new ArrivalsResponse(date, list);
    }

    /**
     * Ficha (D-096). HOST: só de chegada de hoje; fora disso, o 404 de id inexistente. ADMIN e PROSPECTOR
     * com leitura da visita (D-041): qualquer data; visita sem entrada, 409 {@code VISIT_NOT_ARRIVED}.
     */
    public VisitSheetResponse sheet(UUID visitId, Viewer viewer) {
        Visit visit = visits.findWithDetailsById(visitId).orElseThrow(ArrivalService::visitNotFound);
        Optional<AccessRecord> entry = arrivals.findEntryOf(visitId);
        if (viewer.isHost()) {
            if (entry.isEmpty() || !calendar.dateOf(entry.get().getEntryAt()).equals(calendar.today())) {
                throw visitNotFound();
            }
        } else if (!VisitService.canRead(visit, viewer)) {
            throw visitNotFound();
        } else if (entry.isEmpty()) {
            throw ApiException.conflict("VISIT_NOT_ARRIVED", "Esta visita ainda não teve entrada registrada.");
        }
        return toSheet(visit, entry.get());
    }

    private static VisitSheetResponse toSheet(Visit visit, AccessRecord entry) {
        LocalDate on = visit.getScheduledDate();
        Set<UUID> present = entry.getPresentCompanionIds();
        List<VisitCompanion> companions = visit.getCompanions();
        return new VisitSheetResponse(
                visit.getId(),
                on,
                entry.getEntryAt(),
                new LeadInfo(
                        visit.getLead().getName(),
                        visit.getLead().getBirthDate() == null ? null : ageOn(visit.getLead().getBirthDate(), on),
                        visit.getLead().getPhone()),
                visit.getProspector().getUser().getName(),
                companions.stream()
                        .filter(c -> present.contains(c.getId()))
                        .map(c -> new PresentCompanion(c.getName(), c.getRelationship(), ageOn(c.getBirthDate(), on)))
                        .toList(),
                companions.stream()
                        .filter(c -> !present.contains(c.getId()))
                        .map(c -> new AbsentCompanion(c.getName(), c.getRelationship()))
                        .toList(),
                visit.getHostNotes());
    }

    /** Idade na data da visita, que é também o dia da entrada (D-096). */
    static int ageOn(LocalDate birthDate, LocalDate on) {
        return Period.between(birthDate, on).getYears();
    }

    private static ApiException visitNotFound() {
        return ApiException.notFound("VISIT_NOT_FOUND", "Visita não encontrada.");
    }
}
