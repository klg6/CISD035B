package com.example.gridlott;

import javafx.animation.PathTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
    private final List<Pane> pathOverlayLayers = new ArrayList<>(); //Overlay vector sheets for structural paths
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
        this.pathOverlayLayers.clear();

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

            Pane overlay = new Pane();
            overlay.setMouseTransparent(true);
            pathOverlayLayers.add(overlay);

            layeredCanvas.getChildren().addAll(grid, overlay);
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
        }

        if (Config.showPaths) {
            Platform.runLater(() -> renderFloorPaths(0));
        }
    }

    private void configureGridStyle(GridPane grid, boolean isGroundFloor) {
        grid.setStyle("-fx-background-color: #131314;");
        grid.setPadding(new Insets(30, 40, 30, 40));
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setAlignment(Pos.CENTER);
        if (!isGroundFloor) {
            grid.setVisible(false);
            grid.setMouseTransparent(true);
        }
    }

    private StackPane createParkingStall(int row) {
        Rectangle stall = new Rectangle(CELL_WIDTH, CELL_HEIGHT);
        stall.setFill(Color.TRANSPARENT);
        stall.setStroke(Color.web("#2d2d30"));
        stall.setStrokeWidth(0.8);
        stall.setMouseTransparent(true);

        StackPane spot = new StackPane(stall);
        int bottomMargin = (row % 2 == 0) ? 0 : 25;
        GridPane.setMargin(spot, new Insets(0, 1, bottomMargin, 1));
        return spot;
    }

    //DYNAMIC VISIBILITY MANAGEMENT ENGINE
    public void showFloor(int floorIndex) {
        this.currentlyViewedFloor = floorIndex;

        for (int f = 0; f < floors; f++) {
            boolean isTarget = (f == floorIndex);
            floorGrids.get(f).setVisible(isTarget);
            floorGrids.get(f).setMouseTransparent(!isTarget);

            if (f < pathOverlayLayers.size()) {
                pathOverlayLayers.get(f).getChildren().clear();
                if (isTarget && Config.showPaths) {
                    renderFloorPaths(floorIndex);
                }
            }
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

    private void renderFloorPaths(int floorIndex) {//shows the pathing if enabled in Config class
        Pane currentOverlay = pathOverlayLayers.get(floorIndex);
        currentOverlay.getChildren().clear();

        Bounds first = spotUI[floorIndex][0][0].getBoundsInParent();
        Bounds last = spotUI[floorIndex][cols - 1][rows - 1].getBoundsInParent();

        double leftX = first.getMinX() - 20.0;
        double rightX = last.getMaxX() + 20.0;

        double topAisleY = determineAisleY(0, 0, first);
        double bottomAisleY = determineAisleY(rows - 1, 0, first);

        Path redRoadNetwork = new Path();
        redRoadNetwork.setStroke(Color.web("#39FF14"));
        redRoadNetwork.setStrokeWidth(2.0);
        redRoadNetwork.setOpacity(1);

        //draw the main vertical spine lines
        redRoadNetwork.getElements().addAll(
                new MoveTo(leftX, topAisleY),
                new LineTo(leftX, bottomAisleY),
                new MoveTo(rightX, topAisleY),
                new LineTo(rightX, bottomAisleY)
        );

        //adds the top gate tail (for every floor)
        Bounds topGate = spotUI[floorIndex][cols-1][0].getBoundsInParent();
        double extensionOffsets = topGate.getHeight() - 30; //Adjust this value to match your spacing
        redRoadNetwork.getElements().addAll(
                new MoveTo(rightX, topAisleY),
                new LineTo(rightX, topAisleY - extensionOffsets)
        );

        double extension = first.getHeight() * (1.0 - (30.0 / first.getHeight()));
        double rampEndPointX = last.getMaxX() - (first.getWidth() * 0.5); //this is where dots disappear to go up a floor

        if(floorIndex == 0){ //indicates if its 1st floor

            if(Config.rows % 2 == 1){

                Bounds bottomGate = spotUI[floorIndex][0][rows-1].getBoundsInParent(); //adds the bottom gate tail
                double extensionOffset = bottomGate.getHeight() + 32; // Adjust this value to match your spacing
                redRoadNetwork.getElements().addAll(
                        new MoveTo(leftX, bottomAisleY),
                        new LineTo(leftX, bottomAisleY + extensionOffset)
                );

                redRoadNetwork.getElements().addAll( //adds the ramp
                        new MoveTo(leftX, topAisleY), //start at the current top spine point
                        new LineTo(leftX, topAisleY - extension), //draw the vertical extension upward
                        new LineTo(rampEndPointX, topAisleY - extension) //trace horizontally from the spine to the rampEndPointX
                );

            } else { //if its even
                //adds the bottom gate tail
                Bounds bottomGate = spotUI[floorIndex][0][rows-1].getBoundsInParent();
                double extensionOffset = bottomGate.getHeight() - 50; // Adjust this value to match your spacing
                redRoadNetwork.getElements().addAll(
                        new MoveTo(leftX, bottomAisleY),
                        new LineTo(leftX, bottomAisleY + extensionOffset)
                );

                redRoadNetwork.getElements().addAll( //adds the ramp
                        new MoveTo(leftX, topAisleY), //start at the current top spine point
                        new LineTo(leftX, topAisleY - extension), //draw the vertical extension upward
                        new LineTo(rampEndPointX, topAisleY - extension) //trace horizontally from the spine to the rampEndPointX
                );
            }

        } else if(floorIndex < Config.floors-1) { //indicates if its any floor between first and last floor

            redRoadNetwork.getElements().addAll( //adds the ramp
                    new MoveTo(leftX, topAisleY), //start at the current top spine point
                    new LineTo(leftX, topAisleY - extension), //draw the vertical extension upward
                    new LineTo(rampEndPointX, topAisleY - extension) //trace horizontally from the spine to the rampEndPointX
            );

        } //if it's the last floor don't include the ramp

        for (int r = 0; r < rows; r++) { //draw Aisles and Vertical Connectors to each cell
            double aisleY = determineAisleY(r, 0, first);
            redRoadNetwork.getElements().addAll(new MoveTo(leftX, aisleY), new LineTo(rightX, aisleY));

            for (int c = 0; c < cols; c++) {
                Bounds spot = spotUI[floorIndex][c][r].getBoundsInParent();
                double centerX = spot.getMinX() + (spot.getWidth() / 2);
                redRoadNetwork.getElements().addAll(
                        new MoveTo(centerX, aisleY),
                        new LineTo(centerX, spot.getMinY() + (spot.getHeight()/2))
                );
            }
        }
        currentOverlay.getChildren().add(redRoadNetwork);
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
        double bottomRoadY = lastCellBounds.getMaxY() + (firstCellBounds.getHeight() * 0.4); //this used to be 0.3

        double switchbackUpperY = firstCellBounds.getMinY() - (firstCellBounds.getHeight() * 0.815); //this used to be 0.8
        double switchbackLowerY = determineAisleY(0,0, firstCellBounds);
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
                case 0 -> dot.setFill(Color.web("#FFD700"));
                case 1 -> dot.setFill(Color.web("#9370DB"));
                case 2 -> dot.setFill(Color.web("#00FFFF"));
                case 3 -> dot.setFill(Color.web("#FF0000"));
                default -> dot.setFill(Color.web("#800080"));
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
        double cUpperY  = curBounds.getMinY() - (h * 0.815);
        double cLowerY  = curBounds.getMinY() - (h * 0.275);
        double cExitY   = curLast.getMaxY() + (h * 0.3);

        // BLUE CIRCLE X coordinate (center of the last column)
        double cRampX = curLast.getMaxX() - (w * 0.5);

        Path descentPath = new Path();
        double duration;

        if (currentLevel == finalFloor) {
            //leaving the parking spot
            descentPath.getElements().add(new MoveTo(cRightX, cLowerY));
            //drive UP the orange spine to exit the floor
            descentPath.getElements().add(new LineTo(cRightX, cUpperY));
            duration = TIME_ORIGIN_EXIT;

        } else if (currentLevel > 0) {
            //spawn exactly at the Blue Circle (coming down from the floor above)
            descentPath.getElements().add(new MoveTo(cRampX, cUpperY));
            //drive across the top aisle and loop the floor
            descentPath.getElements().add(new LineTo(cLeftX, cUpperY));
            descentPath.getElements().add(new LineTo(cLeftX, cLowerY));
            descentPath.getElements().add(new LineTo(cRightX, cLowerY));
            //drive UP the orange spine to exit the floor
            descentPath.getElements().add(new LineTo(cRightX, cUpperY));
            duration = TIME_INTERMEDIATE_LOOP;

        } else {
            //GROUND FLOOR: spawn exactly at the cRampX
            descentPath.getElements().add(new MoveTo(cRampX, cUpperY));
            //drive straight across to the left exit
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
            Bounds row0Bounds = spotUI[0][targetCol][0].getBoundsInParent();
            double row0TopY = row0Bounds.getMinY();
            return row0TopY - 18.0;
        } else {
            int rowAbove = (targetRow % 2 == 0) ? targetRow - 1 : targetRow;
            int rowBelow = rowAbove + 1;

            Bounds boundsAbove = spotUI[0][targetCol][rowAbove].getBoundsInParent();

            if (rowBelow >= rows) {
                double rowAboveBottomY = boundsAbove.getMaxY();
                return rowAboveBottomY + 12.5;
            }

            Bounds boundsBelow = spotUI[0][targetCol][rowBelow].getBoundsInParent();

            double bottomOfAboveRow = boundsAbove.getMaxY();
            double topOfBelowRow = boundsBelow.getMinY();

            return bottomOfAboveRow + ((topOfBelowRow - bottomOfAboveRow) / 2.0);
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