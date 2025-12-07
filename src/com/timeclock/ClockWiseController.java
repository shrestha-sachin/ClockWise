package com.timeclock;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class ClockWiseController {

    @FXML private Label timeLabel;
    @FXML private Label dateLabel;
    @FXML private Label todayHoursLabel;
    @FXML private Label userNameLabel; // New Label
    @FXML private Button clockInBtn;
    @FXML private Button clockOutBtn;
    @FXML private Button mealBtn;
    @FXML private TableView<TimeEntry> timeTable;
    @FXML private TableColumn<TimeEntry, String> colDate;
    @FXML private TableColumn<TimeEntry, String> colAction;
    @FXML private TableColumn<TimeEntry, String> colTime;
    @FXML private TableColumn<TimeEntry, String> colDuration;

    private ObservableList<TimeEntry> timeEntries = FXCollections.observableArrayList();
    private Timeline clockTimeline;
    private LocalTime clockInTime = null;         
    private long totalMealBreakMinutes = 0;
    private LocalTime mealBreakStartTime = null;
    private String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    private long todayTotalWorkMinutes = 0;
    private boolean isClockedIn = false;
    private boolean isOnMealBreak = false;
    private int currentUserId = -1;

    @FXML
    public void initialize() {
        User currentUser = UserSession.getInstance().getUser();
        if (currentUser != null) this.currentUserId = currentUser.getId();

        startClock();
        setupTableColumns();
        loadUserEntries();
        setupButtonActions();
        updateButtonStates();
        updateTodayHoursLabel();
    }

    public void setWelcomeName(String name) {
        if (userNameLabel != null) userNameLabel.setText("Hello, " + name);
    }

    private void loadUserEntries() {
        if (currentUserId == -1) return;
        ObservableList<TimeEntry> loaded = DataManager.loadTimeEntriesForUser(currentUserId);
        if (loaded != null) {
            timeEntries = loaded;
            if (timeTable != null) timeTable.setItems(timeEntries);
            calculateTodayMinutesLocally(loaded);
        }
    }

    private void calculateTodayMinutesLocally(ObservableList<TimeEntry> entries) {
        long minutes = 0;
        String todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        for (TimeEntry entry : entries) {
            if (entry.getDate().equals(todayStr) && 
               (entry.getAction().equals("Clock Out") || entry.getAction().equals("Meal Break End"))) {
                minutes += parseDurationToMinutes(entry.getDuration());
            }
        }
        todayTotalWorkMinutes = minutes;
    }

    private long parseDurationToMinutes(String duration) {
        if (duration == null || duration.equals("-")) return 0;
        long m = 0;
        try {
            if (duration.contains("h")) {
                String[] parts = duration.split("h");
                m += Long.parseLong(parts[0].trim()) * 60;
                if (parts.length > 1 && parts[1].contains("m")) {
                    m += Long.parseLong(parts[1].replace("m","").trim());
                }
            } else if (duration.contains("m")) {
                m += Long.parseLong(duration.replace("m","").trim());
            }
        } catch(Exception e){}
        return m;
    }

    private void startClock() {
        if (clockTimeline != null) clockTimeline.stop();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateDateTime()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
        updateDateTime(); 
    }

    private void updateDateTime() {
        LocalDateTime now = LocalDateTime.now();
        if (timeLabel != null) timeLabel.setText(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
        if (dateLabel != null) dateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        
        if (isClockedIn && !isOnMealBreak) updateTodayHoursLabel();
    }

    private void updateTodayHoursLabel() {
        long totalMinutes = todayTotalWorkMinutes;
        if (clockInTime != null && isClockedIn) {
            LocalTime now = LocalTime.now();
            long diff = ChronoUnit.MINUTES.between(clockInTime, now);
            totalMinutes += Math.max(0, diff - totalMealBreakMinutes);
        }
        if (todayHoursLabel != null) {
            todayHoursLabel.setText(String.format("Today: %dh %dm", totalMinutes / 60, totalMinutes % 60));
        }
    }

    private void setupTableColumns() {
        if (timeTable == null) return;
        colDate.setCellValueFactory(cd -> cd.getValue().dateProperty());
        colAction.setCellValueFactory(cd -> cd.getValue().actionProperty());
        colTime.setCellValueFactory(cd -> cd.getValue().timeProperty());
        colDuration.setCellValueFactory(cd -> cd.getValue().durationProperty());
        timeTable.setItems(timeEntries);
    }

    private void setupButtonActions() {
        if (clockInBtn != null) clockInBtn.setOnAction(e -> handleClockIn());
        if (clockOutBtn != null) clockOutBtn.setOnAction(e -> handleClockOut());
        if (mealBtn != null) mealBtn.setOnAction(e -> handleMealBreak());
    }

    private void updateButtonStates() {
        if (clockInBtn == null) return;
        clockInBtn.setDisable(isClockedIn);
        clockOutBtn.setDisable(!isClockedIn);
        mealBtn.setDisable(!isClockedIn);
        if (isOnMealBreak) {
            mealBtn.setText("⏹ END BREAK");
            mealBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 20; -fx-background-radius: 8; -fx-cursor: hand;");
        } else {
            mealBtn.setText("☕ MEAL BREAK");
            mealBtn.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 20; -fx-background-radius: 8; -fx-cursor: hand;");
        }
    }

    private void handleClockIn() {
        LocalDateTime now = LocalDateTime.now();
        clockInTime = now.toLocalTime();
        isClockedIn = true;
        totalMealBreakMinutes = 0;
        
        String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
        String date = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        
        TimeEntry entry = new TimeEntry(currentUserId, date, "Clock In", time, "-");
        timeEntries.add(0, entry);
        DataManager.saveTimeEntry(entry);
        
        updateButtonStates();
        updateTodayHoursLabel();
    }

    private void handleClockOut() {
        if (!isClockedIn || isOnMealBreak) return;
        LocalDateTime now = LocalDateTime.now();
        
        long workMinutes = ChronoUnit.MINUTES.between(clockInTime, now.toLocalTime()) - totalMealBreakMinutes;
        if (workMinutes < 0) workMinutes = 0;
        
        String duration = String.format("%dh %dm", workMinutes/60, workMinutes%60);
        String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
        String date = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        
        TimeEntry entry = new TimeEntry(currentUserId, date, "Clock Out", time, duration);
        timeEntries.add(0, entry);
        DataManager.saveTimeEntry(entry);
        
        todayTotalWorkMinutes += workMinutes;
        isClockedIn = false;
        clockInTime = null;
        updateButtonStates();
    }

    private void handleMealBreak() {
        if (!isClockedIn) return;
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
        String date = now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        
        if (!isOnMealBreak) {
            mealBreakStartTime = now.toLocalTime();
            isOnMealBreak = true;
            TimeEntry entry = new TimeEntry(currentUserId, date, "Meal Break Start", time, "-");
            timeEntries.add(0, entry);
            DataManager.saveTimeEntry(entry);
        } else {
            long mins = ChronoUnit.MINUTES.between(mealBreakStartTime, now.toLocalTime());
            totalMealBreakMinutes += mins;
            String dur = String.format("%dh %dm", mins/60, mins%60);
            TimeEntry entry = new TimeEntry(currentUserId, date, "Meal Break End", time, dur);
            timeEntries.add(0, entry);
            DataManager.saveTimeEntry(entry);
            isOnMealBreak = false;
        }
        updateButtonStates();
    }
}