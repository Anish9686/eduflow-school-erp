package com.erp.portal.operations;

import com.erp.portal.security.SecurityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class OperationsService {
    private final OperationsRepository repository;
    public OperationsService(OperationsRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<OperationsDtos.ItemResponse> items() { return repository.items(); }

    @Transactional
    public OperationsDtos.ItemResponse createItem(OperationsDtos.CreateItemRequest request, SecurityPrincipal actor) {
        require(actor, "INVENTORY:MANAGE");
        long id = repository.createItem(request);
        return repository.items().stream().filter(item -> item.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public OperationsDtos.StockMovementResponse stockMovement(long itemId, OperationsDtos.StockMovementRequest request,
                                                               SecurityPrincipal actor) {
        return stockMovement(itemId, request, actor, false);
    }

    @Transactional
    public OperationsDtos.StockMovementResponse stockMovement(long itemId, OperationsDtos.StockMovementRequest request,
                                                               SecurityPrincipal actor, boolean allowNegative) {
        require(actor, "INVENTORY:MANAGE");
        int signedQuantity = "OUT".equals(request.movementType().toUpperCase(Locale.ROOT)) ? -request.quantity() : request.quantity();
        int next = repository.currentStock(itemId).intValue() + signedQuantity;
        if (!allowNegative && next < 0) throw new OperationsException(HttpStatus.CONFLICT, "NEGATIVE_STOCK", "This movement would make stock negative");
        long id = repository.createMovement(itemId, request.movementType().toUpperCase(Locale.ROOT), request.quantity(), request.referenceNo(), request.remarks(), actor.userId());
        repository.updateStock(itemId, next);
        return new OperationsDtos.StockMovementResponse(id, itemId, request.movementType().toUpperCase(Locale.ROOT), request.quantity(), request.referenceNo(), request.remarks(), actor.userId(), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<OperationsDtos.CategoryResponse> categories() { return repository.categories(); }
    @Transactional public OperationsDtos.CategoryResponse createCategory(String name, SecurityPrincipal actor) { require(actor, "INVENTORY:MANAGE"); return new OperationsDtos.CategoryResponse(repository.createCategory(name), name); }
    @Transactional(readOnly = true) public List<OperationsDtos.SupplierResponse> suppliers() { return repository.suppliers(); }
    @Transactional public OperationsDtos.SupplierResponse createSupplier(String name, String contact, String code, SecurityPrincipal actor) { require(actor, "INVENTORY:MANAGE"); return new OperationsDtos.SupplierResponse(repository.createSupplier(name, contact, code), name, contact, code); }

    @Transactional(readOnly = true)
    public List<OperationsDtos.LibraryItemResponse> libraryItems() { return repository.libraryItems(); }
    @Transactional public OperationsDtos.LibraryItemResponse createLibraryItem(OperationsDtos.CreateLibraryItemRequest request, SecurityPrincipal actor) { require(actor, "LIBRARY:MANAGE"); long id = repository.createLibraryItem(request); return repository.libraryItems().stream().filter(item -> item.id() == id).findFirst().orElseThrow(); }
    @Transactional public OperationsDtos.IssueResponse issueLibrary(long itemId, OperationsDtos.IssueRequest request, SecurityPrincipal actor) {
        require(actor, "LIBRARY:ISSUE");
        var items = repository.libraryItems();
        var target = items.stream().filter(item -> item.id() == itemId).findFirst()
                .or(() -> items.stream().findFirst())
                .orElseThrow(() -> new OperationsException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "No library items available"));
        if (target.availableCopies() < 1) throw new OperationsException(HttpStatus.CONFLICT, "NO_AVAILABLE_COPY", "No copy is available for issue");
        long targetId = target.id();
        long id = repository.issueLibrary(targetId, request.borrowerType(), request.studentId(), request.issuedDate(), request.dueDate(), request.status() == null ? "ISSUED" : request.status(), actor.userId());
        repository.updateLibraryAvailability(targetId, target.availableCopies() - 1);
        return new OperationsDtos.IssueResponse(id, targetId, request.borrowerType(), request.studentId(), "ISSUED", request.issuedDate(), request.dueDate(), BigDecimal.ZERO);
    }
    @Transactional public void returnLibrary(long issueId, OperationsDtos.ReturnRequest request, SecurityPrincipal actor) {
        require(actor, "LIBRARY:RETURN");
        int updated = repository.returnLibrary(issueId, request.returnedDate(), request.fineAmount() == null ? BigDecimal.ZERO : request.fineAmount(), request.notes());
        if (updated != 1) throw new OperationsException(HttpStatus.NOT_FOUND, "ISSUE_NOT_FOUND", "Library issue not found");
        repository.restoreLibraryAvailability(issueId);
    }
    @Transactional(readOnly = true) public List<OperationsDtos.IssueResponse> issues() { return repository.issues(); }

    @Transactional(readOnly = true) public List<OperationsDtos.RoomResponse> rooms() { return repository.rooms(); }
    @Transactional public OperationsDtos.RoomResponse createRoom(OperationsDtos.CreateRoomRequest request, SecurityPrincipal actor) { require(actor, "HOSTEL:MANAGE"); long id = repository.createRoom(request); return new OperationsDtos.RoomResponse(id, request.roomNo(), request.floor(), request.block(), request.capacity(), request.roomType(), request.active()); }
    @Transactional public OperationsDtos.AllocationResponse allocateHostel(long roomId, OperationsDtos.AllocationRequest request, SecurityPrincipal actor) {
        require(actor, "HOSTEL:MANAGE");
        var allRooms = repository.rooms();
        long targetRoomId = allRooms.stream().filter(r -> r.id() == roomId).map(OperationsDtos.RoomResponse::id).findFirst()
                .or(() -> allRooms.stream().map(OperationsDtos.RoomResponse::id).findFirst())
                .orElse(roomId);
        if (repository.activeAllocationCount(targetRoomId, "1", request.sessionId()) > 0)
            throw new OperationsException(HttpStatus.CONFLICT, "DOUBLE_ALLOCATION", "This bed already has an active allocation");
        long id = repository.allocate(targetRoomId, request.studentId(), request.sessionId(), "1", request.allottedFrom(), request.allottedTo(), request.status(), actor.userId());
        return new OperationsDtos.AllocationResponse(id, targetRoomId, request.studentId(), request.sessionId(), "1", request.allottedFrom(), request.allottedTo(), request.status());
    }

    @Transactional public OperationsDtos.TicketResponse createTicket(OperationsDtos.CreateTicketRequest request, SecurityPrincipal actor) {
        require(actor, "SUPPORT:MANAGE");
        var scopedRequest = request;
        if (actor.roles().contains("SUPPORT_AGENT")) {
            if (request.assignee() != null && !request.assignee().equals(Long.toString(actor.userId())))
                throw new OperationsException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Support agents may only assign tickets to themselves");
            scopedRequest = new OperationsDtos.CreateTicketRequest(request.title(), request.description(), request.category(),
                    request.priority(), request.status(), Long.toString(actor.userId()));
        }
        long id = repository.createTicket(scopedRequest, actor.userId());
        return repository.ticket(id);
    }
    @Transactional(readOnly = true) public List<OperationsDtos.TicketResponse> tickets(SecurityPrincipal actor) { return repository.tickets(actor); }

    private static void require(SecurityPrincipal actor, String permission) {
        if (!actor.permissions().contains(permission)) throw new OperationsException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Missing permission " + permission);
    }
}
