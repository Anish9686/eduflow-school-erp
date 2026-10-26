package com.erp.portal.dashboard;

import com.erp.portal.security.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {
    @Test
    void rolesWithoutDashboardGrantsReceiveNoInstitutionData() {
        JdbcTemplate jdbc = new JdbcTemplate();
        DashboardService service = new DashboardService(jdbc);

        DashboardResponse parent = service.load(principal("PARENT_GUARDIAN", Set.of()));
        DashboardResponse student = service.load(principal("STUDENT", Set.of()));
        DashboardResponse teacher = service.load(principal("TEACHER", Set.of()));

        assertThat(parent.metrics()).isEmpty();
        assertThat(parent.recentActivity()).isEmpty();
        assertThat(student.metrics()).isEmpty();
        assertThat(teacher.metrics()).isEmpty();
    }

    private static SecurityPrincipal principal(String role, Set<String> permissions) {
        return new SecurityPrincipal(5, "test-user", "Test User", List.of(role), permissions, "hash");
    }
}
