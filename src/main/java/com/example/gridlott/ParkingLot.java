package com.example.gridlott;

import javafx.animation.PathTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

import java.awt.Point;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ParkingLot {
    //LAYOUT CONSTANTS
    private static final double CELL_WIDTH = 40.0;
    private static final double CELL_HEIGHT = 70.0;
    private static final String BACKGROUND_HEX = "#1a1a1a";

    //ANIMATION DURATION SETTINGS
    private static final double TIME_ORIGIN_EXIT = 0.4;
    private static final double TIME_INTERMEDIATE_LOOP = 3.5;
    private static final double TIME_GROUND_CROSS = 1.5;
    private static final double TIME_LOT_EXIT = 1.2;

    //STRUCTURAL STATES
    private int floors = 1;
    private boolean rampsEnabled = false;
    private final int rows, cols;

    private Vehicle[][][] occupancy;
    private StackPane[][][] spotUI;
    private final List<List<Queue<Point>>> floorStructuralZones = new ArrayList<>();
    private int activeZoneTurn = 0;

    //UI LAYOUT CONTAINERS
    private final Pane layeredCanvas = new Pane();
    private final List<GridPane> floorGrids = new ArrayList<>();
    private int currentlyViewedFloor = 0;

    //DASHBOARDUI VARIABLES
    private boolean useBottomLeftGateNext = true;
    private final DoubleProperty totalRevenue = new SimpleDoubleProperty(0.0);
    private final IntegerProperty currentOccupancy = new SimpleIntegerProperty(0);
    private final IntegerProperty totalCars = new SimpleIntegerProperty(0);
    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public record FloorPoint(int floor, int x, int y) {}

    public ParkingLot(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    //STRUCTURAL INITIALIZATION & UI GENERATION
    public void setFloorCount(int count) {
        if (count < 1) throw new IllegalArgumentException("Parking lot must have at least 1 floor!");
        this.floors = count;
        this.rampsEnabled = (count > 1);
        this.initializeStructures();
    }

    private void initializeStructures() {
        this.occupancy = new Vehicle[floors][cols][rows];
        this.spotUI = new StackPane[floors][cols][rows];
        this.floorStructuralZones.clear();
        this.floorGrids.clear();

        int zoneWidth = (int) Math.ceil((double) cols / 3);

        for (int f = 0; f < floors; f++) {
            floorStructuralZones.add(List.of(new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>()));

            List<Point> masterList = new ArrayList<>();
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    masterList.add(new Point(c, r));
                }
            }
            Collections.shuffle(masterList);

            for (Point p : masterList) {
                int zoneId = Math.min(p.x / zoneWidth, 2);
                floorStructuralZones.get(f).get(zoneId).add(p);
            }

            GridPane grid = new GridPane();
            floorGrids.add(grid);
            if (f == 0) layeredCanvas.getChildren().add(grid);
        }

        floorGrids.get(0).boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> {
            layeredCanvas.setPrefSize(newBounds.getWidth(), newBounds.getHeight());
            layeredCanvas.setMaxSize(newBounds.getWidth(), newBounds.getHeight());
        });
    }

    public void generateParkingLot() {
        for (int f = 0; f < floors; f++) {
            GridPane grid = floorGrids.get(f);
            configureGridStyle(grid, f == 0);

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    StackPane spot = createParkingStall(r);
                    grid.add(spot, c, r);
                    spotUI[f][c][r] = spot;
                }
            }
            if (f > 0) layeredCanvas.getChildren().add(grid);
        }
    }

    private void configureGridStyle(GridPane grid, boolean isGroundFloor) {
        grid.setStyle("-fx-background-color: " + BACKGROUND_HEX + ";");
        grid.setPadding(new Insets(60, 40, 40, 40));
        grid.setAlignment(Pos.CENTER);
        if (!isGroundFloor) {
            grid.setVisible(false);
            grid.setMouseTransparent(true);
        }
    }

    private StackPane createParkingStall(int row) {
        Rectangle stall = new Rectangle(CELL_WIDTH, CELL_HEIGHT);
        stall.setFill(Color.TRANSPARENT);
        stall.setStroke(Color.grayRgb(60));
        stall.setMouseTransparent(true);

        StackPane spot = new StackPane(stall);
        int bottomMargin = (row % 2 == 0) ? 0 : 40;
        GridPane.setMargin(spot, new Insets(0, 2, bottomMargin, 2));
        return spot;
    }

    //DYNAMIC VISIBILITY MANAGEMENT ENGINE
    public void showFloor(int floorIndex) {
        this.currentlyViewedFloor = floorIndex;

        for (int f = 0; f < floors; f++) {
            boolean isTarget = (f == floorIndex);
            floorGrids.get(f).setVisible(isTarget);
            floorGrids.get(f).setMouseTransparent(!isTarget);
        }
        syncAllDotsVisibility();
    }

    private void syncAllDotsVisibility() {
        for (javafx.scene.Node node : layeredCanvas.getChildren()) {
            if (node instanceof Circle dot) {
                updateDotVisibilityState(dot);
            }
        }
    }

    private void updateDotVisibilityState(Circle dot) {
        Integer currentPhysicalFloor = (Integer) dot.getProperties().get("currentPhysicalFloor");
        if (currentPhysicalFloor != null) {
            dot.setVisible(currentPhysicalFloor == currentlyViewedFloor);
        }
    }

    //VEHICLE ARRIVAL & PATH ROUTING SIMULATION
    public void simulateParking(Vehicle v, ParkRate rate, RandomizeDuration p) {
        FloorPoint target = checkMultiFloorTarget();
        if (target == null) return;

        int targetFloor = target.floor();
        int targetCol = target.x();
        int targetRow = target.y();

        occupancy[targetFloor][targetCol][targetRow] = v;

        Bounds targetStallBounds = spotUI[0][targetCol][targetRow].getBoundsInParent();
        Bounds firstCellBounds = spotUI[0][0][0].getBoundsInParent();
        Bounds lastCellBounds = spotUI[0][cols - 1][rows - 1].getBoundsInParent();

        double cellCenterX = targetStallBounds.getMinX() + (targetStallBounds.getWidth() / 2.0);
        double cellCenterY = targetStallBounds.getMinY() + (targetStallBounds.getHeight() / 2.0);
        double chosenAisleY = determineAisleY(targetRow, targetCol, firstCellBounds);

        double leftLaneX = firstCellBounds.getMinX() - (firstCellBounds.getWidth() * 0.5);
        double rightLaneX = lastCellBounds.getMaxX() + (firstCellBounds.getWidth() * 0.5);
        double bottomRoadY = lastCellBounds.getMaxY() + (firstCellBounds.getHeight() * 0.3);

        double switchbackUpperY = firstCellBounds.getMinY() - (firstCellBounds.getHeight() * 0.8);
        double switchbackLowerY = firstCellBounds.getMinY() - (firstCellBounds.getHeight() * 0.4);
        double rampEndPointX = lastCellBounds.getMaxX() - (firstCellBounds.getWidth() * 0.5);

        boolean assignedToBottomLeft = useBottomLeftGateNext;
        useBottomLeftGateNext = !useBottomLeftGateNext;

        Circle dot = v.getDot();
        Boolean c = Config.betterDotVisuals;
        assignVehicleColor(dot, targetFloor, c);

        if (assignedToBottomLeft) {
            dot.setTranslateX(leftLaneX);
            dot.setTranslateY(bottomRoadY);
        } else {
            dot.setTranslateX(rightLaneX);
            dot.setTranslateY(switchbackUpperY);
        }

        dot.getProperties().put("targetFloor", targetFloor);
        dot.getProperties().put("currentPhysicalFloor", 0);
        layeredCanvas.getChildren().add(dot);
        updateDotVisibilityState(dot);

        Path entryPath = new Path();
        double duration = 2.0;

        if (assignedToBottomLeft) {
            entryPath.getElements().add(new MoveTo(leftLaneX, bottomRoadY));
            if (rampsEnabled && targetFloor > 0) {
                entryPath.getElements().add(new LineTo(leftLaneX, switchbackUpperY));
                entryPath.getElements().add(new LineTo(rampEndPointX, switchbackUpperY));
            } else {
                entryPath.getElements().add(new LineTo(leftLaneX, chosenAisleY));
                entryPath.getElements().add(new LineTo(cellCenterX, chosenAisleY));
                entryPath.getElements().add(new LineTo(cellCenterX, cellCenterY));
            }
        } else {
            entryPath.getElements().add(new MoveTo(rightLaneX, switchbackUpperY));
            if (rampsEnabled && targetFloor > 0) {
                entryPath.getElements().add(new LineTo(rightLaneX, switchbackLowerY));
                entryPath.getElements().add(new LineTo(leftLaneX, switchbackLowerY));
                entryPath.getElements().add(new LineTo(leftLaneX, switchbackUpperY));
                entryPath.getElements().add(new LineTo(rampEndPointX, switchbackUpperY));
                duration = 3.2;
            } else {
                entryPath.getElements().add(new LineTo(rightLaneX, chosenAisleY));
                entryPath.getElements().add(new LineTo(cellCenterX, chosenAisleY));
                entryPath.getElements().add(new LineTo(cellCenterX, cellCenterY));
            }
        }

        PathTransition entryEngine = new PathTransition(Duration.seconds(duration), entryPath, dot);
        entryEngine.setOnFinished(e -> {
            if (targetFloor > 0 && rampsEnabled) {
                chainUpwardRampClimb(dot, 1, targetFloor, leftLaneX, rightLaneX, switchbackUpperY, switchbackLowerY, chosenAisleY, cellCenterX, cellCenterY, v, targetCol, targetRow, p, rate, bottomRoadY, assignedToBottomLeft);
            } else {
                finalizeArrivalState(v, targetFloor, targetCol, targetRow, p, rate, cellCenterX, cellCenterY, chosenAisleY, leftLaneX, rightLaneX, switchbackUpperY, switchbackLowerY, bottomRoadY, assignedToBottomLeft);
            }
        });
        entryEngine.play();
    }

    private void assignVehicleColor(Circle dot, int targetFloor, Boolean c) {
        if(c){
            switch (targetFloor) {
                case 0 -> dot.setFill(Color.web("#FFD700")); //Yellow
                case 1 -> dot.setFill(Color.web("#9370DB")); //Purple
                case 2 -> dot.setFill(Color.web("#00FFFF")); //Aqua
                case 3 -> dot.setFill(Color.web("#FF0000")); //Red
                default -> dot.setFill(Color.web("#800080")); //Dark Purple
            }
        }
    }

    //RECURSIVE MULTI-FLOOR UPWARD CLIMB ENGINE
    private void chainUpwardRampClimb(Circle dot, int currentLevel, int targetFloor, double leftLaneX, double rightLaneX, double switchbackUpperY, double switchbackLowerY, double finalAisleY, double cellCenterX, double cellCenterY, Vehicle v, int finalCol, int finalRow, RandomizeDuration p, ParkRate rate, double bottomRoadY, boolean cameFromLeftGate) {
        dot.getProperties().put("currentPhysicalFloor", currentLevel);
        updateDotVisibilityState(dot);

        Bounds firstCellBounds = spotUI[0][0][0].getBoundsInParent();
        Bounds lastCellBounds = spotUI[0][cols - 1][0].getBoundsInParent();
        double rampEndPointX = lastCellBounds.getMaxX() - (firstCellBounds.getWidth() * 0.5);

        if (currentLevel < targetFloor) {
            Path transitPath = new Path();
            transitPath.getElements().add(new MoveTo(rightLaneX, switchbackUpperY));
            transitPath.getElements().add(new LineTo(rightLaneX, switchbackLowerY));
            transitPath.getElements().add(new LineTo(leftLaneX, switchbackLowerY));
            transitPath.getElements().add(new LineTo(leftLaneX, switchbackUpperY));
            transitPath.getElements().add(new LineTo(rampEndPointX, switchbackUpperY));

            PathTransition engine = new PathTransition(Duration.seconds(3.5), transitPath, dot);
            engine.setOnFinished(ev -> chainUpwardRampClimb(dot, currentLevel + 1, targetFloor, leftLaneX, rightLaneX, switchbackUpperY, switchbackLowerY, finalAisleY, cellCenterX, cellCenterY, v, finalCol, finalRow, p, rate, bottomRoadY, cameFromLeftGate));
            engine.play();
        } else {
            Path arrivalPath = new Path();
            arrivalPath.getElements().add(new MoveTo(rightLaneX, switchbackLowerY));

            if (cameFromLeftGate && finalRow == 0) {
                arrivalPath.getElements().add(new LineTo(cellCenterX, switchbackLowerY));
                arrivalPath.getElements().add(new LineTo(cellCenterX, finalAisleY));
            } else {
                arrivalPath.getElements().add(new LineTo(rightLaneX, finalAisleY));
                arrivalPath.getElements().add(new LineTo(cellCenterX, finalAisleY));
            }
            arrivalPath.getElements().add(new LineTo(cellCenterX, cellCenterY));

            PathTransition engine = new PathTransition(Duration.seconds(2.5), arrivalPath, dot);
            engine.setOnFinished(ev -> finalizeArrivalState(v, targetFloor, finalCol, finalRow, p, rate, cellCenterX, cellCenterY, finalAisleY, leftLaneX, rightLaneX, switchbackUpperY, switchbackLowerY, bottomRoadY, cameFromLeftGate));
            engine.play();
        }
    }



    //PARKING STAY STATE STAY & SEQUENTIAL DESCENT SYSTEM
    private void finalizeArrivalState(Vehicle v, int finalFloor, int finalCol, int finalRow, RandomizeDuration p, ParkRate rate, double cellCenterX, double cellCenterY, double finalAisleY, double leftLaneX, double rightLaneX, double switchbackUpperY, double switchbackLowerY, double bottomRoadY, boolean finalGateChoice) {
        currentOccupancy.set(currentOccupancy.get() + 1);
        totalCars.set(totalCars.get() + 1);

        Circle dot = v.getDot();
        dot.getProperties().put("currentPhysicalFloor", finalFloor);
        updateDotVisibilityState(dot);

        final double calculatedStayDuration = p.getRandomizeDuration();

        //pass our locked-in duration to the JavaFX PauseTransition timer
        PauseTransition stayTimer = new PauseTransition(Duration.seconds(calculatedStayDuration));
        stayTimer.setOnFinished(stayEvent -> {
            occupancy[finalFloor][finalCol][finalRow] = null;
            currentOccupancy.set(currentOccupancy.get() - 1);

            Path stallExitPath = new Path();
            stallExitPath.getElements().add(new MoveTo(cellCenterX, cellCenterY));

            if (rampsEnabled && finalFloor > 0) {
                stallExitPath.getElements().add(new LineTo(cellCenterX, finalAisleY));
                stallExitPath.getElements().add(new LineTo(rightLaneX, finalAisleY));
                stallExitPath.getElements().add(new LineTo(rightLaneX, switchbackLowerY));
            } else {
                if (finalGateChoice) {
                    stallExitPath.getElements().add(new LineTo(cellCenterX, finalAisleY));
                    stallExitPath.getElements().add(new LineTo(leftLaneX, finalAisleY));
                    stallExitPath.getElements().add(new LineTo(leftLaneX, bottomRoadY));
                } else {
                    if (finalRow == 0) {
                        stallExitPath.getElements().add(new LineTo(cellCenterX, switchbackLowerY));
                        stallExitPath.getElements().add(new LineTo(rightLaneX, switchbackLowerY));
                    } else {
                        stallExitPath.getElements().add(new LineTo(cellCenterX, finalAisleY));
                        stallExitPath.getElements().add(new LineTo(rightLaneX, finalAisleY));
                        stallExitPath.getElements().add(new LineTo(rightLaneX, switchbackLowerY));
                    }
                }
            }

            PathTransition exitEngine = new PathTransition(Duration.seconds(2.0), stallExitPath, dot);
            exitEngine.setOnFinished(ex -> {
                if (finalFloor > 0 && rampsEnabled) {
                    chainDownwardRampDescent(dot, finalFloor, bottomRoadY, leftLaneX, rightLaneX, switchbackUpperY, switchbackLowerY, v, finalFloor, finalCol, finalRow, rate, calculatedStayDuration, finalGateChoice);
                } else {
                    completeCleanUp(v, finalFloor, finalCol, finalRow, rate, calculatedStayDuration);
                }
            });
            exitEngine.play();
        });
        stayTimer.play();
    }

    private void chainDownwardRampDescent(Circle dot, int currentLevel, double bottomRoadY, double leftLaneX,
                                          double rightLaneX, double switchbackUpperY, double switchbackLowerY,
                                          Vehicle v, int finalFloor, int finalCol, int finalRow, ParkRate rate,
                                          double parkingStayDuration, boolean finalGateChoice) {

        dot.getProperties().put("currentPhysicalFloor", currentLevel);
        updateDotVisibilityState(dot);

        Bounds curBounds = spotUI[currentLevel][0][0].getBoundsInParent();
        Bounds curLast   = spotUI[currentLevel][cols - 1][rows - 1].getBoundsInParent();
        double w = curBounds.getWidth();
        double h = curBounds.getHeight();

        double cLeftX   = curBounds.getMinX() - (w * 0.5);
        double cRightX  = curLast.getMaxX() + (w * 0.5);
        double cUpperY  = curBounds.getMinY() - (h * 0.8);
        double cLowerY  = curBounds.getMinY() - (h * 0.4);
        double cExitY   = curLast.getMaxY() + (h * 0.3);

        Path descentPath = new Path();
        double duration;

        if (currentLevel == finalFloor) {
            descentPath.getElements().add(new MoveTo(cRightX, cLowerY));
            descentPath.getElements().add(new LineTo(cRightX, cUpperY));
            duration = TIME_ORIGIN_EXIT;
        } else if (currentLevel > 0) {
            descentPath.getElements().add(new MoveTo(cRightX, cUpperY));
            descentPath.getElements().add(new LineTo(cLeftX, cUpperY));
            descentPath.getElements().add(new LineTo(cLeftX, cLowerY));
            descentPath.getElements().add(new LineTo(cRightX, cLowerY));
            descentPath.getElements().add(new LineTo(cRightX, cUpperY));
            duration = TIME_INTERMEDIATE_LOOP;
        } else {
            descentPath.getElements().add(new MoveTo(cRightX, cUpperY));
            descentPath.getElements().add(new LineTo(cLeftX, cUpperY));
            duration = TIME_GROUND_CROSS;
        }

        PathTransition engine = new PathTransition(Duration.seconds(duration), descentPath, dot);
        engine.setOnFinished(ev -> {
            if (currentLevel > 0) {
                int nextLevel = currentLevel - 1;
                dot.getProperties().put("currentPhysicalFloor", nextLevel);
                updateDotVisibilityState(dot);
                chainDownwardRampDescent(dot, nextLevel, bottomRoadY, leftLaneX, rightLaneX, switchbackUpperY, switchbackLowerY, v, finalFloor, finalCol, finalRow, rate, parkingStayDuration, finalGateChoice);
            } else {
                Path groundExitPath = new Path();
                groundExitPath.getElements().add(new MoveTo(cLeftX, cUpperY));
                groundExitPath.getElements().add(new LineTo(cLeftX, cExitY));

                PathTransition finalOutEngine = new PathTransition(Duration.seconds(TIME_LOT_EXIT), groundExitPath, dot);
                finalOutEngine.setOnFinished(eOut -> completeCleanUp(v, finalFloor, finalCol, finalRow, rate, parkingStayDuration));
                finalOutEngine.play();
            }
        });
        engine.play();
    }


    //DATA UTILITIES & RESOURCE CLEANUP
    private void completeCleanUp(Vehicle v, int finalFloor, int finalCol, int finalRow, ParkRate rate, double parkingStayDuration) {
        Circle dot = v.getDot();
        Tooltip.uninstall(dot, (Tooltip) dot.getProperties().get("tooltip"));
        layeredCanvas.getChildren().remove(dot);

        recordVehicleData(v, rate, parkingStayDuration);

        int zoneWidth = (int) Math.ceil((double) cols / 3);
        int returnZoneId = Math.min(finalCol / zoneWidth, 2);
        floorStructuralZones.get(finalFloor).get(returnZoneId).add(new Point(finalCol, finalRow));
    }

    private FloorPoint checkMultiFloorTarget() {
        for (int f = 0; f < floors; f++) {
            int zonesInspected = 0;
            int temporaryTurn = activeZoneTurn;

            while (zonesInspected < 3) {
                Queue<Point> targetQueue = floorStructuralZones.get(f).get(temporaryTurn);
                if (!targetQueue.isEmpty()) {
                    activeZoneTurn = (temporaryTurn + 1) % 3;
                    Point p = targetQueue.poll();
                    return new FloorPoint(f, p.x, p.y);
                }
                temporaryTurn = (temporaryTurn + 1) % 3;
                zonesInspected++;
            }
        }
        return null;
    }

    private double determineAisleY(int targetRow, int targetCol, Bounds firstCellBounds) {
        if (targetRow == 0) {
            return firstCellBounds.getMinY() - (firstCellBounds.getHeight() * 0.4);
        } else {
            int servingOddRow = (targetRow % 2 == 0) ? targetRow - 1 : targetRow;
            Bounds oddRowCellBounds = spotUI[0][targetCol][servingOddRow].getBoundsInParent();
            return oddRowCellBounds.getMaxY() + (oddRowCellBounds.getHeight() * 0.25);
        }
    }

    private void recordVehicleData(Vehicle v, ParkRate rate, double parkingStayDuration) {
        v.setTotalDuration(parkingStayDuration);
        v.setExitTime(LocalTime.now());
        v.setAmountPaid(rate.processFeeRate(v));
        totalRevenue.set(totalRevenue.get() + v.getAmountPaid());

        System.out.printf("\n@%s | Vehicle: %s [%s-%s] left | PAID: $%.2f | STAYED: %.1fs | ENTRY: %s",
                v.getExitTime().format(logTimeFormatter), v.getPlate(), v.getModel(), v.getType(),
                v.getAmountPaid(), v.getTotalDuration(), v.getEntryTime().format(logTimeFormatter));
    }

    //GETTERS
    public Pane getLayeredPaneCanvas() { return layeredCanvas; }
    public DoubleProperty getRevenueProperty() { return totalRevenue; }
    public IntegerProperty getOccupancyProperty() { return currentOccupancy; }
    public IntegerProperty getTotalCars() { return totalCars; }
    public int getFloorCount() { return floors; }
    public int getMaxCapacity() { return rows * cols * floors; }
}