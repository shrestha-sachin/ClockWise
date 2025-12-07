package com.timeclock;

public abstract class Employee {
    private int id;
    private String name;
    private Role role;
    private Integer managerId;

    public Employee(int id, String name, Role role, Integer managerId) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.managerId = managerId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public Integer getManagerId() { return managerId; }

    public abstract double getMonthlyCost();
}