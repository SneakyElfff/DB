package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
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

            switch (command) {
                case "GET_TABLES":
                    List<String> tableNames = getTablesFromDatabase();
                    out.writeObject(tableNames);
                    break;

                case "GET_TABLE_DATA":
                    String tableName = (String) in.readObject();
                    String orderBy = (String) in.readObject();
                    boolean isAscending = in.readBoolean();
                    List<List<Object>> tableData = getTableDataFromDatabase(tableName, orderBy, isAscending);
                    out.writeObject(tableData);
                    break;

                case "ADD_ROW":
                    tableName = (String) in.readObject();
                    List<Object> rowData = (List<Object>) in.readObject();
                    boolean success = addRowToDatabase(tableName, rowData);
                    out.writeObject(success ? "SUCCESS" : "FAILURE");
                    break;

                case "DELETE_ROW":
                    tableName = (String) in.readObject(); // Получаем имя таблицы
                    Object keyValue = in.readObject();    // Получаем значение первичного ключа
                    boolean deleteSuccess = deleteRowFromDatabase(tableName, keyValue);
                    out.writeObject(deleteSuccess ? "SUCCESS" : "FAILURE");
                    break;

                case "UPDATE_ROW":
                    tableName = (String) in.readObject(); // Получаем имя таблицы
                    String columnName = (String) in.readObject(); // Имя столбца для обновления
                    Object newValue = in.readObject(); // Новое значение
                    keyValue = in.readObject(); // Значение первичного ключа

                    boolean updateSuccess = updateRowInDatabase(tableName, columnName, newValue, keyValue);
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
        }
    }

    private List<String> getTablesFromDatabase() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet tables = metaData.getTables(null, null, null, new String[]{"TABLE"});

        while (tables.next()) {
            tableNames.add(tables.getString("TABLE_NAME"));
        }
        return tableNames;
    }

    private List<List<Object>> getTableDataFromDatabase(String tableName, String orderByColumn, boolean isAscending) throws SQLException {
        String query = "SELECT * FROM " + tableName;
        if (orderByColumn != null) {
            query += " ORDER BY " + orderByColumn;
            query += isAscending ? " ASC" : " DESC";
        }

        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query);
        ResultSetMetaData metaData = result.getMetaData();
        int colCount = metaData.getColumnCount();

        List<List<Object>> tableData = new ArrayList<>();

        List<Object> headers = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            headers.add(metaData.getColumnName(i));
        }
        tableData.add(headers);

        // Добавляем строки данных
        while (result.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                Object value = result.getObject(i);

                // Преобразуем массив в List<String>, если это PgArray
                if (value instanceof Array) {
                    Object[] array = (Object[]) ((Array) value).getArray();
                    row.add(Arrays.asList(array)); // Конвертируем массив в List
                } else {
                    row.add(value);
                }
            }
            tableData.add(row);
        }

        return tableData;
    }

    private boolean addRowToDatabase(String tableName, List<Object> rowData) {
        try {
            String columnQuery = "SELECT * FROM " + tableName + " LIMIT 1";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(columnQuery);
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            DatabaseMetaData dbMetaData = connection.getMetaData();
            ResultSet pkResultSet = dbMetaData.getPrimaryKeys(null, null, tableName);
            List<String> primaryKeys = new ArrayList<>();
            while (pkResultSet.next()) {
                primaryKeys.add(pkResultSet.getString("COLUMN_NAME"));
            }
            pkResultSet.close();

            StringBuilder query = new StringBuilder("INSERT INTO " + tableName + " (");
            List<Integer> nonPrimaryKeyIndexes = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                if (!primaryKeys.contains(columnName)) {
                    query.append(columnName).append(", ");
                    nonPrimaryKeyIndexes.add(i - 1);
                }
            }

            query.setLength(query.length() - 2);
            query.append(") VALUES (");
            for (int i = 0; i < nonPrimaryKeyIndexes.size(); i++) {
                query.append("?");
                if (i < nonPrimaryKeyIndexes.size() - 1) {
                    query.append(", ");
                }
            }
            query.append(")");

            PreparedStatement preparedStatement = connection.prepareStatement(query.toString(), Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < nonPrimaryKeyIndexes.size(); i++) {
                int columnIndex = nonPrimaryKeyIndexes.get(i);
                int columnType = metaData.getColumnType(columnIndex + 1);
                Object value = rowData.get(i);

                if (columnType == Types.INTEGER) {
                    preparedStatement.setInt(i + 1, Integer.parseInt(value.toString()));
                } else if (columnType == Types.DOUBLE) {
                    preparedStatement.setDouble(i + 1, Double.parseDouble(value.toString()));
                } else if (columnType == Types.BOOLEAN) {
                    preparedStatement.setBoolean(i + 1, Boolean.parseBoolean(value.toString()));
                } else if (columnType == Types.ARRAY) {
                    if (value instanceof String) {
                        String stringValue = (String) value;
                        stringValue = stringValue.replaceAll("[{}]", "");
                        String[] arrayValue = stringValue.split(",");
                        Array array = connection.createArrayOf("varchar", arrayValue);
                        preparedStatement.setArray(i + 1, array);
                    } else {
                        String[] arrayValue = ((List<String>) value).toArray(new String[0]);
                        Array array = connection.createArrayOf("varchar", arrayValue);
                        preparedStatement.setArray(i + 1, array);
                    }
                } else {
                    preparedStatement.setObject(i + 1, value);
                }
            }

            // Выполнение запроса для вставки строки
            preparedStatement.executeUpdate();

            // Получение сгенерированного ID для tour_id (если автоинкремент)
            ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                int tourId = generatedKeys.getInt(1); // Получаем tour_id
                // Предполагается, что excursion_id уже есть в rowData
                Object excursionId = rowData.get(rowData.size() - 1); // Последний элемент в rowData — это excursion_id

                // Добавляем строку в таблицу leisure
                String leisureQuery = "INSERT INTO leisure (tour_id, excursion_id) VALUES (?, ?)";
                try (PreparedStatement leisureStatement = connection.prepareStatement(leisureQuery)) {
                    leisureStatement.setInt(1, tourId);
                    leisureStatement.setObject(2, excursionId);
                    leisureStatement.executeUpdate();
                }
            }

            return true;
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (NumberFormatException e) {
            System.err.println("Invalid data format: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean deleteRowFromDatabase(String tableName, Object keyValue) {
        try {
            // Получаем имя столбца первичного ключа
            String keyColumn = "";
            DatabaseMetaData dbMetaData = connection.getMetaData();
            try (ResultSet rs = dbMetaData.getPrimaryKeys(null, null, tableName)) {
                if (rs.next()) {
                    keyColumn = rs.getString("COLUMN_NAME");
                }
            }

            if (keyColumn.isEmpty()) {
                throw new SQLException("Primary key column not found for table " + tableName);
            }

            // Подготовка параметризованного запроса
            String query = "DELETE FROM " + tableName + " WHERE " + keyColumn + " = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setObject(1, keyValue);
                return stmt.executeUpdate() > 0; // Возвращаем true, если строка была удалена
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean updateRowInDatabase(String tableName, String columnName, Object newValue, Object keyValue) {
        try {
            // Получение имени первичного ключа
            String keyColumn = "";
            DatabaseMetaData dbMetaData = connection.getMetaData();
            try (ResultSet rs = dbMetaData.getPrimaryKeys(null, null, tableName)) {
                if (rs.next()) {
                    keyColumn = rs.getString("COLUMN_NAME");
                }
            }

            if (keyColumn.isEmpty()) {
                throw new SQLException("Primary key column not found for table " + tableName);
            }

            // Определяем, требуется ли преобразование строки в массив
            String query;
            boolean isArray = false;
            if (newValue instanceof String && ((String) newValue).startsWith("[") && ((String) newValue).endsWith("]")) {
                query = "UPDATE " + tableName + " SET " + columnName + " = string_to_array(?, ',') WHERE " + keyColumn + " = ?";
                isArray = true;
            } else {
                query = "UPDATE " + tableName + " SET " + columnName + " = ? WHERE " + keyColumn + " = ?";
            }

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                if (isArray) {
                    String arrayString = ((String) newValue)
                            .substring(1, ((String) newValue).length() - 1)
                            .replaceAll(",\\s+", ",");
                    stmt.setString(1, arrayString);
                } else {
                    stmt.setObject(1, newValue);
                }
                stmt.setObject(2, keyValue);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
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

    public static void main(String[] args) {
        try {
            DatabaseServer server = new DatabaseServer();
            server.startServer();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
