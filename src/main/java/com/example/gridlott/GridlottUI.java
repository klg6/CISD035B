package com.example.gridlott;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class GridlottUI extends Application {

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Label totalSpotsVal;
    private Label availableSpotsVal;
    private Label occupiedSpotsVal;
    private Label totalCarsVal;
    private Label occupancyPercentVal;
    private VBox floorButtonContainer;
    private Label floorIndicatorValueLabel;
    private VBox logContainer;

    //SHARED ENGINE STATE
    private SettingsMenu settingsMenu;
    private ParkingLot currentLot;
    private PauseTransition flow;
    private BorderPane root;
    private BorderPane contentBody;
    private StackPane zoomContainer;

    //DURATIONS AND RATE
    private RandomizeDuration stayingDuration;
    private RandomizeDuration nextArrivalDelay;
    private ParkRate rate;

    //ZOOM & PAN ENGINE STATE
    private double zoomFactor = 1.0;
    private static final double MAX_ZOOM = 1.5;
    private static final double MIN_ZOOM = 0.3;

    private double anchorX;
    private double anchorY;

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #131314;");
        root.setPadding(new Insets(20));

        contentBody = new BorderPane();
        contentBody.setStyle("-fx-background-color: #131314;");
        root.setCenter(contentBody);

        zoomContainer = new StackPane();
        zoomContainer.setAlignment(Pos.CENTER);

        StackPane viewportFrame = new StackPane(zoomContainer);
        viewportFrame.setAlignment(Pos.CENTER);
        viewportFrame.setStyle("-fx-background-color: #131314; -fx-padding: 10;");

        ScrollPane sp = new ScrollPane(viewportFrame);
        sp.setPannable(false);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle(
                "-fx-background: transparent; -fx-background-color: transparent; " +
                        "-fx-viewport-background-color: transparent; -fx-border-color: #444446; " +
                        "-fx-border-style: dashed; -fx-border-width: 1.5;"
        );
        contentBody.setCenter(sp);
        BorderPane.setMargin(sp, new Insets(0, 0, 0, 20));

        //EXECUTE INITIAL FRESH REBOOT RUN
        rebootEntireSimulationRun();

        //SETTINGS MENU
        settingsMenu = new SettingsMenu(() -> {
            //runs a true clean reset using updated configurations
            rebootEntireSimulationRun();
            root.setEffect(null);
            settingsMenu.setVisible(false);
        });
        settingsMenu.setVisible(false);

        StackPane finalRoot = new StackPane(root, settingsMenu);

        //MOUSE & PAN EVENT FILTERS
        viewportFrame.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
        });

        viewportFrame.setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - anchorX;
            double deltaY = event.getSceneY() - anchorY;
            double viewW = viewportFrame.getWidth();
            double viewH = viewportFrame.getHeight();
            double padding = 10.0;
            Bounds boundsInParent = zoomContainer.getBoundsInParent();

            double targetX = zoomContainer.getTranslateX() + deltaX;
            double targetY = zoomContainer.getTranslateY() + deltaY;

            if (boundsInParent.getWidth() <= (viewW - (padding * 2))) {
                double maxLeftDelta = boundsInParent.getMinX() - padding;
                double maxRightDelta = (viewW - padding) - boundsInParent.getMaxX();
                if (deltaX < 0 && Math.abs(deltaX) > maxLeftDelta) deltaX = -maxLeftDelta;
                if (deltaX > 0 && deltaX > maxRightDelta) deltaX = maxRightDelta;
                targetX = zoomContainer.getTranslateX() + deltaX;
            } else {
                double maxLeftDelta = padding - boundsInParent.getMinX();
                double maxRightDelta = boundsInParent.getMaxX() - (viewW - padding);
                if (deltaX > 0 && deltaX > maxLeftDelta) deltaX = maxLeftDelta;
                if (deltaX < 0 && Math.abs(deltaX) > maxRightDelta) deltaX = -maxRightDelta;
                targetX = zoomContainer.getTranslateX() + deltaX;
            }

            if (boundsInParent.getHeight() <= (viewH - (padding * 2))) {
                double maxTopDelta = boundsInParent.getMinY() - padding;
                double maxBottomDelta = (viewH - padding) - boundsInParent.getMaxY();
                if (deltaY < 0 && Math.abs(deltaY) > maxTopDelta) deltaY = -maxTopDelta;
                if (deltaY > 0 && deltaY > maxBottomDelta) deltaY = maxBottomDelta;
                targetY = zoomContainer.getTranslateY() + deltaY;
            } else {
                double maxTopDelta = padding - boundsInParent.getMinY();
                double maxBottomDelta = boundsInParent.getMaxY() - (viewH - padding);
                if (deltaY > 0 && deltaY > maxTopDelta) deltaY = maxTopDelta;
                if (deltaY < 0 && Math.abs(deltaY) > maxBottomDelta) deltaY = -maxBottomDelta;
                targetY = zoomContainer.getTranslateY() + deltaY;
            }

            zoomContainer.setTranslateX(targetX);
            zoomContainer.setTranslateY(targetY);
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
        });

        //ZOOM FILTER MECHANICS (lets user zoom in and out when interacting with the grid)
        sp.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                event.consume();
                double zoomDelta = (event.getDeltaY() > 0) ? 0.05 : -0.05;
                double newScale = zoomFactor + zoomDelta;

                if (newScale >= MIN_ZOOM && newScale <= MAX_ZOOM) {
                    zoomFactor = newScale;
                    zoomContainer.setScaleX(zoomFactor);
                    zoomContainer.setScaleY(zoomFactor);

                    double viewW = viewportFrame.getWidth();
                    double viewH = viewportFrame.getHeight();
                    double padding = 10.0;

                    Bounds boundsInParent = zoomContainer.getBoundsInParent();
                    double fixTx = zoomContainer.getTranslateX();
                    double fixTy = zoomContainer.getTranslateY();

                    if (boundsInParent.getWidth() <= (viewW - (padding * 2))) {
                        double boundLimitX = (viewW - boundsInParent.getWidth()) / 2.0 - padding;
                        fixTx = Math.max(-boundLimitX, Math.min(boundLimitX, fixTx));
                    } else {
                        if (boundsInParent.getMinX() > padding) fixTx -= (boundsInParent.getMinX() - padding);
                        if (boundsInParent.getMaxX() < (viewW - padding)) fixTx += ((viewW - padding) - boundsInParent.getMaxX());
                    }

                    if (boundsInParent.getHeight() <= (viewH - (padding * 2))) {
                        double boundLimitY = (viewH - boundsInParent.getHeight()) / 2.0 - padding;
                        fixTy = Math.max(-boundLimitY, Math.min(boundLimitY, fixTy));
                    } else {
                        if (boundsInParent.getMinY() > padding) fixTy -= (boundsInParent.getMinY() - padding);
                        if (boundsInParent.getMaxY() < (viewH - padding)) fixTy += ((viewW - padding) - boundsInParent.getMaxY());
                    }

                    zoomContainer.setTranslateX(fixTx);
                    zoomContainer.setTranslateY(fixTy);
                }
            }
        });

        //SCENE CREATION & SCROLLBAR STYLE DESIGN
        Scene scene = new Scene(finalRoot, 1400, 850);
        String scrollbarCss = "data:text/css," +
                ".scroll-pane > .scroll-bar:vertical { -fx-background-color: #1e1e1f; -fx-width: 6px; -fx-padding: 0; }" +
                ".scroll-pane > .scroll-bar:vertical > .thumb { -fx-background-color: #444446; -fx-background-radius: 3px; }" +
                ".scroll-pane > .scroll-bar:vertical > .thumb:hover { -fx-background-color: #39FF14; }" +
                ".scroll-pane > .scroll-bar > .increment-button, .scroll-pane > .scroll-bar > .decrement-button { -fx-padding: 0; -fx-background-color: transparent; }" +
                ".scroll-pane > .scroll-bar > .increment-button > .increment-arrow, .scroll-pane > .scroll-bar > .decrement-button > .decrement-arrow { -fx-shape: ' '; -fx-padding: 0; }";
        scene.getStylesheets().add(scrollbarCss.replace(" ", "%20"));

        //ESC KEY OVERLAY LISTEN ENGINE
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (settingsMenu.isVisible()) {
                    settingsMenu.setVisible(false);
                    root.setEffect(null);
                } else {
                    settingsMenu.refreshMenuState(); //dynamically checks inputs and resets apply button to disabled
                    settingsMenu.setVisible(true);
                    root.setEffect(new GaussianBlur(15));
                }
            }
        });

        stage.setTitle("GRIDLOTT");
        stage.setScene(scene);
        stage.show();
    }

    //THE CORE ENGINE REBOOT SYSTEM (behaves exactly like a fresh program launch)
    private void rebootEntireSimulationRun() {
        //instantly stop active arrival spawner loop
        if (flow != null) flow.stop();

        //kill all active ghost cars in the old lots
        if (currentLot != null) {
            currentLot.stopAllActiveTimers();
        }

        //re-read all configuration properties freshly from the updated Config fields
        stayingDuration = new RandomizeDuration(Config.minDuration, Config.maxDuration);
        nextArrivalDelay = new RandomizeDuration(Config.minDelay, Config.maxDelay);
        rate = new ParkRate(Config.fee, Config.perSeconds);

        //completely construct a brand new ParkingLot instantiation
        currentLot = new ParkingLot(Config.rows, Config.cols);
        currentLot.setFloorCount(Config.floors);
        currentLot.generateParkingLot();

        //update the visual viewport container tracking targets
        zoomContainer.getChildren().clear();
        zoomContainer.getChildren().add(currentLot.getLayeredPaneCanvas());

        //restore standard camera scale constraints for the grid
        zoomFactor = 1.0;
        zoomContainer.setScaleX(1.0);
        zoomContainer.setScaleY(1.0);
        zoomContainer.setTranslateX(0);
        zoomContainer.setTranslateY(0);

        //completely regenerate dashboards and sidebars bound to new data structures
        HBox topDashboard = createTopDashboard(currentLot);
        root.setTop(topDashboard);
        BorderPane.setMargin(topDashboard, new Insets(0, 0, 20, 0));

        VBox sidebar = createSidebar(currentLot);
        contentBody.setLeft(sidebar);
        BorderPane.setAlignment(sidebar, Pos.TOP_LEFT);

        //re-bind metric listeners and spin up spawner threads
        currentLot.getOccupancyProperty().addListener((obs, o, n) -> updateDynamicSummary(currentLot, n.intValue()));
        updateDynamicSummary(currentLot, 0);

        //sets up logs reader for each vehicle dependent from ParkingLot class method
        currentLot.setLogListener((dotColor, plate, isEntry, row, col, floor) -> {
            Platform.runLater(() -> appendLog(dotColor, plate, isEntry, row, col, floor));
        });

        //core block for continuously generating vehicles
        flow = new PauseTransition(Duration.seconds(0.5));
        flow.setOnFinished(e -> {
            LocalTime realCurrentTime = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
            Vehicle v = new Vehicle();
            v.createDotToolTip(realCurrentTime);
            currentLot.simulateParking(v, rate, stayingDuration);
            flow.setDuration(Duration.seconds(nextArrivalDelay.getRandomizeDuration()));
            flow.playFromStart();
        });
        flow.play();
    }

    //Create the top metric boxes: revenue, occupancy, total car entries, viewing floor, and time
    private HBox createTopDashboard(ParkingLot lot) {
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);

        HBox revCard = createMetricCard("REVENUE", lot.getRevenueProperty(), "$0.00", true);
        HBox occCard = createMetricCard("OCCUPANCY", lot.getOccupancyProperty(), "0 / " + lot.getMaxCapacity(), false);
        HBox carsCard = createMetricCard("TOTAL CAR ENTRIES", lot.getTotalCars(), "0", false);

        revCard.setPrefWidth(260);
        occCard.setPrefWidth(260);
        carsCard.setPrefWidth(260);

        VBox floorTextContainer = new BoxCardTextBuilder("VIEWING FLOOR", "Floor 1");
        floorIndicatorValueLabel = (Label) floorTextContainer.getChildren().get(1);

        HBox floorCard = new HBox(floorTextContainer);
        floorCard.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 24 12 24;");
        floorCard.setPrefWidth(260);

        VBox timeTextContainer = new BoxCardTextBuilder("TIME", LocalTime.now().format(timeFormatter));
        HBox timeCard = new HBox(timeTextContainer);
        timeCard.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 24 12 24;");
        timeCard.setPrefWidth(260);

        PauseTransition clockTick = new PauseTransition(Duration.seconds(1));
        clockTick.setOnFinished(e -> {
            ((Label)timeTextContainer.getChildren().get(1)).setText(LocalTime.now().format(timeFormatter));
            clockTick.playFromStart();
        });
        clockTick.play();

        topBar.getChildren().addAll(revCard, occCard, carsCard, floorCard, timeCard);
        return topBar;
    }

    //creates the legend, summary, floors, and log boxes
    private VBox createSidebar(ParkingLot lot) {
        VBox sidebar = new VBox(15);
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setMinWidth(120);
        sidebar.setPrefWidth(260);
        sidebar.setMaxWidth(260);
        sidebar.setMaxHeight(Region.USE_PREF_SIZE);
        sidebar.setStyle("-fx-background-color: transparent;");

        //LEGEND CARD
        VBox legendCard = new VBox(8);
        legendCard.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 15 12 15;");
        legendCard.setMinWidth(100);
        legendCard.setPrefWidth(260);
        legendCard.setMaxWidth(260);
        legendCard.setMinHeight(50);
        legendCard.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(legendCard, Priority.ALWAYS);

        Label legendTitle = new Label("LEGEND");
        legendTitle.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
        legendCard.getChildren().add(legendTitle);

        VBox legendRowsContainer = new VBox(8);
        legendRowsContainer.setStyle("-fx-background-color: transparent;");
        legendRowsContainer.getChildren().addAll(
                createLegendRow("#FFD700", "Floor 1 (Yellow)"),
                createLegendRow("#9370DB", "Floor 2 (Purple)"),
                createLegendRow("#00FFFF", "Floor 3 (Aqua)"),
                createLegendRow("#FF0000", "Floor 4 (Red)"),
                createLegendRow("#800080", "Floor 5 (Deep Purple)")
        );

        ScrollPane legendScrollPane = new ScrollPane(legendRowsContainer);
        legendScrollPane.setFitToWidth(true);
        legendScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        legendScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        legendScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent; -fx-viewport-background-color: transparent;");
        legendCard.getChildren().add(legendScrollPane);

        //SUMMARY CARD
        VBox summaryCard = new VBox(10);
        summaryCard.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 15 12 15;");
        summaryCard.setMinWidth(100);
        summaryCard.setPrefWidth(260);
        summaryCard.setMaxWidth(260);
        summaryCard.setMinHeight(50);
        summaryCard.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(summaryCard, Priority.ALWAYS);

        Label summaryTitle = new Label("SUMMARY");
        summaryTitle.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
        summaryCard.getChildren().add(summaryTitle);

        totalSpotsVal = new Label(String.valueOf(lot.getMaxCapacity()));
        availableSpotsVal = new Label(String.valueOf(lot.getMaxCapacity()));
        occupiedSpotsVal = new Label("0");
        totalCarsVal = new Label("0");
        occupancyPercentVal = new Label("0.00%");

        VBox summaryRowsContainer = new VBox(10);
        summaryRowsContainer.setStyle("-fx-background-color: transparent;");
        summaryRowsContainer.getChildren().addAll(
                createSummaryRow("Total Spots", totalSpotsVal, "#ffffff"),
                createSummaryRow("Available", availableSpotsVal, "#39FF14"),
                createSummaryRow("Occupied", occupiedSpotsVal, "#ffffff"),
                createSummaryRow("Total Car Entries", totalCarsVal, "#ffffff"),
                createSummaryRow("Occupancy", occupancyPercentVal, "#39FF14")
        );

        ScrollPane summaryScrollPane = new ScrollPane(summaryRowsContainer);
        summaryScrollPane.setFitToWidth(true);
        summaryScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        summaryScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        summaryScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent; -fx-viewport-background-color: transparent;");
        summaryCard.getChildren().add(summaryScrollPane);

        //FLOORS CARD
        VBox floorCard = new VBox(8);
        floorCard.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 15 12 15;");
        floorCard.setMinWidth(100);
        floorCard.setPrefWidth(260);
        floorCard.setMaxWidth(260);
        floorCard.setMinHeight(60);
        floorCard.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(floorCard, Priority.ALWAYS);

        Label floorTitle = new Label("FLOORS");
        floorTitle.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        floorButtonContainer = new VBox(5);
        populateFloorButtons(lot);

        //LOGS CARD
        VBox logsCard = new VBox(8);
        logsCard.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 15 12 15;");
        logsCard.setMinWidth(100);
        logsCard.setPrefWidth(260);
        logsCard.setMaxWidth(260);
        logsCard.setMinHeight(150);
        logsCard.setPrefHeight(220);
        logsCard.setMaxHeight(260);
        VBox.setVgrow(logsCard, Priority.NEVER); //doesn't allow it expands it card or box

        Label logsTitle = new Label("LOGS");
        logsTitle.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        logContainer = new VBox(5);
        logContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane logsScrollPane = new ScrollPane(logContainer);
        logsScrollPane.setFitToWidth(true);
        logsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        logsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        logsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent; -fx-viewport-background-color: transparent;");

        //auto-scroll to the bottom whenever a new log is added
        logContainer.heightProperty().addListener((obs, oldVal, newVal) -> logsScrollPane.setVvalue(1.0));

        logsCard.getChildren().addAll(logsTitle, logsScrollPane);

        ScrollPane floorScrollPane = new ScrollPane(floorButtonContainer);
        floorScrollPane.setFitToWidth(true);
        floorScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        floorScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        floorScrollPane.setMaxHeight(180);
        floorScrollPane.setMinHeight(50);
        floorScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent; -fx-viewport-background-color: transparent;");
        floorButtonContainer.setStyle("-fx-background-color: transparent; -fx-padding: 0 4 0 0;");

        floorCard.getChildren().addAll(floorTitle, floorScrollPane);
        sidebar.getChildren().addAll(legendCard, summaryCard, floorCard, logsCard);
        return sidebar;
    }

    //method dedicated for logs card/box that simply updates whenever a vehicle leaves or enters from a spot
    private void appendLog(javafx.scene.paint.Paint dotColor, String plate, boolean isEntry, int row, int col, int floor) {
        if (logContainer == null) return;

        HBox rowBox = new HBox(8);
        rowBox.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(4, dotColor);

        //determine action text and terminal colors
        String actionText = isEntry ? "going to" : "exited from";
        String colorHex = isEntry ? "#39FF14" : "#FF3333";

        String text = String.format("(%s) %s [%d][%d][%d]", plate, actionText, row + 1, col + 1, floor + 1);

        Label logLabel = new Label(text);
        logLabel.setStyle("-fx-font-size: 11px; -fx-font-family: 'Consolas', 'Courier New', monospace; -fx-text-fill: " + colorHex + ";");
        logLabel.setWrapText(true);

        rowBox.getChildren().addAll(dot, logLabel);
        logContainer.getChildren().add(rowBox);

        if (logContainer.getChildren().size() > 25) {
            logContainer.getChildren().remove(0);
        }
    }

    //dedicated for the floors card/box that bundles floors into a seamless UI floor integration
    private void populateFloorButtons(ParkingLot lot) {
        if (floorButtonContainer == null) return;
        floorButtonContainer.getChildren().clear();

        for (int i = 0; i < lot.getFloorCount(); i++) {
            final int floorIdx = i;
            BorderPane btn = new BorderPane();
            Label lbl = new Label(String.valueOf(i + 1));
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
            btn.setCenter(lbl);
            btn.setPrefHeight(34);
            btn.setMinHeight(20);

            if (i == 0) {
                btn.setStyle("-fx-background-color: transparent; -fx-border-color: #39FF14; -fx-border-width: 1; -fx-background-radius: 6; -fx-border-radius: 6; -fx-cursor: hand;");
            } else {
                btn.setStyle("-fx-background-color: #2c2c2e; -fx-background-radius: 6; -fx-cursor: hand;");
            }

            btn.setOnMouseClicked(e -> {
                lot.showFloor(floorIdx);
                highlightActiveFloorButton(floorIdx);
                if (floorIndicatorValueLabel != null) {
                    floorIndicatorValueLabel.setText("Floor " + (floorIdx + 1));
                }
            });
            floorButtonContainer.getChildren().add(btn);
        }
    }

    //dedicated for the top dashboard metric cards/boxes that listens to updated information from ParkingLot class
    private HBox createMetricCard(String title, javafx.beans.property.Property<Number> property, String defaultVal, boolean isCurrency) {
        VBox textContainer = new BoxCardTextBuilder(title, defaultVal);
        Label valLabel = (Label) textContainer.getChildren().get(1);

        property.addListener((obs, o, n) -> {
            if (isCurrency) {
                valLabel.setText(String.format("$%,.2f", n.doubleValue()));
            } else {
                if (title.equals("OCCUPANCY")) {
                    valLabel.setText(n.intValue() + " / " + Config.rows * Config.cols * Config.floors);
                } else {
                    valLabel.setText(String.valueOf(n.intValue()));
                }
            }
        });

        HBox card = new HBox(textContainer);
        card.setStyle("-fx-background-color: #1e1e1f; -fx-background-radius: 8; -fx-border-color: #2d2d30; -fx-border-width: 1; -fx-padding: 12 24 12 24;");
        card.setPrefWidth(240);
        return card;
    }

    private static class BoxCardTextBuilder extends VBox {
        public BoxCardTextBuilder(String header, String value) {
            super(2);
            Label headerLabel = new Label(header);
            headerLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
            Label valueLabel = new Label(value);
            valueLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
            this.getChildren().addAll(headerLabel, valueLabel);
        }
    }

    //dedicated for legends card/box information if the user turned on better dot visualization
    private HBox createLegendRow(String colorHex, String labelText) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Circle indicator = new Circle(5, Color.web(colorHex));
        Label label = new Label(labelText);
        label.setMinWidth(0);
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setStyle("-fx-text-fill: #e5e5ea; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
        row.getChildren().addAll(indicator, label);
        return row;
    }

    //dedicated for summaries card/box information
    private BorderPane createSummaryRow(String metricName, Label valueLabel, String valueColorHex) {
        BorderPane row = new BorderPane();
        Label name = new Label(metricName);
        name.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
        valueLabel.setStyle("-fx-text-fill: " + valueColorHex + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
        row.setLeft(name);
        row.setRight(valueLabel);
        return row;
    }

    //dedicated for summaries card/box as well but this time it listens and updates based on ParkingLot class
    private void updateDynamicSummary(ParkingLot lot, int currentOcc) {
        if (lot != this.currentLot) return;//gatekeeper to ignore updates from abandoned ghost lots

        int capacity = lot.getMaxCapacity();
        int available = capacity - currentOcc;
        double pct = (capacity == 0) ? 0.0 : ((double) currentOcc / capacity) * 100.0;

        if(availableSpotsVal != null) availableSpotsVal.setText(String.valueOf(available));
        if(occupiedSpotsVal != null) occupiedSpotsVal.setText(String.valueOf(currentOcc));
        if(totalCarsVal != null) totalCarsVal.setText(String.valueOf(lot.getTotalCars().get()));
        if(occupancyPercentVal != null) occupancyPercentVal.setText(String.format("%.2f%%", pct));
    }

    //dedicated to floors card/box to simply let the user know what floor they are viewing on if clicked
    private void highlightActiveFloorButton(int targetIdx) {
        for (int i = 0; i < floorButtonContainer.getChildren().size(); i++) {
            BorderPane btn = (BorderPane) floorButtonContainer.getChildren().get(i);
            if (i == targetIdx) {
                btn.setStyle("-fx-background-color: transparent; -fx-border-color: #39FF14; -fx-border-width: 1; -fx-background-radius: 6; -fx-border-radius: 6; -fx-cursor: hand;");
            } else {
                btn.setStyle("-fx-background-color: #2c2c2e; -fx-background-radius: 6; -fx-cursor: hand;");
            }
        }
    }

    public static void main(String[] args) { launch(); }
}