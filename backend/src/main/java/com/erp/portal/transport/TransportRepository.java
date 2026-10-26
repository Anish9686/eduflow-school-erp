package com.erp.portal.transport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class TransportRepository {
    private final JdbcTemplate jdbc;

    public TransportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==========================================
    // BUS OPERATIONS
    // ==========================================

    public List<TransportDtos.BusResponse> listBuses(String search, String status, Long routeId, int limit, int offset) {
        var sql = new StringBuilder(
                "SELECT b.id, b.bus_name, b.registration_no, b.fleet_no, b.bus_type, b.capacity, " +
                "       (SELECT COUNT(*) FROM student_transport_assignments sta WHERE sta.bus_id = b.id AND sta.status = 'ACTIVE') AS active_occupancy, " +
                "       b.current_route_id, r.route_name, r.route_code, " +
                "       b.driver_staff_id, CONCAT_WS(' ', d.first_name, d.last_name) AS driver_name, d.phone AS driver_phone, " +
                "       b.conductor_staff_id, CONCAT_WS(' ', c.first_name, c.last_name) AS conductor_name, c.phone AS conductor_phone, " +
                "       b.status, b.institution_id, b.created_at, b.updated_at " +
                "FROM transport_buses b " +
                "LEFT JOIN transport_routes r ON r.id = b.current_route_id " +
                "LEFT JOIN staff d ON d.id = b.driver_staff_id " +
                "LEFT JOIN staff c ON c.id = b.conductor_staff_id WHERE 1=1 ");

        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(b.bus_name) LIKE LOWER(?) OR LOWER(b.registration_no) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(b.fleet_no,'')) LIKE LOWER(?) OR LOWER(COALESCE(r.route_name,'')) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term); params.add(term);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND b.status = ? ");
            params.add(status.trim());
        }
        if (routeId != null) {
            sql.append("AND b.current_route_id = ? ");
            params.add(routeId);
        }

        sql.append("ORDER BY b.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), this::mapBusResponse, params.toArray());
    }

    public int countBuses(String search, String status, Long routeId) {
        var sql = new StringBuilder("SELECT COUNT(*) FROM transport_buses b LEFT JOIN transport_routes r ON r.id = b.current_route_id WHERE 1=1 ");
        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(b.bus_name) LIKE LOWER(?) OR LOWER(b.registration_no) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(b.fleet_no,'')) LIKE LOWER(?) OR LOWER(COALESCE(r.route_name,'')) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term); params.add(term);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND b.status = ? ");
            params.add(status.trim());
        }
        if (routeId != null) {
            sql.append("AND b.current_route_id = ? ");
            params.add(routeId);
        }

        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count == null ? 0 : count;
    }

    public Optional<TransportDtos.BusResponse> findBusById(long id) {
        String sql =
                "SELECT b.id, b.bus_name, b.registration_no, b.fleet_no, b.bus_type, b.capacity, " +
                "       (SELECT COUNT(*) FROM student_transport_assignments sta WHERE sta.bus_id = b.id AND sta.status = 'ACTIVE') AS active_occupancy, " +
                "       b.current_route_id, r.route_name, r.route_code, " +
                "       b.driver_staff_id, CONCAT_WS(' ', d.first_name, d.last_name) AS driver_name, d.phone AS driver_phone, " +
                "       b.conductor_staff_id, CONCAT_WS(' ', c.first_name, c.last_name) AS conductor_name, c.phone AS conductor_phone, " +
                "       b.status, b.institution_id, b.created_at, b.updated_at " +
                "FROM transport_buses b " +
                "LEFT JOIN transport_routes r ON r.id = b.current_route_id " +
                "LEFT JOIN staff d ON d.id = b.driver_staff_id " +
                "LEFT JOIN staff c ON c.id = b.conductor_staff_id WHERE b.id = ?";

        List<TransportDtos.BusResponse> list = jdbc.query(sql, this::mapBusResponse, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public long createBus(TransportDtos.CreateBusRequest req, Long actorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO transport_buses (bus_name, registration_no, fleet_no, bus_type, capacity, " +
                    "current_route_id, driver_staff_id, conductor_staff_id, status, institution_id, created_by, updated_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, req.busName().trim());
            ps.setString(2, req.registrationNo().trim());
            ps.setString(3, trimToNull(req.fleetNo()));
            ps.setString(4, req.busType() == null || req.busType().isBlank() ? "STANDARD" : req.busType().trim());
            ps.setInt(5, req.capacity());
            ps.setObject(6, req.routeId());
            ps.setObject(7, req.driverStaffId());
            ps.setObject(8, req.conductorStaffId());
            ps.setString(9, req.institutionId() == null || req.institutionId().isBlank() ? "MAIN" : req.institutionId().trim());
            ps.setObject(10, actorId);
            ps.setObject(11, actorId);
            return ps;
        }, keyHolder);

        return extractGeneratedId(keyHolder);
    }

    public void updateBus(long id, TransportDtos.UpdateBusRequest req, Long actorId) {
        jdbc.update(
                "UPDATE transport_buses SET bus_name = ?, registration_no = ?, fleet_no = ?, bus_type = ?, " +
                "capacity = ?, current_route_id = ?, driver_staff_id = ?, conductor_staff_id = ?, status = ?, updated_by = ? " +
                "WHERE id = ?",
                req.busName().trim(), req.registrationNo().trim(), trimToNull(req.fleetNo()),
                req.busType() == null || req.busType().isBlank() ? "STANDARD" : req.busType().trim(),
                req.capacity(), req.routeId(), req.driverStaffId(), req.conductorStaffId(),
                req.status() == null || req.status().isBlank() ? "ACTIVE" : req.status().trim(),
                actorId, id);
    }

    public void deactivateBus(long id, Long actorId) {
        jdbc.update("UPDATE transport_buses SET status = 'INACTIVE', updated_by = ? WHERE id = ?", actorId, id);
    }

    public Optional<BusLockInfo> lockBusForUpdate(long busId) {
        List<BusLockInfo> rows = jdbc.query(
                "SELECT id, capacity, status, current_route_id, " +
                "(SELECT COUNT(*) FROM student_transport_assignments sta WHERE sta.bus_id = transport_buses.id AND sta.status = 'ACTIVE') AS occupancy " +
                "FROM transport_buses WHERE id = ? FOR UPDATE",
                (rs, row) -> new BusLockInfo(
                        rs.getLong("id"),
                        rs.getInt("capacity"),
                        rs.getString("status"),
                        rs.getObject("current_route_id") != null ? rs.getLong("current_route_id") : null,
                        rs.getInt("occupancy")
                ), busId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public int getBusActiveOccupancy(long busId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_transport_assignments WHERE bus_id = ? AND status = 'ACTIVE'",
                Integer.class, busId);
        return count == null ? 0 : count;
    }

    public boolean isRegistrationNoTaken(String registrationNo, Long excludeId) {
        if (excludeId == null) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transport_buses WHERE LOWER(registration_no) = LOWER(?)",
                    Integer.class, registrationNo.trim());
            return count != null && count > 0;
        } else {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transport_buses WHERE LOWER(registration_no) = LOWER(?) AND id != ?",
                    Integer.class, registrationNo.trim(), excludeId);
            return count != null && count > 0;
        }
    }

    public Optional<Long> findActiveBusForDriver(long staffId, Long excludeBusId) {
        String sql = "SELECT id FROM transport_buses WHERE driver_staff_id = ? AND status = 'ACTIVE'";
        if (excludeBusId != null) {
            sql += " AND id != " + excludeBusId;
        }
        sql += " LIMIT 1";
        List<Long> ids = jdbc.query(sql, (rs, row) -> rs.getLong("id"), staffId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }

    public Optional<Long> findActiveBusForConductor(long staffId, Long excludeBusId) {
        String sql = "SELECT id FROM transport_buses WHERE conductor_staff_id = ? AND status = 'ACTIVE'";
        if (excludeBusId != null) {
            sql += " AND id != " + excludeBusId;
        }
        sql += " LIMIT 1";
        List<Long> ids = jdbc.query(sql, (rs, row) -> rs.getLong("id"), staffId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }

    // ==========================================
    // ROUTE OPERATIONS
    // ==========================================

    public List<TransportDtos.RouteResponse> listRoutes(String search, String status, int limit, int offset) {
        var sql = new StringBuilder(
                "SELECT r.id, r.route_name, r.route_code, r.description, r.status, " +
                "       b.id AS assigned_bus_id, b.bus_name AS assigned_bus_name, " +
                "       (SELECT COUNT(*) FROM transport_route_stops s WHERE s.route_id = r.id) AS stops_count, " +
                "       (SELECT COUNT(*) FROM student_transport_assignments sta WHERE sta.route_id = r.id AND sta.status = 'ACTIVE') AS active_students_count, " +
                "       r.institution_id, r.created_at, r.updated_at " +
                "FROM transport_routes r " +
                "LEFT JOIN transport_buses b ON b.current_route_id = r.id AND b.status = 'ACTIVE' " +
                "WHERE 1=1 ");

        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(r.route_name) LIKE LOWER(?) OR LOWER(r.route_code) LIKE LOWER(?) OR LOWER(COALESCE(r.description,'')) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND r.status = ? ");
            params.add(status.trim());
        }

        sql.append("ORDER BY r.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), this::mapRouteResponse, params.toArray());
    }

    public int countRoutes(String search, String status) {
        var sql = new StringBuilder("SELECT COUNT(*) FROM transport_routes r WHERE 1=1 ");
        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(r.route_name) LIKE LOWER(?) OR LOWER(r.route_code) LIKE LOWER(?) OR LOWER(COALESCE(r.description,'')) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND r.status = ? ");
            params.add(status.trim());
        }

        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count == null ? 0 : count;
    }

    public Optional<TransportDtos.RouteResponse> findRouteById(long id) {
        String sql =
                "SELECT r.id, r.route_name, r.route_code, r.description, r.status, " +
                "       b.id AS assigned_bus_id, b.bus_name AS assigned_bus_name, " +
                "       (SELECT COUNT(*) FROM transport_route_stops s WHERE s.route_id = r.id) AS stops_count, " +
                "       (SELECT COUNT(*) FROM student_transport_assignments sta WHERE sta.route_id = r.id AND sta.status = 'ACTIVE') AS active_students_count, " +
                "       r.institution_id, r.created_at, r.updated_at " +
                "FROM transport_routes r " +
                "LEFT JOIN transport_buses b ON b.current_route_id = r.id AND b.status = 'ACTIVE' " +
                "WHERE r.id = ?";

        List<TransportDtos.RouteResponse> list = jdbc.query(sql, this::mapRouteResponse, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public long createRoute(TransportDtos.CreateRouteRequest req, Long actorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO transport_routes (route_name, route_code, description, status, institution_id, created_by, updated_by) " +
                    "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, req.routeName().trim());
            ps.setString(2, req.routeCode().trim());
            ps.setString(3, trimToNull(req.description()));
            ps.setString(4, req.institutionId() == null || req.institutionId().isBlank() ? "MAIN" : req.institutionId().trim());
            ps.setObject(5, actorId);
            ps.setObject(6, actorId);
            return ps;
        }, keyHolder);

        return extractGeneratedId(keyHolder);
    }

    public void updateRoute(long id, TransportDtos.UpdateRouteRequest req, Long actorId) {
        jdbc.update(
                "UPDATE transport_routes SET route_name = ?, route_code = ?, description = ?, status = ?, updated_by = ? " +
                "WHERE id = ?",
                req.routeName().trim(), req.routeCode().trim(), trimToNull(req.description()),
                req.status() == null || req.status().isBlank() ? "ACTIVE" : req.status().trim(),
                actorId, id);
    }

    public void deactivateRoute(long id, Long actorId) {
        jdbc.update("UPDATE transport_routes SET status = 'INACTIVE', updated_by = ? WHERE id = ?", actorId, id);
    }

    public boolean isRouteCodeTaken(String routeCode, Long excludeId) {
        if (excludeId == null) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transport_routes WHERE LOWER(route_code) = LOWER(?)",
                    Integer.class, routeCode.trim());
            return count != null && count > 0;
        } else {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transport_routes WHERE LOWER(route_code) = LOWER(?) AND id != ?",
                    Integer.class, routeCode.trim(), excludeId);
            return count != null && count > 0;
        }
    }

    // ==========================================
    // ROUTE STOPS OPERATIONS
    // ==========================================

    public List<TransportDtos.RouteStopResponse> findStopsByRouteId(long routeId) {
        return jdbc.query(
                "SELECT id, route_id, stop_name, stop_order, pickup_time, drop_time " +
                "FROM transport_route_stops WHERE route_id = ? ORDER BY stop_order ASC",
                this::mapRouteStopResponse, routeId);
    }

    public Optional<TransportDtos.RouteStopResponse> findStopById(long stopId) {
        List<TransportDtos.RouteStopResponse> rows = jdbc.query(
                "SELECT id, route_id, stop_name, stop_order, pickup_time, drop_time " +
                "FROM transport_route_stops WHERE id = ?",
                this::mapRouteStopResponse, stopId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public long createStop(long routeId, String stopName, int stopOrder, LocalTime pickupTime, LocalTime dropTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO transport_route_stops (route_id, stop_name, stop_order, pickup_time, drop_time) " +
                    "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, routeId);
            ps.setString(2, stopName.trim());
            ps.setInt(3, stopOrder);
            ps.setObject(4, pickupTime != null ? Time.valueOf(pickupTime) : null);
            ps.setObject(5, dropTime != null ? Time.valueOf(dropTime) : null);
            return ps;
        }, keyHolder);

        return extractGeneratedId(keyHolder);
    }

    public void updateStop(long stopId, String stopName, int stopOrder, LocalTime pickupTime, LocalTime dropTime) {
        jdbc.update(
                "UPDATE transport_route_stops SET stop_name = ?, stop_order = ?, pickup_time = ?, drop_time = ? WHERE id = ?",
                stopName.trim(), stopOrder,
                pickupTime != null ? Time.valueOf(pickupTime) : null,
                dropTime != null ? Time.valueOf(dropTime) : null,
                stopId);
    }

    public void deleteStop(long stopId) {
        jdbc.update("DELETE FROM transport_route_stops WHERE id = ?", stopId);
    }

    public void deleteAllStopsForRoute(long routeId) {
        jdbc.update("DELETE FROM transport_route_stops WHERE route_id = ?", routeId);
    }

    public int countActiveAssignmentsForRoute(long routeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_transport_assignments WHERE route_id = ? AND status = 'ACTIVE'",
                Integer.class, routeId);
        return count == null ? 0 : count;
    }

    public int countActiveBusesForRoute(long routeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_buses WHERE current_route_id = ? AND status = 'ACTIVE'",
                Integer.class, routeId);
        return count == null ? 0 : count;
    }

    // ==========================================
    // STUDENT ASSIGNMENT OPERATIONS
    // ==========================================

    public List<TransportDtos.StudentAssignmentResponse> listAssignments(
            String search, Long busId, Long routeId, String status, int limit, int offset) {
        var sql = new StringBuilder(
                "SELECT sta.id, sta.student_id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS student_name, " +
                "       sta.bus_id, b.bus_name, b.registration_no, " +
                "       sta.route_id, r.route_name, " +
                "       sta.pickup_stop_id, ps.stop_name AS pickup_stop_name, ps.pickup_time, " +
                "       sta.drop_stop_id, ds.stop_name AS drop_stop_name, ds.drop_time, " +
                "       sta.start_date, sta.end_date, sta.status, sta.created_at, sta.updated_at " +
                "FROM student_transport_assignments sta " +
                "JOIN students s ON s.id = sta.student_id " +
                "JOIN transport_buses b ON b.id = sta.bus_id " +
                "JOIN transport_routes r ON r.id = sta.route_id " +
                "LEFT JOIN transport_route_stops ps ON ps.id = sta.pickup_stop_id " +
                "LEFT JOIN transport_route_stops ds ON ds.id = sta.drop_stop_id " +
                "WHERE 1=1 ");

        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(s.first_name) LIKE LOWER(?) OR LOWER(COALESCE(s.last_name,'')) LIKE LOWER(?) " +
                    "OR LOWER(s.admission_no) LIKE LOWER(?) OR LOWER(b.bus_name) LIKE LOWER(?) OR LOWER(r.route_name) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term); params.add(term); params.add(term);
        }
        if (busId != null) {
            sql.append("AND sta.bus_id = ? ");
            params.add(busId);
        }
        if (routeId != null) {
            sql.append("AND sta.route_id = ? ");
            params.add(routeId);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND sta.status = ? ");
            params.add(status.trim());
        }

        sql.append("ORDER BY sta.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), this::mapAssignmentResponse, params.toArray());
    }

    public int countAssignments(String search, Long busId, Long routeId, String status) {
        var sql = new StringBuilder(
                "SELECT COUNT(*) FROM student_transport_assignments sta " +
                "JOIN students s ON s.id = sta.student_id " +
                "JOIN transport_buses b ON b.id = sta.bus_id " +
                "JOIN transport_routes r ON r.id = sta.route_id WHERE 1=1 ");

        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(s.first_name) LIKE LOWER(?) OR LOWER(COALESCE(s.last_name,'')) LIKE LOWER(?) " +
                    "OR LOWER(s.admission_no) LIKE LOWER(?) OR LOWER(b.bus_name) LIKE LOWER(?) OR LOWER(r.route_name) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term); params.add(term); params.add(term);
        }
        if (busId != null) {
            sql.append("AND sta.bus_id = ? ");
            params.add(busId);
        }
        if (routeId != null) {
            sql.append("AND sta.route_id = ? ");
            params.add(routeId);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND sta.status = ? ");
            params.add(status.trim());
        }

        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count == null ? 0 : count;
    }

    public Optional<TransportDtos.StudentAssignmentResponse> findAssignmentById(long id) {
        String sql =
                "SELECT sta.id, sta.student_id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS student_name, " +
                "       sta.bus_id, b.bus_name, b.registration_no, " +
                "       sta.route_id, r.route_name, " +
                "       sta.pickup_stop_id, ps.stop_name AS pickup_stop_name, ps.pickup_time, " +
                "       sta.drop_stop_id, ds.stop_name AS drop_stop_name, ds.drop_time, " +
                "       sta.start_date, sta.end_date, sta.status, sta.created_at, sta.updated_at " +
                "FROM student_transport_assignments sta " +
                "JOIN students s ON s.id = sta.student_id " +
                "JOIN transport_buses b ON b.id = sta.bus_id " +
                "JOIN transport_routes r ON r.id = sta.route_id " +
                "LEFT JOIN transport_route_stops ps ON ps.id = sta.pickup_stop_id " +
                "LEFT JOIN transport_route_stops ds ON ds.id = sta.drop_stop_id " +
                "WHERE sta.id = ?";

        List<TransportDtos.StudentAssignmentResponse> list = jdbc.query(sql, this::mapAssignmentResponse, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public Optional<TransportDtos.StudentAssignmentResponse> findActiveAssignmentByStudentId(long studentId) {
        String sql =
                "SELECT sta.id, sta.student_id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS student_name, " +
                "       sta.bus_id, b.bus_name, b.registration_no, " +
                "       sta.route_id, r.route_name, " +
                "       sta.pickup_stop_id, ps.stop_name AS pickup_stop_name, ps.pickup_time, " +
                "       sta.drop_stop_id, ds.stop_name AS drop_stop_name, ds.drop_time, " +
                "       sta.start_date, sta.end_date, sta.status, sta.created_at, sta.updated_at " +
                "FROM student_transport_assignments sta " +
                "JOIN students s ON s.id = sta.student_id " +
                "JOIN transport_buses b ON b.id = sta.bus_id " +
                "JOIN transport_routes r ON r.id = sta.route_id " +
                "LEFT JOIN transport_route_stops ps ON ps.id = sta.pickup_stop_id " +
                "LEFT JOIN transport_route_stops ds ON ds.id = sta.drop_stop_id " +
                "WHERE sta.student_id = ? AND sta.status = 'ACTIVE' LIMIT 1";

        List<TransportDtos.StudentAssignmentResponse> list = jdbc.query(sql, this::mapAssignmentResponse, studentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public long createAssignment(long studentId, long busId, long routeId, Long pickupStopId, Long dropStopId,
                                 LocalDate startDate, LocalDate endDate, Long actorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO student_transport_assignments (student_id, bus_id, route_id, pickup_stop_id, drop_stop_id, " +
                    "start_date, end_date, status, institution_id, created_by, updated_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 'MAIN', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, studentId);
            ps.setLong(2, busId);
            ps.setLong(3, routeId);
            ps.setObject(4, pickupStopId);
            ps.setObject(5, dropStopId);
            ps.setDate(6, Date.valueOf(startDate));
            ps.setObject(7, endDate != null ? Date.valueOf(endDate) : null);
            ps.setObject(8, actorId);
            ps.setObject(9, actorId);
            return ps;
        }, keyHolder);

        return extractGeneratedId(keyHolder);
    }

    public void updateAssignmentStatus(long assignmentId, String status, LocalDate endDate, Long actorId) {
        jdbc.update(
                "UPDATE student_transport_assignments SET status = ?, end_date = ?, updated_by = ? WHERE id = ?",
                status, endDate != null ? Date.valueOf(endDate) : null, actorId, assignmentId);
    }

    public void reassignAssignment(long assignmentId, long busId, long routeId, Long pickupStopId, Long dropStopId, Long actorId) {
        jdbc.update(
                "UPDATE student_transport_assignments SET bus_id = ?, route_id = ?, pickup_stop_id = ?, drop_stop_id = ?, updated_by = ? WHERE id = ?",
                busId, routeId, pickupStopId, dropStopId, actorId, assignmentId);
    }

    // ==========================================
    // ASSIGNMENT HISTORY
    // ==========================================

    public void recordHistory(long assignmentId, long studentId, long busId, long routeId,
                              Long pickupStopId, Long dropStopId, String action, String reason, Long changedBy) {
        jdbc.update(
                "INSERT INTO transport_assignment_history (assignment_id, student_id, bus_id, route_id, " +
                "pickup_stop_id, drop_stop_id, action, reason, changed_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                assignmentId, studentId, busId, routeId, pickupStopId, dropStopId, action, reason, changedBy);
    }

    public List<TransportDtos.AssignmentHistoryResponse> listHistory(Long studentId, Long busId, int limit) {
        var sql = new StringBuilder(
                "SELECT h.id, h.assignment_id, h.student_id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS student_name, " +
                "       h.bus_id, b.bus_name, h.route_id, r.route_name, " +
                "       h.action, h.reason, h.changed_by, COALESCE(u.full_name, u.username, 'System') AS changer_name, h.changed_at " +
                "FROM transport_assignment_history h " +
                "JOIN students s ON s.id = h.student_id " +
                "JOIN transport_buses b ON b.id = h.bus_id " +
                "JOIN transport_routes r ON r.id = h.route_id " +
                "LEFT JOIN users u ON u.id = h.changed_by WHERE 1=1 ");

        var params = new ArrayList<Object>();
        if (studentId != null) {
            sql.append("AND h.student_id = ? ");
            params.add(studentId);
        }
        if (busId != null) {
            sql.append("AND h.bus_id = ? ");
            params.add(busId);
        }
        sql.append("ORDER BY h.id DESC LIMIT ?");
        params.add(limit);

        return jdbc.query(sql.toString(), (rs, row) -> new TransportDtos.AssignmentHistoryResponse(
                rs.getLong("id"),
                rs.getLong("assignment_id"),
                rs.getLong("student_id"),
                rs.getString("student_name"),
                rs.getString("admission_no"),
                rs.getLong("bus_id"),
                rs.getString("bus_name"),
                rs.getLong("route_id"),
                rs.getString("route_name"),
                rs.getString("action"),
                rs.getString("reason"),
                rs.getObject("changed_by") != null ? rs.getLong("changed_by") : null,
                rs.getString("changer_name"),
                rs.getTimestamp("changed_at").toLocalDateTime()
        ), params.toArray());
    }

    // ==========================================
    // STUDENT & PARENT SCOPED VIEWS
    // ==========================================

    public Optional<TransportDtos.StudentTransportView> findStudentTransportByUserId(long userId) {
        String sql =
                "SELECT s.id AS student_id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS student_name, " +
                "       sta.id AS assignment_id, sta.status AS assignment_status, " +
                "       b.bus_name, b.registration_no, r.id AS route_id, r.route_name, " +
                "       ps.stop_name AS pickup_stop_name, ps.pickup_time, " +
                "       ds.stop_name AS drop_stop_name, ds.drop_time, " +
                "       CONCAT_WS(' ', d.first_name, d.last_name) AS driver_name, " +
                "       CONCAT_WS(' ', c.first_name, c.last_name) AS conductor_name " +
                "FROM students s " +
                "LEFT JOIN student_transport_assignments sta ON sta.student_id = s.id AND sta.status = 'ACTIVE' " +
                "LEFT JOIN transport_buses b ON b.id = sta.bus_id " +
                "LEFT JOIN transport_routes r ON r.id = sta.route_id " +
                "LEFT JOIN transport_route_stops ps ON ps.id = sta.pickup_stop_id " +
                "LEFT JOIN transport_route_stops ds ON ds.id = sta.drop_stop_id " +
                "LEFT JOIN staff d ON d.id = b.driver_staff_id " +
                "LEFT JOIN staff c ON c.id = b.conductor_staff_id " +
                "WHERE s.user_id = ? LIMIT 1";

        List<TransportDtos.StudentTransportView> list = jdbc.query(sql, (rs, row) -> {
            Long assignmentId = rs.getObject("assignment_id") != null ? rs.getLong("assignment_id") : null;
            boolean assigned = assignmentId != null;
            long studentId = rs.getLong("student_id");
            String studentName = rs.getString("student_name");
            String admissionNo = rs.getString("admission_no");

            if (!assigned) {
                return new TransportDtos.StudentTransportView(
                        false, null, studentId, studentName, admissionNo,
                        null, null, null, null, null, null, null, null, null, null,
                        List.of(), "No bus or route is currently assigned to you.");
            }

            long routeId = rs.getLong("route_id");
            List<TransportDtos.RouteStopResponse> stops = findStopsByRouteId(routeId);

            return new TransportDtos.StudentTransportView(
                    true, assignmentId, studentId, studentName, admissionNo,
                    rs.getString("bus_name"),
                    rs.getString("registration_no"),
                    rs.getString("route_name"),
                    rs.getString("pickup_stop_name"),
                    rs.getTime("pickup_time") != null ? rs.getTime("pickup_time").toLocalTime() : null,
                    rs.getString("drop_stop_name"),
                    rs.getTime("drop_time") != null ? rs.getTime("drop_time").toLocalTime() : null,
                    rs.getString("driver_name"),
                    rs.getString("conductor_name"),
                    rs.getString("assignment_status"),
                    stops,
                    "Currently Assigned");
        }, userId);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<TransportDtos.ParentChildTransportView> findChildrenTransportByParentUserId(long userId) {
        String sql =
                "SELECT s.id AS student_id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS child_name, " +
                "       sta.id AS assignment_id, sta.status AS assignment_status, " +
                "       b.id AS bus_id, b.bus_name, b.registration_no, b.capacity, " +
                "       (SELECT COUNT(*) FROM student_transport_assignments sub WHERE sub.bus_id = b.id AND sub.status = 'ACTIVE') AS occupancy, " +
                "       r.id AS route_id, r.route_name, " +
                "       ps.stop_name AS pickup_stop_name, ps.pickup_time, " +
                "       ds.stop_name AS drop_stop_name, ds.drop_time, " +
                "       CONCAT_WS(' ', d.first_name, d.last_name) AS driver_name, " +
                "       CONCAT_WS(' ', c.first_name, c.last_name) AS conductor_name " +
                "FROM guardians g " +
                "JOIN student_guardians sg ON sg.guardian_id = g.id " +
                "JOIN students s ON s.id = sg.student_id " +
                "LEFT JOIN student_transport_assignments sta ON sta.student_id = s.id AND sta.status = 'ACTIVE' " +
                "LEFT JOIN transport_buses b ON b.id = sta.bus_id " +
                "LEFT JOIN transport_routes r ON r.id = sta.route_id " +
                "LEFT JOIN transport_route_stops ps ON ps.id = sta.pickup_stop_id " +
                "LEFT JOIN transport_route_stops ds ON ds.id = sta.drop_stop_id " +
                "LEFT JOIN staff d ON d.id = b.driver_staff_id " +
                "LEFT JOIN staff c ON c.id = b.conductor_staff_id " +
                "WHERE g.user_id = ? ORDER BY s.id ASC";

        return jdbc.query(sql, (rs, row) -> {
            long childId = rs.getLong("student_id");
            String childName = rs.getString("child_name");
            String admissionNo = rs.getString("admission_no");
            Long assignmentId = rs.getObject("assignment_id") != null ? rs.getLong("assignment_id") : null;
            boolean assigned = assignmentId != null;

            if (!assigned) {
                return new TransportDtos.ParentChildTransportView(
                        childId, childName, admissionNo, false, null,
                        null, null, null, null, null, null, null, null, null, null,
                        null, null, null, List.of(),
                        "No bus or route is currently assigned to this child.");
            }

            int capacity = rs.getInt("capacity");
            int occupancy = rs.getInt("occupancy");
            int availableSeats = Math.max(0, capacity - occupancy);
            long routeId = rs.getLong("route_id");
            List<TransportDtos.RouteStopResponse> stops = findStopsByRouteId(routeId);

            return new TransportDtos.ParentChildTransportView(
                    childId, childName, admissionNo, true, assignmentId,
                    rs.getString("bus_name"),
                    rs.getString("registration_no"),
                    rs.getString("route_name"),
                    rs.getString("pickup_stop_name"),
                    rs.getTime("pickup_time") != null ? rs.getTime("pickup_time").toLocalTime() : null,
                    rs.getString("drop_stop_name"),
                    rs.getTime("drop_time") != null ? rs.getTime("drop_time").toLocalTime() : null,
                    rs.getString("driver_name"),
                    rs.getString("conductor_name"),
                    rs.getString("assignment_status"),
                    capacity, occupancy, availableSeats, stops,
                    "Currently Assigned");
        }, userId);
    }

    // ==========================================
    // OVERVIEW METRICS
    // ==========================================

    public TransportDtos.TransportOverviewResponse getOverview() {
        Integer totalActiveBuses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_buses WHERE status = 'ACTIVE'", Integer.class);
        Integer totalActiveRoutes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_routes WHERE status = 'ACTIVE'", Integer.class);
        Integer totalAssignedStudents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_transport_assignments WHERE status = 'ACTIVE'", Integer.class);

        Integer totalCapacity = jdbc.queryForObject(
                "SELECT COALESCE(SUM(capacity), 0) FROM transport_buses WHERE status = 'ACTIVE'", Integer.class);
        int availableSeats = Math.max(0, (totalCapacity == null ? 0 : totalCapacity) - (totalAssignedStudents == null ? 0 : totalAssignedStudents));

        Integer fullBusesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_buses b WHERE b.status = 'ACTIVE' AND b.capacity <= " +
                "(SELECT COUNT(*) FROM student_transport_assignments sta WHERE sta.bus_id = b.id AND sta.status = 'ACTIVE')",
                Integer.class);

        Integer busesWithoutDriver = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_buses WHERE status = 'ACTIVE' AND driver_staff_id IS NULL", Integer.class);

        Integer busesWithoutConductor = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_buses WHERE status = 'ACTIVE' AND conductor_staff_id IS NULL", Integer.class);

        Integer routesWithoutBus = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_routes r WHERE r.status = 'ACTIVE' AND NOT EXISTS " +
                "(SELECT 1 FROM transport_buses b WHERE b.current_route_id = r.id AND b.status = 'ACTIVE')", Integer.class);

        List<TransportDtos.AssignmentHistoryResponse> recent = listHistory(null, null, 8);

        return new TransportDtos.TransportOverviewResponse(
                totalActiveBuses == null ? 0 : totalActiveBuses,
                totalActiveRoutes == null ? 0 : totalActiveRoutes,
                totalAssignedStudents == null ? 0 : totalAssignedStudents,
                availableSeats,
                fullBusesCount == null ? 0 : fullBusesCount,
                busesWithoutDriver == null ? 0 : busesWithoutDriver,
                busesWithoutConductor == null ? 0 : busesWithoutConductor,
                routesWithoutBus == null ? 0 : routesWithoutBus,
                recent
        );
    }

    // ==========================================
    // OPTIONS & HELPERS
    // ==========================================

    public List<TransportDtos.TransportStaffOption> listStaffOptions(String role) {
        StringBuilder sql = new StringBuilder(
                "SELECT st.id, st.employee_no, CONCAT_WS(' ', st.first_name, st.last_name) AS name, " +
                "       st.designation, st.phone, b.id AS assigned_bus_id, b.bus_name AS assigned_bus_name " +
                "FROM staff st " +
                "LEFT JOIN transport_buses b ON (b.driver_staff_id = st.id OR b.conductor_staff_id = st.id) AND b.status = 'ACTIVE' " +
                "WHERE st.status = 'ACTIVE' "
        );
        if ("DRIVER".equalsIgnoreCase(role)) {
            sql.append("AND LOWER(COALESCE(st.designation, '')) LIKE '%driver%' ");
        } else if ("CONDUCTOR".equalsIgnoreCase(role)) {
            sql.append("AND LOWER(COALESCE(st.designation, '')) LIKE '%conductor%' ");
        } else {
            // General transport staff list: only drivers and conductors (strictly excludes teachers, accountants, etc.)
            sql.append("AND (LOWER(COALESCE(st.designation, '')) LIKE '%driver%' OR LOWER(COALESCE(st.designation, '')) LIKE '%conductor%') ");
        }
        sql.append("ORDER BY st.first_name, st.last_name");

        return jdbc.query(sql.toString(),
                (rs, row) -> new TransportDtos.TransportStaffOption(
                        rs.getLong("id"),
                        rs.getString("employee_no"),
                        rs.getString("name"),
                        rs.getString("designation"),
                        rs.getString("phone"),
                        rs.getObject("assigned_bus_id") != null ? rs.getLong("assigned_bus_id") : null,
                        rs.getString("assigned_bus_name")
                ));
    }

    public List<TransportDtos.TransportStaffOption> listStaffOptions() {
        return listStaffOptions(null);
    }

    public TransportDtos.TransportStaffOption createTransportStaff(TransportDtos.CreateTransportStaffRequest req) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE employee_no = ?", Integer.class, req.employeeNo().trim());
        if (count != null && count > 0) {
            throw TransportException.conflict("employeeNo", "Employee number '" + req.employeeNo().trim() + "' is already registered");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            var ps = conn.prepareStatement(
                    "INSERT INTO staff(employee_no, first_name, last_name, designation, phone, department, join_date, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, 'Transport', CURRENT_DATE(), 'ACTIVE', NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, req.employeeNo().trim());
            ps.setString(2, req.firstName().trim());
            ps.setString(3, trimToNull(req.lastName()));
            ps.setString(4, req.designation().trim());
            ps.setString(5, trimToNull(req.phone()));
            return ps;
        }, keyHolder);

        long id = extractGeneratedId(keyHolder);
        String fullName = (req.firstName().trim() + " " + (req.lastName() != null ? req.lastName().trim() : "")).trim();
        return new TransportDtos.TransportStaffOption(
                id,
                req.employeeNo().trim(),
                fullName,
                req.designation().trim(),
                trimToNull(req.phone()),
                null,
                null
        );
    }

    public List<TransportDtos.StudentTransportOption> listStudentOptions(String search) {
        var sql = new StringBuilder(
                "SELECT s.id, s.admission_no, CONCAT_WS(' ', s.first_name, s.last_name) AS name, s.status, " +
                "       sta.id AS active_assignment_id, b.bus_name AS current_bus_name " +
                "FROM students s " +
                "LEFT JOIN student_transport_assignments sta ON sta.student_id = s.id AND sta.status = 'ACTIVE' " +
                "LEFT JOIN transport_buses b ON b.id = sta.bus_id " +
                "WHERE s.status = 'ACTIVE' ");

        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(s.first_name) LIKE LOWER(?) OR LOWER(COALESCE(s.last_name,'')) LIKE LOWER(?) OR LOWER(s.admission_no) LIKE LOWER(?)) ");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term);
        }
        sql.append("ORDER BY s.first_name, s.last_name LIMIT 50");

        return jdbc.query(sql.toString(), (rs, row) -> new TransportDtos.StudentTransportOption(
                rs.getLong("id"),
                rs.getString("admission_no"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getObject("active_assignment_id") != null,
                rs.getString("current_bus_name")
        ), params.toArray());
    }

    public boolean isStaffActive(long staffId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff WHERE id = ? AND status = 'ACTIVE'", Integer.class, staffId);
        return count != null && count > 0;
    }

    public boolean isStaffDriver(long staffId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff WHERE id = ? AND status = 'ACTIVE' AND LOWER(COALESCE(designation, '')) LIKE '%driver%'",
                Integer.class, staffId);
        return count != null && count > 0;
    }

    public boolean isStaffConductor(long staffId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff WHERE id = ? AND status = 'ACTIVE' AND LOWER(COALESCE(designation, '')) LIKE '%conductor%'",
                Integer.class, staffId);
        return count != null && count > 0;
    }

    public boolean isStudentActive(long studentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM students WHERE id = ? AND status = 'ACTIVE'", Integer.class, studentId);
        return count != null && count > 0;
    }

    public boolean isStopOnRoute(long stopId, long routeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_route_stops WHERE id = ? AND route_id = ?",
                Integer.class, stopId, routeId);
        return count != null && count > 0;
    }

    // ==========================================
    // MAPPING HELPERS
    // ==========================================

    private TransportDtos.BusResponse mapBusResponse(ResultSet rs, int row) throws SQLException {
        int capacity = rs.getInt("capacity");
        int occupancy = rs.getInt("active_occupancy");
        int available = Math.max(0, capacity - occupancy);

        return new TransportDtos.BusResponse(
                rs.getLong("id"),
                rs.getString("bus_name"),
                rs.getString("registration_no"),
                rs.getString("fleet_no"),
                rs.getString("bus_type"),
                capacity,
                occupancy,
                available,
                rs.getObject("current_route_id") != null ? rs.getLong("current_route_id") : null,
                rs.getString("route_name"),
                rs.getString("route_code"),
                rs.getObject("driver_staff_id") != null ? rs.getLong("driver_staff_id") : null,
                rs.getString("driver_name"),
                rs.getString("driver_phone"),
                rs.getObject("conductor_staff_id") != null ? rs.getLong("conductor_staff_id") : null,
                rs.getString("conductor_name"),
                rs.getString("conductor_phone"),
                rs.getString("status"),
                rs.getString("institution_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private TransportDtos.RouteResponse mapRouteResponse(ResultSet rs, int row) throws SQLException {
        return new TransportDtos.RouteResponse(
                rs.getLong("id"),
                rs.getString("route_name"),
                rs.getString("route_code"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getObject("assigned_bus_id") != null ? rs.getLong("assigned_bus_id") : null,
                rs.getString("assigned_bus_name"),
                rs.getInt("stops_count"),
                rs.getInt("active_students_count"),
                rs.getString("institution_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private TransportDtos.RouteStopResponse mapRouteStopResponse(ResultSet rs, int row) throws SQLException {
        Time pickup = rs.getTime("pickup_time");
        Time drop = rs.getTime("drop_time");
        return new TransportDtos.RouteStopResponse(
                rs.getLong("id"),
                rs.getLong("route_id"),
                rs.getString("stop_name"),
                rs.getInt("stop_order"),
                pickup != null ? pickup.toLocalTime() : null,
                drop != null ? drop.toLocalTime() : null
        );
    }

    private TransportDtos.StudentAssignmentResponse mapAssignmentResponse(ResultSet rs, int row) throws SQLException {
        Time pickup = rs.getTime("pickup_time");
        Time drop = rs.getTime("drop_time");
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("end_date");

        return new TransportDtos.StudentAssignmentResponse(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("admission_no"),
                rs.getString("student_name"),
                rs.getLong("bus_id"),
                rs.getString("bus_name"),
                rs.getString("registration_no"),
                rs.getLong("route_id"),
                rs.getString("route_name"),
                rs.getObject("pickup_stop_id") != null ? rs.getLong("pickup_stop_id") : null,
                rs.getString("pickup_stop_name"),
                pickup != null ? pickup.toLocalTime() : null,
                rs.getObject("drop_stop_id") != null ? rs.getLong("drop_stop_id") : null,
                rs.getString("drop_stop_name"),
                drop != null ? drop.toLocalTime() : null,
                start != null ? start.toLocalDate() : null,
                end != null ? end.toLocalDate() : null,
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private static long extractGeneratedId(KeyHolder keyHolder) {
        if (keyHolder.getKeys() != null) {
            Object idVal = keyHolder.getKeys().get("ID");
            if (idVal == null) idVal = keyHolder.getKeys().get("id");
            if (idVal instanceof Number n) return n.longValue();
        }
        if (keyHolder.getKeyList() != null && !keyHolder.getKeyList().isEmpty()) {
            var firstMap = keyHolder.getKeyList().getFirst();
            Object idVal = firstMap.get("ID");
            if (idVal == null) idVal = firstMap.get("id");
            if (idVal instanceof Number n) return n.longValue();
        }
        Number key = keyHolder.getKey();
        if (key != null) return key.longValue();
        throw new IllegalStateException("Failed to extract generated ID from database");
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    public record BusLockInfo(
            long id,
            int capacity,
            String status,
            Long currentRouteId,
            int occupancy
    ) {}
}
