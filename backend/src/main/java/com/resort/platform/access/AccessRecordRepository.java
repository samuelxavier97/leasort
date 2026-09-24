package com.resort.platform.access;

import com.resort.platform.access.dto.RecentAccessResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessRecordRepository extends JpaRepository<AccessRecord, UUID> {

    /** Acessos a partir de {@code since}, do mais recente para o mais antigo, sem o código tentado (D-092). */
    @Query("""
            select new com.resort.platform.access.dto.RecentAccessResponse(
                r.id, r.createdAt, r.result, r.denialReason, l.name, r.gate, u.name)
            from AccessRecord r
            join r.validatedBy u
            left join r.invitation i
            left join i.visit v
            left join v.lead l
            where r.createdAt >= :since
            order by r.createdAt desc, r.id desc
            """)
    List<RecentAccessResponse> findRecent(@Param("since") Instant since, Pageable pageable);
}
