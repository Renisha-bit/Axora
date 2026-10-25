package com.axora.export;

import com.axora.model.Transaction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exports transactions to RFC 4180-compliant CSV with UTF-8 BOM
 * (BOM ensures Excel opens the file correctly in any locale).
 */
public class CsvExporter {

    private static final String[] HEADERS = {
        "Date", "Type", "Category", "Amount (₹)", "Description", "Merchant", "ID"
    };

    public void export(List<Transaction> transactions, File target) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
            // UTF-8 BOM
            pw.print('\uFEFF');
            // Header row
            pw.println(csvRow(HEADERS));
            // Data rows
            for (Transaction tx : transactions) {
                pw.println(csvRow(
                    tx.getTxDate().toString(),
                    tx.getType().name(),
                    tx.getCategoryName() == null ? "" : tx.getCategoryName(),
                    tx.getAmount().toPlainString(),
                    tx.getDescription() == null ? "" : tx.getDescription(),
                    tx.getMerchant()    == null ? "" : tx.getMerchant(),
                    String.valueOf(tx.getId())
                ));
            }
        }
    }

    /** Writes arbitrary rows (UTF-8 BOM, RFC 4180 escaping). A zero-length row produces a blank line. */
    public void exportRows(List<String[]> rows, File target) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
            pw.print('\uFEFF');
            for (String[] row : rows) pw.println(row.length == 0 ? "" : csvRow(row));
        }
    }

    private String csvRow(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    /** RFC 4180: quote fields containing comma, quote, or newline. */
    private String escape(String field) {
        if (field == null) return "\"\"";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}
