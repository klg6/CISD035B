package com.example.gridlott;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class GridlottUI extends Application {

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void start(Stage stage) {

        ParkingLot lot = new ParkingLot(Config.rows, Config.cols);
        lot.setFloorCount(Config.floors);
        RandomizeDuration stayingDuration = new RandomizeDuration(Config.minDuration, Config.maxDuration);
        RandomizeDuration nextArrivalDelay = new RandomizeDuration(Config.minDelay, Config.maxDelay);
        ParkRate rate = new ParkRate(Config.fee, Config.perSeconds);

        lot.generateParkingLot();
        DashBoardUI dashboard = setUpDashBoard(lot);

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
        root.setTop(dashboard.container);
        root.setCenter(sp);
        root.setStyle("-fx-background-color: #1a1a1a;");

        PauseTransition flow = new PauseTransition(Duration.seconds(0.5));
        flow.setOnFinished(e -> {
            LocalTime realCurrentTime = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);

            Vehicle v = new Vehicle();
            v.createDotToolTip(realCurrentTime);

            lot.simulateParking(v, rate, stayingDuration);

            dashboard.clockLabel.setText("Time: " + LocalTime.now().format(timeFormatter));

            flow.setDuration(Duration.seconds(nextArrivalDelay.getRandomizeDuration()));
            flow.playFromStart();
        });
        flow.play();

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("GRIDLOTT");
        stage.setScene(scene);
        stage.show();
    }

    public DashBoardUI setUpDashBoard(ParkingLot lot) {
        Label revLabel = new Label("Revenue: $0.00");
        Label occLabel = new Label("Occupancy: 0 / " + lot.getMaxCapacity());
        Label carsLabel = new Label("Total Cars: 0");
        Label clockLabel = new Label("Time: " + LocalTime.now().format(timeFormatter));

        //creates the dropdown button
        ComboBox<String> floorDropdown = new ComboBox<>();
        floorDropdown.setStyle(
                "-fx-background-color: #444; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-family: 'Courier New'; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px; " +
                        "-fx-context-menu-background-color: #333;"
        );

        int totalFloorLayers = lot.getFloorCount();

        //populate the dropdown menu based on how many floors exist
        if (totalFloorLayers <= 1) {
            floorDropdown.getItems().add("Single Level");
            floorDropdown.getSelectionModel().selectFirst();
            floorDropdown.setDisable(true);
        } else {
            for (int i = 0; i < totalFloorLayers; i++) {
                floorDropdown.getItems().add("View: Floor " + (i + 1));
            }
            //default to showing Floor 1 initially
            floorDropdown.getSelectionModel().selectFirst();
        }

        //dandle direct floor jumping when an option is selected
        floorDropdown.setOnAction(e -> {
            int selectedIndex = floorDropdown.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0) {
                lot.showFloor(selectedIndex);
            }
        });

        String style = "-fx-text-fill: #39FF14; -fx-font-size: 20px; -fx-font-family: 'Courier New'; -fx-font-weight: bold;";
        revLabel.setStyle(style);
        occLabel.setStyle(style);
        carsLabel.setStyle(style);
        clockLabel.setStyle(style);

        lot.getRevenueProperty().addListener((obs, o, n) -> revLabel.setText(String.format("Revenue: $%.2f", n.doubleValue())));
        lot.getOccupancyProperty().addListener((obs, o, n) -> occLabel.setText("Occupancy: " + n.intValue() + " / " + lot.getMaxCapacity()));
        lot.getTotalCars().addListener((obs, o, n) -> carsLabel.setText("Total Cars: " + n.intValue()));

        HBox dashboard = new HBox(30, revLabel, occLabel, carsLabel, clockLabel, floorDropdown);
        dashboard.setAlignment(Pos.CENTER);
        dashboard.setPadding(new Insets(15));
        dashboard.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #444; -fx-border-width: 0 0 2 0;");

        return new DashBoardUI(dashboard, clockLabel);
    }

    public record DashBoardUI(HBox container, Label clockLabel){}

    public static void main(String[] args) {launch();}
}