package org.example;

import com.fasterxml.jackson.core.JsonGenerator;
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

                        boolean deleteSuccess = deleteRowFromDatabase(tableFolder, keyValue);
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

                case "SEARCH_ROWS":
                    String searchTable = (String) in.readObject();  // Имя таблицы
                    String searchColumn = (String) in.readObject(); // Имя столбца для поиска
                    String searchValue = (String) in.readObject();  // Значение для поиска
                    List<List<Object>> searchResults = searchInDatabase(searchTable, searchColumn, searchValue);
                    out.writeObject(searchResults);
                    break;

                case "GET_PRIMARY_KEY_VALUES":
                    String keyColumn = (String) in.readObject();
                    tableName = (String) in.readObject();
                    List<Object> primaryKeyValues = getPrimaryKeyValues(tableName, keyColumn);
                    out.writeObject(primaryKeyValues);
                    break;

                case "EXECUTE_SQL":
                    List<String> sqlCommands = (List<String>) in.readObject(); // Получаем список SQL-команд
                    StringBuilder resultMessage = new StringBuilder();
                    success = executeSQLQueries(sqlCommands, resultMessage);

                    out.writeBoolean(success);
                    out.writeObject(resultMessage.toString());
                    out.flush();
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

    private List<List<Object>> searchInDatabase(String tableName, String columnName, String searchValue) throws SQLException {
        String query;
        PreparedStatement preparedStatement;

        if (isInteger(searchValue)) {
            // Поиск по числовому значению
            query = "SELECT * FROM " + tableName + " WHERE " + columnName + " = ?";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setInt(1, Integer.parseInt(searchValue));
        } else if ("true".equalsIgnoreCase(searchValue) || "false".equalsIgnoreCase(searchValue)) {
            // Поиск по значению boolean
            query = "SELECT * FROM " + tableName + " WHERE " + columnName + " = ?";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setBoolean(1, Boolean.parseBoolean(searchValue));
        } else if (isTimestamp(searchValue)) {
            // Поиск по значению даты и времени
            query = "SELECT * FROM " + tableName + " WHERE " + columnName + " = ?";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setTimestamp(1, Timestamp.valueOf(searchValue));
        } else if (isArrayColumn(tableName, columnName)) {
            // Поиск в массиве, если поле типа array
            searchValue = "{\"" + searchValue.replaceAll(", ", "\", \"") + "\"}"; // преобразование в формат массива
            query = "SELECT * FROM " + tableName + " WHERE " + columnName + " && ?";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setObject(1, searchValue, Types.OTHER);
        } else {
            // Поиск по строковому значению
            query = "SELECT * FROM " + tableName + " WHERE " + columnName + " LIKE ?";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, "%" + searchValue + "%");
        }

        ResultSet result = preparedStatement.executeQuery();
        ResultSetMetaData metaData = result.getMetaData();
        int colCount = metaData.getColumnCount();
        List<List<Object>> results = new ArrayList<>();

        // Заголовки
        List<Object> headers = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            headers.add(metaData.getColumnName(i));
        }
        results.add(headers);

        // Данные строк
        while (result.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                Object value = result.getObject(i);
                if (value instanceof Array) {
                    Object[] array = (Object[]) ((Array) value).getArray();
                    row.add(Arrays.asList(array));
                } else {
                    row.add(value);
                }
            }
            results.add(row);
        }
        return results;
    }

    private List<Object> getPrimaryKeyValues(String tableName, String keyColumn) throws SQLException {
        List<Object> primaryKeyValues = new ArrayList<>();
        String query = "SELECT " + keyColumn + " FROM " + tableName;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                primaryKeyValues.add(rs.getObject(keyColumn));
            }
        }
        return primaryKeyValues;
    }

    private boolean executeSQLQueries(List<String> sqlCommands, StringBuilder resultMessage) {
        boolean allSuccess = true;
        int successfulCount = 0;
        int totalCommands = 0;

        for (String sql : sqlCommands) {
            if (sql.trim().isEmpty()) {
                continue; // Пропускаем пустые строки
            }
            totalCommands++;

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql.trim() + ";"); // Выполнение SQL-команды
                successfulCount++;
            } catch (SQLException e) {
                e.printStackTrace();
                resultMessage.append("Error executing command: ").append(sql).append("\n")
                        .append("Error: ").append(e.getMessage()).append("\n");
                allSuccess = false;
            }
        }

        resultMessage.append("Executed successfully: ")
                .append(successfulCount).append("/").append(totalCommands).append(" commands.\n");

        return allSuccess;
    }

    // Дополнительный метод для проверки типа колонки как массива
    private boolean isArrayColumn(String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                String columnType = columns.getString("TYPE_NAME");
                return columnType.startsWith("_"); // Проверка на массив
            }
        }
        return false;
    }

    private boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isTimestamp(String value) {
        try {
            Timestamp.valueOf(value); // Проверяет формат YYYY-MM-DD HH:MM:SS.S
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
