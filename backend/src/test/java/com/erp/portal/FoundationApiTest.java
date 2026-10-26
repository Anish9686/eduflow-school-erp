package com.erp.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void platformInfoIsPublicAndCarriesTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ERP Portal API"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void protectedApiReturnsStructured401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v1/private"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser
    void adminApiReturnsStructured403WithoutFoundationPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/foundation-check"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/foundation-check"));
    }

    @Test
    void actuatorHealthIsPublicAndReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void correlationIdIsPropagatedFromIncomingHeader() throws Exception {
        mockMvc.perform(get("/api/v1/info").header("X-Correlation-ID", "corr-test-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-test-12345"))
                .andExpect(header().string("X-Trace-Id", "corr-test-12345"));
    }
}
