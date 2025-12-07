package com.timeclock;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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
                    FOREIGN KEY(employee_id) REFERENCES Employees(id)
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
                    FOREIGN KEY(user_id) REFERENCES Users(id)
                )
            """);

            if (countRows("Users") == 0) {
                User admin = new User("admin", hashPassword("admin123"), Role.ADMIN, null);
                saveUser(admin);
                System.out.println("Default admin created: admin / admin123");
            }

        } catch (SQLException e) {
            System.err.println("DB Init Error: " + e.getMessage());
        }
    }

    // --- Helpers ---
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

    public static User getUserByEmployeeId(int empId) {
        String sql = "SELECT * FROM Users WHERE employee_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    Role.valueOf(rs.getString("role")),
                    rs.getInt("employee_id")
                );
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // --- Updates ---

    public static boolean updateEmployee(Employee emp) {
        String sql = "UPDATE Employees SET name=?, role=?, hourly_rate=?, monthly_salary=?, manager_id=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emp.getName());
            ps.setString(2, emp.getRole().name());
            
            if (emp instanceof HourlyEmployee) {
                ps.setDouble(3, ((HourlyEmployee) emp).getHourlyRate());
                ps.setNull(4, Types.REAL);
            } else {
                ps.setNull(3, Types.REAL);
                ps.setDouble(4, ((SalariedEmployee) emp).getMonthlySalary());
            }
            
            if (emp.getManagerId() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, emp.getManagerId());
            
            ps.setInt(6, emp.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public static boolean updateUser(User user) {
        // Updates username, role, and password (if changed)
        String sql = "UPDATE Users SET username=?, role=?, password_hash=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getRole().name());
            ps.setString(3, user.getPasswordHash());
            ps.setInt(4, user.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // --- Time Entries ---

    public static void saveTimeEntry(TimeEntry entry) {
        writeExecutor.submit(() -> {
            String sql = "INSERT INTO TimeEntries(user_id, date, action, time, duration) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (entry.getUserId() <= 0) ps.setNull(1, Types.INTEGER);
                else ps.setInt(1, entry.getUserId());
                ps.setString(2, entry.getDate());
                ps.setString(3, entry.getAction());
                ps.setString(4, entry.getTime());
                ps.setString(5, entry.getDuration());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public static void updateTimeEntry(TimeEntry entry) {
        writeExecutor.submit(() -> {
            String sql = "UPDATE TimeEntries SET date=?, action=?, time=?, duration=? WHERE id=?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entry.getDate());
                ps.setString(2, entry.getAction());
                ps.setString(3, entry.getTime());
                ps.setString(4, entry.getDuration());
                ps.setInt(5, entry.getId());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public static ObservableList<TimeEntry> loadTimeEntriesForUser(int userId) {
        return loadEntries("SELECT id, user_id, date, action, time, duration FROM TimeEntries WHERE user_id = " + userId + " ORDER BY id DESC");
    }

    public static ObservableList<TimeEntry> loadAllEntries() {
        return loadEntries("SELECT id, user_id, date, action, time, duration FROM TimeEntries ORDER BY id DESC");
    }

    private static ObservableList<TimeEntry> loadEntries(String sql) {
        ObservableList<TimeEntry> list = FXCollections.observableArrayList();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new TimeEntry(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("date"),
                        rs.getString("action"),
                        rs.getString("time"),
                        rs.getString("duration")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // --- Users & Pay ---

    public static User authenticate(String username, String password) {
        String sql = "SELECT * FROM Users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (storedHash.equals(hashPassword(password))) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            storedHash,
                            Role.valueOf(rs.getString("role")),
                            rs.getObject("employee_id") == null ? null : rs.getInt("employee_id")
                    );
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static double getEmployeeHourlyRate(int userId) {
        String sql = "SELECT e.hourly_rate FROM Employees e JOIN Users u ON u.employee_id = e.id WHERE u.id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("hourly_rate");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    public static int saveUser(User user) {
        String sql = "INSERT INTO Users(username, password_hash, role, employee_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole().name());
            if (user.getEmployeeId() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, user.getEmployeeId());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if(keys.next()) return keys.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    // --- Employee CRUD ---

    public static List<Employee> loadAllEmployees() {
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT id, name, role, hourly_rate, monthly_salary, manager_id FROM Employees ORDER BY name";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                Role role = Role.valueOf(rs.getString("role"));
                Integer managerId = rs.getObject("manager_id") == null ? null : rs.getInt("manager_id");
                double hourly = rs.getDouble("hourly_rate");
                double salary = rs.getDouble("monthly_salary");

                if (hourly > 0) {
                    list.add(new HourlyEmployee(id, name, role, managerId, hourly));
                } else {
                    list.add(new SalariedEmployee(id, name, role, managerId, salary));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static int createEmployee(Employee emp) {
        String sql = "INSERT INTO Employees(name, role, hourly_rate, monthly_salary, manager_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, emp.getName());
            ps.setString(2, emp.getRole().name());
            if (emp instanceof HourlyEmployee) {
                ps.setDouble(3, ((HourlyEmployee) emp).getHourlyRate());
                ps.setNull(4, Types.REAL);
            } else {
                ps.setNull(3, Types.REAL);
                ps.setDouble(4, ((SalariedEmployee) emp).getMonthlySalary());
            }
            if (emp.getManagerId() == null) ps.setNull(5, Types.INTEGER); 
            else ps.setInt(5, emp.getManagerId());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    // --- Utils ---
    private static String hashPassword(String pass) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pass.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for(byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
    public static String hashPasswordForPublicUse(String p) { return hashPassword(p); }

    private static long countRows(String table) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    // Fixed methods for Manager.java
    public static List<Employee> loadEmployeesReportingTo(int managerId) {
        List<Employee> out = new ArrayList<>();
        String sql = "SELECT id, name, role, hourly_rate, monthly_salary, manager_id FROM Employees WHERE manager_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, managerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                Role role = Role.valueOf(rs.getString("role"));
                Integer mid = rs.getObject("manager_id") == null ? null : rs.getInt("manager_id");
                double hourly = rs.getDouble("hourly_rate");
                double salary = rs.getDouble("monthly_salary");

                if (hourly > 0) {
                    out.add(new HourlyEmployee(id, name, role, mid, hourly));
                } else {
                    out.add(new SalariedEmployee(id, name, role, mid, salary));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public static String getOrgChart(int rootManagerId) {
        StringBuilder sb = new StringBuilder();
        buildOrgChartRecursively(rootManagerId, 0, sb);
        return sb.toString();
    }

    private static void buildOrgChartRecursively(int managerId, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        String sql = "SELECT id, name, role FROM Employees WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, managerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                sb.append(String.format("%s (%s)%n", rs.getString("name"), rs.getString("role")));
                List<Employee> reports = loadEmployeesReportingTo(managerId);
                for (Employee e : reports) {
                    buildOrgChartRecursively(e.getId(), depth + 1, sb);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void shutdown() { 
        writeExecutor.shutdown(); 
    }
}