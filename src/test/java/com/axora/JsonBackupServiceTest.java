package com.axora;

import com.axora.dao.sqlite.DatabaseManager;
import com.axora.export.JsonBackupService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonBackupService: round-trip, validation, and rollback on failure.
 */
class JsonBackupServiceTest {

    private JsonBackupService svc;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        TestDb.withLegacyCategories();
        conn = DatabaseManager.getInstance().getConnection();
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO transactions(type,amount_cents,category_id,tx_date,description,merchant) VALUES('EXPENSE',5000,1,'2026-10-01','Lunch','Cafe')");
            st.execute("INSERT INTO transactions(type,amount_cents,category_id,tx_date,description,merchant) VALUES('INCOME',100000,2,'2026-10-05','October Pay','Employer')");
        }
        svc = new JsonBackupService();
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    // ----------------------------------------------------------------
    // Round-trip: backup then restore
    // ----------------------------------------------------------------

    @Test
    void roundTripPreservesAllData() throws Exception {
        File f = File.createTempFile("axora-backup-", ".json");
        f.deleteOnExit();

        svc.backup(f);
        assertTrue(f.length() > 0, "Backup file should not be empty");

        // Clear data then restore
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM transactions");
            st.execute("DELETE FROM categories");
        }

        svc.restore(f);

        // Verify categories restored
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM categories")) {
            assertEquals(2, rs.getInt(1));
        }

        // Verify transactions restored
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
            assertEquals(2, rs.getInt(1));
        }
    }

    // ----------------------------------------------------------------
    // Invalid backup files
    // ----------------------------------------------------------------

    @Test
    void invalidJsonThrowsException() throws Exception {
        File f = File.createTempFile("axora-bad-", ".json");
        f.deleteOnExit();
        java.nio.file.Files.writeString(f.toPath(), "{ this is not valid json }");
        assertThrows(Exception.class, () -> svc.restore(f));
    }

    @Test
    void wrongVersionThrowsIllegalArgument() throws Exception {
        File f = File.createTempFile("axora-ver-", ".json");
        f.deleteOnExit();
        java.nio.file.Files.writeString(f.toPath(),
            """
            {"version":99,"categories":[{"id":1,"name":"X","type":"EXPENSE"}],
             "transactions":[]}
            """);
        assertThrows(IllegalArgumentException.class, () -> svc.restore(f));
    }

    @Test
    void unknownCategoryRefThrowsIllegalArgument() throws Exception {
        File f = File.createTempFile("axora-ref-", ".json");
        f.deleteOnExit();
        java.nio.file.Files.writeString(f.toPath(),
            """
            {"version":1,
             "categories":[{"id":1,"name":"Food","type":"EXPENSE"}],
             "transactions":[{"id":1,"type":"EXPENSE","amountCents":500,"categoryId":999,
                              "txDate":"2026-10-01","description":"x","merchant":""}]}
            """);
        assertThrows(IllegalArgumentException.class, () -> svc.restore(f));
    }

    // ----------------------------------------------------------------
    // Rollback on failure
    // ----------------------------------------------------------------

    @Test
    void failedRestoreDoesNotChangeExistingData() throws Exception {
        // Count rows before
        int txBefore;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
            txBefore = rs.getInt(1);
        }

        // Try restoring an invalid backup (wrong version) — should throw and roll back
        File f = File.createTempFile("axora-fail-", ".json");
        f.deleteOnExit();
        java.nio.file.Files.writeString(f.toPath(),
            """
            {"version":99,"categories":[],"transactions":[]}
            """);
        assertThrows(Exception.class, () -> svc.restore(f));

        // Data must be unchanged
        int txAfter;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
            txAfter = rs.getInt(1);
        }
        assertEquals(txBefore, txAfter, "Rollback should leave transaction count unchanged");
    }
}
