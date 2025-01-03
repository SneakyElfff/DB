package org.example;

import com.formdev.flatlaf.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

public class DatabaseClient extends JFrame {
    private JComboBox<String> tables_list;
    private JTable table_db;
    private JScrollPane scroll_panel;
    private boolean is_ascending = true;
    private JComboBox<String> columns_list;
    private JTextField search_field;
    private JMenu file_menu;
    private JMenu edit_menu;
    private JMenu help_menu;
    private JButton search_button;

    private Vector<String> allColumns = new Vector<>();
    private Map<Integer, Object> primaryKeyValues = new HashMap<>();
    private Map<Integer, Map<String, Object>> foreignKeyValues = new HashMap<>();
    private Map<String, List<String>> foreignKeyMapping = new HashMap<>();

    public DatabaseClient() {
        setTitle("Travel Agency Database");
        setSize(600, 400);
        setLayout(new BorderLayout());

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JMenuBar menu_bar = new JMenuBar();
        setJMenuBar(menu_bar);

        file_menu = new JMenu("File");
        menu_bar.add(file_menu);
        edit_menu = new JMenu("Edit");
        menu_bar.add(edit_menu);
        help_menu = new JMenu("Help");
        menu_bar.add(help_menu);

        tables_list = new JComboBox<>();
        add(tables_list, BorderLayout.NORTH);

        table_db = new JTable();
        table_db.setAutoCreateRowSorter(true);

        columns_list = new JComboBox<>();
        search_field = new JTextField();

        JPanel search_panel = new JPanel(new BorderLayout());
        search_panel.add(columns_list, BorderLayout.WEST);
        search_panel.add(search_field, BorderLayout.CENTER);
        add(search_panel, BorderLayout.SOUTH);

        scroll_panel = new JScrollPane(table_db);
        add(scroll_panel, BorderLayout.CENTER);

        DatabaseUtils.loadTables(tables_list);

        tables_list.setSelectedItem("tours");
        displayTable("tours");

        addKeyListeners();
        addListeners();

        List<Component> focusableComponents = new ArrayList<>();
        focusableComponents.add(file_menu);
        focusableComponents.add(edit_menu);
        focusableComponents.add(help_menu);
        focusableComponents.add(tables_list);
        focusableComponents.add(table_db);
        focusableComponents.add(columns_list);
        focusableComponents.add(search_field);

        setFocusTraversalPolicy(new CustomFocusTraversalPolicy(focusableComponents));

        setLocationRelativeTo(null);
    }

