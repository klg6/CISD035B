package com.example.gridlott;

public class ParkRate {//this class takes care of fee rate for vehicles and for easier configuration
    private double fee;
    private int perSeconds;

    public ParkRate(double fee, int perSeconds){
        this.fee = fee;
        this.perSeconds = perSeconds;
    }

    public double processFeeRate(Vehicle vehicle){//determines the rate fee for vehicles
        double total = (fee * vehicle.getTotalDuration()) / perSeconds;
        return Math.round(total * 100) / 100.0;
    }
}
