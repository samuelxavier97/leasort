package com.resort.platform.arrivals;

import com.resort.platform.access.AccessRecord;
import com.resort.platform.arrivals.dto.ArrivalResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Leitura das entradas liberadas (§16.6). Só consulta: nenhuma escrita passa por aqui. */
interface ArrivalRepository extends Repository<AccessRecord, UUID> {

    String SELECT = """
            select new com.resort.platform.arrivals.dto.ArrivalResponse(
                v.id, r.entryAt, l.name, size(r.presentCompanionIds), u.name)
            from AccessRecord r
                join r.invitation i
                join i.visit v
                join v.lead l
                join v.prospector p
                join p.user u
            where r.result = com.resort.platform.access.AccessResult.AUTHORIZED
                and r.entryAt >= :from and r.entryAt < :to
            """;

    String ORDER = " order by r.entryAt desc, r.id desc";

    /** ADMIN e HOST: todas as chegadas do intervalo. */
    @Query(SELECT + ORDER)
    List<ArrivalResponse> findAll(@Param("from") Instant from, @Param("to") Instant to);

    /** PROSPECTOR: responsável pela visita ou dono atual do Lead (D-041). */
    @Query(SELECT + " and (p.id = :prospector or l.prospector.id = :prospector)" + ORDER)
    List<ArrivalResponse> findForProspector(
            @Param("from") Instant from, @Param("to") Instant to, @Param("prospector") UUID prospectorId);

    /** A entrada liberada da visita, se houver: só o convite usado tem uma (índice único da V8). */
    @Query("""
            select r from AccessRecord r join r.invitation i
            where i.visit.id = :visit and r.result = com.resort.platform.access.AccessResult.AUTHORIZED
            """)
    Optional<AccessRecord> findEntryOf(@Param("visit") UUID visitId);
}