    private void addKeyListeners() {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.isShiftDown()) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }
            return false;
        });

        file_menu.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    file_menu.doClick();
                }
            }
        });

        edit_menu.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    edit_menu.doClick();
                }
            }
        });

        help_menu.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    help_menu.doClick();
                }
            }
        });

        table_db.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleArrowKeys(e, columns_list, tables_list);
            }
        });

        search_field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String searchValue = search_field.getText().trim();
                if (searchValue.isEmpty()) {
                    ((TableRowSorter<?>) table_db.getRowSorter()).setRowFilter(null);
                } else {
                    String columnName = (String) columns_list.getSelectedItem();
                    int columnIndex = table_db.getColumnModel().getColumnIndex(columnName);

                    TableRowSorter<?> sorter = (TableRowSorter<?>) table_db.getRowSorter();
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)^" + Pattern.quote(searchValue), columnIndex));

                    if (table_db.getRowCount() > 0) {
                        table_db.changeSelection(0, columnIndex, false, false);
                    }
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

    private void addListeners() {
        JMenuItem add_item = new JMenuItem("Add data...");
        add_item.addActionListener(e -> addNewRow());
        edit_menu.add(add_item);

        JMenuItem delete_item = new JMenuItem("Delete data");
        delete_item.addActionListener(e -> deleteSelectedRow());
        edit_menu.add(delete_item);

        JMenuItem edit_item = new JMenuItem("Edit selected row");
        edit_item.addActionListener(e -> editSelectedRow());
        edit_menu.add(edit_item);

        JMenuItem shortcuts_item = new JMenuItem("Keyboard Control");
        shortcuts_item.addActionListener(e -> showKeysDialog());
        help_menu.add(shortcuts_item);

        tables_list.addActionListener(e -> {
            String selected = (String) tables_list.getSelectedItem();
            if (selected != null)
                displayTable(selected);
        });

        table_db.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = table_db.columnAtPoint(e.getPoint());
                if (column != -1) {
                    String table_name = (String) tables_list.getSelectedItem();
                    String column_name = table_db.getColumnName(column);

                    is_ascending = !is_ascending;

                    displayTable(table_name, column_name);
                }
            }
        });
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
            setTableData(table_name, tableData);

            columns_list.removeAllItems();
            if (!tableData.isEmpty()) {
                for (Object column : tableData.get(0)) {
                    String columnName = column.toString();
                    if (!columnName.toLowerCase().contains("_id")) {
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
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table_db.setRowSorter(sorter);
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

            if (relevantForeignKeys.contains(columnName)) {
                JComboBox<Object> comboBox = new JComboBox<>();
                List<Object> foreignKeyValues = getPrimaryKeyValues(columnName);
                for (Object value : foreignKeyValues) {
                    comboBox.addItem(value);
                }
                inputPanel.add(comboBox);
                fields[i] = comboBox;
            } else if (columnName.toLowerCase().contains("date")) {
                JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
                JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd HH:mm:ss");
                dateSpinner.setEditor(dateEditor);
                inputPanel.add(dateSpinner);
                fields[i] = dateSpinner;
            } else {
                if (columnName.toLowerCase().startsWith("is_")) {
                    JCheckBox checkBox = new JCheckBox();
                    inputPanel.add(checkBox);
                    fields[i] = checkBox;
                } else {
                    JTextField textField = new JTextField();
                    inputPanel.add(textField);
                    fields[i] = textField;
                }
            }
        }

        JButton okButton = new JButton("OK");
        okButton.setBackground(new Color(108, 63, 149));
        Font boldFont = new Font("Arial", Font.BOLD, 14);
        okButton.setFont(boldFont);
        JButton cancelButton = new JButton("Cancel");

        JOptionPane optionPane = new JOptionPane(inputPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, new Object[]{okButton, cancelButton});
        JDialog dialog = optionPane.createDialog(this, "Enter new row data (" + tableName + ")");

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
            Map<String, Object> rowData = new HashMap<>();
            for (int i = 0; i < fields.length; i++) {
                Object field = fields[i];
                String columnName = allColumns.get(i);

                if (field instanceof JTextField) {
                    rowData.put(columnName, ((JTextField) field).getText());
                } else if (field instanceof JCheckBox) {
                    rowData.put(columnName, ((JCheckBox) field).isSelected());
                } else if (field instanceof JSpinner) {
                    Date selectedDate = (Date) ((JSpinner) field).getValue();
                    rowData.put(columnName, new Timestamp(selectedDate.getTime()));
                } else if (field instanceof JComboBox) {
                    rowData.put(columnName, ((JComboBox<?>) field).getSelectedItem());
                }
            }

            if (!sendNewRowToServer(tableName, rowData)) {
                JOptionPane.showMessageDialog(this, "Failed to add a new row.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private List<Object> getPrimaryKeyValues(String columnName) {
        List<Object> primaryKeyValues = new ArrayList<>();
        String tableName = null;
        try (Socket socket = new Socket("localhost", 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            tableName = columnName.replaceAll("_id$", "") + "s";

            out.writeObject("GET_PRIMARY_KEY_VALUES");
            out.writeObject(columnName);
            out.writeObject(tableName);
            out.flush();

            primaryKeyValues = (List<Object>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to retrieve primary key values for " + tableName, "Error", JOptionPane.ERROR_MESSAGE);
        }
        return primaryKeyValues;
    }

    private boolean sendNewRowToServer(String tableName, Map<String, Object> rowData) {
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

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete the selected row?",
                    "Confirm deletion (" + tableName + ")",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
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
            JOptionPane.showMessageDialog(this, "Please select a cell to edit.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DefaultTableModel model = (DefaultTableModel) table_db.getModel();
        String tableName = (String) tables_list.getSelectedItem();
        String columnName = table_db.getColumnName(selectedCol);
        Object currentValue = model.getValueAt(selectedRow, selectedCol);
        Object editorField = null;

        // Создаем панель для редактирования, отображающую все поля строки
        JPanel inputPanel = new JPanel(new GridLayout(model.getColumnCount(), 2));
        JTextField[] textFields = new JTextField[model.getColumnCount()];

        for (int i = 0; i < model.getColumnCount(); i++) {
            String colName = table_db.getColumnName(i);
            Object cellValue = model.getValueAt(selectedRow, i);

            inputPanel.add(new JLabel(colName)); // Добавляем название колонки

            if (i == selectedCol) { // Выделенное поле — редактируемое
                if (colName.toLowerCase().contains("date")) {
                    JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
                    JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd HH:mm:ss");
                    dateSpinner.setEditor(dateEditor);

                    if (cellValue instanceof Date) {
                        dateSpinner.setValue(cellValue);
                    } else if (cellValue instanceof Timestamp) {
                        dateSpinner.setValue(new Date(((Timestamp) cellValue).getTime()));
                    } else if (cellValue instanceof String) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            dateSpinner.setValue(sdf.parse((String) cellValue));
                        } catch (ParseException e) {
                            dateSpinner.setValue(new Date());
                        }
                    } else {
                        dateSpinner.setValue(new Date());
                    }

                    inputPanel.add(dateSpinner);
                    editorField = dateSpinner;
                } else if (cellValue instanceof Boolean) {
                    JCheckBox checkBox = new JCheckBox();
                    checkBox.setSelected((Boolean) cellValue);
                    inputPanel.add(checkBox);
                    editorField = checkBox;
                } else {
                    JTextField textField = new JTextField(cellValue != null ? cellValue.toString() : "");
                    inputPanel.add(textField);
                    textFields[i] = textField;
                    editorField = textField;
                }
            } else {
                JTextField textField = new JTextField(cellValue != null ? cellValue.toString() : "");
                textField.setEditable(false);
                textField.setFocusable(false);
                inputPanel.add(textField);
                textFields[i] = textField;
            }
        }

        int result = JOptionPane.showConfirmDialog(this, inputPanel, "Edit value (" + tableName + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            Object newValue;
            if (editorField instanceof JTextField) {
                String text = ((JTextField) editorField).getText();
                try {
                    if (text.matches("\\d+")) {
                        newValue = Integer.parseInt(text); // Преобразуем в Integer
                    } else if (text.matches("\\d*\\.\\d+")) {
                        newValue = Double.parseDouble(text); // Преобразуем в Double
                    } else {
                        newValue = text; // Оставляем строку
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Invalid number format", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else if (editorField instanceof JCheckBox) {
                newValue = ((JCheckBox) editorField).isSelected();
            } else if (editorField instanceof JSpinner) {
                Date selectedDate = (Date) ((JSpinner) editorField).getValue();
                newValue = new Timestamp(selectedDate.getTime());
            } else {
                newValue = currentValue; // Без изменений
            }

            Object keyValue = primaryKeyValues.get(selectedRow);

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

    private void showKeysDialog() {
        JDialog dialog = new JDialog(this, "Keyboard control", true);
        dialog.setSize(600, 200);
        dialog.setLayout(new BorderLayout());

        String keysText = """
            Tab: Move to the next element
            Shift + Tab: Move to the previous element
            Shift + Right Arrow: Move from the table to the next element
            Shift + Left Arrow: Move from the table to the previous element
            Enter: Imitate a mouse click
            Escape: Close a dialog pane
            Shift + Escape: Exit
            """;

        JTextArea textArea = new JTextArea(keysText);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(textArea);

        JButton closeButton = new JButton("Close");
        closeButton.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    dialog.dispose();
                }
            }
        });
        closeButton.addActionListener(e -> dialog.dispose());

        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(closeButton, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        try {
            IntelliJTheme.setup(DatabaseClient.class.getResourceAsStream("/themes/DarkPurple.theme.json"));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Не удалось загрузить тему.");
        }
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", true);
        UIManager.put("Table.gridColor", new Color(57, 56, 76));

        SwingUtilities.invokeLater(() -> new DatabaseClient().setVisible(true));
    }
}
