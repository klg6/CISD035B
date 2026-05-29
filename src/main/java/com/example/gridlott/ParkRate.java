package com.example.gridlott;

public class ParkRate {
    private final double fee;
    private final int perSeconds;

    public ParkRate(double fee, int perSeconds) {
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
        double total = (fee * vehicle.getTotalDuration()) / perSeconds;
        return Math.round(total * 100) / 100.0;
    }
}
