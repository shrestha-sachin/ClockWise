package com.timeclock;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public class DataManager {

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + "/clockwise.db";
    private static final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    static {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) { e.printStackTrace(); }
    }

    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    hourly_rate REAL,
                    monthly_salary REAL,
                    manager_id INTEGER
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    employee_id INTEGER,
                    FOREIGN KEY(employee_id) REFERENCES Employees(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS TimeEntries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    date TEXT NOT NULL,
                    action TEXT NOT NULL,
                    time TEXT NOT NULL,
                    duration TEXT,
                    FOREIGN KEY(user_id) REFERENCES Users(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS PayrollPeriods (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    start_date TEXT NOT NULL,
                    end_date TEXT NOT NULL,
                    is_active INTEGER NOT NULL
                )
            """);

            // NEW: Company Settings Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS CompanySettings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    name TEXT NOT NULL,
                    location TEXT NOT NULL
                )
            """);

            if (countRows("Users") == 0) {
                User admin = new User("admin", hashPassword("admin123"), Role.ADMIN, null);
                saveUser(admin);
                System.out.println("Default admin created");
            }

            if (countRows("PayrollPeriods") == 0) {
                LocalDate start = LocalDate.now();
                LocalDate end = start.plusDays(13); 
                createPayrollPeriod(start.toString(), end.toString(), true);
            }

        } catch (SQLException e) {
            System.err.println("DB Init Error: " + e.getMessage());
        }
    }

    // ==========================================
    //           COMPANY SETTINGS (NEW)
    // ==========================================

    public static boolean hasCompanyInfo() {
        return countRows("CompanySettings") > 0;
    }

    public static void saveCompanyInfo(String name, String location) {
        String sql = "INSERT OR REPLACE INTO CompanySettings (id, name, location) VALUES (1, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, location);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static Company getCompany() {
        String sql = "SELECT name, location FROM CompanySettings WHERE id = 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new Company(rs.getString("name"), rs.getString("location"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // ==========================================
    //           PAYROLL MANAGEMENT
    // ==========================================

    public static List<PayrollPeriod> getAllPayrollPeriods() {
        List<PayrollPeriod> list = new ArrayList<>();
        String sql = "SELECT * FROM PayrollPeriods ORDER BY id DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PayrollPeriod(
                    rs.getInt("id"),
                    rs.getString("start_date"),
                    rs.getString("end_date"),
                    rs.getInt("is_active") == 1
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static PayrollPeriod getCurrentPayrollPeriod() {
        String sql = "SELECT * FROM PayrollPeriods WHERE is_active = 1 LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new PayrollPeriod(
                    rs.getInt("id"),
                    rs.getString("start_date"),
                    rs.getString("end_date"),
                    rs.getInt("is_active") == 1
                );
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public static void closeCurrentPayrollAndStartNew() {
        PayrollPeriod current = getCurrentPayrollPeriod();
        if (current == null) return;

        LocalDate oldEnd = LocalDate.parse(current.getEndDate());
        LocalDate newStart = oldEnd.plusDays(1);
        LocalDate newEnd = newStart.plusDays(13); // Bi-weekly

        writeExecutor.submit(() -> {
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                conn.setAutoCommit(false);
                try (PreparedStatement psClose = conn.prepareStatement("UPDATE PayrollPeriods SET is_active = 0 WHERE id = ?")) {
                    psClose.setInt(1, current.getId());
                    psClose.executeUpdate();
                }
                try (PreparedStatement psNew = conn.prepareStatement("INSERT INTO PayrollPeriods (start_date, end_date, is_active) VALUES (?, ?, 1)")) {
                    psNew.setString(1, newStart.toString());
                    psNew.setString(2, newEnd.toString());
                    psNew.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private static void createPayrollPeriod(String start, String end, boolean active) {
        String sql = "INSERT INTO PayrollPeriods (start_date, end_date, is_active) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start);
            ps.setString(2, end);
            ps.setInt(3, active ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ==========================================
    //            CORE HELPERS
    // ==========================================

    public static String getEmployeeName(int userId) {
        String sql = "SELECT e.name FROM Employees e JOIN Users u ON u.employee_id = e.id WHERE u.id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) { e.printStackTrace(); }
        return null; 
    }

    public static Employee getEmployeeByUserId(int userId) {
        String sql = "SELECT e.* FROM Employees e JOIN Users u ON u.employee_id = e.id WHERE u.id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                Role role = Role.valueOf(rs.getString("role"));
                Integer mid = rs.getObject("manager_id") == null ? null : rs.getInt("manager_id");
                double hourly = rs.getDouble("hourly_rate");
                double salary = rs.getDouble("monthly_salary");
                if (hourly > 0) return new HourlyEmployee(id, name, role, mid, hourly);
                else return new SalariedEmployee(id, name, role, mid, salary);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public static User getUserByEmployeeId(int empId) {
        String sql = "SELECT * FROM Users WHERE employee_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, empId); ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"), Role.valueOf(rs.getString("role")), rs.getInt("employee_id"));
        } catch (SQLException e) { e.printStackTrace(); } return null;
    }

    public static String getLatestEntryAction(int userId) {
        String sql = "SELECT action FROM TimeEntries WHERE user_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ResultSet rs = ps.executeQuery(); if (rs.next()) return rs.getString("action");
        } catch (SQLException e) { e.printStackTrace(); } return "Unknown";
    }

    // ==========================================
    //              TIME ENTRIES
    // ==========================================

    public static void saveTimeEntry(TimeEntry entry) {
        String sql = "INSERT INTO TimeEntries(user_id, date, action, time, duration) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (entry.getUserId() <= 0) ps.setNull(1, Types.INTEGER); else ps.setInt(1, entry.getUserId());
            ps.setString(2, entry.getDate()); ps.setString(3, entry.getAction()); ps.setString(4, entry.getTime()); ps.setString(5, entry.getDuration()); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void updateTimeEntry(TimeEntry entry) {
        String sql = "UPDATE TimeEntries SET date=?, action=?, time=?, duration=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getDate()); ps.setString(2, entry.getAction()); ps.setString(3, entry.getTime()); ps.setString(4, entry.getDuration()); ps.setInt(5, entry.getId()); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
        recalculateDailyDurations(entry.getUserId(), entry.getDate());
    }

    public static void recalculateDailyDurations(int userId, String date) {
        List<TimeEntry> dailyEntries = new ArrayList<>(); String sql = "SELECT * FROM TimeEntries WHERE user_id=? AND date=?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setString(2, date); ResultSet rs = ps.executeQuery();
            while(rs.next()) dailyEntries.add(new TimeEntry(rs.getInt("id"), rs.getInt("user_id"), rs.getString("date"), rs.getString("action"), rs.getString("time"), rs.getString("duration")));
        } catch (SQLException e) { e.printStackTrace(); return; }
        if (dailyEntries.isEmpty()) return;
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);
        dailyEntries.sort(Comparator.comparing(e -> { try { return LocalTime.parse(e.getTime(), timeFmt); } catch (Exception ex) { return LocalTime.MIN; } }));
        LocalTime lastClockIn = null; LocalTime lastMealStart = null;
        for (TimeEntry entry : dailyEntries) {
            String action = entry.getAction(); LocalTime currentTime;
            try { currentTime = LocalTime.parse(entry.getTime(), timeFmt); } catch(Exception e) { continue; }
            String newDuration = "-";
            if ("Clock In".equals(action)) lastClockIn = currentTime;
            else if ("Clock Out".equals(action)) { if (lastClockIn != null) { long minutes = ChronoUnit.MINUTES.between(lastClockIn, currentTime); if (minutes < 0) minutes = 0; newDuration = String.format("%dh %dm", minutes/60, minutes%60); lastClockIn = null; } }
            else if ("Meal Break Start".equals(action)) lastMealStart = currentTime;
            else if ("Meal Break End".equals(action)) { if (lastMealStart != null) { long minutes = ChronoUnit.MINUTES.between(lastMealStart, currentTime); if (minutes < 0) minutes = 0; newDuration = String.format("%dh %dm", minutes/60, minutes%60); lastMealStart = null; } }
            if (!Objects.equals(newDuration, entry.getDuration())) updateEntryDurationInDB(entry.getId(), newDuration);
        }
    }

    private static void updateEntryDurationInDB(int id, String duration) {
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement("UPDATE TimeEntries SET duration=? WHERE id=?")) {
            ps.setString(1, duration); ps.setInt(2, id); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static ObservableList<TimeEntry> loadTimeEntries(int userId, LocalDate start, LocalDate end) { return filterEntries("SELECT * FROM TimeEntries WHERE user_id = ? ORDER BY id DESC", userId, start, end); }
    public static ObservableList<TimeEntry> loadAllEntries(LocalDate start, LocalDate end) { return filterEntries("SELECT * FROM TimeEntries ORDER BY id DESC", -1, start, end); }
    private static ObservableList<TimeEntry> filterEntries(String sql, int userId, LocalDate start, LocalDate end) {
        ObservableList<TimeEntry> list = FXCollections.observableArrayList(); DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != -1) ps.setInt(1, userId); ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String dateStr = rs.getString("date");
                try { LocalDate entryDate = LocalDate.parse(dateStr, dateFmt); if (start != null && entryDate.isBefore(start)) continue; if (end != null && entryDate.isAfter(end)) continue;
                    list.add(new TimeEntry(rs.getInt("id"), rs.getInt("user_id"), dateStr, rs.getString("action"), rs.getString("time"), rs.getString("duration")));
                } catch (Exception e) { list.add(new TimeEntry(rs.getInt("id"), rs.getInt("user_id"), dateStr, rs.getString("action"), rs.getString("time"), rs.getString("duration"))); }
            }
        } catch (SQLException e) { e.printStackTrace(); } return list;
    }

    // --- Updates ---
    public static boolean updateEmployee(Employee emp) {
        String sql = "UPDATE Employees SET name=?, role=?, hourly_rate=?, monthly_salary=?, manager_id=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emp.getName()); ps.setString(2, emp.getRole().name());
            if (emp instanceof HourlyEmployee) { ps.setDouble(3, ((HourlyEmployee) emp).getHourlyRate()); ps.setNull(4, Types.REAL); } else { ps.setNull(3, Types.REAL); ps.setDouble(4, ((SalariedEmployee) emp).getMonthlySalary()); }
            if (emp.getManagerId() == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, emp.getManagerId());
            ps.setInt(6, emp.getId()); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
    public static boolean updateUser(User user) {
        String sql = "UPDATE Users SET username=?, role=?, password_hash=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername()); ps.setString(2, user.getRole().name()); ps.setString(3, user.getPasswordHash()); ps.setInt(4, user.getId()); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // --- Standard Methods ---
    public static ObservableList<TimeEntry> loadTimeEntriesForUser(int userId) { return loadTimeEntries(userId, null, null); }
    public static ObservableList<TimeEntry> loadAllEntries() { return loadAllEntries(null, null); }
    public static List<Employee> loadAllEmployees() {
        List<Employee> list = new ArrayList<>(); String sql = "SELECT id, name, role, hourly_rate, monthly_salary, manager_id FROM Employees ORDER BY name";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id"); String name = rs.getString("name"); Role role = Role.valueOf(rs.getString("role")); Integer mid = rs.getObject("manager_id") == null ? null : rs.getInt("manager_id"); double hourly = rs.getDouble("hourly_rate"); double salary = rs.getDouble("monthly_salary");
                if (hourly > 0) list.add(new HourlyEmployee(id, name, role, mid, hourly)); else list.add(new SalariedEmployee(id, name, role, mid, salary));
            }
        } catch (SQLException e) { e.printStackTrace(); } return list;
    }
    public static int createEmployee(Employee emp) { return createEmployeeInternal(emp); }
    private static int createEmployeeInternal(Employee emp) {
        String sql = "INSERT INTO Employees(name, role, hourly_rate, monthly_salary, manager_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, emp.getName()); ps.setString(2, emp.getRole().name());
            if (emp instanceof HourlyEmployee) { ps.setDouble(3, ((HourlyEmployee) emp).getHourlyRate()); ps.setNull(4, Types.REAL); } else { ps.setNull(3, Types.REAL); ps.setDouble(4, ((SalariedEmployee) emp).getMonthlySalary()); }
            if (emp.getManagerId() == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, emp.getManagerId());
            ps.executeUpdate(); ResultSet keys = ps.getGeneratedKeys(); if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); } return -1;
    }
    public static int saveUser(User user) {
        String sql = "INSERT INTO Users(username, password_hash, role, employee_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername()); ps.setString(2, user.getPasswordHash()); ps.setString(3, user.getRole().name());
            if (user.getEmployeeId() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, user.getEmployeeId());
            ps.executeUpdate(); ResultSet keys = ps.getGeneratedKeys(); if(keys.next()) return keys.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); } return -1;
    }
    public static User authenticate(String u, String p) {
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement ps = conn.prepareStatement("SELECT * FROM Users WHERE username = ?")) {
            ps.setString(1, u); ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getString("password_hash").equals(hashPassword(p))) return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"), Role.valueOf(rs.getString("role")), rs.getObject("employee_id") == null ? null : rs.getInt("employee_id"));
        } catch (Exception e) {} return null;
    }
    public static double getEmployeeHourlyRate(int uid) {
        try(Connection c = DriverManager.getConnection(DB_URL); PreparedStatement p = c.prepareStatement("SELECT e.hourly_rate FROM Employees e JOIN Users u ON u.employee_id = e.id WHERE u.id = ?")) { p.setInt(1, uid); ResultSet r = p.executeQuery(); if(r.next()) return r.getDouble(1); } catch(Exception e){} return 0.0;
    }
    public static List<Employee> loadEmployeesReportingTo(int mid) {
        List<Employee> l = new ArrayList<>();
        try(Connection c = DriverManager.getConnection(DB_URL); PreparedStatement p = c.prepareStatement("SELECT * FROM Employees WHERE manager_id = ?")) {
            p.setInt(1, mid); ResultSet r = p.executeQuery();
            while(r.next()) { if(r.getDouble("hourly_rate") > 0) l.add(new HourlyEmployee(r.getInt("id"), r.getString("name"), Role.valueOf(r.getString("role")), mid, r.getDouble("hourly_rate"))); else l.add(new SalariedEmployee(r.getInt("id"), r.getString("name"), Role.valueOf(r.getString("role")), mid, r.getDouble("monthly_salary"))); }
        } catch(Exception e){} return l;
    }
    public static String getOrgChart(int id) { return ""; }
    private static String hashPassword(String p) { try { java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256"); byte[] h = md.digest(p.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for(byte b:h) sb.append(String.format("%02x",b)); return sb.toString(); } catch(Exception e){return null;}}
    public static String hashPasswordForPublicUse(String p) { return hashPassword(p); }
    private static long countRows(String t) { try(Connection c=DriverManager.getConnection(DB_URL)) { ResultSet r=c.createStatement().executeQuery("SELECT COUNT(*) FROM "+t); return r.next()?r.getLong(1):0;} catch(Exception e){return 0;}}
    public static void shutdown() { writeExecutor.shutdown(); }
}