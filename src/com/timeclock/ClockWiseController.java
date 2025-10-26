package com.timeclock;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class ClockWiseController {

	// FXML Components - linked to ClockWise.fxml
	@FXML
	private BorderPane root;
	@FXML
	private ImageView logo;
	@FXML
	private Button logoutBtn;
	@FXML
	private Label timeLabel;
	@FXML
	private Label dateLabel;
	@FXML
	private Label todayHoursLabel;
	@FXML
	private Button clockInBtn;
	@FXML
	private Button clockOutBtn;
	@FXML
	private Button mealBtn;
	@FXML
	private Button viewAllBtn;
	@FXML
	private TableView<TimeEntry> timeTable;
	@FXML
	private TableColumn<TimeEntry, String> colDate;
	@FXML
	private TableColumn<TimeEntry, String> colAction;
	@FXML
	private TableColumn<TimeEntry, String> colTime;
	@FXML
	private TableColumn<TimeEntry, String> colDuration;

	// Data storage
	private ObservableList<TimeEntry> timeEntries = FXCollections.observableArrayList();
	private Timeline clockTimeline;
	private LocalTime clockInTime = null; // Tracks when user clocked in today
	private LocalTime mealBreakStartTime = null; // Tracks when meal break started
	private long totalMealBreakMinutes = 0;
	private String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
	private long todayTotalWorkMinutes = 0;
	private boolean isClockedIn = false;
	private boolean isOnMealBreak = false;

	/**
	 * Automatically called after FXML is loaded
	 */
	@FXML
	public void initialize() {
		System.out.println("Controller initialized!");

		// Start the real-time clock
		startClock();

		// Setup the table columns
		setupTableColumns();
		
		//Load saved entries from database
		timeEntries = DataManager.loadAllEntries();
		timeTable.setItems(timeEntries);
		
		
	    String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
	    todayTotalWorkMinutes = DataManager.getTodayTotalMinutes(today);
	    
		// Setup button actions
		setupButtonActions();

		// Set initial button states
		updateButtonStates();

		// Initialize today's hours label
		updateTodayHoursLabel();
	}

	/**
	 * Starts the clock that updates every second
	 */
	private void startClock() {
		clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateDateTime()));
		clockTimeline.setCycleCount(Animation.INDEFINITE);
		clockTimeline.play();
		updateDateTime(); // Initial update
	}

	/**
	 * Updates the time and date labels
	 */
	private void updateDateTime() {
		LocalDateTime now = LocalDateTime.now();

		// Format time: 03:45:23 PM
		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a");
		timeLabel.setText(now.format(timeFormatter));

		// Format date: Monday, March 17, 2025
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy");
		dateLabel.setText(now.format(dateFormatter));
		
		String newDate = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        if (!newDate.equals(currentDate)) {
            currentDate = newDate;
            todayTotalWorkMinutes = 0; // Reset for new day
            System.out.println("New day detected - resetting daily total");
        }
		// Update today's hours if clocked in
		if (isClockedIn && !isOnMealBreak) {
			updateTodayHoursLabel();
		}
	}

	/**
	 * Updates the "Today : Xh Ym" Label based on actual clock in time
	 */
	private void updateTodayHoursLabel() {
		if (clockInTime != null) {
			LocalTime now = LocalTime.now();
			long totalMinutes = todayTotalWorkMinutes;
			
			if (isClockedIn && !isOnMealBreak) {
				totalMinutes += ChronoUnit.MINUTES.between(clockInTime, now) - totalMealBreakMinutes;
				
			}
			if (totalMinutes < 0) {
				totalMinutes = 0;
			}
			long hours = totalMinutes / 60;
			long remainingMinutes = totalMinutes % 60;
			todayHoursLabel.setText(String.format("Today: %dh %dm", hours, remainingMinutes));
		}
		else {
			long hours = todayTotalWorkMinutes / 60;
			long remainingMinutes = todayTotalWorkMinutes % 60;
			todayHoursLabel.setText(String.format("Today: %dh %dm", hours,remainingMinutes));
		}
		}

	/**
	 * Configure the TableView columns
	 */
	private void setupTableColumns() {
		colDate.setCellValueFactory(cellData -> cellData.getValue().dateProperty());
		colAction.setCellValueFactory(cellData -> cellData.getValue().actionProperty());
		colTime.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
		colDuration.setCellValueFactory(cellData -> cellData.getValue().durationProperty());

		timeTable.setItems(timeEntries);
	}

	/**
	 * Assign actions to buttons
	 */
	private void setupButtonActions() {
		clockInBtn.setOnAction(e -> handleClockIn());
		clockOutBtn.setOnAction(e -> handleClockOut());
		mealBtn.setOnAction(e -> handleMealBreak());
		logoutBtn.setOnAction(e -> handleLogout());
		viewAllBtn.setOnAction(e -> handleViewAll());
	}

	/**
	 * Enable/disable buttons based on clock in status
	 */
	private void updateButtonStates() {
		if (isClockedIn) {
			clockInBtn.setDisable(true);
			clockOutBtn.setDisable(false);
			mealBtn.setDisable(false);

			// Update meal button text based on break status
			if (isOnMealBreak) {
				mealBtn.setText("END MEAL BREAK");
				mealBtn.setStyle(
						"-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12 24; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.3), 10, 0, 0, 4); -fx-cursor: hand;");
			} else {
				mealBtn.setText("MEAL BREAK");
				mealBtn.setStyle(
						"-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12 24; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(245, 158, 11, 0.3), 10, 0, 0, 4); -fx-cursor: hand;");
			}
		} else {
			clockInBtn.setDisable(false);
			clockOutBtn.setDisable(true);
			mealBtn.setDisable(true);
			mealBtn.setText("MEAL BREAK");
		}
	}

	/**
	 * Handle Clock In button click
	 */
	private void handleClockIn() {
		LocalDateTime now = LocalDateTime.now();
		clockInTime = now.toLocalTime();
		isClockedIn = true;
		totalMealBreakMinutes = 0;
		String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
		String date = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));

		// Create new time entry
		TimeEntry entry = new TimeEntry(date, "Clock In", time, "-");
		timeEntries.add(0, entry); // Add to top of table

		updateButtonStates();
		showAlert("Success", "Clocked in at " + time, Alert.AlertType.INFORMATION);

		DataManager.saveTimeEntry(entry);
	}

	/**
	 * Handle Clock Out button click
	 */
	private void handleClockOut() {
		if (!isClockedIn) {
			showAlert("Error", "You must clock in first!", Alert.AlertType.ERROR);
			return;
		}

		// Check if still on meal break
		if (isOnMealBreak) {
			Alert alert = new Alert(Alert.AlertType.WARNING);
			alert.setTitle("Warning");
			alert.setHeaderText("You are still on meal break!");
			alert.setContentText("Please end your meal break before clocking out.");
			alert.showAndWait();
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		LocalTime clockOutTime = now.toLocalTime();

		// Calculate total work duration
		long totalMinutes = ChronoUnit.MINUTES.between(clockInTime, clockOutTime);
		long workMinutes = totalMinutes - totalMealBreakMinutes;
		long hours = workMinutes / 60;
		long remainingMinutes = workMinutes % 60;
		String duration = String.format("%dh %dm", hours, remainingMinutes);
		todayTotalWorkMinutes += workMinutes;
		String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
		String date = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));

		// Create new time entry
		TimeEntry entry = new TimeEntry(date, "Clock Out", time, duration);
		timeEntries.add(0, entry);

		// Reset all status
		clockInTime = null;
		isClockedIn = false;
		mealBreakStartTime = null;
		isOnMealBreak = false;
		
		updateButtonStates();
		updateTodayHoursLabel();
		showAlert("Success", "Clocked out at " + time + "\nTotal work time: " + duration, Alert.AlertType.INFORMATION);

		// TODO: Save to database and calculate pay
		DataManager.saveTimeEntry(entry);
	}

	/**
	 * Handle Meal Break button click
	 */
	private void handleMealBreak() {
		if (!isClockedIn) {
			showAlert("Error", "You must clock in first!", Alert.AlertType.ERROR);
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
		String date = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));

		if (!isOnMealBreak) {
			// START MEAL BREAK
			mealBreakStartTime = now.toLocalTime();
			isOnMealBreak = true;

			// Create meal break START entry
			TimeEntry entry = new TimeEntry(date, "Meal Break Start", time, "-");
			timeEntries.add(0, entry);
			
			DataManager.saveTimeEntry(entry);
			
			updateButtonStates();
			showAlert("Meal Break Started", "Break started at " + time + "\nClick 'END MEAL BREAK' when done",
					Alert.AlertType.INFORMATION);

		} else {
			// END MEAL BREAK
			LocalTime breakEndTime = now.toLocalTime();

			// Calculate break duration
			long minutes = ChronoUnit.MINUTES.between(mealBreakStartTime, breakEndTime);
			long hours = minutes / 60;
			long remainingMinutes = minutes % 60;
			String duration = String.format("%dh %dm", hours, remainingMinutes);
			totalMealBreakMinutes += minutes;
			// Create meal break END entry
			TimeEntry entry = new TimeEntry(date, "Meal Break End", time, duration);
			timeEntries.add(0, entry);

			// Reset meal break status
			mealBreakStartTime = null;
			isOnMealBreak = false;
			updateButtonStates();

			showAlert("Meal Break Ended", "Break ended at " + time + "\nBreak duration: " + duration,
					Alert.AlertType.INFORMATION);
			DataManager.saveTimeEntry(entry);
		}

		// TODO: Save to database
		
	}
	
	/**
	 * Handle Logout button click
	 */
	private void handleLogout() {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Logout");
		alert.setHeaderText("Are you sure you want to logout?");

		if (isClockedIn) {
			alert.setContentText("Warning: You are still clocked in!\nMake sure to clock out first.");
		} else {
			alert.setContentText("You will exit the application.");
		}

		alert.showAndWait().ifPresent(response -> {
			if (response == ButtonType.OK) {
				shutdown();
				System.exit(0);
			}
		});
	}

	/**
	 * Handle View All button click
	 */
		private void handleViewAll() {
		ObservableList<TimeEntry> allEntries = DataManager.loadAllEntries();	
		// Create alert
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("All Time Entries");
		alert.setHeaderText("Complete Time Entry History");

		// Create table for the alert
		TableView<TimeEntry> Table = new TableView<>();
		Table.setPrefHeight(400);
		Table.setPrefWidth(650);

		// Create columns
		TableColumn<TimeEntry, String> dateCol = new TableColumn<>("DATE");
		dateCol.setPrefWidth(150);
		dateCol.setCellValueFactory(cellData -> cellData.getValue().dateProperty());

		TableColumn<TimeEntry, String> actionCol = new TableColumn<>("ACTION");
		actionCol.setPrefWidth(200);
		actionCol.setCellValueFactory(cellData -> cellData.getValue().actionProperty());

		TableColumn<TimeEntry, String> timeCol = new TableColumn<>("TIME");
		timeCol.setPrefWidth(150);
		timeCol.setCellValueFactory(cellData -> cellData.getValue().timeProperty());

		TableColumn<TimeEntry, String> durationCol = new TableColumn<>("DURATION");
		durationCol.setPrefWidth(150);
		durationCol.setCellValueFactory(cellData -> cellData.getValue().durationProperty());

		// Add columns to table
		Table.getColumns().addAll(dateCol, actionCol, timeCol, durationCol);

		// Add all entries to the table
		Table.setItems(allEntries);

		// Create VBox container
		VBox content = new VBox(10);
		content.setPadding(new javafx.geometry.Insets(10));

		// Add info label
		Label infoLabel = new Label("Total Entries: " + timeEntries.size());
		infoLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

		content.getChildren().addAll(infoLabel, Table);

		// Set the table as expandable content
		alert.getDialogPane().setExpandableContent(content);
		alert.getDialogPane().setExpanded(true); // Start expanded

		// Show alert
		alert.showAndWait();
	}

	/**
	 * Show alert dialog
	 */
	private void showAlert(String title, String message, Alert.AlertType type) {
		Alert alert = new Alert(type);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	/**
	 * Cleanup when application closes
	 */
	public void shutdown() {
		if (clockTimeline != null) {
			clockTimeline.stop();
		}
		System.out.println("Application shut down");
	}
}