package com.example.gridlott;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public class Gridlott extends Application {

    //this uses the computer's actual live system time down to the exact second
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void start(Stage stage) {

        ParkingLot lot = new ParkingLot(6, 15);
        ParkingLot.randomizeDuration stayingDuration = lot.new randomizeDuration(60,180); //ranges between 60s-180s
        ParkingLot.randomizeDuration nextArrivalDelay = lot.new randomizeDuration(0.5, 2.5); //ranges between 0.5s-2.5s
        ParkingLot.ParkRate rate = lot.new ParkRate(30, 3600); //charges $30/hr

        lot.generateParkingLot();
        DashBoardUI dashboard = setUpDashBoard(lot);

        //This centers the grid since by default its stuck on the top left of the corner
        StackPane centeringWrapper = new StackPane(lot.getLayeredPaneCanvas());
        centeringWrapper.setAlignment(Pos.CENTER);
        centeringWrapper.setStyle("-fx-background-color: #1a1a1a;");

        //makes the grid pannable along with custom configurations
        ScrollPane sp = new ScrollPane(centeringWrapper);
        sp.setPannable(true);
        sp.setFitToWidth(false);
        sp.setFitToHeight(false);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        centeringWrapper.minWidthProperty().bind(sp.widthProperty().subtract(2));
        centeringWrapper.minHeightProperty().bind(sp.heightProperty().subtract(2));
        BorderPane root = new BorderPane();
        root.setTop(dashboard.container);
        root.setCenter(sp);
        root.setStyle("-fx-background-color: #1a1a1a;");

        /*This section is responsible for managing flow for each Vehicle object generated. This generates Vehicle
        objects one at a time with a random fixated value.*/
        PauseTransition flow = new PauseTransition(Duration.seconds(0.5));

        flow.setOnFinished(e -> {
            //grabs the live real-world time at the exact moment of creation
            LocalTime realCurrentTime = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);

            Vehicle v = new Vehicle();
            v.createDotToolTip(realCurrentTime);//create the dot tooltip as soon as its created
            lot.simulateParking(v, rate, stayingDuration, dashboard);//this takes care of how Vehicle objects behave in terms of searching parking

            //automatically updates the clock in relation to real time clock
            dashboard.clockLabel.setText("Time: " + LocalTime.now().format(timeFormatter));

            //this generates another random additive time for the next Vehicle object to generate
            flow.setDuration(Duration.seconds(nextArrivalDelay.getRandomizeDuration()));
            flow.playFromStart();
        });
        flow.play();

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("GRIDLOTT: TASTE THE RAINBOW ");
        stage.setScene(scene);
        stage.show();
    }

    public DashBoardUI setUpDashBoard(ParkingLot lot){ //creates the dashboard and is used for making code readable

        //these sets up the label for live feed as a foundation
        Label revLabel = new Label("Revenue: $0.00");
        Label occLabel = new Label("Occupancy: 0 / " + lot.getMaxCapacity());
        Label carsLabel = new Label("Total Cars: 0");
        Label clockLabel = new Label("Time: " + LocalTime.now().format(timeFormatter));

        //sets the font size, font family, and other custom text configurations for Label creations
        String style = "-fx-text-fill: #FFFFFF; -fx-font-size: 20px; -fx-font-family: 'Courier New'; -fx-font-weight: bold;";
        revLabel.setStyle(style);
        occLabel.setStyle(style);
        carsLabel.setStyle(style);
        clockLabel.setStyle(style);

        //Tracks live feed from ParkingLot data
        lot.getRevenueProperty().addListener((obs, o, n) -> revLabel.setText(String.format("Revenue: $%.2f", n.doubleValue())));
        lot.getOccupancyProperty().addListener((obs, o, n) -> occLabel.setText("Occupancy: " + n.intValue() + " / " + lot.getMaxCapacity()));
        lot.getTotalCars().addListener((obs, o, n) -> carsLabel.setText("Total Cars: " + n.intValue()));

        //creates the central hub for revenue,occupancy, amount of cars (lifetime), and real time clock
        HBox dashboard = new HBox(40, revLabel, occLabel, carsLabel, clockLabel);
        dashboard.setAlignment(Pos.CENTER);
        dashboard.setPadding(new Insets(15));
        dashboard.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #444; -fx-border-width: 0 0 2 0;");

        return new DashBoardUI(dashboard, clockLabel, revLabel);
    }

    public void flashLabelGreen(Label label) {
        // 1. Change text color to green immediately
        label.setStyle("-fx-text-fill: #00FF00; -fx-font-size: 20px; -fx-font-family: 'Courier New'; -fx-font-weight: bold;");

        // 2. Create a fade out transition to simulate the fade effect
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), label);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.4); // Dims slightly to emphasize the fade

        // 3. Create a fade back in transition that restores full opacity and original color
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), label);
        fadeIn.setFromValue(0.4);
        fadeIn.setToValue(1.0);

        // When the fade out finishes, restore the original white text style and fade back up
        fadeOut.setOnFinished(e -> {
            label.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 20px; -fx-font-family: 'Courier New'; -fx-font-weight: 900;");
            fadeIn.play();
        });

        fadeOut.play();
    }

    //very useful for returning & self-documenting code since record type acts as a mini class
    public record DashBoardUI(HBox container, Label clockLabel, Label revLabel){}

    public static void main(String[] args) {
        launch();
    }
}