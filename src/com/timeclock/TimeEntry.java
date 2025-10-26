package com.timeclock;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TimeEntry {
    private final StringProperty date;
    private final StringProperty action;
    private final StringProperty time;
    private final StringProperty duration;

    public TimeEntry(String date, String action, String time, String duration) {
        this.date = new SimpleStringProperty(date);
        this.action = new SimpleStringProperty(action);
        this.time = new SimpleStringProperty(time);
        this.duration = new SimpleStringProperty(duration);
    }

    // Property getters (required for TableView binding)
    public StringProperty dateProperty() {
        return date;
    }

    public StringProperty actionProperty() {
        return action;
    }

    public StringProperty timeProperty() {
        return time;
    }

    public StringProperty durationProperty() {
        return duration;
    }

    // Regular getters and setters
    public String getDate() {
        return date.get();
    }

    public void setDate(String date) {
        this.date.set(date);
    }

    public String getAction() {
        return action.get();
    }

    public void setAction(String action) {
        this.action.set(action);
    }

    public String getTime() {
        return time.get();
    }

    public void setTime(String time) {
        this.time.set(time);
    }

    public String getDuration() {
        return duration.get();
    }

    public void setDuration(String duration) {
        this.duration.set(duration);
    }
}