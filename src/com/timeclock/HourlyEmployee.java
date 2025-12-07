package com.timeclock;

public class HourlyEmployee extends Employee {
    private double hourlyRate;

    public HourlyEmployee(int id, String name, Role role, Integer managerId, double hourlyRate) {
        super(id, name, role, managerId);
        this.hourlyRate = hourlyRate;
    }

    public double getHourlyRate() { return hourlyRate; }

    @Override
    public double getMonthlyCost() {
        return hourlyRate * 160;
    }
}