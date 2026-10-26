package com.erp.portal.operations;

import com.erp.portal.security.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationsServiceUnitTest {

    @Mock OperationsRepository repository;

    private OperationsService service;

    private final SecurityPrincipal admin = new SecurityPrincipal(
            1L, "admin", "Admin", List.of("ADMIN"),
            Set.of("INVENTORY:MANAGE", "LIBRARY:MANAGE", "LIBRARY:ISSUE", "LIBRARY:RETURN", "HOSTEL:MANAGE"), "hash");

    private final SecurityPrincipal unauthorized = new SecurityPrincipal(
            2L, "user", "User", List.of("STUDENT"),
            Set.of(), "hash");

    @BeforeEach
    void setUp() {
        service = new OperationsService(repository);
    }

    @Test
    void stockMovementRejectsNegativeResult() {
        when(repository.currentStock(5L)).thenReturn(10L);

        OperationsDtos.StockMovementRequest req = new OperationsDtos.StockMovementRequest(
                "OUT", 15, "REF-001", "Withdrawal");

        assertThatThrownBy(() -> service.stockMovement(5L, req, admin))
                .isInstanceOf(OperationsException.class)
                .matches(e -> ((OperationsException) e).status().value() == 409)
                .hasMessageContaining("make stock negative");
    }

    @Test
    void stockMovementRequiresPermission() {
        OperationsDtos.StockMovementRequest req = new OperationsDtos.StockMovementRequest(
                "IN", 10, "REF-002", "Restock");

        assertThatThrownBy(() -> service.stockMovement(5L, req, unauthorized))
                .isInstanceOf(OperationsException.class)
                .matches(e -> ((OperationsException) e).status().value() == 403);
    }

    @Test
    void issueLibraryRejectsWhenNoCopiesAvailable() {
        OperationsDtos.LibraryItemResponse book = new OperationsDtos.LibraryItemResponse(
                1L, "Java 101", "Author", "ISBN123", "CAT", 5, 0, "Shelf 1");
        when(repository.libraryItems()).thenReturn(List.of(book));

        OperationsDtos.IssueRequest req = new OperationsDtos.IssueRequest(
                "STUDENT", 50L, LocalDate.now(), LocalDate.now().plusDays(14), "ISSUED");

        assertThatThrownBy(() -> service.issueLibrary(1L, req, admin))
                .isInstanceOf(OperationsException.class)
                .matches(e -> ((OperationsException) e).status().value() == 409)
                .hasMessageContaining("No copy is available for issue");
    }

    @Test
    void returnLibraryThrowsNotFoundWhenIssueNotFound() {
        when(repository.returnLibrary(eq(99L), any(), any(), any())).thenReturn(0);

        OperationsDtos.ReturnRequest req = new OperationsDtos.ReturnRequest(LocalDate.now(), BigDecimal.ZERO, "Returned");

        assertThatThrownBy(() -> service.returnLibrary(99L, req, admin))
                .isInstanceOf(OperationsException.class)
                .matches(e -> ((OperationsException) e).status().value() == 404);
    }
}
