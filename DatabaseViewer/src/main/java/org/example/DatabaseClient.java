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

    private Vector<String> allColumns = new Vector<>();

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

        List<Component> focusableComponents = new ArrayList<>();
        focusableComponents.add(file_menu);
        focusableComponents.add(tables_list);
        focusableComponents.add(table_db);
        focusableComponents.add(columns_list);
        focusableComponents.add(search_field);
        focusableComponents.add(search_button);

        setFocusTraversalPolicy(new CustomFocusTraversalPolicy(focusableComponents));
    }

    private void addKeyListeners() {
        file_menu.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    file_menu.doClick();
                }
            }
        });

        table_db.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, columns_list, tables_list);
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

    private Map<Integer, Object> primaryKeyValues = new HashMap<>();
    private Map<Integer, Map<String, Object>> foreignKeyValues = new HashMap<>();
    private Map<String, List<String>> foreignKeyMapping = new HashMap<>();

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
            setTableData(table_name, tableData);

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

    private void setTableData(String tableName, List<List<Object>> tableData) {
        Vector<String> visibleColumns = new Vector<>();
        List<Integer> visibleIndexes = new ArrayList<>();
        primaryKeyValues.clear();
        foreignKeyValues.clear();
        allColumns.clear();

        if (!tableData.isEmpty()) {
            List<String> foreignKeysForTable = new ArrayList<>(); // Список внешних ключей для текущей таблицы

            for (int i = 0; i < tableData.get(0).size(); i++) {
                String columnName = tableData.get(0).get(i).toString();
                allColumns.add(columnName);

                if (!columnName.toLowerCase().contains("_id")) {
                    visibleColumns.add(columnName);
                    visibleIndexes.add(i);
                }

                String modifiedTableName = tableName.endsWith("s") ? tableName.substring(0, tableName.length() - 1) : tableName;
                if (!columnName.toLowerCase().startsWith(modifiedTableName.toLowerCase())) {

                    if (columnName.toLowerCase().endsWith("_id")) {
                        foreignKeysForTable.add(columnName);
                    }
                }
            }

            foreignKeyMapping.put(tableName, foreignKeysForTable);
        }

        Vector<Vector<Object>> data = new Vector<>();
        for (int i = 1; i < tableData.size(); i++) {
            Vector<Object> row = new Vector<>();
            Map<String, Object> rowForeignKeys = new HashMap<>();

            for (int index : visibleIndexes) {
                row.add(tableData.get(i).get(index));
            }
            data.add(row);

            primaryKeyValues.put(i - 1, tableData.get(i).get(0));

            for (String foreignKey : foreignKeyMapping.getOrDefault(tableName, new ArrayList<>())) {
                int foreignKeyIndex = allColumns.indexOf(foreignKey);
                if (foreignKeyIndex != -1) {
                    rowForeignKeys.put(foreignKey, tableData.get(i).get(foreignKeyIndex));
                }
            }
            foreignKeyValues.put(i - 1, rowForeignKeys);
        }

        DefaultTableModel model = new DefaultTableModel(data, visibleColumns);
        table_db.setModel(model);
    }

    private void addNewRow() {
        String tableName = (String) tables_list.getSelectedItem();
        if (tableName == null) {
            JOptionPane.showMessageDialog(this, "Please select a table.");
            return;
        }

        List<String> relevantForeignKeys = foreignKeyMapping.getOrDefault(tableName, new ArrayList<>());

        JPanel inputPanel = new JPanel(new GridLayout(allColumns.size(), 2));
        Object[] fields = new Object[allColumns.size()]; // Массив для хранения введенных значений

        for (int i = 0; i < allColumns.size(); i++) {
            String columnName = allColumns.get(i);

            String modifiedTableName = tableName.endsWith("s") ? tableName.substring(0, tableName.length() - 1) : tableName;
            if (columnName.toLowerCase().startsWith(modifiedTableName.toLowerCase()))
                continue;

            inputPanel.add(new JLabel(columnName));

            // Проверяем, является ли столбец внешним ключом для этой таблицы
            if (relevantForeignKeys.contains(columnName)) {
                JComboBox<Object> comboBox = new JComboBox<>();
                foreignKeyValues.forEach((index, foreignKeys) -> {
                    Object fkValue = foreignKeys.get(columnName);
                    if (fkValue != null) {
                        comboBox.addItem(fkValue);
                    }
                });
                inputPanel.add(comboBox);
                fields[i] = comboBox;
            } else if (columnName.toLowerCase().contains("date")) {
                JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
                JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd HH:mm:ss");
                dateSpinner.setEditor(dateEditor);
                inputPanel.add(dateSpinner);
                fields[i] = dateSpinner; // Store the spinner reference
            } else {
                // Other fields (e.g., text or checkbox)
//                Object value = table_db.getValueAt(0, i); // Check the value type
//                if (value instanceof Boolean) {
//                    JCheckBox checkBox = new JCheckBox();
//                    inputPanel.add(checkBox);
//                    fields[i] = checkBox; // Store checkbox reference
//                 else {
                    JTextField textField = new JTextField();
                    inputPanel.add(textField);
                    fields[i] = textField; // Store text field reference
//                }
            }
        }

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        JOptionPane optionPane = new JOptionPane(inputPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, new Object[]{okButton, cancelButton});
        JDialog dialog = optionPane.createDialog(this, "Enter new row data");

        dialog.getRootPane().setDefaultButton(null);

        okButton.addActionListener(e -> optionPane.setValue(JOptionPane.OK_OPTION));
        cancelButton.addActionListener(e -> optionPane.setValue(JOptionPane.CANCEL_OPTION));

        okButton.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    okButton.doClick();
                }
            }
        });

        cancelButton.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    cancelButton.doClick();
                }
            }
        });

        dialog.setVisible(true);

        int result = (int) optionPane.getValue();
        if (result == JOptionPane.OK_OPTION) {
            Vector<Object> rowData = new Vector<>();
            for (Object field : fields) {
                if (field instanceof JTextField) {
                    rowData.add(((JTextField) field).getText());
                } else if (field instanceof JCheckBox) {
                    rowData.add(((JCheckBox) field).isSelected());
                } else if (field instanceof JSpinner) {
                    java.util.Date selectedDate = (java.util.Date) ((JSpinner) field).getValue();
                    rowData.add(new java.sql.Timestamp(selectedDate.getTime()));
                } else if (field instanceof JComboBox) {
                    rowData.add(((JComboBox<?>) field).getSelectedItem());
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
            Object id = primaryKeyValues.get(selectedRow);

            boolean success = sendDeleteRowToServer(tableName, id);

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

    private boolean sendDeleteRowToServer(String tableName, Object id) {
        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeObject("DELETE_ROW");
            out.writeObject(tableName);
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

            Object keyValue = primaryKeyValues.get(selectedRow);

            // Отправляем запрос на сервер
            if (sendEditRequestToServer(tableName, columnName, newValue, keyValue)) {
                model.setValueAt(newValue, selectedRow, selectedCol);
            }
        }
    }

    private boolean sendEditRequestToServer(String tableName, String columnName, Object newValue, Object keyValue) {
        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("UPDATE_ROW");
            out.writeObject(tableName);
            out.writeObject(columnName);
            out.writeObject(newValue);
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
            setTableData(tableName, searchResults);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error while searching: " + e.getMessage(), "Search Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DatabaseClient().setVisible(true));
    }
}
