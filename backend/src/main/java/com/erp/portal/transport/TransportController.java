package com.erp.portal.transport;

import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transport")
public class TransportController {
    private final TransportService service;

    public TransportController(TransportService service) {
        this.service = service;
    }

    // ==========================================
    // OVERVIEW
    // ==========================================

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public TransportDtos.TransportOverviewResponse overview(
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getOverview();
    }

    // ==========================================
    // BUSES
    // ==========================================

    @GetMapping("/buses")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.BusResponse> listBuses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long routeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.listBuses(search, status, routeId, page, size);
    }

    @GetMapping("/buses/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public TransportDtos.BusDetailResponse getBus(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getBusDetails(id);
    }

    @PostMapping("/buses")
    @PreAuthorize("hasAuthority('TRANSPORT:CREATE')")
    public ResponseEntity<TransportDtos.BusResponse> createBus(
            @Valid @RequestBody TransportDtos.CreateBusRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        TransportDtos.BusResponse response = service.createBus(request, actor, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/buses/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT:UPDATE')")
    public TransportDtos.BusResponse updateBus(
            @PathVariable long id,
            @Valid @RequestBody TransportDtos.UpdateBusRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        return service.updateBus(id, request, actor, servletRequest);
    }

    @PatchMapping("/buses/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('TRANSPORT:UPDATE','TRANSPORT:DELETE')")
    public ResponseEntity<Void> deactivateBus(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        service.deactivateBus(id, actor, servletRequest);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // ROUTES
    // ==========================================

    @GetMapping("/routes")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.RouteResponse> listRoutes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.listRoutes(search, status, page, size);
    }

    @GetMapping("/routes/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public TransportDtos.RouteDetailResponse getRoute(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getRouteDetails(id);
    }

    @PostMapping("/routes")
    @PreAuthorize("hasAnyAuthority('TRANSPORT:MANAGE_ROUTES','TRANSPORT:CREATE')")
    public ResponseEntity<TransportDtos.RouteResponse> createRoute(
            @Valid @RequestBody TransportDtos.CreateRouteRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        TransportDtos.RouteResponse response = service.createRoute(request, actor, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/routes/{id}")
    @PreAuthorize("hasAnyAuthority('TRANSPORT:MANAGE_ROUTES','TRANSPORT:UPDATE')")
    public TransportDtos.RouteResponse updateRoute(
            @PathVariable long id,
            @Valid @RequestBody TransportDtos.UpdateRouteRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        return service.updateRoute(id, request, actor, servletRequest);
    }

    @PatchMapping("/routes/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('TRANSPORT:MANAGE_ROUTES','TRANSPORT:DELETE')")
    public ResponseEntity<Void> deactivateRoute(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        service.deactivateRoute(id, actor, servletRequest);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // ROUTE STOPS
    // ==========================================

    @GetMapping("/routes/{id}/stops")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.RouteStopResponse> getRouteStops(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getRouteStops(id);
    }

    @PostMapping("/routes/{id}/stops")
    @PreAuthorize("hasAuthority('TRANSPORT:MANAGE_ROUTES')")
    public ResponseEntity<TransportDtos.RouteStopResponse> addRouteStop(
            @PathVariable long id,
            @Valid @RequestBody TransportDtos.RouteStopRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        TransportDtos.RouteStopResponse response = service.addRouteStop(id, request, actor, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/routes/{id}/stops")
    @PreAuthorize("hasAuthority('TRANSPORT:MANAGE_ROUTES')")
    public List<TransportDtos.RouteStopResponse> setRouteStops(
            @PathVariable long id,
            @Valid @RequestBody List<TransportDtos.RouteStopRequest> stops,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        return service.setRouteStops(id, stops, actor, servletRequest);
    }

    @DeleteMapping("/routes/{routeId}/stops/{stopId}")
    @PreAuthorize("hasAuthority('TRANSPORT:MANAGE_ROUTES')")
    public ResponseEntity<Void> deleteRouteStop(
            @PathVariable long routeId,
            @PathVariable long stopId,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        service.deleteRouteStop(routeId, stopId, actor, servletRequest);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // ASSIGNMENTS
    // ==========================================

    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.StudentAssignmentResponse> listAssignments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long busId,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.listAssignments(search, busId, routeId, status, page, size);
    }

    @GetMapping("/assignments/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public TransportDtos.StudentAssignmentResponse getAssignment(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getAssignment(id);
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('TRANSPORT:ASSIGN')")
    public ResponseEntity<TransportDtos.StudentAssignmentResponse> assignStudent(
            @Valid @RequestBody TransportDtos.AssignStudentRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        TransportDtos.StudentAssignmentResponse response = service.assignStudent(request, actor, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/assignments/{id}/reassign")
    @PreAuthorize("hasAuthority('TRANSPORT:ASSIGN')")
    public TransportDtos.StudentAssignmentResponse reassignStudent(
            @PathVariable long id,
            @Valid @RequestBody TransportDtos.ReassignStudentRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        return service.reassignStudent(id, request, actor, servletRequest);
    }

    @PostMapping("/assignments/{id}/end")
    @PreAuthorize("hasAuthority('TRANSPORT:ASSIGN')")
    public ResponseEntity<Void> endAssignment(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        String reason = body != null ? body.get("reason") : null;
        service.endAssignment(id, reason, actor, servletRequest);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // HISTORY & OPTIONS
    // ==========================================

    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('TRANSPORT:VIEW_HISTORY','TRANSPORT:READ')")
    public List<TransportDtos.AssignmentHistoryResponse> history(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long busId,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getHistory(studentId, busId, limit);
    }

    @GetMapping("/staff-options")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.TransportStaffOption> staffOptions(
            @RequestParam(required = false) String role,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getStaffOptions(role);
    }

    @PostMapping("/staff")
    @PreAuthorize("hasAnyAuthority('TRANSPORT:MANAGE_STAFF','TRANSPORT:CREATE')")
    public ResponseEntity<TransportDtos.TransportStaffOption> createStaff(
            @Valid @RequestBody TransportDtos.CreateTransportStaffRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        requireOperationalAccess(actor);
        var staff = service.createTransportStaff(request, actor, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(staff);
    }

    @GetMapping("/student-options")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.StudentTransportOption> studentOptions(
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        requireOperationalAccess(actor);
        return service.getStudentOptions(search);
    }

    // ==========================================
    // STUDENT & PARENT READ-ONLY PORTALS
    // ==========================================

    @GetMapping("/my-assignment")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public TransportDtos.StudentTransportView myAssignment(
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getMyAssignment(actor);
    }

    @GetMapping("/my-children")
    @PreAuthorize("hasAuthority('TRANSPORT:READ')")
    public List<TransportDtos.ParentChildTransportView> myChildren(
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getMyChildren(actor);
    }

    // ==========================================
    // SECURITY SCOPE HELPER
    // ==========================================

    private static void requireOperationalAccess(SecurityPrincipal actor) {
        if (actor == null) {
            throw TransportException.forbidden("Authentication is required");
        }
        boolean isOperational = actor.roles().stream().anyMatch(r ->
                r.equals("SUPER_ADMIN") || r.equals("ADMIN") || r.equals("MANAGEMENT") ||
                r.equals("STAFF_ADMIN") || r.equals("SUPPORT_AGENT"));

        if (!isOperational) {
            throw TransportException.forbidden("Access denied: operational transport access required");
        }
    }
}
