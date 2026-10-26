package com.erp.portal.transport;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransportServiceUnitTest {

    @Mock TransportRepository repository;
    @Mock AuditService auditService;
    @Mock HttpServletRequest request;

    private TransportService service;

    private final SecurityPrincipal admin = new SecurityPrincipal(
            1L, "admin", "Admin", List.of("ADMIN"),
            Set.of("TRANSPORT:MANAGE", "TRANSPORT:READ"), "hash");

    @BeforeEach
    void setUp() {
        service = new TransportService(repository, auditService);
    }

    @Test
    void createBusRejectsNonPositiveCapacity() {
        TransportDtos.CreateBusRequest req = new TransportDtos.CreateBusRequest(
                "BUS-01", "REG-1234", "F-1", "Standard", 0, null, null, null, null);

        assertThatThrownBy(() -> service.createBus(req, admin, request))
                .isInstanceOf(TransportException.class)
                .matches(e -> ((TransportException) e).status().value() == 400)
                .hasMessageContaining("capacity");
    }

    @Test
    void createBusRejectsDuplicateRegistrationNumber() {
        TransportDtos.CreateBusRequest req = new TransportDtos.CreateBusRequest(
                "BUS-01", "REG-1234", "F-1", "Standard", 40, null, null, null, null);

        when(repository.isRegistrationNoTaken("REG-1234", null)).thenReturn(true);

        assertThatThrownBy(() -> service.createBus(req, admin, request))
                .isInstanceOf(TransportException.class)
                .matches(e -> ((TransportException) e).status().value() == 409)
                .hasMessageContaining("already in use");
    }

    @Test
    void createBusRejectsInactiveRoute() {
        TransportDtos.CreateBusRequest req = new TransportDtos.CreateBusRequest(
                "BUS-01", "REG-1234", "F-1", "Standard", 40, 10L, null, null, null);

        when(repository.isRegistrationNoTaken("REG-1234", null)).thenReturn(false);
        TransportDtos.RouteResponse inactiveRoute = new TransportDtos.RouteResponse(
                10L, "Route 10", "R-10", "Desc", "INACTIVE", null, null, 0, 0, null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findRouteById(10L)).thenReturn(Optional.of(inactiveRoute));

        assertThatThrownBy(() -> service.createBus(req, admin, request))
                .isInstanceOf(TransportException.class)
                .matches(e -> ((TransportException) e).status().value() == 400)
                .hasMessageContaining("Cannot assign an inactive route");
    }

    @Test
    void createBusRejectsDriverAlreadyAssignedToAnotherBus() {
        TransportDtos.CreateBusRequest req = new TransportDtos.CreateBusRequest(
                "BUS-01", "REG-1234", "F-1", "Standard", 40, null, 5L, null, null);

        when(repository.isRegistrationNoTaken("REG-1234", null)).thenReturn(false);
        when(repository.isStaffActive(5L)).thenReturn(true);
        when(repository.isStaffDriver(5L)).thenReturn(true);
        when(repository.findActiveBusForDriver(5L, null)).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.createBus(req, admin, request))
                .isInstanceOf(TransportException.class)
                .matches(e -> ((TransportException) e).status().value() == 409)
                .hasMessageContaining("already assigned as driver");
    }

    @Test
    void getBusDetailsThrowsNotFoundWhenBusDoesNotExist() {
        when(repository.findBusById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBusDetails(999L))
                .isInstanceOf(TransportException.class)
                .matches(e -> ((TransportException) e).status().value() == 404);
    }
}
