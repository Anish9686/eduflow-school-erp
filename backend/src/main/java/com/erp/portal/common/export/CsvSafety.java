package com.erp.portal.common.export;

/** Escapes CSV cells and neutralizes spreadsheet formula prefixes in text values. */
public final class CsvSafety {
    private CsvSafety() { }

    public static String cell(Object raw) {
        if (raw == null) return "";
        String value = raw.toString();
        if (raw instanceof String && startsWithFormulaAfterIgnorablePrefix(value)) value = "'" + value;
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static boolean startsWithFormulaAfterIgnorablePrefix(String value) {
        int index = 0;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || Character.isISOControl(character) || character == '\uFEFF') {
                index++;
            } else {
                break;
            }
        }
        return index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0;
    }
}
