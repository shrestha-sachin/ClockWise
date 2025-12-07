package com.timeclock;

import java.util.List;

public class Manager extends SalariedEmployee {
    public Manager(int id, String name, Role role, Integer managerId, double monthlySalary) {
        super(id, name, role, managerId, monthlySalary);
    }

    // Convenience method to get this manager's team
    public List<Employee> getTeam() {
        return DataManager.loadEmployeesReportingTo(getId());
    }

    // Recursion example
    public String getOrgChart() {
        return DataManager.getOrgChart(getId());
    }
}