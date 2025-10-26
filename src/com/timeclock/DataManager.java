package com.timeclock;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;

/**
 * Handles all database operations for the ClockWise app.
 * Uses SQLite for local storage.
 */
public class DataManager {

    // Database file path (always in project root)
    private static final String DB_PATH = System.getProperty("user.dir") + "/clockwise.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    /**
     * Creates the database file and tables if they don't exist.
     */
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                Statement stmt = conn.createStatement();

                // Create TimeEntries table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS TimeEntries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        date TEXT NOT NULL,
                        action TEXT NOT NULL,
                        time TEXT NOT NULL,
                        duration TEXT
                    )
                """);

                // Create Employees table (future use)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Employees (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT,
                        role TEXT
                    )
                """);

                System.out.println("✅ Database initialized successfully!");
                System.out.println("📁 Database path: " + DB_PATH);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error initializing database: " + e.getMessage());
        }
    }

    /**
     * Saves a new time entry to the database.
     */
    public static void saveTimeEntry(TimeEntry entry) {
        String sql = "INSERT INTO TimeEntries(date, action, time, duration) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entry.getDate());
            pstmt.setString(2, entry.getAction());
            pstmt.setString(3, entry.getTime());
            pstmt.setString(4, entry.getDuration());
            pstmt.executeUpdate();

            System.out.println("💾 Saved entry: " + entry.getAction() + " @ " + entry.getTime());

        } catch (SQLException e) {
            System.err.println("❌ Error saving time entry: " + e.getMessage());
        }
    }

    /**
     * Loads all time entries from the database.
     */
    public static ObservableList<TimeEntry> loadAllEntries() {
        ObservableList<TimeEntry> entries = FXCollections.observableArrayList();
        String sql = "SELECT date, action, time, duration FROM TimeEntries ORDER BY id DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                entries.add(new TimeEntry(
                        rs.getString("date"),
                        rs.getString("action"),
                        rs.getString("time"),
                        rs.getString("duration")
                ));
            }

            System.out.println("📊 Loaded " + entries.size() + " time entries from database.");

        } catch (SQLException e) {
            System.err.println("❌ Error loading entries: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Deletes all entries (for testing/reset only).
     */
    public static void clearAllEntries() {
        String sql = "DELETE FROM TimeEntries";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("🧹 All time entries cleared.");
        } catch (SQLException e) {
            System.err.println("❌ Error clearing entries: " + e.getMessage());
        }
    }
    
    public static long getTodayTotalMinutes(String date) {
        long totalMinutes = 0;
        String sql = "SELECT duration FROM TimeEntries WHERE date = ? AND action = 'Clock Out'";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, date);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String duration = rs.getString("duration");
                totalMinutes += parseDurationToMinutes(duration);
            }
            
            System.out.println("📊 Today's total from DB: " + totalMinutes + " minutes");
            
        } catch (SQLException e) {
            System.err.println("❌ Error calculating today's total: " + e.getMessage());
        }
        
        return totalMinutes;
    }

    /**
     * Helper: Parse duration string (e.g., "8h 30m") to minutes
     */
    private static long parseDurationToMinutes(String duration) {
        if (duration == null || duration.equals("-")) {
            return 0;
        }
        
        long minutes = 0;
        
        try {
            // Extract hours
            if (duration.contains("h")) {
                int hIndex = duration.indexOf("h");
                String hoursStr = duration.substring(0, hIndex).trim();
                minutes += Long.parseLong(hoursStr) * 60;
            }
            
            // Extract minutes
            if (duration.contains("m")) {
                int hIndex = duration.indexOf("h");
                int mIndex = duration.indexOf("m");
                String minutesStr = duration.substring(hIndex + 1, mIndex).trim();
                minutes += Long.parseLong(minutesStr);
            }
        } catch (Exception e) {
            System.err.println("Error parsing duration: " + duration);
        }
        
        return minutes;
    }
}
