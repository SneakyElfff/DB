package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sleepycat.je.*;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DatabaseExporter {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/Travel+agency?user=nina";

        File berkeleyDbFolder = new File("berkeley_db");
        if (!berkeleyDbFolder.exists()) {
            berkeleyDbFolder.mkdir();
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected to PostgreSQL database successfully");

//            List<Map<String, Object>> schema = extractSchema(conn);

            List<String> tables = getTables(conn);

            ObjectMapper mapper = new ObjectMapper();

            for (String table : tables) {
                System.out.println("Processing table: " + table);

                String primaryKey = getPrimaryKey(conn, table);
                if (primaryKey == null) {
                    System.out.println("Skipping table " + table + " (no primary key found)");
                    continue;
                }

                List<Map<String, Object>> tableData = fetchTableData(conn, table);

                File dbFolder = new File(berkeleyDbFolder, table);
                if (!dbFolder.exists()) {
                    dbFolder.mkdir();
                }

                EnvironmentConfig envConfig = new EnvironmentConfig();
                envConfig.setAllowCreate(true);
                Environment dbEnvironment = new Environment(dbFolder, envConfig);

                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setAllowCreate(true);
                Database berkeleyDb = dbEnvironment.openDatabase(null, table, dbConfig);

                saveToBerkeleyDB(berkeleyDb, tableData, primaryKey, mapper);

                berkeleyDb.close();
                dbEnvironment.close();
            }

            System.out.println("Data exported to Berkeley DB successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Map<String, Object>> extractSchema(Connection conn) throws SQLException {
        String query = """
            SELECT table_name, column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
            """;

        List<Map<String, Object>> schema = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> column = new HashMap<>();
                column.put("table_name", rs.getString("table_name"));
                column.put("column_name", rs.getString("column_name"));
                column.put("data_type", rs.getString("data_type"));
                column.put("is_nullable", rs.getString("is_nullable"));
                schema.add(column);
            }
        }
        return schema;
    }

    public static List<String> getTables(Connection conn) throws SQLException {
        String query = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            """;

        List<String> tables = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(query);
        ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    public static String getPrimaryKey(Connection conn, String tableName) throws SQLException {
        String query = """
        SELECT kcu.column_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
        WHERE tc.constraint_type = 'PRIMARY KEY'
          AND tc.table_name = ?
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("column_name");
                }
            }
        }
        return null;
    }

    public static List<Map<String, Object>> fetchTableData(Connection conn, String tableName) throws SQLException {
        String query = "SELECT * FROM " + tableName;
        List<Map<String, Object>> tableData = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);

                    // Convert PgArray to Java List
                    if (value instanceof java.sql.Array) {
                        value = Arrays.asList((Object[]) ((java.sql.Array) value).getArray());
                    }

                    // Convert Timestamp or Date to formatted String
                    if (value instanceof Timestamp) {
                        value = dateFormat.format((Timestamp) value);
                    } else if (value instanceof java.sql.Date) {
                        value = dateFormat.format((java.sql.Date) value);
                    }

                    row.put(metaData.getColumnName(i), value);
                }
                tableData.add(row);
            }
        }
        return tableData;
    }

    public static void saveToBerkeleyDB(Database db, List<Map<String, Object>> tableData, String primaryKey, ObjectMapper mapper) {
        try {
            for (Map<String, Object> row : tableData) {
                Object primaryKeyValue = row.get(primaryKey);
                if (primaryKeyValue == null) {
                    System.out.println("Skipping row without primary key value");
                    continue;
                }

                String key = primaryKeyValue.toString();
                row.remove(primaryKey);
                String jsonValue = mapper.writeValueAsString(row);

                DatabaseEntry keyEntry = new DatabaseEntry(key.getBytes());
                DatabaseEntry valueEntry = new DatabaseEntry(jsonValue.getBytes());

                db.put(null, keyEntry, valueEntry);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> deserializeFromJson(String filePath, ObjectMapper mapper) {
        try {
            return mapper.readValue(new File(filePath), Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

