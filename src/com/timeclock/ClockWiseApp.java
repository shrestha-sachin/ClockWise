package com.timeclock;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Optional;

public class ClockWiseApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        DataManager.initializeDatabase();

        if (!DataManager.hasCompanyInfo()) {
            showCompanySetupDialog();
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);

            primaryStage.setTitle("ClockWise - Login");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(520);
            primaryStage.setMinHeight(450);
            primaryStage.show();

            primaryStage.setOnCloseRequest(e -> {
                DataManager.shutdown();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showCompanySetupDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Welcome to ClockWise");
        dialog.setHeaderText("Let's get started!\nPlease enter your company details.");

        TextField nameField = new TextField();
        nameField.setPromptText("Company Name");
        TextField locationField = new TextField();
        locationField.setPromptText("Location / Branch");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Company Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Location:"), 0, 1);
        grid.add(locationField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        ButtonType saveButtonType = new ButtonType("Save & Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String name = nameField.getText().trim();
                String loc = locationField.getText().trim();
                if (!name.isEmpty() && !loc.isEmpty()) {
                    DataManager.saveCompanyInfo(name, loc);
                    return true;
                }
            }
            return null;
        });

        // Force user to setup
        Optional<Boolean> result = dialog.showAndWait();
        if (result.isEmpty()) {
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}