package com.example.gridlott;

import java.util.Random;

//this class is for generating random durations / checking inputs and used for determining a vehicle's staying duration
public class RandomizeDuration {
    private final double minDuration;
    private final double maxDuration;
    private final Random random = new Random();

    public RandomizeDuration(double minDuration, double maxDuration) {
        if (minDuration <= 0 || maxDuration <= 0) {
            throw new IllegalArgumentException("Values must be > 0");
        }
        if (minDuration > maxDuration) {
            throw new IllegalArgumentException("Min cannot exceed Max");
        }
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    public double getRandomizeDuration() {
        return minDuration + (random.nextDouble() * (maxDuration - minDuration));
    }
}
