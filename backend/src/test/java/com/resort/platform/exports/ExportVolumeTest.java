package com.resort.platform.exports;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.StatementCounter;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** X10: 5.000 Leads em streaming, com cursor e sem carregar entidades (D-101). */
class ExportVolumeTest extends ExportTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void fiveThousandLeadsAreStreamedWithACursor() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID prospector = me.prospector().getId();
        jdbc.sql("""
                INSERT INTO leads (id, name, cpf, status, prospector_id)
                SELECT gen_random_uuid(), 'Lead Fictício Volume ' || n, NULL, 'NEW', :p FROM generate_series(1, 5000) AS n
                """).param("p", prospector).update();
        User adminUser = testData.user(Role.ADMIN);
        var admin = client().login(adminUser.getEmail(), TestData.PASSWORD);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        int fetchSizesBefore = StatementCounter.fetchSizes().size();
        statistics.clear();

        Download file = download(admin, "/api/exports/leads?prospectorId=" + prospector);

        assertThat(file.rows()).hasSize(5_001);
        assertThat(file.data()).allMatch(r -> r.size() == 9 && r.get(1).startsWith("Lead Fictício Volume "));
        assertThat(file.text().split("\r\n", -1)).hasSize(5_002);
        assertThat(exportAudits(adminUser.getId())).singleElement().satisfies(m -> assertThat(json(m).get("rows").asLong()).isEqualTo(5_000));
        List<Integer> fetchSizes = StatementCounter.fetchSizes();
        assertThat(fetchSizes.subList(fetchSizesBefore, fetchSizes.size())).contains(ExportService.FETCH_SIZE);
        assertThat(statistics.getEntityLoadCount() + statistics.getEntityFetchCount()).isZero();
    }
}
