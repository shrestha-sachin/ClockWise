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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);

            primaryStage.setTitle("ClockWise - Login");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(520);
            primaryStage.setMinHeight(420);
            primaryStage.show();

            primaryStage.setOnCloseRequest(e -> {
                DataManager.shutdown();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}