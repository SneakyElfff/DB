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
                    tableName = (String) in.readObject();
                    String keyColumn = (String) in.readObject();
                    Object id = in.readObject();

                    // Подготовка запроса с параметризованным значением для предотвращения SQL-инъекций
                    String query;
                    if(tableName.equals("clients"))
                        query = "DELETE FROM " + tableName + " WHERE " + keyColumn + " = '" + id + "'";
                    else
                        query = "DELETE FROM " + tableName + " WHERE " + keyColumn + " = " + id;

                    PreparedStatement stmt = connection.prepareStatement(query);

                    int rowsAffected = stmt.executeUpdate();
                    out.writeObject(rowsAffected > 0 ? "SUCCESS" : "FAILURE");

                case "UPDATE_ROW":  // Новая команда для обновления данных
                    tableName = (String) in.readObject();
                    String columnName = (String) in.readObject();
                    Object newValue = in.readObject();
                    keyColumn = (String) in.readObject();
                    Object keyValue = in.readObject();
                    boolean updateSuccess = updateRowInDatabase(tableName, columnName, newValue, keyColumn, keyValue);
                    out.writeObject(updateSuccess ? "SUCCESS" : "FAILURE");
                    break;

                case "SEARCH_ROWS":
                    String searchTable = (String) in.readObject();  // Имя таблицы
                    String searchColumn = (String) in.readObject(); // Имя столбца для поиска
                    String searchValue = (String) in.readObject();  // Значение для поиска
                    List<List<Object>> searchResults = searchInDatabase(searchTable, searchColumn, searchValue);
                    out.writeObject(searchResults);
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
                if (value instanceof java.sql.Array) {
                    Object[] array = (Object[]) ((java.sql.Array) value).getArray();
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

            StringBuilder query = new StringBuilder("INSERT INTO " + tableName + " (");
            for (int i = 1; i <= columnCount; i++) {
                query.append(metaData.getColumnName(i));
                if (i < columnCount) {
                    query.append(", ");
                }
            }
            query.append(") VALUES (");
            for (int i = 0; i < columnCount; i++) {
                query.append("?");
                if (i < columnCount - 1) {
                    query.append(", ");
                }
            }
            query.append(")");

            PreparedStatement preparedStatement = connection.prepareStatement(query.toString());
            for (int i = 0; i < columnCount; i++) {
                int columnType = metaData.getColumnType(i + 1);
                Object value = rowData.get(i);

                if (columnType == Types.INTEGER) {
                    preparedStatement.setInt(i + 1, Integer.parseInt(value.toString()));
                } else if (columnType == Types.DOUBLE) {
                    preparedStatement.setDouble(i + 1, Double.parseDouble(value.toString()));
                } else if (columnType == Types.BOOLEAN) {
                    preparedStatement.setBoolean(i + 1, Boolean.parseBoolean(value.toString()));
                } else if (columnType == Types.ARRAY) {
                    // Если value является строкой, разбиваем его на массив строк
                    if (value instanceof String) {
                        String stringValue = (String) value;
                        // Предположим, что массив записан в формате '{value1,value2}'
                        stringValue = stringValue.replaceAll("[{}]", ""); // Убираем фигурные скобки
                        String[] arrayValue = stringValue.split(","); // Разделяем по запятой
                        Array array = connection.createArrayOf("varchar", arrayValue);
                        preparedStatement.setArray(i + 1, array);
                    } else {
                        // Обработка других типов, если это не строка
                        String[] arrayValue = ((List<String>) value).toArray(new String[0]);
                        Array array = connection.createArrayOf("varchar", arrayValue);
                        preparedStatement.setArray(i + 1, array);
                    }
                } else {
                    preparedStatement.setObject(i + 1, value);
                }
            }
            preparedStatement.executeUpdate();
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

    private boolean deleteRowFromDatabase(String tableName, int rowId) {
        try {
            String query = "DELETE FROM " + tableName + " WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setInt(1, rowId);
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean updateRowInDatabase(String tableName, String columnName, Object newValue, String keyColumn, Object keyValue) {
        try {
            String sql = "UPDATE " + tableName + " SET " + columnName + " = ? WHERE " + keyColumn + " = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setObject(1, newValue);
            preparedStatement.setObject(2, keyValue);
            return preparedStatement.executeUpdate() > 0;
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
        } else if (isArrayColumn(tableName, columnName)) { // Добавим проверку, является ли колонка массивом
            // Поиск в массиве, если поле типа array
            query = "SELECT * FROM " + tableName + " WHERE ? = ANY(" + columnName + ")";
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, searchValue);
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
                if (value instanceof java.sql.Array) {
                    Object[] array = (Object[]) ((java.sql.Array) value).getArray();
                    row.add(Arrays.asList(array));
                } else {
                    row.add(value);
                }
            }
            results.add(row);
        }
        return results;
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
