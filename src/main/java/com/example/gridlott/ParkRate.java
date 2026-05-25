package com.example.gridlott;

public class ParkRate {//this class takes care of fee rate for vehicles and for easier configuration
    private double fee;
    private int perSeconds;

    public ParkRate(double fee, int perSeconds){

        if (fee < 0) { //can be zero as well since there's such as free parking
            throw new IllegalArgumentException("Fee cannot be negative!");
        }

        if (perSeconds <= 0) { //perSeconds must be greater than 0 to avoid division by zero
            throw new IllegalArgumentException("perSeconds must be greater than zero!");
        }

        this.fee = fee;
        this.perSeconds = perSeconds;
    }

    public double processFeeRate(Vehicle vehicle){//determines the rate fee for vehicles
        double total = (fee * vehicle.getTotalDuration()) / perSeconds;
        return Math.round(total * 100) / 100.0;
    }
}
