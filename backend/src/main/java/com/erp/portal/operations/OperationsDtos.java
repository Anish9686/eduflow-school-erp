package com.erp.portal.operations;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OperationsDtos() {
    public record CreateItemRequest(@NotBlank @Size(max=200) String name,
                                    @NotBlank @Size(max=50) String code,
                                    @Size(max=100) String category,
                                    @NotBlank @Size(max=20) String unit,
                                    @Min(0) int currentStock,
                                    @Min(0) int minStockLevel,
                                    @Size(max=500) String description) {}
    public record ItemResponse(long id, String name, String code, String category, String unit,
                               int currentStock, int minStockLevel, String description) {}
    public record CategoryResponse(long id, String name) {}
    public record SupplierResponse(long id, String name, String contact, String code) {}
    public record StockMovementRequest(@NotBlank String movementType,
                                        @Positive int quantity,
                                        @Size(max=80) String referenceNo,
                                        @Size(max=500) String remarks) {}
    public record StockMovementResponse(long id, long itemId, String movementType, int quantity,
                                        String referenceNo, String remarks, long movedBy, LocalDate movedAt) {}
    public record CreateLibraryItemRequest(@Size(max=20) String isbn,
                                             @NotBlank @Size(max=255) String title,
                                             @Size(max=200) String author,
                                             @Size(max=100) String category,
                                             @Min(0) int totalCopies,
                                             @Min(0) int availableCopies,
                                             @Size(max=50) String rackLocation) {}
    public record LibraryItemResponse(long id, String isbn, String title, String author, String category,
                                      int totalCopies, int availableCopies, String rackLocation) {}
    public record IssueRequest(@NotBlank String borrowerType, @NotNull Long studentId,
                                LocalDate issuedDate, @NotNull LocalDate dueDate,
                                @Size(max=50) String status) {}
    public record IssueResponse(long id, long itemId, String borrowerType, Long studentId,
                                String status, LocalDate issuedDate, LocalDate dueDate,
                                BigDecimal fineAmount) {}
    public record ReturnRequest(LocalDate returnedDate, BigDecimal fineAmount, String notes) {}
    public record CreateRoomRequest(@NotBlank @Size(max=20) String roomNo,
                                    @Size(max=10) String floor,
                                    @Size(max=20) String block,
                                    @Min(1) int capacity,
                                    @NotBlank String roomType,
                                    boolean active) {}
    public record RoomResponse(long id, String roomNo, String floor, String block, int capacity,
                              String roomType, boolean active) {}
    public record AllocationRequest(@NotNull Long studentId, @NotNull Long sessionId,
                                    LocalDate allottedFrom, LocalDate allottedTo,
                                    @NotBlank String status) {}
    public record AllocationResponse(long id, long roomId, long studentId, long sessionId,
                                      String bedNo, LocalDate allottedFrom, LocalDate allottedTo,
                                      String status) {}
    public record CreateTicketRequest(@NotBlank @Size(max=255) String title,
                                     @NotBlank String description,
                                     @Size(max=100) String category,
                                     @NotBlank String priority,
                                     @Size(max=100) String status,
                                     @Size(max=100) String assignee) {}
    public record TicketResponse(long id, String title, String description, String category,
                                 String priority, String status, Long raisedBy, Long assignedTo,
                                 LocalDate createdAt, LocalDate updatedAt) {}
    public record TicketCommentRequest(@NotBlank String comment) {}
}
