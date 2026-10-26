package com.erp.portal.operations;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service = service; }

    @GetMapping("/inventory/items") @PreAuthorize("hasAuthority('INVENTORY:READ')") public List<OperationsDtos.ItemResponse> items(@AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.items(); }
    @PostMapping("/inventory/items") @PreAuthorize("hasAuthority('INVENTORY:MANAGE')") public OperationsDtos.ItemResponse createItem(@Valid @RequestBody OperationsDtos.CreateItemRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.createItem(request, actor); }
    @PostMapping("/inventory/movements") @PreAuthorize("hasAuthority('INVENTORY:MANAGE')") public OperationsDtos.StockMovementResponse movement(@RequestBody OperationsDtos.StockMovementRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.stockMovement(1L, request, actor); }
    @GetMapping("/inventory/categories") @PreAuthorize("hasAuthority('INVENTORY:READ')") public List<OperationsDtos.CategoryResponse> categories() { return service.categories(); }
    @PostMapping("/inventory/categories") @PreAuthorize("hasAuthority('INVENTORY:MANAGE')") public OperationsDtos.CategoryResponse createCategory(@RequestBody String name, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.createCategory(name, actor); }
    @GetMapping("/inventory/suppliers") @PreAuthorize("hasAuthority('INVENTORY:READ')") public List<OperationsDtos.SupplierResponse> suppliers() { return service.suppliers(); }
    @PostMapping("/inventory/suppliers") @PreAuthorize("hasAuthority('INVENTORY:MANAGE')") public OperationsDtos.SupplierResponse createSupplier(@RequestBody String name, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.createSupplier(name, null, null, actor); }
    @GetMapping("/library/items") @PreAuthorize("hasAuthority('LIBRARY:READ')") public List<OperationsDtos.LibraryItemResponse> libraryItems() { return service.libraryItems(); }
    @PostMapping("/library/items") @PreAuthorize("hasAuthority('LIBRARY:MANAGE')") public OperationsDtos.LibraryItemResponse createLibraryItem(@Valid @RequestBody OperationsDtos.CreateLibraryItemRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.createLibraryItem(request, actor); }
    @PostMapping("/library/issues") @PreAuthorize("hasAuthority('LIBRARY:ISSUE')") public OperationsDtos.IssueResponse issue(@RequestBody OperationsDtos.IssueRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.issueLibrary(1L, request, actor); }
    @PostMapping("/library/issues/{id}/return") @PreAuthorize("hasAuthority('LIBRARY:RETURN')") public ResponseEntity<Void> returnItem(@PathVariable long id, @RequestBody OperationsDtos.ReturnRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { service.returnLibrary(id, request, actor); return ResponseEntity.status(HttpStatus.NO_CONTENT).build(); }
    @GetMapping("/library/issues") @PreAuthorize("hasAuthority('LIBRARY:READ')") public List<OperationsDtos.IssueResponse> issues() { return service.issues(); }
    @GetMapping("/hostel/rooms") @PreAuthorize("hasAuthority('HOSTEL:READ')") public List<OperationsDtos.RoomResponse> rooms() { return service.rooms(); }
    @PostMapping("/hostel/rooms") @PreAuthorize("hasAuthority('HOSTEL:MANAGE')") public OperationsDtos.RoomResponse createRoom(@Valid @RequestBody OperationsDtos.CreateRoomRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.createRoom(request, actor); }
    @PostMapping("/hostel/allocations") @PreAuthorize("hasAuthority('HOSTEL:MANAGE')") public OperationsDtos.AllocationResponse allocate(@RequestBody OperationsDtos.AllocationRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.allocateHostel(1L, request, actor); }
    @GetMapping("/support/tickets") @PreAuthorize("hasAuthority('SUPPORT:READ')") public List<OperationsDtos.TicketResponse> tickets(@AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.tickets(actor); }
    @PostMapping("/support/tickets") @PreAuthorize("hasAuthority('SUPPORT:MANAGE')") public OperationsDtos.TicketResponse createTicket(@Valid @RequestBody OperationsDtos.CreateTicketRequest request, @AuthenticationPrincipal com.erp.portal.security.SecurityPrincipal actor) { return service.createTicket(request, actor); }
}
