package com.timeclock;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TimeEntry {

    private int id;
    private int userId; 
    private final StringProperty date;
    private final StringProperty action;
    private final StringProperty time;
    private final StringProperty duration;

    // Constructor for creating NEW entries (no DB ID yet)
    public TimeEntry(int userId, String date, String action, String time, String duration) {
        this(-1, userId, date, action, time, duration);
    }

    // Constructor for loading entries FROM DB
    public TimeEntry(int id, int userId, String date, String action, String time, String duration) {
        this.id = id;
        this.userId = userId;
        this.date = new SimpleStringProperty(date);
        this.action = new SimpleStringProperty(action);
        this.time = new SimpleStringProperty(time);
        this.duration = new SimpleStringProperty(duration == null ? "-" : duration);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    // --- Property Accessors (Required for JavaFX TableView) ---
    public StringProperty dateProperty() { return date; }
    public StringProperty actionProperty() { return action; }
    public StringProperty timeProperty() { return time; }
    public StringProperty durationProperty() { return duration; }

    // --- Standard Getters ---
    public String getDate() { return date.get(); }
    public String getAction() { return action.get(); }
    public String getTime() { return time.get(); }
    public String getDuration() { return duration.get(); }

    // --- Setters ---
    public void setDate(String v) { date.set(v); }
    public void setAction(String v) { action.set(v); }
    public void setTime(String v) { time.set(v); }
    public void setDuration(String v) { duration.set(v); }
}