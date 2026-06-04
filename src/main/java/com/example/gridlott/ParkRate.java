package com.example.gridlott;

import java.time.Duration;

public class ParkRate {
    private final double fee;
    private final double perSeconds;

    //this class is used for calculating fees based on inputs
    public ParkRate(double fee, double perSeconds) {
        if (fee < 0) {
            throw new IllegalArgumentException("Fee cannot be negative!");
        }
        if (perSeconds <= 0) {
            throw new IllegalArgumentException("Time step must be > 0");
        }
        this.fee = fee;
        this.perSeconds = perSeconds;
    }

    public double processFeeRate(Vehicle vehicle) {
        // (fee * (55 + 5)) / perSeconds
        double total = (fee * (vehicle.getTotalDuration() + vehicle.getTotalTraversalTime())) / perSeconds;
        return Math.round(total * 100) / 100.0;
    }
}
