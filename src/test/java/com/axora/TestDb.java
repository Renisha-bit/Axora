package com.axora;

import com.axora.dao.sqlite.DatabaseManager;

import java.sql.Connection;
import java.sql.Statement;

/** Test helper: fresh in-memory database built by the REAL migrations. */
final class TestDb {
    private TestDb() {}

    /** Real schema with the real seeded categories and the default "Cash" account. */
    static DatabaseManager fresh() {
        DatabaseManager db = DatabaseManager.getInstance();
        db.initializeInMemory();
        return db;
    }

    /**
     * Real schema, but the seeded categories are replaced by two known rows (id 1 = Food/EXPENSE,
     * id 2 = Salary/INCOME) so older tests that hard-code category ids keep working.
     */
    static DatabaseManager withLegacyCategories() throws Exception {
        DatabaseManager db = fresh();
        Connection c = db.getConnection();
        try (Statement st = c.createStatement()) {
            st.execute("DELETE FROM categories");
            st.execute("DELETE FROM sqlite_sequence WHERE name='categories'");
            st.execute("INSERT INTO categories(name,type,is_default) VALUES('Food','EXPENSE',1)");
            st.execute("INSERT INTO categories(name,type,is_default) VALUES('Salary','INCOME',1)");
        }
        return db;
    }

    static long categoryId(String name) throws Exception {
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement();
             var rs = st.executeQuery("SELECT id FROM categories WHERE name='" + name + "'")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }
}
