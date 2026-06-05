package com.example.gridlott;

import javafx.scene.control.Tooltip;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

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
    private double totalTraversalTime;
    private double amountPaid;
    private final double WARNING_TIME = 2.0; //the 2-second glow buffer
    private double initialWait;
    private double spawnTime;
    private int ticketNumber;
    private javafx.animation.ScaleTransition exitPulseAnimation;

    //INNER CLASSES=====================================================================================================
    enum Model {
        TOYOTA, VOLKSWAGEN, HONDA, FORD, CHEVROLET, HYUNDAI, NISSAN, KIA, TESLA,
        BMW, MERCEDES_BENZ, AUDI, PORSCHE, FERRARI, LAMBORGHINI, MAZDA, SUBARU,
        CHRYSLER, JEEP, DODGE, RAM, BUICK, CADILLAC, GMC, LAND_ROVER, JAGUAR,
        BENTLEY, ROLLS_ROYCE, VOLVO, FIAT, RENAULT, PEUGEOT, SKODA, MITSUBISHI,
        BYD, NIO, XPENG, LUCID, RIVIAN, ACURA, LEXUS
    }

    enum Type { PICKUP_TRUCK, SUV, COUPE, MOTORCYCLE, VAN, SEDAN, HATCHBACK }
    //==================================================================================================================

    //PRIVATE GENERATION FUNCTIONS======================================================================================
    private String generateRandomPlate() {//dedicated to CA license plates
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

    private Paint generateColor() { // Return type changed to Paint to support Gradients
        Random rand = new Random();

        //roll a 200-sided die. If it lands on 1, its 0.5% rarity
        if (rand.nextInt(200) < 1) {
            //creates a linear gradient spectrum across the dot
            return new LinearGradient(
                    0, 1, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.DARKGREEN),
                    new Stop(0.2, Color.BLACK),
                    new Stop(0.4, Color.DARKGREEN),
                    new Stop(0.6, Color.GREENYELLOW),
                    new Stop(0.8, Color.YELLOWGREEN),
                    new Stop(1.0, Color.WHITE)
            );
        }

        //99.5% chance: regular random solid color
        return Color.rgb(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
    }
    //==================================================================================================================

    //CONSTRUCTOR=======================================================================================================
    public Vehicle() {
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
    public double getInitialWait() { return initialWait; }
    public double getWarningTime() { return WARNING_TIME; }
    public double getTotalTraversalTime(){ return totalTraversalTime; }
    public int getTicketNumber(){ return ticketNumber; }
    public double getSpawnTime(){ return spawnTime; }
    //==================================================================================================================

    //SETTERS===========================================================================================================
    public void setEntryTime(LocalTime t){entryTime = t;}
    public void setExitTime(LocalTime t){exitTime = t;}
    public void setTotalDuration(double t){totalDurationParked = t;}
    public void setAmountPaid(double money){amountPaid = money;}
    public void setTotalTraversalTime(double t){totalTraversalTime = t;}
    public void setTicketNumber(int num){ticketNumber = num;}
    public void setSpawnTime(double t){spawnTime = t;}

    //==================================================================================================================
    /*
        This is for the dots on the grid, showing each Vehicle object's (represented as dots) info of:
        model/type/entryTime/plate
    */
    public void createDotToolTip(LocalTime time, int ticketNum) {
        Paint dynamicColor = generateColor();
        entryTime = time;
        ticketNumber = ticketNum;

        //identify if this vehicle hit has that 0.5% rarity
        boolean isRareMatrix = (dynamicColor instanceof LinearGradient);

        this.dot = new Circle(9, dynamicColor);

        if (isRareMatrix) {
            this.dot.setStroke(Color.GREENYELLOW);
            this.dot.setStrokeWidth(1.5);
        }

        String info =
                "Ticket: #" + ticketNumber +
                "\nPlate: " + plate +
                "\nType: " + type.toString() +
                "\nModel: " + model.toString() +
                "\nEntry time: " + entryTime.toString();

        this.dot.setPickOnBounds(false);
        Tooltip vehicleTooltip = new Tooltip(info);
        vehicleTooltip.setShowDelay(Duration.millis(100));

        //conditional UI styling for dots that are 99.5% and 0.5% rarity
        String cardStyle;
        if (!isRareMatrix) {
            //98% Standard Base Style ---
            Color solidColor = (Color) dynamicColor;
            String hexColor = String.format("#%02X%02X%02X",
                    (int) (solidColor.getRed() * 255),
                    (int) (solidColor.getGreen() * 255),
                    (int) (solidColor.getBlue() * 255));

            cardStyle = "-fx-background-color: rgba(28, 28, 28, 0.75); " +
                    "-fx-border-color: " + hexColor + "; " +
                    "-fx-border-width: 1px;";
        } else {
            //0.5% rare card ticket ---
            cardStyle = "-fx-background-color: linear-gradient(to bottom right, #001a00, #000000, #052e05); " +
                    "-fx-border-color: linear-gradient(to right, black, #00FF00, #ADFF2F, #7FFF00, black); " +
                    "-fx-border-width: 1px; " +
                    "-fx-inner-border-color: #00FF00;";

            this.dot.setStroke(Color.rgb(173, 255, 47, 0.3)); //GREENYELLOW with 30% opacity
            this.dot.setStrokeWidth(3.5);
        }

        //apply corporate terminal typography styling
        vehicleTooltip.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 5; " +
                        "-fx-padding: 10px;" +
                        "-fx-font-family: 'Consolas';" +
                        cardStyle
        );

        Tooltip.install(this.dot, vehicleTooltip);
        this.dot.getProperties().put("tooltip", vehicleTooltip);
    }

    //VISUAL EFFECT COMPONENTS==========================================================================================

    public void calculateWaitTimes(double totalCalculatedStay) {
        //calculates how long to wait before triggering the glow warning
        this.initialWait = Math.max(0.1, totalCalculatedStay - WARNING_TIME);
    }

    public void triggerExitPulse() { //makes it easy for the user to determine which dot is exiting
        if (this.dot == null) return;

        javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
        javafx.scene.paint.Paint fill = this.dot.getFill();
        glow.setColor(fill instanceof javafx.scene.paint.Color ? (javafx.scene.paint.Color) fill : javafx.scene.paint.Color.WHITE);
        glow.setRadius(15);
        glow.setSpread(0.6);

        javafx.scene.effect.InnerShadow core = new javafx.scene.effect.InnerShadow();
        core.setColor(javafx.scene.paint.Color.WHITE);
        core.setRadius(8);
        core.setChoke(0.5);

        glow.setInput(core);
        this.dot.setEffect(glow);

        //pulses the dot
        exitPulseAnimation = new javafx.animation.ScaleTransition(javafx.util.Duration.seconds(0.5), this.dot);
        exitPulseAnimation.setByX(0.2);
        exitPulseAnimation.setByY(0.2);
        exitPulseAnimation.setAutoReverse(true);
        exitPulseAnimation.setCycleCount(2);
        exitPulseAnimation.play();
    }

    public void endExitPulse() { //stops the glowing/pulsing animation
        if (exitPulseAnimation != null) {
            exitPulseAnimation.stop();
            exitPulseAnimation = null;
        }
        if (this.dot != null) {
            this.dot.setEffect(null);
            this.dot.setScaleX(1.0); //reset dot scale
            this.dot.setScaleY(1.0);
        }
    }
    //==================================================================================================================
}