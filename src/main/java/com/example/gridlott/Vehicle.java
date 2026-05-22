package com.example.gridlott;

import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Random;

public class Vehicle {
    private String plate;
    private Vehicle.Model model;
    private Vehicle.Type type;
    private Circle dot;
    private LocalTime entryTime;
    private LocalTime exitTime;
    private double totalDurationParked;
    private double amountPaid;

    //INNER CLASSES=====================================================================================================
    enum Model { //vehicle models
        TOYOTA, VOLKSWAGEN, HONDA, FORD, CHEVROLET, HYUNDAI, NISSAN, KIA, TESLA,
        BMW, MERCEDES_BENZ, AUDI, PORSCHE, FERRARI, LAMBORGHINI, MAZDA, SUBARU,
        CHRYSLER, JEEP, DODGE, RAM, BUICK, CADILLAC, GMC, LAND_ROVER, JAGUAR,
        BENTLEY, ROLLS_ROYCE, VOLVO, FIAT, RENAULT, PEUGEOT, SKODA, MITSUBISHI,
        BYD, NIO, XPENG, LUCID, RIVIAN, ACURA, LEXUS
    }

    //vehicle types
    enum Type { PICKUP_TRUCK, SUV, COUPE, MOTORCYCLE, VAN, SEDAN, HATCHBACK }
    //==================================================================================================================

    //PRIVATE GENERATION FUNCTIONS======================================================================================
    private String generateRandomPlate() {//generates random plate number dedicated to CA license plates
        Random random = new Random();
        StringBuilder plateBuilder = new StringBuilder();
        plateBuilder.append(random.nextInt(9) + 1);

        for (int i = 0; i < 3; i++) {
            char letter = (char) (random.nextInt(26) + 'A');
            plateBuilder.append(letter);
        }

        int lastDigits = random.nextInt(1000);
        plateBuilder.append(String.format("%03d", lastDigits));
        return plateBuilder.toString();
    }

    private Vehicle.Type generateVehicleType() {
        ArrayList<Vehicle.Type> vehicleTypes = new ArrayList<>(EnumSet.allOf(Vehicle.Type.class));
        Random random = new Random();
        return vehicleTypes.get(random.nextInt(vehicleTypes.size()));
    }

    private Vehicle.Model generateVehicleModel() {
        ArrayList<Vehicle.Model> vehicleModels = new ArrayList<>(EnumSet.allOf(Vehicle.Model.class));
        Random random = new Random();
        return vehicleModels.get(random.nextInt(vehicleModels.size()));
    }

    private Color generateColor() {//generates a random color of the spectrum dedicated for dots
        Random rand = new Random();
        return Color.rgb(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
    }
    //==================================================================================================================

    //CONSTRUCTOR=======================================================================================================
    public Vehicle() {//Constructor for each Vehicle Object associated with default values
        this.plate = generateRandomPlate();
        this.type = generateVehicleType();
        this.entryTime = LocalTime.MIDNIGHT.truncatedTo(ChronoUnit.SECONDS);
        this.exitTime = LocalTime.MIDNIGHT.truncatedTo(ChronoUnit.SECONDS);
        this.model = generateVehicleModel();
        this.amountPaid = 0.0;
        this.totalDurationParked = 0;
    }
    //==================================================================================================================

    //GETTERS===========================================================================================================
    public Vehicle.Type getType() { return type; }
    public Vehicle.Model getModel() { return model; }
    public String getPlate() { return plate; }
    public LocalTime getEntryTime() { return entryTime; }
    public Circle getDot() { return this.dot; }
    public LocalTime getExitTime() { return exitTime; }
    public double getTotalDuration() { return Math.round(totalDurationParked * 100) / 100.0; }
    public double getAmountPaid() { return amountPaid; }
    //==================================================================================================================

    //SETTERS===========================================================================================================
    public void setEntryTime(LocalTime t){entryTime = t;}
    public void setExitTime(LocalTime t){exitTime = t;}
    public void setTotalDuration(double t){totalDurationParked = t;}
    public void setAmountPaid(double money){amountPaid = money;}
    //==================================================================================================================
    /*
        This is for the dots on the grid, showing each Vehicle object's (represented as dots) info of:
        model/type/entryTime/plate
    */
    public void createDotToolTip(LocalTime time) {
        this.dot = new Circle(9, generateColor());
        entryTime = time;

        String info = "Plate: " + plate +
                "\nType: " + type.toString() +
                "\nModel: " + model.toString() +
                "\nEntry time: " + entryTime.toString();

        this.dot.setPickOnBounds(true);
        Tooltip vehicleTooltip = new Tooltip(info);
        vehicleTooltip.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 5; " +
                        "-fx-padding: 10px;" +
                        "-fx-font-family: 'Courier New';" +
                        "-fx-border-color: red;"
        );

        Tooltip.install(this.dot, vehicleTooltip);
        this.dot.getProperties().put("tooltip", vehicleTooltip); //keeps reference for clean uninstallation later
    }
}