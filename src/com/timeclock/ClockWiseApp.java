package com.timeclock;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClockWiseApp extends Application {

    @Override
    public void start(Stage primaryStage) {
    	DataManager.initializeDatabase();
        try {
            // Load FXML file from default package or root of classpath
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ClockWise.fxml"));
            Parent root = loader.load();

            // Create scene
            Scene scene = new Scene(root);

            // Configure stage
            primaryStage.setTitle("ClockWise - Time Tracking System");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1020);
            primaryStage.setMinHeight(705);
            primaryStage.setResizable(true);
            
            // Show the window
            primaryStage.show();

            // Handle window close
            primaryStage.setOnCloseRequest(e -> {
                ClockWiseController controller = loader.getController();
                if (controller != null) {
                    controller.shutdown();
                }
            });

        } catch (Exception e) {
            System.err.println("Error loading FXML file:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}