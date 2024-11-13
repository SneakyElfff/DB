package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import javax.swing.table.TableModel;
import java.util.List;
import java.util.Vector;

public class DatabaseClient extends JFrame {
    private JComboBox<String> tables_list;
    private JTable table_db;
    private JScrollPane scroll_panel;
    private boolean is_ascending = true;
    private JComboBox<String> columns_list;
    private JTextField search_field;
    private JMenu file_menu;
    private JButton search_button;

    public DatabaseClient() {
        setTitle("Travel Agency Database");
        setSize(600, 400);
        setLayout(new BorderLayout());

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JMenuBar menu_bar = new JMenuBar();
        setJMenuBar(menu_bar);

        file_menu = new JMenu("Edit");
        menu_bar.add(file_menu);

        JMenuItem add_item = new JMenuItem("Add data...");
        add_item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addNewRow();
            }
        });
        file_menu.add(add_item);

        JMenuItem delete_item = new JMenuItem("Delete data");
        delete_item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedRow();
            }
        });
        file_menu.add(delete_item);

        JMenuItem edit_item = new JMenuItem("Edit selected row");
        edit_item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editSelectedRow();
            }
        });
        file_menu.add(edit_item);

        tables_list = new JComboBox<>();
        tables_list.addActionListener(e -> {
            String selected = (String) tables_list.getSelectedItem();
            if (selected != null)
                displayTable(selected);
        });
        add(tables_list, BorderLayout.NORTH);

        table_db = new JTable();
        table_db.setAutoCreateRowSorter(true);
        table_db.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int column = table_db.columnAtPoint(e.getPoint());
                if (column != -1) {
                    String table_name = (String) tables_list.getSelectedItem();
                    String column_name = table_db.getColumnName(column);

                    is_ascending = !is_ascending;

                    displayTable(table_name, column_name);
                }
            }
        });

        columns_list = new JComboBox<>();
        search_field = new JTextField();
        search_button = new JButton("Search");
        search_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(search_field.getText().equals("")) {
                    String table_name = (String) tables_list.getSelectedItem();
                    displayTable(table_name);
                }
                else
                    searchRows();
            }
        });

        JPanel search_panel = new JPanel(new BorderLayout());
        search_panel.add(columns_list, BorderLayout.WEST);
        search_panel.add(search_field, BorderLayout.CENTER);
        search_panel.add(search_button, BorderLayout.EAST);
        add(search_panel, BorderLayout.SOUTH);

        scroll_panel = new JScrollPane(table_db);
        add(scroll_panel, BorderLayout.CENTER);

        loadTables();
        addKeyListeners();
    }

    private void addKeyListeners() {
        file_menu.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, tables_list, search_button);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    file_menu.doClick();
                }
            }
        });

        tables_list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, table_db, file_menu);
            }
        });

        table_db.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, columns_list, tables_list);
            }
        });

        columns_list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, search_field, table_db);
            }
        });

        search_field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, search_button, columns_list);
            }
        });

        search_button.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, file_menu, search_field);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    search_button.doClick();
                }
            }
        });
    }

    private void handleArrowKeys(KeyEvent e, JComponent next, JComponent previous) {
        if (e.isShiftDown()) {
            if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                next.requestFocus();
            } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                previous.requestFocus();
            }
        }
    }

    private void loadTables() {
        try {
            Socket socket = new Socket("localhost", 8080);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("GET_TABLES");
            out.flush();

            List<String> tableNames = (List<String>) in.readObject();
            tables_list.removeAllItems();
            for (String tableName : tableNames) {
                if (!tableName.toLowerCase().contains("leisure")) {
                    tables_list.addItem(tableName);
                }
            }

            socket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void displayTable(String table_name) {
        displayTable(table_name, null);
    }

    private void displayTable(String table_name, String order_by_column) {
        try {
            Socket socket = new Socket("localhost", 8080);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("GET_TABLE_DATA");
            out.writeObject(table_name);
            out.writeObject(order_by_column);
            out.writeBoolean(is_ascending);
            out.flush();

            List<List<Object>> tableData = (List<List<Object>>) in.readObject();
            setTableData(tableData);

            columns_list.removeAllItems();
            if (!tableData.isEmpty()) {
                for (Object column : tableData.get(0)) {
                    String columnName = column.toString();
                    if (!columnName.toLowerCase().contains("_id") && !columnName.toLowerCase().contains("code") && !columnName.toLowerCase().contains("booking_number")) {
                        columns_list.addItem(columnName);
                    }
                }
            }

            socket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setTableData(List<List<Object>> tableData) {
        // Получаем имена столбцов из первой строки, предполагая, что она содержит заголовки
        Vector<String> columns = new Vector<>();
        Vector<String> visibleColumns = new Vector<>();
        List<Integer> visibleIndexes = new ArrayList<>();

        if (!tableData.isEmpty()) {
            for (int i = 0; i < tableData.get(0).size(); i++) {
                String columnName = tableData.get(0).get(i).toString();
                columns.add(columnName);

                if (!columnName.toLowerCase().contains("_id") && !columnName.toLowerCase().contains("code") && !columnName.toLowerCase().contains("booking_number")) {
                    visibleColumns.add(columnName);
                    visibleIndexes.add(i);
                }
            }
        }

        // Извлекаем строки данных только для отображаемых столбцов
        Vector<Vector<Object>> data = new Vector<>();
        for (int i = 1; i < tableData.size(); i++) {
            Vector<Object> row = new Vector<>();
            for (int index : visibleIndexes) {
                row.add(tableData.get(i).get(index));
            }
            data.add(row);
        }

        // Устанавливаем модель таблицы только с видимыми столбцами
        DefaultTableModel model = new DefaultTableModel(data, visibleColumns);
        table_db.setModel(model);
    }

    private void addNewRow() {
        String tableName = (String) tables_list.getSelectedItem();
        if (tableName == null) {
            JOptionPane.showMessageDialog(this, "Please select a table.");
            return;
        }

        Vector<String> columnNames = new Vector<>();
        for (int i = 0; i < table_db.getColumnCount(); i++) {
            columnNames.add(table_db.getColumnName(i));
        }

        JPanel inputPanel = new JPanel(new GridLayout(columnNames.size(), 2));
        Object[] fields = new Object[columnNames.size()];

        for (int i = 0; i < columnNames.size(); i++) {
            inputPanel.add(new JLabel(columnNames.get(i)));

            // Check if column name contains "date"
            if (columnNames.get(i).toLowerCase().contains("date")) {
                // Create a JSpinner for date input
                JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
                JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd HH:mm:ss");
                dateSpinner.setEditor(dateEditor);
                inputPanel.add(dateSpinner);
                fields[i] = dateSpinner; // Store the spinner reference
            } else {
                // Other fields (e.g., text or checkbox)
                Object value = table_db.getValueAt(0, i); // Check the value type
                if (value instanceof Boolean) {
                    JCheckBox checkBox = new JCheckBox();
                    inputPanel.add(checkBox);
                    fields[i] = checkBox; // Store checkbox reference
                } else {
                    JTextField textField = new JTextField();
                    inputPanel.add(textField);
                    fields[i] = textField; // Store text field reference
                }
            }
        }

        int result = JOptionPane.showConfirmDialog(this, inputPanel, "Enter new row data",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            Vector<Object> rowData = new Vector<>();
            for (Object field : fields) {
                if (field instanceof JTextField) {
                    rowData.add(((JTextField) field).getText());
                } else if (field instanceof JCheckBox) {
                    rowData.add(((JCheckBox) field).isSelected());
                } else if (field instanceof JSpinner) {
                    // Get the selected date from the spinner and convert to java.sql.Timestamp
                    java.util.Date selectedDate = (java.util.Date) ((JSpinner) field).getValue();
                    rowData.add(new java.sql.Timestamp(selectedDate.getTime())); // Convert to Timestamp
                }
            }
            if (!sendNewRowToServer(tableName, rowData)) {
                JOptionPane.showMessageDialog(this, "Failed to add a new row.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean sendNewRowToServer(String tableName, Vector<Object> rowData) {
        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("ADD_ROW");
            out.writeObject(tableName);
            out.writeObject(rowData);
            out.flush();

            String response = (String) in.readObject();
            if ("SUCCESS".equals(response)) {
                JOptionPane.showMessageDialog(this, "Row added successfully!");
                displayTable(tableName);
                return true;
            } else {
                JOptionPane.showMessageDialog(this, "Failed to add row.");
                return false;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void deleteSelectedRow() {
        try {
            String tableName = (String) tables_list.getSelectedItem();
            if (tableName == null) {
                JOptionPane.showMessageDialog(this, "Choose a table for deleting data", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int selectedRow = table_db.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Choose, which row to delete", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            TableModel model = table_db.getModel();
            String keyColumn = model.getColumnName(0);  // Название столбца для идентификатора
            Object id = table_db.getValueAt(selectedRow, 0);  // Значение ключа

            boolean success = sendDeleteRowToServer(tableName, keyColumn, id);

            if (success) {
                displayTable(tableName);  // Обновляем таблицу после удаления
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete row on the server", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean sendDeleteRowToServer(String tableName, String keyColumn, Object id) {
        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeObject("DELETE_ROW");
            out.writeObject(tableName);
            out.writeObject(keyColumn);  // Отправляем название ключевого столбца
            out.writeObject(id);  // Отправляем значение ключа для удаления
            out.flush();

            // Получаем ответ от сервера
            String response = (String) in.readObject();
            return "SUCCESS".equals(response);  // Проверка, если операция была успешна
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void editSelectedRow() {
        int selectedRow = table_db.getSelectedRow();
        int selectedCol = table_db.getSelectedColumn();

        if (selectedRow == -1 || selectedCol == -1) {
            JOptionPane.showMessageDialog(this, "Please select a cell to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DefaultTableModel model = (DefaultTableModel) table_db.getModel();
        String tableName = (String) tables_list.getSelectedItem();
        String columnName = table_db.getColumnName(selectedCol);
        Object currentValue = model.getValueAt(selectedRow, selectedCol);
        Object editorField;

        // Создаем панель для редактирования
        JPanel inputPanel = new JPanel(new GridLayout(2, 2));
        inputPanel.add(new JLabel("Edit value for: " + columnName));

        // Проверяем тип значения и добавляем соответствующий компонент
        if (columnName.toLowerCase().contains("date")) {
            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
            JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd HH:mm:ss");
            dateSpinner.setEditor(dateEditor);
            dateSpinner.setValue(currentValue != null ? currentValue : new java.util.Date());
            inputPanel.add(dateSpinner);
            editorField = dateSpinner;
        } else if (currentValue instanceof Boolean) {
            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected((Boolean) currentValue);
            inputPanel.add(checkBox);
            editorField = checkBox;
        } else {
            JTextField textField = new JTextField(currentValue != null ? currentValue.toString() : "");
            inputPanel.add(textField);
            editorField = textField;
        }

        int result = JOptionPane.showConfirmDialog(this, inputPanel, "Edit value", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            Object newValue;
            if (editorField instanceof JTextField) {
                String text = ((JTextField) editorField).getText();
                try {
                    // Проверяем, является ли текст целым числом
                    if (text.matches("\\d+")) {
                        newValue = Integer.parseInt(text); // Преобразуем в Integer
                    } else if (text.matches("\\d*\\.\\d+")) {
                        newValue = Double.parseDouble(text); // Преобразуем в Double для десятичных чисел
                    } else {
                        newValue = text; // Оставляем как строку, если не число
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Invalid number format", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else if (editorField instanceof JCheckBox) {
                newValue = ((JCheckBox) editorField).isSelected();
            } else if (editorField instanceof JSpinner) {
                java.util.Date selectedDate = (java.util.Date) ((JSpinner) editorField).getValue();
                newValue = new java.sql.Timestamp(selectedDate.getTime());
            } else {
                newValue = currentValue;
            }

            String keyColumn = table_db.getColumnName(0);
            Object keyValue = model.getValueAt(selectedRow, 0);

            // Отправляем запрос на сервер
            if (sendEditRequestToServer(tableName, columnName, newValue, keyColumn, keyValue)) {
                model.setValueAt(newValue, selectedRow, selectedCol);
            }
        }
    }

    private boolean sendEditRequestToServer(String tableName, String columnName, Object newValue, String keyColumn, Object keyValue) {
        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("UPDATE_ROW");
            out.writeObject(tableName);
            out.writeObject(columnName);
            out.writeObject(newValue);
            out.writeObject(keyColumn);
            out.writeObject(keyValue);
            out.flush();

            String response = (String) in.readObject();
            if ("SUCCESS".equals(response)) {
                return true;
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update the value.", "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to connect to the server.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void searchRows() {
        String tableName = (String) tables_list.getSelectedItem();
        String columnName = (String) columns_list.getSelectedItem();
        String searchValue = search_field.getText();

        if (tableName == null || columnName == null || searchValue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a table, a column, and enter a search value.", "Search Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Отправляем команду поиска
            out.writeObject("SEARCH_ROWS");
            out.writeObject(tableName);
            out.writeObject(columnName);
            out.writeObject(searchValue);
            out.flush();

            // Получаем отфильтрованные данные
            List<List<Object>> searchResults = (List<List<Object>>) in.readObject();
            setTableData(searchResults);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error while searching: " + e.getMessage(), "Search Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DatabaseClient().setVisible(true));
    }
}