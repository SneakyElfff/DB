package org.example;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class DatabaseUtils {
    static void loadTables(JComboBox<String> tablesList) {
        try {
            Socket socket = new Socket("localhost", 8080);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("GET_TABLES");
            out.flush();

            List<String> tableNames = (List<String>) in.readObject();
            tablesList.removeAllItems();
            for (String tableName : tableNames) {
                if (!tableName.toLowerCase().contains("leisure")) {
                    tablesList.addItem(tableName);
                }
            }

            socket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
