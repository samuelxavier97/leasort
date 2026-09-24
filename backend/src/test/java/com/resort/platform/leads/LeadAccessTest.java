package com.resort.platform.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.users.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/** Carteira do Prospector (RN02, D-013) e autorização por rota. */
class LeadAccessTest extends IntegrationTestSupport {

    @Test
    void prospectorListsOnlyOwnLeadsWhateverFiltersAreSent() throws Exception {
        ProspectorSession me = loggedInProspector();
        ProspectorSession other = loggedInProspector();
        Lead mine = testData.lead(me.prospector());
        Lead others = testData.lead(other.prospector());
        Lead unassigned = testData.lead(null);

        for (String query : List.of(
                "",
                "?prospectorId=" + other.prospector().getId(),
                "?unassigned=true",
                "?cpf=" + others.getCpf(),
                "?size=100")) {
            List<String> ids = ids(me.client(), "/api/leads" + query);
            assertThat(ids).as(query).doesNotContain(others.getId().toString(), unassigned.getId().toString());
            if (!query.startsWith("?cpf")) {
                assertThat(ids).as(query).contains(mine.getId().toString());
            }
        }
    }

    @Test
    void leadOutsideTheWalletLooksExactlyLikeANonexistentOne() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead others = testData.lead(loggedInProspector().prospector());
        Lead unassigned = testData.lead(null);

        String nonexistent = body(me.client(), "/api/leads/" + UUID.randomUUID(), 404);
        assertThat(jsonMapper.readTree(nonexistent).get("code").asString()).isEqualTo("LEAD_NOT_FOUND");
        assertThat(withoutInstance(body(me.client(), "/api/leads/" + others.getId(), 404))).isEqualTo(withoutInstance(nonexistent));
        assertThat(withoutInstance(body(me.client(), "/api/leads/" + unassigned.getId(), 404))).isEqualTo(withoutInstance(nonexistent));
    }

    @Test
    void prospectorCannotWriteLeadsOutsideTheWallet() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead others = testData.lead(loggedInProspector().prospector());

        me.client().put("/api/leads/" + others.getId(), Map.of("name", "Nome Alterado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAD_NOT_FOUND"));
        me.client().patch("/api/leads/" + others.getId() + "/status", Map.of("status", "CANCELLED"))
                .andExpect(status().isNotFound());

        Lead unchanged = testData.reloadLead(others);
        assertThat(unchanged.getName()).isEqualTo(others.getName());
        assertThat(unchanged.getStatus()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    void reassignmentMovesAccessToTheNewOwner() throws Exception {
        ProspectorSession oldOwner = loggedInProspector();
        ProspectorSession newOwner = loggedInProspector();
        Lead lead = testData.lead(oldOwner.prospector());
        oldOwner.client().get("/api/leads/" + lead.getId()).andExpect(status().isOk());

        loggedIn(Role.ADMIN).patch("/api/leads/assign",
                        Map.of("leadIds", List.of(lead.getId()), "prospectorId", newOwner.prospector().getId()))
                .andExpect(status().isOk());

        oldOwner.client().get("/api/leads/" + lead.getId()).andExpect(status().isNotFound());
        newOwner.client().get("/api/leads/" + lead.getId()).andExpect(status().isOk());
    }

    static Stream<Arguments> adminOnly() {
        return Stream.of(
                Arguments.of("POST", "/api/leads"),
                Arguments.of("PATCH", "/api/leads/assign"),
                Arguments.of("POST", "/api/leads/import"),
                Arguments.of("GET", "/api/leads/import/template"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminOnly")
    void adminOnlyRoutes(String method, String path) throws Exception {
        for (Role role : List.of(Role.PROSPECTOR, Role.GATE, Role.HOST)) {
            assertThat(statusOf(loggedIn(role), method, path)).as(role.name()).isEqualTo(403);
        }
        assertThat(statusOf(client(), method, path)).as("anônimo").isEqualTo(401);
        assertThat(statusOf(loggedIn(Role.ADMIN), method, path)).as("ADMIN").isNotIn(401, 403);
    }

    static Stream<Arguments> walletRoutes() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("GET", "/api/leads"),
                Arguments.of("GET", "/api/leads/" + id),
                Arguments.of("PUT", "/api/leads/" + id),
                Arguments.of("PATCH", "/api/leads/" + id + "/status"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("walletRoutes")
    void walletRoutesAreClosedToGateHostAndAnonymous(String method, String path) throws Exception {
        assertThat(statusOf(loggedIn(Role.GATE), method, path)).isEqualTo(403);
        assertThat(statusOf(loggedIn(Role.HOST), method, path)).isEqualTo(403);
        assertThat(statusOf(client(), method, path)).isEqualTo(401);
        assertThat(statusOf(loggedIn(Role.PROSPECTOR), method, path)).isNotIn(401, 403);
    }

    private List<String> ids(ApiClient client, String path) throws Exception {
        JsonNode page = jsonMapper.readTree(body(client, path, 200));
        return page.get("content").valueStream().map(item -> item.get("id").asString()).toList();
    }

    private String body(ApiClient client, String path, int expectedStatus) throws Exception {
        return client.get(path).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }

    private static int statusOf(ApiClient client, String method, String path) throws Exception {
        Map<String, Object> body = Map.of("name", "Lead Fictício", "status", "CONTACTED");
        return (switch (method) {
                    case "GET" -> client.get(path);
                    case "POST" -> client.post(path, body);
                    case "PUT" -> client.put(path, body);
                    case "PATCH" -> client.patch(path, body);
                    default -> throw new IllegalArgumentException(method);
                })
                .andReturn().getResponse().getStatus();
    }
}
