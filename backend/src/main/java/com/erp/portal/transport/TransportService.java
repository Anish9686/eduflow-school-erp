package com.erp.portal.transport;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class TransportService {
    private final TransportRepository repository;
    private final AuditService auditService;

    public TransportService(TransportRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    // ==========================================
    // BUS MANAGEMENT
    // ==========================================

    public List<TransportDtos.BusResponse> listBuses(String search, String status, Long routeId, int page, int size) {
        int limit = Math.clamp(size, 1, 100);
        int offset = Math.max(0, page) * limit;
        return repository.listBuses(search, status, routeId, limit, offset);
    }

    public int countBuses(String search, String status, Long routeId) {
        return repository.countBuses(search, status, routeId);
    }

    public TransportDtos.BusDetailResponse getBusDetails(long id) {
        TransportDtos.BusResponse bus = repository.findBusById(id)
                .orElseThrow(() -> TransportException.notFound("Bus with ID " + id + " not found"));

        List<TransportDtos.StudentAssignmentResponse> students = repository.listAssignments(null, id, null, "ACTIVE", 500, 0);
        List<TransportDtos.RouteStopResponse> stops = bus.currentRouteId() != null
                ? repository.findStopsByRouteId(bus.currentRouteId())
                : List.of();

        return new TransportDtos.BusDetailResponse(bus, students, stops);
    }

    @Transactional
    public TransportDtos.BusResponse createBus(TransportDtos.CreateBusRequest req, SecurityPrincipal actor, HttpServletRequest request) {
        if (req.capacity() <= 0) {
            throw TransportException.badRequest("capacity", "Seating capacity must be a positive integer");
        }
        if (repository.isRegistrationNoTaken(req.registrationNo(), null)) {
            throw TransportException.conflict("registrationNo", "Registration number '" + req.registrationNo() + "' is already in use");
        }
        if (req.routeId() != null) {
            TransportDtos.RouteResponse route = repository.findRouteById(req.routeId())
                    .orElseThrow(() -> TransportException.notFound("Selected route does not exist"));
            if (!"ACTIVE".equalsIgnoreCase(route.status())) {
                throw TransportException.badRequest("routeId", "Cannot assign an inactive route to a bus");
            }
        }
        if (req.driverStaffId() != null) {
            if (!repository.isStaffActive(req.driverStaffId())) {
                throw TransportException.badRequest("driverStaffId", "Assigned driver is not an active staff member");
            }
            if (!repository.isStaffDriver(req.driverStaffId())) {
                throw TransportException.badRequest("driverStaffId", "Assigned staff member is not designated as a driver");
            }
            Optional<Long> existingBus = repository.findActiveBusForDriver(req.driverStaffId(), null);
            if (existingBus.isPresent()) {
                throw TransportException.conflict("driverStaffId", "Staff member is already assigned as driver to bus ID " + existingBus.get());
            }
        }
        if (req.conductorStaffId() != null) {
            if (!repository.isStaffActive(req.conductorStaffId())) {
                throw TransportException.badRequest("conductorStaffId", "Assigned conductor is not an active staff member");
            }
            if (!repository.isStaffConductor(req.conductorStaffId())) {
                throw TransportException.badRequest("conductorStaffId", "Assigned staff member is not designated as a conductor");
            }
            Optional<Long> existingBus = repository.findActiveBusForConductor(req.conductorStaffId(), null);
            if (existingBus.isPresent()) {
                throw TransportException.conflict("conductorStaffId", "Staff member is already assigned as conductor to bus ID " + existingBus.get());
            }
        }

        long id = repository.createBus(req, actor != null ? actor.userId() : null);
        TransportDtos.BusResponse created = repository.findBusById(id)
                .orElseThrow(() -> TransportException.notFound("Bus not found after creation"));

        auditService.record("BUS_CREATED", "Bus", String.valueOf(id), actor,
                actor != null ? actor.username() : "system", null, created, request);

        return created;
    }

    @Transactional
    public TransportDtos.BusResponse updateBus(long id, TransportDtos.UpdateBusRequest req, SecurityPrincipal actor, HttpServletRequest request) {
        TransportRepository.BusLockInfo lockInfo = repository.lockBusForUpdate(id)
                .orElseThrow(() -> TransportException.notFound("Bus with ID " + id + " not found"));

        if (req.capacity() <= 0) {
            throw TransportException.badRequest("capacity", "Seating capacity must be a positive integer");
        }
        if (req.capacity() < lockInfo.occupancy()) {
            throw TransportException.conflict("capacity", "Cannot reduce bus capacity to " + req.capacity() +
                    " below current active occupancy of " + lockInfo.occupancy());
        }
        if (repository.isRegistrationNoTaken(req.registrationNo(), id)) {
            throw TransportException.conflict("registrationNo", "Registration number '" + req.registrationNo() + "' is already in use");
        }
        if (req.routeId() != null) {
            TransportDtos.RouteResponse route = repository.findRouteById(req.routeId())
                    .orElseThrow(() -> TransportException.notFound("Selected route does not exist"));
            if (!"ACTIVE".equalsIgnoreCase(route.status())) {
                throw TransportException.badRequest("routeId", "Cannot assign an inactive route to a bus");
            }
        }
        if (req.driverStaffId() != null) {
            if (!repository.isStaffActive(req.driverStaffId())) {
                throw TransportException.badRequest("driverStaffId", "Assigned driver is not an active staff member");
            }
            if (!repository.isStaffDriver(req.driverStaffId())) {
                throw TransportException.badRequest("driverStaffId", "Assigned staff member is not designated as a driver");
            }
            Optional<Long> existingBus = repository.findActiveBusForDriver(req.driverStaffId(), id);
            if (existingBus.isPresent()) {
                throw TransportException.conflict("driverStaffId", "Staff member is already assigned as driver to another active bus");
            }
        }
        if (req.conductorStaffId() != null) {
            if (!repository.isStaffActive(req.conductorStaffId())) {
                throw TransportException.badRequest("conductorStaffId", "Assigned conductor is not an active staff member");
            }
            if (!repository.isStaffConductor(req.conductorStaffId())) {
                throw TransportException.badRequest("conductorStaffId", "Assigned staff member is not designated as a conductor");
            }
            Optional<Long> existingBus = repository.findActiveBusForConductor(req.conductorStaffId(), id);
            if (existingBus.isPresent()) {
                throw TransportException.conflict("conductorStaffId", "Staff member is already assigned as conductor to another active bus");
            }
        }

        TransportDtos.BusResponse before = repository.findBusById(id).orElse(null);
        repository.updateBus(id, req, actor != null ? actor.userId() : null);
        TransportDtos.BusResponse updated = repository.findBusById(id)
                .orElseThrow(() -> TransportException.notFound("Bus not found after update"));

        auditService.record("BUS_UPDATED", "Bus", String.valueOf(id), actor,
                actor != null ? actor.username() : "system", before, updated, request);

        return updated;
    }

    @Transactional
    public void deactivateBus(long id, SecurityPrincipal actor, HttpServletRequest request) {
        TransportRepository.BusLockInfo lockInfo = repository.lockBusForUpdate(id)
                .orElseThrow(() -> TransportException.notFound("Bus with ID " + id + " not found"));

        if (lockInfo.occupancy() > 0) {
            throw TransportException.conflict("status", "Cannot deactivate bus with " + lockInfo.occupancy() +
                    " active student assignment(s). Reassign students before deactivating.");
        }

        TransportDtos.BusResponse before = repository.findBusById(id).orElse(null);
        repository.deactivateBus(id, actor != null ? actor.userId() : null);

        auditService.record("BUS_DEACTIVATED", "Bus", String.valueOf(id), actor,
                actor != null ? actor.username() : "system", before, "INACTIVE", request);
    }

    // ==========================================
    // ROUTE MANAGEMENT
    // ==========================================

    public List<TransportDtos.RouteResponse> listRoutes(String search, String status, int page, int size) {
        int limit = Math.clamp(size, 1, 100);
        int offset = Math.max(0, page) * limit;
        return repository.listRoutes(search, status, limit, offset);
    }

    public int countRoutes(String search, String status) {
        return repository.countRoutes(search, status);
    }

    public TransportDtos.RouteDetailResponse getRouteDetails(long id) {
        TransportDtos.RouteResponse route = repository.findRouteById(id)
                .orElseThrow(() -> TransportException.notFound("Route with ID " + id + " not found"));

        List<TransportDtos.RouteStopResponse> stops = repository.findStopsByRouteId(id);
        List<TransportDtos.BusResponse> buses = repository.listBuses(null, null, id, 50, 0);

        return new TransportDtos.RouteDetailResponse(route, stops, buses);
    }

    @Transactional
    public TransportDtos.RouteResponse createRoute(TransportDtos.CreateRouteRequest req, SecurityPrincipal actor, HttpServletRequest request) {
        if (repository.isRouteCodeTaken(req.routeCode(), null)) {
            throw TransportException.conflict("routeCode", "Route code '" + req.routeCode() + "' is already in use");
        }

        long routeId = repository.createRoute(req, actor != null ? actor.userId() : null);

        if (req.stops() != null && !req.stops().isEmpty()) {
            for (TransportDtos.RouteStopRequest stop : req.stops()) {
                if (stop.stopName() == null || stop.stopName().isBlank()) {
                    throw TransportException.badRequest("stopName", "Stop name cannot be blank");
                }
                repository.createStop(routeId, stop.stopName(), stop.stopOrder(), stop.pickupTime(), stop.dropTime());
            }
        }

        TransportDtos.RouteResponse created = repository.findRouteById(routeId)
                .orElseThrow(() -> TransportException.notFound("Route not found after creation"));

        auditService.record("ROUTE_CREATED", "Route", String.valueOf(routeId), actor,
                actor != null ? actor.username() : "system", null, created, request);

        return created;
    }

    @Transactional
    public TransportDtos.RouteResponse updateRoute(long id, TransportDtos.UpdateRouteRequest req, SecurityPrincipal actor, HttpServletRequest request) {
        TransportDtos.RouteResponse before = repository.findRouteById(id)
                .orElseThrow(() -> TransportException.notFound("Route with ID " + id + " not found"));

        if (repository.isRouteCodeTaken(req.routeCode(), id)) {
            throw TransportException.conflict("routeCode", "Route code '" + req.routeCode() + "' is already in use");
        }

        repository.updateRoute(id, req, actor != null ? actor.userId() : null);
        TransportDtos.RouteResponse updated = repository.findRouteById(id)
                .orElseThrow(() -> TransportException.notFound("Route not found after update"));

        auditService.record("ROUTE_UPDATED", "Route", String.valueOf(id), actor,
                actor != null ? actor.username() : "system", before, updated, request);

        return updated;
    }

    @Transactional
    public void deactivateRoute(long id, SecurityPrincipal actor, HttpServletRequest request) {
        TransportDtos.RouteResponse before = repository.findRouteById(id)
                .orElseThrow(() -> TransportException.notFound("Route with ID " + id + " not found"));

        int activeAssignments = repository.countActiveAssignmentsForRoute(id);
        if (activeAssignments > 0) {
            throw TransportException.conflict("status", "Cannot deactivate route with " + activeAssignments +
                    " active student assignment(s). Reassign students first.");
        }

        int activeBuses = repository.countActiveBusesForRoute(id);
        if (activeBuses > 0) {
            throw TransportException.conflict("status", "Cannot deactivate route with " + activeBuses +
                    " active bus(es) assigned. Reassign buses first.");
        }

        repository.deactivateRoute(id, actor != null ? actor.userId() : null);

        auditService.record("ROUTE_DEACTIVATED", "Route", String.valueOf(id), actor,
                actor != null ? actor.username() : "system", before, "INACTIVE", request);
    }

    // ==========================================
    // ROUTE STOP MANAGEMENT
    // ==========================================

    public List<TransportDtos.RouteStopResponse> getRouteStops(long routeId) {
        repository.findRouteById(routeId)
                .orElseThrow(() -> TransportException.notFound("Route with ID " + routeId + " not found"));
        return repository.findStopsByRouteId(routeId);
    }

    @Transactional
    public TransportDtos.RouteStopResponse addRouteStop(long routeId, TransportDtos.RouteStopRequest req, SecurityPrincipal actor, HttpServletRequest request) {
        repository.findRouteById(routeId)
                .orElseThrow(() -> TransportException.notFound("Route with ID " + routeId + " not found"));

        if (req.stopName() == null || req.stopName().isBlank()) {
            throw TransportException.badRequest("stopName", "Stop name is required");
        }
        if (req.stopOrder() == null || req.stopOrder() < 1) {
            throw TransportException.badRequest("stopOrder", "Stop order must be at least 1");
        }

        long stopId = repository.createStop(routeId, req.stopName(), req.stopOrder(), req.pickupTime(), req.dropTime());
        TransportDtos.RouteStopResponse created = repository.findStopById(stopId)
                .orElseThrow(() -> TransportException.notFound("Stop not found after creation"));

        auditService.record("ROUTE_STOP_ADDED", "RouteStop", String.valueOf(stopId), actor,
                actor != null ? actor.username() : "system", null, created, request);

        return created;
    }

    @Transactional
    public List<TransportDtos.RouteStopResponse> setRouteStops(long routeId, List<TransportDtos.RouteStopRequest> stops, SecurityPrincipal actor, HttpServletRequest request) {
        repository.findRouteById(routeId)
                .orElseThrow(() -> TransportException.notFound("Route with ID " + routeId + " not found"));

        repository.deleteAllStopsForRoute(routeId);
        int order = 1;
        for (TransportDtos.RouteStopRequest stop : stops) {
            int stopOrder = stop.stopOrder() != null && stop.stopOrder() > 0 ? stop.stopOrder() : order;
            repository.createStop(routeId, stop.stopName(), stopOrder, stop.pickupTime(), stop.dropTime());
            order++;
        }

        List<TransportDtos.RouteStopResponse> updated = repository.findStopsByRouteId(routeId);
        auditService.record("ROUTE_STOPS_UPDATED", "Route", String.valueOf(routeId), actor,
                actor != null ? actor.username() : "system", null, updated, request);

        return updated;
    }

    @Transactional
    public void deleteRouteStop(long routeId, long stopId, SecurityPrincipal actor, HttpServletRequest request) {
        TransportDtos.RouteStopResponse stop = repository.findStopById(stopId)
                .orElseThrow(() -> TransportException.notFound("Route stop not found"));

        if (stop.routeId() != routeId) {
            throw TransportException.badRequest("stopId", "Stop does not belong to the specified route");
        }

        repository.deleteStop(stopId);
        auditService.record("ROUTE_STOP_DELETED", "RouteStop", String.valueOf(stopId), actor,
                actor != null ? actor.username() : "system", stop, null, request);
    }

    // ==========================================
    // STUDENT TRANSPORT ASSIGNMENT & CAPACITY
    // ==========================================

    public List<TransportDtos.StudentAssignmentResponse> listAssignments(
            String search, Long busId, Long routeId, String status, int page, int size) {
        int limit = Math.clamp(size, 1, 100);
        int offset = Math.max(0, page) * limit;
        return repository.listAssignments(search, busId, routeId, status, limit, offset);
    }

    public int countAssignments(String search, Long busId, Long routeId, String status) {
        return repository.countAssignments(search, busId, routeId, status);
    }

    public TransportDtos.StudentAssignmentResponse getAssignment(long id) {
        return repository.findAssignmentById(id)
                .orElseThrow(() -> TransportException.notFound("Assignment with ID " + id + " not found"));
    }

    @Transactional
    public TransportDtos.StudentAssignmentResponse assignStudent(
            TransportDtos.AssignStudentRequest req, SecurityPrincipal actor, HttpServletRequest request) {

        if (!repository.isStudentActive(req.studentId())) {
            throw TransportException.badRequest("studentId", "Student record is not active or does not exist");
        }

        Optional<TransportDtos.StudentAssignmentResponse> activeAssignment =
                repository.findActiveAssignmentByStudentId(req.studentId());
        if (activeAssignment.isPresent()) {
            throw TransportException.conflict("studentId", "Student already has an active transport assignment on bus '" +
                    activeAssignment.get().busName() + "'");
        }

        TransportRepository.BusLockInfo bus = repository.lockBusForUpdate(req.busId())
                .orElseThrow(() -> TransportException.notFound("Bus with ID " + req.busId() + " not found"));

        if (!"ACTIVE".equalsIgnoreCase(bus.status())) {
            throw TransportException.conflict("busId", "Cannot assign student to an inactive bus");
        }

        if (bus.occupancy() >= bus.capacity()) {
            throw new TransportException(HttpStatus.CONFLICT, "BUS_CAPACITY_FULL",
                    "Bus is at full capacity (" + bus.occupancy() + "/" + bus.capacity() + " seats occupied)");
        }

        TransportDtos.RouteResponse route = repository.findRouteById(req.routeId())
                .orElseThrow(() -> TransportException.notFound("Route with ID " + req.routeId() + " not found"));
        if (!"ACTIVE".equalsIgnoreCase(route.status())) {
            throw TransportException.conflict("routeId", "Cannot assign student to an inactive route");
        }

        if (bus.currentRouteId() != null && !bus.currentRouteId().equals(req.routeId())) {
            throw TransportException.badRequest("routeId", "Selected route does not match the active route configured for this bus");
        }

        if (req.pickupStopId() != null && !repository.isStopOnRoute(req.pickupStopId(), req.routeId())) {
            throw TransportException.badRequest("pickupStopId", "Pickup stop does not belong to the selected route");
        }
        if (req.dropStopId() != null && !repository.isStopOnRoute(req.dropStopId(), req.routeId())) {
            throw TransportException.badRequest("dropStopId", "Drop-off stop does not belong to the selected route");
        }

        LocalDate startDate = req.startDate() != null ? req.startDate() : LocalDate.now();
        long assignmentId = repository.createAssignment(
                req.studentId(), req.busId(), req.routeId(), req.pickupStopId(), req.dropStopId(),
                startDate, req.endDate(), actor != null ? actor.userId() : null);

        repository.recordHistory(assignmentId, req.studentId(), req.busId(), req.routeId(),
                req.pickupStopId(), req.dropStopId(), "ASSIGNED", "Initial transport assignment",
                actor != null ? actor.userId() : null);

        TransportDtos.StudentAssignmentResponse created = repository.findAssignmentById(assignmentId)
                .orElseThrow(() -> TransportException.notFound("Assignment not found after creation"));

        auditService.record("STUDENT_TRANSPORT_ASSIGNED", "StudentTransportAssignment",
                String.valueOf(assignmentId), actor, actor != null ? actor.username() : "system", null, created, request);

        return created;
    }

    @Transactional
    public TransportDtos.StudentAssignmentResponse reassignStudent(
            long assignmentId, TransportDtos.ReassignStudentRequest req, SecurityPrincipal actor, HttpServletRequest request) {

        TransportDtos.StudentAssignmentResponse existing = repository.findAssignmentById(assignmentId)
                .orElseThrow(() -> TransportException.notFound("Assignment with ID " + assignmentId + " not found"));

        if (!"ACTIVE".equalsIgnoreCase(existing.status())) {
            throw TransportException.conflict("status", "Cannot reassign a student with assignment status '" + existing.status() + "'");
        }

        TransportRepository.BusLockInfo targetBus = repository.lockBusForUpdate(req.busId())
                .orElseThrow(() -> TransportException.notFound("Target bus with ID " + req.busId() + " not found"));

        if (!"ACTIVE".equalsIgnoreCase(targetBus.status())) {
            throw TransportException.conflict("busId", "Target bus is not active");
        }

        if (targetBus.id() != existing.busId() && targetBus.occupancy() >= targetBus.capacity()) {
            throw new TransportException(HttpStatus.CONFLICT, "BUS_CAPACITY_FULL",
                    "Target bus is at full capacity (" + targetBus.occupancy() + "/" + targetBus.capacity() + ")");
        }

        TransportDtos.RouteResponse targetRoute = repository.findRouteById(req.routeId())
                .orElseThrow(() -> TransportException.notFound("Target route with ID " + req.routeId() + " not found"));
        if (!"ACTIVE".equalsIgnoreCase(targetRoute.status())) {
            throw TransportException.conflict("routeId", "Target route is not active");
        }

        if (targetBus.currentRouteId() != null && !targetBus.currentRouteId().equals(req.routeId())) {
            throw TransportException.badRequest("routeId", "Target route does not match the route configured for the target bus");
        }

        if (req.pickupStopId() != null && !repository.isStopOnRoute(req.pickupStopId(), req.routeId())) {
            throw TransportException.badRequest("pickupStopId", "Pickup stop does not belong to the selected route");
        }
        if (req.dropStopId() != null && !repository.isStopOnRoute(req.dropStopId(), req.routeId())) {
            throw TransportException.badRequest("dropStopId", "Drop-off stop does not belong to the selected route");
        }

        repository.reassignAssignment(assignmentId, req.busId(), req.routeId(), req.pickupStopId(), req.dropStopId(),
                actor != null ? actor.userId() : null);

        String reason = req.reason() != null && !req.reason().isBlank() ? req.reason().trim() : "Transport reallocation";
        repository.recordHistory(assignmentId, existing.studentId(), req.busId(), req.routeId(),
                req.pickupStopId(), req.dropStopId(), "REASSIGNED", reason, actor != null ? actor.userId() : null);

        TransportDtos.StudentAssignmentResponse updated = repository.findAssignmentById(assignmentId)
                .orElseThrow(() -> TransportException.notFound("Assignment not found after reassignment"));

        auditService.record("STUDENT_TRANSPORT_REASSIGNED", "StudentTransportAssignment",
                String.valueOf(assignmentId), actor, actor != null ? actor.username() : "system", existing, updated, request);

        return updated;
    }

    @Transactional
    public void endAssignment(long assignmentId, String reason, SecurityPrincipal actor, HttpServletRequest request) {
        TransportDtos.StudentAssignmentResponse existing = repository.findAssignmentById(assignmentId)
                .orElseThrow(() -> TransportException.notFound("Assignment with ID " + assignmentId + " not found"));

        if (!"ACTIVE".equalsIgnoreCase(existing.status())) {
            throw TransportException.conflict("status", "Assignment is already in '" + existing.status() + "' status");
        }

        repository.updateAssignmentStatus(assignmentId, "ENDED", LocalDate.now(), actor != null ? actor.userId() : null);

        repository.recordHistory(assignmentId, existing.studentId(), existing.busId(), existing.routeId(),
                existing.pickupStopId(), existing.dropStopId(), "ENDED",
                reason != null && !reason.isBlank() ? reason.trim() : "Assignment concluded",
                actor != null ? actor.userId() : null);

        auditService.record("STUDENT_TRANSPORT_ENDED", "StudentTransportAssignment",
                String.valueOf(assignmentId), actor, actor != null ? actor.username() : "system", existing, "ENDED", request);
    }

    // ==========================================
    // STUDENT & PARENT READ-ONLY PORTAL
    // ==========================================

    public TransportDtos.StudentTransportView getMyAssignment(SecurityPrincipal actor) {
        if (actor == null) {
            throw TransportException.forbidden("Authentication required");
        }
        return repository.findStudentTransportByUserId(actor.userId())
                .orElse(new TransportDtos.StudentTransportView(
                        false, null, null, actor.fullName(), null,
                        null, null, null, null, null, null, null, null, null, null,
                        List.of(), "No student profile is linked to this account."));
    }

    public List<TransportDtos.ParentChildTransportView> getMyChildren(SecurityPrincipal actor) {
        if (actor == null) {
            throw TransportException.forbidden("Authentication required");
        }
        return repository.findChildrenTransportByParentUserId(actor.userId());
    }

    // ==========================================
    // OVERVIEW & HISTORY
    // ==========================================

    public TransportDtos.TransportOverviewResponse getOverview() {
        return repository.getOverview();
    }

    public List<TransportDtos.AssignmentHistoryResponse> getHistory(Long studentId, Long busId, int limit) {
        int boundedLimit = Math.clamp(limit, 1, 200);
        return repository.listHistory(studentId, busId, boundedLimit);
    }

    public List<TransportDtos.TransportStaffOption> getStaffOptions(String role) {
        return repository.listStaffOptions(role);
    }

    public List<TransportDtos.TransportStaffOption> getStaffOptions() {
        return getStaffOptions(null);
    }

    @Transactional
    public TransportDtos.TransportStaffOption createTransportStaff(
            TransportDtos.CreateTransportStaffRequest req,
            SecurityPrincipal actor,
            HttpServletRequest request) {
        var staff = repository.createTransportStaff(req);
        auditService.record("TRANSPORT_STAFF_CREATED", "Staff", String.valueOf(staff.id()), actor,
                actor != null ? actor.username() : "system", null, staff, request);
        return staff;
    }

    public List<TransportDtos.StudentTransportOption> getStudentOptions(String search) {
        return repository.listStudentOptions(search);
    }
}
