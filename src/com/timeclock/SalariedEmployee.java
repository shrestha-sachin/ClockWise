package com.timeclock;

public class SalariedEmployee extends Employee {
    private double monthlySalary;

    public SalariedEmployee(int id, String name, Role role, Integer managerId, double monthlySalary) {
        super(id, name, role, managerId);
        this.monthlySalary = monthlySalary;
    }

    public double getMonthlySalary() { return monthlySalary; }

    @Override
    public double getMonthlyCost() {
        return monthlySalary;
    }
}