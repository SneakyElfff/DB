package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseExporter {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/Travel+agency?user=nina";

        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected to PostgreSQL database successfully");

            List<Map<String, Object>> schema = extractSchema(conn);

            List<String> tables = getTables(conn);
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            for (String table : tables) {
                data.put(table, fetchTableData(conn, table));
            }

            Map<String, Object> output = new HashMap<>();
            output.put("schema", schema);
            output.put("data", data);

            ObjectMapper mapper = new ObjectMapper();
            String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            System.out.println(jsonOutput);

            mapper.writeValue(new java.io.File("db_dump.json"), output);
            System.out.println("Database schema and data exported to db_dump.json");

            Map<String, Object> deserializedData = deserializeFromJson("db_dump.json", mapper);
            System.out.println("Deserialized data:");
            System.out.println(deserializedData);
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

    public static List<Map<String, Object>> fetchTableData(Connection conn, String tableName) throws SQLException {
        String query = "SELECT * FROM " + tableName;
        List<Map<String, Object>> tableData = new ArrayList<>();

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

                    row.put(metaData.getColumnName(i), value);
                }
                tableData.add(row);
            }
        }
        return tableData;
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

