package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.Vector;

public class DatabaseViewer extends JFrame {
    private JComboBox<String> tables_list;
    private JTable table_db;
    private JScrollPane scroll_panel;
    private JTextField[] input_fields;
    private boolean is_ascending = true;
    private JComboBox<String> columns_list;
    private JTextField search_field;
    private Connection connection;

    public DatabaseViewer() {
        setTitle("Travel agency");
        setSize(600, 400);
        setLayout(new BorderLayout());

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JMenuBar menu_bar = new JMenuBar();
        setJMenuBar(menu_bar);

        JMenu file_menu = new JMenu("Edit");
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

        tables_list = new JComboBox<>();
        tables_list.addActionListener(e -> {
            String selected = (String) tables_list.getSelectedItem();
            if (selected != null)
                displayTable(selected);
        });
        add(tables_list, BorderLayout.NORTH);

        table_db = new JTable();
        table_db.setAutoCreateRowSorter(true);
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

        columns_list = new JComboBox<>();
        search_field = new JTextField();
        JButton search_button = new JButton("Search");
        search_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchRows();
            }
        });

        JPanel search_panel = new JPanel(new BorderLayout());
        search_panel.add(columns_list, BorderLayout.WEST);
        search_panel.add(search_field, BorderLayout.CENTER);
        search_panel.add(search_button, BorderLayout.EAST);
        add(search_panel, BorderLayout.SOUTH);

        // listeners
        table_db.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = table_db.rowAtPoint(e.getPoint());
                    int col = table_db.columnAtPoint(e.getPoint());

                    if (row >= 0 && col >= 0)
                        editCellValue(row, col);
                }
            }
        });

        tables_list.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                columns_list.removeAllItems();
                String table_name = (String) tables_list.getSelectedItem();

                try {
                    DatabaseMetaData meta_data = connection.getMetaData();
                    ResultSet columns = meta_data.getColumns(null, null, table_name, null);
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        columns_list.addItem(columnName);
                    }
                }
                catch (SQLException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(DatabaseViewer.this, "Failed to fetch column names: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        columns_list.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String column_name = (String) columns_list.getSelectedItem();
                String table_name = (String) tables_list.getSelectedItem();

                if (column_name != null && table_name != null)
                    displayTable(table_name, column_name);

                search_field.setText("");
            }
        });

        scroll_panel = new JScrollPane(table_db);
        add(scroll_panel, BorderLayout.CENTER);

        try {
            connectToDatabase();
            loadTables();
        }
        catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to connect to a database: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/Travel+agency?user=nina";
        connection = DriverManager.getConnection(url);
    }

    private void loadTables() throws SQLException {
        DatabaseMetaData data_db = connection.getMetaData();
        ResultSet tables = data_db.getTables(null, null, null, new String[]{"TABLE"});

        while (tables.next()) {
            String table_name = tables.getString("TABLE_NAME");
            tables_list.addItem(table_name);
        }
    }

    private void displayTable(String table_name) {
        displayTable(table_name, null);
    }

    private void displayTable(String table_name, String order_by_column) {
        try {
            String query = "SELECT * FROM " + table_name;
            if (order_by_column != null) {
                query += " ORDER BY " + order_by_column;
                if (!is_ascending) {
                    query += " DESC";
                }
            }

            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(query);
            ResultSetMetaData meta_data = result.getMetaData();

            int col_counter = meta_data.getColumnCount();
            Vector<String> columns = new Vector<>();
            Vector<Vector<Object>> data = new Vector<>();

            for (int i=1; i<=col_counter; i++)
                columns.add(meta_data.getColumnName(i));

            while (result.next()) {
                Vector<Object> row = new Vector<>();
                for (int i=1; i<=col_counter; i++)
                    row.add(result.getObject(i));
                data.add(row);
            }

            DefaultTableModel model = new DefaultTableModel(data, columns);
            table_db.setModel(model);
        }
        catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to display a table: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addNewRow() {
        try {
            String table_name = (String) tables_list.getSelectedItem();
            if (table_name == null) {
                JOptionPane.showMessageDialog(this, "Choose a table for adding new data", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Statement statement = connection.createStatement();

            ResultSet result = statement.executeQuery("SELECT * FROM " + table_name);
            ResultSetMetaData meta_data = result.getMetaData();

            int col_counter = meta_data.getColumnCount();
            String[] columns = new String[col_counter];
            input_fields = new JTextField[col_counter];

            JPanel input_panel = new JPanel(new GridLayout(col_counter, 2));

            for (int i=1; i<=col_counter; i++) {
                columns[i-1] = meta_data.getColumnName(i);
                input_panel.add(new JLabel(columns[i-1]));

                JTextField text_field = new JTextField();
                input_fields[i-1] = text_field;
                input_panel.add(text_field);
            }

            int insertion = JOptionPane.showConfirmDialog(null, input_panel, "Enter data", JOptionPane.OK_CANCEL_OPTION);
            if (insertion == JOptionPane.OK_OPTION) {
                StringBuilder query = new StringBuilder("INSERT INTO " + table_name + " (");

                for (int i=0; i<col_counter; i++) {
                    query.append(columns[i]);
                    if (i < col_counter - 1)
                        query.append(", ");
                }

                query.append(") VALUES (");
                for (int i=0; i<col_counter; i++) {
                    query.append("'").append(input_fields[i].getText()).append("'");
                    if (i < col_counter - 1)
                        query.append(", ");
                }
                query.append(")");

                connection.createStatement().executeUpdate(query.toString());
                displayTable(table_name);
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to add a new row: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedRow() {
        try {
            String table_name = (String) tables_list.getSelectedItem();
            if (table_name == null) {
                JOptionPane.showMessageDialog(this, "Choose a table for deleting data", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int selected_row = table_db.getSelectedRow();
            if (selected_row == -1) {
                JOptionPane.showMessageDialog(this, "Choose, which row to delete", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            TableModel model = table_db.getModel();
            String key_column = model.getColumnName(0);
            Object id = table_db.getValueAt(selected_row, 0);

            String query;
            if(table_name.equals("clients"))
                query = "DELETE FROM " + table_name + " WHERE " + key_column + " = '" + id + "'";
            else
                query = "DELETE FROM " + table_name + " WHERE " + key_column + " = " + id;

            connection.createStatement().executeUpdate(query);
            displayTable(table_name);
        }
        catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to delete a row: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editCellValue(int row, int col) {
        DefaultTableModel model = (DefaultTableModel) table_db.getModel();
        String table_name = (String) tables_list.getSelectedItem();
        String column_name = table_db.getColumnName(col);
        String key_column = table_db.getColumnName(0);

        String new_value_str = JOptionPane.showInputDialog(this, "Enter a new value:", "Edit the field", JOptionPane.PLAIN_MESSAGE);
        if (new_value_str != null) {
            Object new_value;
            if (new_value_str.startsWith("\"") && new_value_str.endsWith("\"")) //string
                new_value = new_value_str.substring(1, new_value_str.length() - 1);
            else if (new_value_str.chars().allMatch(Character::isDigit)) //digit
                new_value = Integer.parseInt(new_value_str);
            else
                new_value = new_value_str;

            try {
                Object key_value = model.getValueAt(row, 0);
                String sql = "UPDATE " + table_name + " SET " + column_name + " = ? WHERE " + key_column + " = ?";
                PreparedStatement preparedStatement = connection.prepareStatement(sql);

                preparedStatement.setObject(1, new_value);
                preparedStatement.setObject(2, key_value);
                preparedStatement.executeUpdate();

                model.setValueAt(new_value, row, col);
            }
            catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to update a value: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void searchRows() {
        String table_name = (String) tables_list.getSelectedItem();
        String search_query = search_field.getText();
        String search_col = (String) columns_list.getSelectedItem();

        if (!search_query.isEmpty() && search_col != null) {
            try {
                String query;
                if (isInteger(search_query))
                    query = "SELECT * FROM " + table_name + " WHERE " + search_col + " = " + search_query;
                else {
                    query = "SELECT * FROM " + table_name + " WHERE " + search_col + " LIKE ?";
                    search_query = "%" + search_query + "%";
                }

                PreparedStatement prep_statement = connection.prepareStatement(query);
                ResultSet result;
                if (!isInteger(search_query)) {
                    prep_statement.setObject(1, search_query);
                    result = prep_statement.executeQuery();
                }
                else
                    result = prep_statement.executeQuery();

                table_db.setModel(buildTableModel(result));
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Search failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please enter search criteria.", "Error", JOptionPane.ERROR_MESSAGE);
        }
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

    public static DefaultTableModel buildTableModel(ResultSet result) throws SQLException {
        ResultSetMetaData meta_data = result.getMetaData();

        int col_counter = meta_data.getColumnCount();

        DefaultTableModel model = new DefaultTableModel();

        for (int ind=1; ind<=col_counter; ind++)
            model.addColumn(meta_data.getColumnName(ind));

        while (result.next()) {
            Vector<Object> row = new Vector<>();

            for (int ind=1; ind<=col_counter; ind++)
                row.add(result.getObject(ind));
            model.addRow(row);
        }

        return model;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DatabaseViewer viewer = new DatabaseViewer();
            viewer.setVisible(true);
        });
    }
}