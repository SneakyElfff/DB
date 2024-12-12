package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sleepycat.je.*;

import java.io.File;

public class BerkeleyServer {
    public static void main(String[] args) {
        File dbEnvFile = new File("/Users/nina/IdeaProjects/DB/DatabaseViewer/berkeley_db/tour_bookings");
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(false); // не создавать базу, если она не существует
        envConfig.setTransactional(true);

        Environment env = new Environment(dbEnvFile, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(false);
        dbConfig.setTransactional(true);
        Database db = env.openDatabase(null, "tour_bookings", dbConfig);

        ObjectMapper objectMapper = new ObjectMapper();

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        Cursor cursor = db.openCursor(null, null);

        try {
            while (cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                String keyStr = new String(key.getData());
                String dataStr = new String(data.getData());

                try {
                    Object deserializedValue = objectMapper.readValue(dataStr, Object.class);
                    System.out.println("Key: " + keyStr);
                    System.out.println("Deserialized Data: " + deserializedValue);
                } catch (Exception e) {
                    System.err.println("Failed to deserialize value for key: " + keyStr);
                    e.printStackTrace();
                }
            }
        } finally {
            cursor.close();
            db.close();
            env.close();
        }
    }
}