package com.erp.portal.operations;

import com.erp.portal.security.SecurityPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class OperationsRepository {
    private final JdbcTemplate jdbc;
    public OperationsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public List<OperationsDtos.ItemResponse> items() {
        return jdbc.query("SELECT id,name,code,category,unit,current_stock,min_stock_level,description FROM inventory_items ORDER BY name", (r, row) ->
                new OperationsDtos.ItemResponse(r.getLong("id"), r.getString("name"), r.getString("code"), r.getString("category"),
                        r.getString("unit"), r.getInt("current_stock"), r.getInt("min_stock_level"), r.getString("description")));
    }

    public long createItem(OperationsDtos.CreateItemRequest request) {
        return insert("INSERT INTO inventory_items(name,code,category,unit,current_stock,min_stock_level,description) VALUES(?,?,?,?,?,?,?)",
                request.name(), request.code(), request.category(), request.unit(), request.currentStock(), request.minStockLevel(), request.description());
    }

    public int updateStock(long itemId, int balance) {
        return jdbc.update("UPDATE inventory_items SET current_stock=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", balance, itemId);
    }

    public long createMovement(long itemId, String type, int quantity, String referenceNo, String remarks, long actorId) {
        return insert("INSERT INTO stock_movements(item_id,movement_type,quantity,reference_no,remarks,moved_by) VALUES(?,?,?,?,?,?)",
                itemId, type, quantity, referenceNo, remarks, actorId);
    }

    public List<OperationsDtos.CategoryResponse> categories() {
        return jdbc.query("SELECT id,name FROM inventory_categories ORDER BY name", (r, row) -> new OperationsDtos.CategoryResponse(r.getLong("id"), r.getString("name")));
    }

    public long createCategory(String name) { return insert("INSERT INTO inventory_categories(name) VALUES(?)", name); }

    public List<OperationsDtos.SupplierResponse> suppliers() {
        return jdbc.query("SELECT id,name,contact,code FROM inventory_suppliers ORDER BY name", (r, row) ->
                new OperationsDtos.SupplierResponse(r.getLong("id"), r.getString("name"), r.getString("contact"), r.getString("code")));
    }

    public long createSupplier(String name, String contact, String code) {
        return insert("INSERT INTO inventory_suppliers(name,contact,code) VALUES(?,?,?)", name, contact, code);
    }

    public List<OperationsDtos.LibraryItemResponse> libraryItems() {
        return jdbc.query("SELECT id,isbn,title,author,category,total_copies,available_copies,rack_location FROM library_items ORDER BY title", (r, row) ->
                new OperationsDtos.LibraryItemResponse(r.getLong("id"), r.getString("isbn"), r.getString("title"), r.getString("author"),
                        r.getString("category"), r.getInt("total_copies"), r.getInt("available_copies"), r.getString("rack_location")));
    }

    public long createLibraryItem(OperationsDtos.CreateLibraryItemRequest request) {
        return insert("INSERT INTO library_items(isbn,title,author,category,total_copies,available_copies,rack_location) VALUES(?,?,?,?,?,?,?)",
                request.isbn(), request.title(), request.author(), request.category(), request.totalCopies(), request.availableCopies(), request.rackLocation());
    }

    public Long currentStock(long itemId) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id=?", Long.class, itemId);
    }

    public long issueLibrary(long itemId, String borrowerType, Long studentId, LocalDate issuedDate,
                              LocalDate dueDate, String status, long actorId) {
        return insert("INSERT INTO library_issues(item_id,borrower_type,student_id,issued_date,due_date,fine_amount,status,issued_by) VALUES(?,?,?,?,?,?,?,?)",
                itemId, borrowerType, studentId, Date.valueOf(issuedDate), Date.valueOf(dueDate), BigDecimal.ZERO, status, actorId);
    }

    public int updateLibraryAvailability(long itemId, int available) {
        return jdbc.update("UPDATE library_items SET available_copies=? WHERE id=?", available, itemId);
    }

    public int returnLibrary(long issueId, LocalDate returnedDate, BigDecimal fine, String notes) {
        return jdbc.update("UPDATE library_issues SET returned_date=?,fine_amount=?,status='RETURNED' WHERE id=?",
                Date.valueOf(returnedDate), fine, issueId);
    }

    public int restoreLibraryAvailability(long issueId) {
        return jdbc.update("UPDATE library_items l SET available_copies = available_copies + 1 WHERE l.id = (SELECT item_id FROM library_issues WHERE id = ?)", issueId);
    }

    public List<OperationsDtos.IssueResponse> issues() {
        return jdbc.query("SELECT id,item_id,borrower_type,student_id,status,issued_date,due_date,fine_amount FROM library_issues ORDER BY issued_date DESC,id DESC", (r, row) ->
                new OperationsDtos.IssueResponse(r.getLong("id"), r.getLong("item_id"), r.getString("borrower_type"), r.getLong("student_id"),
                        r.getString("status"), r.getDate("issued_date").toLocalDate(), r.getDate("due_date").toLocalDate(), r.getBigDecimal("fine_amount")));
    }

    public long createRoom(OperationsDtos.CreateRoomRequest request) {
        return insert("INSERT INTO hostel_rooms(room_no,floor,block,capacity,room_type,is_active) VALUES(?,?,?,?,?,?)",
                request.roomNo(), request.floor(), request.block(), request.capacity(), request.roomType(), request.active() ? 1 : 0);
    }

    public List<OperationsDtos.RoomResponse> rooms() {
        return jdbc.query("SELECT id,room_no,floor,block,capacity,room_type,is_active FROM hostel_rooms ORDER BY room_no", (r, row) ->
                new OperationsDtos.RoomResponse(r.getLong("id"), r.getString("room_no"), r.getString("floor"),
                        r.getString("block"), r.getInt("capacity"), r.getString("room_type"), r.getBoolean("is_active")));
    }

    public long allocate(long roomId, long studentId, long sessionId, String bedNo, LocalDate from, LocalDate to, String status, long actorId) {
        return insert("INSERT INTO hostel_allocations(room_id,student_id,session_id,bed_no,allotted_from,allotted_to,status,allocated_by) VALUES(?,?,?,?,?,?,?,?)",
                roomId, studentId, sessionId, bedNo, Date.valueOf(from), to == null ? null : Date.valueOf(to), status, actorId);
    }

    public int activeAllocationCount(long roomId, String bedNo, long sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM hostel_allocations WHERE room_id=? AND bed_no=? AND session_id=? AND status='ACTIVE'", Integer.class, roomId, bedNo, sessionId);
    }

    public long createTicket(OperationsDtos.CreateTicketRequest request, long actorId) {
        return insert("INSERT INTO support_tickets(title,description,category,priority,status,raised_by,assigned_to,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                request.title(), request.description(), request.category(), request.priority(), request.status(), actorId,
                request.assignee() == null ? null : Long.parseLong(request.assignee()), LocalDate.now(), LocalDate.now());
    }

    public List<OperationsDtos.TicketResponse> tickets(SecurityPrincipal actor) {
        String scope = actor.roles().contains("SUPPORT_AGENT") ? " WHERE assigned_to = ?" : "";
        Object[] args = actor.roles().contains("SUPPORT_AGENT") ? new Object[]{actor.userId()} : new Object[]{};
        return jdbc.query("SELECT id,title,description,category,priority,status,raised_by,assigned_to,created_at,updated_at FROM support_tickets" + scope + " ORDER BY updated_at DESC,id DESC", (r, row) ->
                new OperationsDtos.TicketResponse(r.getLong("id"), r.getString("title"), r.getString("description"), r.getString("category"),
                        r.getString("priority"), r.getString("status"), r.getLong("raised_by"),
                        r.getObject("assigned_to") == null ? null : r.getLong("assigned_to"),
                        r.getDate("created_at").toLocalDate(), r.getDate("updated_at").toLocalDate()), args);
    }

    public OperationsDtos.TicketResponse ticket(long id) {
        return jdbc.queryForObject("SELECT id,title,description,category,priority,status,raised_by,assigned_to,created_at,updated_at FROM support_tickets WHERE id=?", (r, row) ->
                new OperationsDtos.TicketResponse(r.getLong("id"), r.getString("title"), r.getString("description"), r.getString("category"),
                        r.getString("priority"), r.getString("status"), r.getLong("raised_by"),
                        r.getObject("assigned_to") == null ? null : r.getLong("assigned_to"),
                        r.getDate("created_at").toLocalDate(), r.getDate("updated_at").toLocalDate()), id);
    }
}
