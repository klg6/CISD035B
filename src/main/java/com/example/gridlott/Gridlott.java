package com.example.gridlott;

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

        //Dimensions for parking lot (you can change rows/cols values)
        int rows = 20;
        int cols = 15; //<--- ANYTHING OVER 15 doesnt work, gonna have to make it work for any value
        ParkingLot lot = new ParkingLot(rows, cols);

        //Parking rate settings (can also change as well) -> (ex.) charges $30 per hr or 3600s
        ParkRate rate = new ParkRate(30, 3600);
        lot.generateParkingLot();

        //these sets up the label for live feed as a foundation
        Label revLabel = new Label("Revenue: $0.00");
        Label occLabel = new Label("Occupancy: 0 / " + lot.getMaxCapacity());
        Label carsLabel = new Label("Total Cars: 0");
        Label clockLabel = new Label("Time: " + LocalTime.now().format(timeFormatter));

        //sets the font size, font family, and other custom text configurations for Label creations
        String style = "-fx-text-fill: #00ff00; -fx-font-size: 20px; -fx-font-family: 'Courier New'; -fx-font-weight: bold;";
        revLabel.setStyle(style);
        occLabel.setStyle(style);
        carsLabel.setStyle(style);
        clockLabel.setStyle(style);

        //Tracks live feed from ParkingLot data
        lot.getRevenueProperty().addListener((obs, o, n) -> revLabel.setText(String.format("Revenue: $%.2f", n.doubleValue())));
        lot.getOccupancyProperty().addListener((obs, o, n) -> occLabel.setText("Occupancy: " + n.intValue() + " / " + lot.getMaxCapacity()));
        lot.getTotalCars().addListener((obs, o, n) -> carsLabel.setText("Total Cars: " + n.intValue()));

        //creates the central hub for revene,occupancy, amount of cars (lifetime), and real time clock
        HBox dashboard = new HBox(40, revLabel, occLabel, carsLabel, clockLabel);
        dashboard.setAlignment(Pos.CENTER);
        dashboard.setPadding(new Insets(15));
        dashboard.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #444; -fx-border-width: 0 0 2 0;");

        //This huge section centers the grid since by default its stuck on the top left of the corner
        StackPane centeringWrapper = new StackPane(lot.getLayeredPaneCanvas());
        centeringWrapper.setAlignment(Pos.CENTER);
        centeringWrapper.setStyle("-fx-background-color: #1a1a1a;");
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
        root.setTop(dashboard);
        root.setCenter(sp);
        root.setStyle("-fx-background-color: #1a1a1a;");

        /*
            This section is responsible for managing flow for each Vehicle object generated. This generates Vehicle
            objects one at a time with a random fixated value.
         */
        Random arrivalRand = new Random();
        PauseTransition flow = new PauseTransition(Duration.seconds(0.5));

        flow.setOnFinished(e -> {
            //grabs the live real-world time at the exact moment of creation
            LocalTime realCurrentTime = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);

            Vehicle v = new Vehicle();
            v.createDotToolTip(realCurrentTime);//create the dot tooltip as soon as its created
            lot.simulateParking(v, rate);//this takes care of how Vehicle objects behave in terms of searching parking

            //automatically updates the clock in relation to real time clock
            clockLabel.setText("Time: " + LocalTime.now().format(timeFormatter));

            //this generates another random additive time for the next Vehicle object to generate
            double nextArrivalDelay = 0.5 + (arrivalRand.nextDouble() * 2.5);
            flow.setDuration(Duration.seconds(nextArrivalDelay));
            flow.playFromStart();
        });
        flow.play();

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("GRIDLOTT: TASTE THE RAINBOW ");
        stage.setScene(scene);
        stage.show();
    }
    public static void main(String[] args) {
        launch();
    }
}