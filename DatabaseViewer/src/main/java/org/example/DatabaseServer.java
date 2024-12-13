package org.example;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sleepycat.je.*;
import com.sleepycat.je.Cursor;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;

public class DatabaseServer extends Component {
    private Connection connection;

    public DatabaseServer() throws SQLException {
        // Подключение к базе данных PostgreSQL
        String url = "jdbc:postgresql://localhost:5432/Travel+agency?user=nina";
        connection = DriverManager.getConnection(url);
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Server started. Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected.");
                handleClientRequest(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClientRequest(Socket clientSocket) {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            String command = (String) in.readObject();

            File berkeleyDbFolder = new File("berkeley_db");

            switch (command) {
                case "GET_TABLES":
                    List<String> tableNames = getTablesFromDatabase(berkeleyDbFolder);
                    out.writeObject(tableNames);
                    break;

                case "GET_TABLE_DATA":
                    String tableName = (String) in.readObject();
                    String orderBy = (String) in.readObject();
                    boolean isAscending = in.readBoolean();

                    File tableFolder = new File(berkeleyDbFolder, tableName);
                    if (!tableFolder.exists() || !tableFolder.isDirectory()) {
                        out.writeObject("TABLE_NOT_FOUND");
                        break;
                    }

                    List<List<Object>> tableData = getTableDataFromDatabase(tableFolder, orderBy, isAscending);
                    out.writeObject(tableData);
                    break;

                case "ADD_ROW":
                    tableName = (String) in.readObject();
                    Map<String, Object> rowData = (Map<String, Object>) in.readObject();

                    tableFolder = new File(berkeleyDbFolder, tableName);
                    if (!tableFolder.exists() || !tableFolder.isDirectory()) {
                        out.writeObject("TABLE_NOT_FOUND");
                        break;
                    }

                    boolean success = addRowToDatabase(tableFolder, rowData);
                    out.writeObject(success ? "SUCCESS" : "FAILURE");
                    break;

                case "DELETE_ROW":
                    Object keyValue;
                    try {
                        tableName = (String) in.readObject();
                        keyValue = in.readObject();

                        tableFolder = new File(berkeleyDbFolder, tableName);
                        if (!tableFolder.exists() || !tableFolder.isDirectory()) {
                            out.writeObject("TABLE_NOT_FOUND");
                            break;
                        }

                        boolean deleteSuccess = deleteRowWithCascade(tableFolder, keyValue);
                        out.writeObject(deleteSuccess ? "SUCCESS" : "FAILURE");
                    } catch (Exception e) {
                        e.printStackTrace();
                        out.writeObject("ERROR");
                    }
                    break;

                case "UPDATE_ROW":
                    tableName = (String) in.readObject();
                    String columnName = (String) in.readObject();
                    Object newValue = in.readObject();
                    keyValue = in.readObject();

                    tableFolder = new File(berkeleyDbFolder, tableName);
                    if (!tableFolder.exists() || !tableFolder.isDirectory()) {
                        out.writeObject("TABLE_NOT_FOUND");
                        break;
                    }

                    boolean updateSuccess = updateRowInDatabase(tableFolder, columnName, newValue, keyValue);
                    out.writeObject(updateSuccess ? "SUCCESS" : "FAILURE");
                    break;

                case "GET_PRIMARY_KEY_VALUES":
                    String keyColumn = (String) in.readObject();
                    tableName = (String) in.readObject();

                    tableFolder = new File(berkeleyDbFolder, tableName);
                    if (!tableFolder.exists() || !tableFolder.isDirectory()) {
                        out.writeObject("TABLE_NOT_FOUND");
                        break;
                    }

                    List<Object> primaryKeyValues = getPrimaryKeyValues(tableFolder);
                    out.writeObject(primaryKeyValues);
                    break;

                // Другие команды, если необходимо
            }

            out.flush();
        } catch (IOException | ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getTablesFromDatabase(File berkeleyDbFolder) {
        List<String> tableNames = new ArrayList<>();
        if (berkeleyDbFolder.exists() && berkeleyDbFolder.isDirectory()) {
            File[] tables = berkeleyDbFolder.listFiles();
            if (tables != null) {
                for (File table : tables) {
                    if (table.isDirectory()) {
                        tableNames.add(table.getName());
                    }
                }
            }
        }
        return tableNames;
    }

    public static String singularize(String tableName) {
        if (tableName.endsWith("s")) {
            return tableName.substring(0, tableName.length() - 1);
        }
        return tableName;
    }

    private List<List<Object>> getTableDataFromDatabase(File tableFolder, String orderByColumn, boolean isAscending) throws Exception {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(false);
        Environment dbEnvironment = new Environment(tableFolder, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(false);
        Database berkeleyDb = dbEnvironment.openDatabase(null, tableFolder.getName(), dbConfig);

        List<List<Object>> tableData = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        String primaryKeyField = singularize(tableFolder.getName()) + "_id";

        try (Cursor cursor = berkeleyDb.openCursor(null, null)) {
            DatabaseEntry keyEntry = new DatabaseEntry();
            DatabaseEntry valueEntry = new DatabaseEntry();

            boolean headersAdded = false;

            while (cursor.getNext(keyEntry, valueEntry, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                String key = new String(keyEntry.getData());
                String value = new String(valueEntry.getData());

                Map<String, Object> row = mapper.readValue(value, Map.class);

                if (!headersAdded) {
                    List<Object> headers = new ArrayList<>(row.keySet());
                    if (!headers.contains(primaryKeyField)) {
                        headers.add(0, primaryKeyField);
                    }
                    tableData.add(headers);
                    headersAdded = true;
                }

                List<Object> rowData = new ArrayList<>(row.values());
                rowData.add(0, key);
                tableData.add(rowData);
            }
        }

        berkeleyDb.close();
        dbEnvironment.close();

        if (orderByColumn != null) {
            int columnIndex = tableData.get(0).indexOf(orderByColumn);
            if (columnIndex >= 0) {
                tableData.subList(1, tableData.size()).sort((row1, row2) -> {
                    Comparable<Object> val1 = (Comparable<Object>) row1.get(columnIndex);
                    Comparable<Object> val2 = (Comparable<Object>) row2.get(columnIndex);
                    return isAscending ? val1.compareTo(val2) : val2.compareTo(val1);
                });
            }
        }

        return tableData;
    }

    private boolean addRowToDatabase(File tableFolder, Map<String, Object> rowData) {
        try {
            String primaryKey = generatePrimaryKey(tableFolder.getName());

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            SimpleModule dateModule = new SimpleModule();
            dateModule.addSerializer(java.util.Date.class, new JsonSerializer<Date>() {
                @Override
                public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
                    gen.writeString(formattedDate);
                }
            });
            mapper.registerModule(dateModule);

            String serializedData = mapper.writeValueAsString(rowData);

            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(false);
            Environment dbEnvironment = new Environment(tableFolder, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(false);
            Database berkeleyDb = dbEnvironment.openDatabase(null, tableFolder.getName(), dbConfig);

            DatabaseEntry keyEntry = new DatabaseEntry(primaryKey.getBytes(StandardCharsets.UTF_8));
            DatabaseEntry valueEntry = new DatabaseEntry(serializedData.getBytes(StandardCharsets.UTF_8));
            berkeleyDb.put(null, keyEntry, valueEntry);

            berkeleyDb.close();
            dbEnvironment.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean deleteRowFromDatabase(File tableFolder, Object keyValue) {
        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(false);
            Environment dbEnvironment = new Environment(tableFolder, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(false);
            Database berkeleyDb = dbEnvironment.openDatabase(null, tableFolder.getName(), dbConfig);

            DatabaseEntry keyEntry = new DatabaseEntry(keyValue.toString().getBytes(StandardCharsets.UTF_8));

            OperationStatus status = berkeleyDb.delete(null, keyEntry);

            berkeleyDb.close();
            dbEnvironment.close();

            return status == OperationStatus.SUCCESS;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Map<String, String> getForeignKeyReferences(String tableName) {
        Map<String, String> references = new HashMap<>();
        if ("clients".equals(tableName)) {
            references.put("tour_bookings", "client_id");
        }
        if ("accommodations".equals(tableName)) {
            references.put("tours", "accommodation_id");
        }
        if ("excursions".equals(tableName)) {
            references.put("tours", "excursion_id");
        }
        if ("tours".equals(tableName)) {
            references.put("tour_bookings", "tour_id");
        }
        return references;
    }

    private boolean deleteRowWithCascade(File tableFolder, Object keyValue) {
        try {
            boolean success = deleteRowFromDatabase(tableFolder, keyValue);
            if (!success) return false;

            String tableName = tableFolder.getName();
            Map<String, String> foreignKeyReferences = getForeignKeyReferences(tableName);

            if (foreignKeyReferences.isEmpty()) {
                System.out.println("No related tables found for table: " + tableName);
                return true;
            }

            for (Map.Entry<String, String> entry : foreignKeyReferences.entrySet()) {
                String relatedTableName = entry.getKey();
                String foreignKeyColumn = entry.getValue();

                File relatedTableFolder = new File("berkeley_db/" + relatedTableName);

                EnvironmentConfig envConfig = new EnvironmentConfig();
                envConfig.setAllowCreate(false);
                Environment dbEnvironment = new Environment(relatedTableFolder, envConfig);

                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setAllowCreate(false);
                Database relatedDb = dbEnvironment.openDatabase(null, relatedTableName, dbConfig);

                Cursor cursor = relatedDb.openCursor(null, null);
                DatabaseEntry keyEntry = new DatabaseEntry();
                DatabaseEntry valueEntry = new DatabaseEntry();

                while (cursor.getNext(keyEntry, valueEntry, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                    String jsonData = new String(valueEntry.getData(), StandardCharsets.UTF_8);

                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> rowData = mapper.readValue(jsonData, new TypeReference<>() {});

                    if (keyValue.toString().equals(rowData.get(foreignKeyColumn).toString())) {
                        relatedDb.delete(null, keyEntry);
                    }
                }

                cursor.close();
                relatedDb.close();
                dbEnvironment.close();
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean updateRowInDatabase(File tableFolder, String columnName, Object newValue, Object keyValue) {
        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(false);
            Environment dbEnvironment = new Environment(tableFolder, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(false);
            Database berkeleyDb = dbEnvironment.openDatabase(null, tableFolder.getName(), dbConfig);

            DatabaseEntry keyEntry = new DatabaseEntry(keyValue.toString().getBytes(StandardCharsets.UTF_8));
            DatabaseEntry valueEntry = new DatabaseEntry();

            if (berkeleyDb.get(null, keyEntry, valueEntry, LockMode.DEFAULT) != OperationStatus.SUCCESS) {
                System.err.println("Key not found: " + keyValue);
                berkeleyDb.close();
                dbEnvironment.close();
                return false;
            }

            String serializedData = new String(valueEntry.getData(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rowData = mapper.readValue(serializedData, Map.class);

            if (!rowData.containsKey(columnName)) {
                System.err.println("Column not found: " + columnName);
                berkeleyDb.close();
                dbEnvironment.close();
                return false;
            }

            if (newValue instanceof Date) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                newValue = dateFormat.format((Date) newValue);
            }

            rowData.put(columnName, newValue);

            String updatedSerializedData = mapper.writeValueAsString(rowData);
            valueEntry.setData(updatedSerializedData.getBytes(StandardCharsets.UTF_8));

            if (berkeleyDb.put(null, keyEntry, valueEntry) != OperationStatus.SUCCESS) {
                System.err.println("Failed to update key: " + keyValue);
                berkeleyDb.close();
                dbEnvironment.close();
                return false;
            }

            berkeleyDb.close();
            dbEnvironment.close();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<Object> getPrimaryKeyValues(File tableFolder) {
        List<Object> primaryKeyValues = new ArrayList<>();

        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(false);
            Environment dbEnvironment = new Environment(tableFolder, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(false);
            Database berkeleyDb = dbEnvironment.openDatabase(null, tableFolder.getName(), dbConfig);

            CursorConfig cursorConfig = new CursorConfig();
            Cursor cursor = berkeleyDb.openCursor(null, cursorConfig);

            DatabaseEntry keyEntry = new DatabaseEntry();
            DatabaseEntry valueEntry = new DatabaseEntry();

            while (cursor.getNext(keyEntry, valueEntry, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                String key = new String(keyEntry.getData(), StandardCharsets.UTF_8);
                primaryKeyValues.add(key);
            }

            cursor.close();
            berkeleyDb.close();
            dbEnvironment.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return primaryKeyValues;
    }

    private synchronized String generatePrimaryKey(String tableName) throws IOException {
        File keyFile = new File("primary_keys.txt");
        Map<String, Long> keyMap = new HashMap<>();

        if (keyFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(keyFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    keyMap.put(parts[0], Long.parseLong(parts[1].trim()));
                }
            }
        }

        String keyName = singularize(tableName) + "_id";
        long nextId = keyMap.getOrDefault(keyName, 0L) + 1;
        keyMap.put(keyName, nextId);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(keyFile))) {
            for (Map.Entry<String, Long> entry : keyMap.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        }

        return String.valueOf(nextId);
    }

    public static void main(String[] args) {
        try {
            DatabaseServer server = new DatabaseServer();
            server.startServer();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
