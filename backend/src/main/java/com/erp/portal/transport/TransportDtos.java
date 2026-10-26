package com.erp.portal.transport;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class TransportDtos {
    private TransportDtos() {}

    // ==========================================
    // BUS DTOS
    // ==========================================

    public record CreateBusRequest(
            @NotBlank(message = "Bus name is required")
            @Size(max = 100, message = "Bus name cannot exceed 100 characters")
            String busName,

            @NotBlank(message = "Registration number is required")
            @Size(max = 50, message = "Registration number cannot exceed 50 characters")
            String registrationNo,

            String fleetNo,
            String busType,

            @NotNull(message = "Seating capacity is required")
            @Min(value = 1, message = "Seating capacity must be at least 1")
            Integer capacity,

            Long routeId,
            Long driverStaffId,
            Long conductorStaffId,
            String institutionId
    ) {}

    public record UpdateBusRequest(
            @NotBlank(message = "Bus name is required")
            @Size(max = 100, message = "Bus name cannot exceed 100 characters")
            String busName,

            @NotBlank(message = "Registration number is required")
            @Size(max = 50, message = "Registration number cannot exceed 50 characters")
            String registrationNo,

            String fleetNo,
            String busType,

            @NotNull(message = "Seating capacity is required")
            @Min(value = 1, message = "Seating capacity must be at least 1")
            Integer capacity,

            Long routeId,
            Long driverStaffId,
            Long conductorStaffId,
            String status
    ) {}

    public record BusResponse(
            long id,
            String busName,
            String registrationNo,
            String fleetNo,
            String busType,
            int capacity,
            int activeOccupancy,
            int availableSeats,
            Long currentRouteId,
            String routeName,
            String routeCode,
            Long driverStaffId,
            String driverName,
            String driverPhone,
            Long conductorStaffId,
            String conductorName,
            String conductorPhone,
            String status,
            String institutionId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record BusDetailResponse(
            BusResponse bus,
            List<StudentAssignmentResponse> assignedStudents,
            List<RouteStopResponse> routeStops
    ) {}

    // ==========================================
    // ROUTE DTOS
    // ==========================================

    public record CreateRouteRequest(
            @NotBlank(message = "Route name is required")
            @Size(max = 150, message = "Route name cannot exceed 150 characters")
            String routeName,

            @NotBlank(message = "Route code is required")
            @Size(max = 50, message = "Route code cannot exceed 50 characters")
            String routeCode,

            String description,
            List<RouteStopRequest> stops,
            String institutionId
    ) {}

    public record UpdateRouteRequest(
            @NotBlank(message = "Route name is required")
            @Size(max = 150, message = "Route name cannot exceed 150 characters")
            String routeName,

            @NotBlank(message = "Route code is required")
            @Size(max = 50, message = "Route code cannot exceed 50 characters")
            String routeCode,

            String description,
            String status
    ) {}

    public record RouteResponse(
            long id,
            String routeName,
            String routeCode,
            String description,
            String status,
            Long assignedBusId,
            String assignedBusName,
            int stopsCount,
            int activeStudentsCount,
            String institutionId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record RouteDetailResponse(
            RouteResponse route,
            List<RouteStopResponse> stops,
            List<BusResponse> assignedBuses
    ) {}

    // ==========================================
    // ROUTE STOP DTOS
    // ==========================================

    public record RouteStopRequest(
            Long id,
            @NotBlank(message = "Stop name is required")
            String stopName,
            @NotNull(message = "Stop order is required")
            @Min(value = 1, message = "Stop order must be at least 1")
            Integer stopOrder,
            LocalTime pickupTime,
            LocalTime dropTime
    ) {}

    public record RouteStopResponse(
            long id,
            long routeId,
            String stopName,
            int stopOrder,
            LocalTime pickupTime,
            LocalTime dropTime
    ) {}

    // ==========================================
    // STUDENT ASSIGNMENT DTOS
    // ==========================================

    public record AssignStudentRequest(
            @NotNull(message = "Student ID is required")
            Long studentId,

            @NotNull(message = "Bus ID is required")
            Long busId,

            @NotNull(message = "Route ID is required")
            Long routeId,

            Long pickupStopId,
            Long dropStopId,

            @NotNull(message = "Start date is required")
            LocalDate startDate,
            LocalDate endDate
    ) {}

    public record ReassignStudentRequest(
            @NotNull(message = "Bus ID is required")
            Long busId,

            @NotNull(message = "Route ID is required")
            Long routeId,

            Long pickupStopId,
            Long dropStopId,

            String reason
    ) {}

    public record StudentAssignmentResponse(
            long id,
            long studentId,
            String admissionNo,
            String studentName,
            long busId,
            String busName,
            String registrationNo,
            long routeId,
            String routeName,
            Long pickupStopId,
            String pickupStopName,
            LocalTime pickupTime,
            Long dropStopId,
            String dropStopName,
            LocalTime dropTime,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    // ==========================================
    // STUDENT & PARENT READ-ONLY VIEWS
    // ==========================================

    public record StudentTransportView(
            boolean assigned,
            Long assignmentId,
            Long studentId,
            String studentName,
            String admissionNo,
            String busName,
            String registrationNo,
            String routeName,
            String pickupStopName,
            LocalTime pickupTime,
            String dropStopName,
            LocalTime dropTime,
            String driverName,
            String conductorName,
            String status,
            List<RouteStopResponse> routeStops,
            String message
    ) {}

    public record ParentChildTransportView(
            long childId,
            String childName,
            String admissionNo,
            boolean assigned,
            Long assignmentId,
            String busName,
            String registrationNo,
            String routeName,
            String pickupStopName,
            LocalTime pickupTime,
            String dropStopName,
            LocalTime dropTime,
            String driverName,
            String conductorName,
            String status,
            Integer capacity,
            Integer occupancy,
            Integer availableSeats,
            List<RouteStopResponse> routeStops,
            String message
    ) {}

    // ==========================================
    // DASHBOARD & OVERVIEW DTOS
    // ==========================================

    public record TransportOverviewResponse(
            int totalActiveBuses,
            int totalActiveRoutes,
            int totalAssignedStudents,
            int totalAvailableSeats,
            int fullBusesCount,
            int busesWithoutDriver,
            int busesWithoutConductor,
            int routesWithoutBus,
            List<AssignmentHistoryResponse> recentHistory
    ) {}

    // ==========================================
    // ASSIGNMENT HISTORY DTOS
    // ==========================================

    public record AssignmentHistoryResponse(
            long id,
            long assignmentId,
            long studentId,
            String studentName,
            String admissionNo,
            long busId,
            String busName,
            long routeId,
            String routeName,
            String action,
            String reason,
            Long changedBy,
            String changerName,
            LocalDateTime changedAt
    ) {}

    // ==========================================
    // OPTIONS DTOS
    // ==========================================

    public record TransportStaffOption(
            long id,
            String employeeNo,
            String name,
            String designation,
            String phone,
            Long assignedBusId,
            String assignedBusName
    ) {}

    public record CreateTransportStaffRequest(
            @NotBlank(message = "Employee number is required")
            @Size(max = 50, message = "Employee number cannot exceed 50 characters")
            String employeeNo,

            @NotBlank(message = "First name is required")
            @Size(max = 100, message = "First name cannot exceed 100 characters")
            String firstName,

            @Size(max = 100, message = "Last name cannot exceed 100 characters")
            String lastName,

            @NotBlank(message = "Designation is required")
            @Size(max = 100, message = "Designation cannot exceed 100 characters")
            String designation,

            @Size(max = 30, message = "Phone number cannot exceed 30 characters")
            String phone
    ) {}

    public record StudentTransportOption(
            long id,
            String admissionNo,
            String name,
            String status,
            boolean currentlyAssigned,
            String currentBusName
    ) {}
}
