package com.erp.portal.common.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvSafetyTest {
    @Test
    void neutralizesSpreadsheetFormulasIncludingAfterWhitespace() {
        assertEquals("'=1+1", CsvSafety.cell("=1+1"));
        assertEquals("'  +SUM(A1:A2)", CsvSafety.cell("  +SUM(A1:A2)"));
        assertEquals("'\t@SUM(A1)", CsvSafety.cell("\t@SUM(A1)"));
        assertEquals("'-2", CsvSafety.cell("-2"));
    }

    @Test
    void preservesCsvEscapingForSafeTextAndNonTextValues() {
        assertEquals("\"Safe, value\"", CsvSafety.cell("Safe, value"));
        assertEquals("\"Say \"\"hello\"\"\"", CsvSafety.cell("Say \"hello\""));
        assertEquals("-2", CsvSafety.cell(-2));
        assertEquals("", CsvSafety.cell(null));
    }
}
