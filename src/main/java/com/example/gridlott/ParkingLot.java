package com.example.gridlott;

import javafx.animation.PathTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
    private int rows, cols;
    private Vehicle[][] occupancy;
    private StackPane[][] spotUI;

    private List<Queue<Point>> structuralZones;
    private int activeZoneTurn = 0;

    private Pane layeredCanvas;
    private GridPane backgroundGrid;

    //Track which gateway to use for the next incoming car (alternates which gate they entered from)
    private boolean useBottomLeftGateNext = true;

    private DoubleProperty totalRevenue = new SimpleDoubleProperty(0.0);
    private IntegerProperty currentOccupancy = new SimpleIntegerProperty(0);
    private IntegerProperty totalCars = new SimpleIntegerProperty(0);

    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ParkingLot(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.occupancy = new Vehicle[cols][rows];
        this.spotUI = new StackPane[cols][rows];
        this.layeredCanvas = new Pane();
        this.backgroundGrid = new GridPane();
        this.structuralZones = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            structuralZones.add(new ArrayDeque<>());
        }

        List<Point> masterList = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                masterList.add(new Point(c, r));
            }
        }
        Collections.shuffle(masterList);

        for (Point p : masterList) {
            int zoneId = p.x / 5;
            structuralZones.get(zoneId).add(p);
        }

        backgroundGrid.boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> {
            layeredCanvas.setPrefSize(newBounds.getWidth(), newBounds.getHeight());
            layeredCanvas.setMaxSize(newBounds.getWidth(), newBounds.getHeight());
        });

        layeredCanvas.getChildren().add(backgroundGrid);
    }

    public GridPane generateParkingLot() {
        backgroundGrid.setStyle("-fx-background-color: #1a1a1a;");
        backgroundGrid.setPadding(new Insets(20));
        backgroundGrid.setAlignment(Pos.CENTER);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Rectangle stall = new Rectangle(40, 70);
                stall.setFill(Color.TRANSPARENT);
                stall.setStroke(Color.grayRgb(60));
                stall.setMouseTransparent(true);

                StackPane spot = new StackPane(stall);
                int vMargin = (r % 2 == 0) ? 0 : 40;
                GridPane.setMargin(spot, new Insets(0, 2, vMargin, 2));

                backgroundGrid.add(spot, c, r);
                spotUI[c][r] = spot;
            }
        }
        return backgroundGrid;
    }

    /*
        This function is responsible for how a Vehicle behaves in terms of:
            -how/where they enter/exit from
            -using pathfinding when entering/exiting to either find spots or leaving lot
            -records Vehicle data
    */
    public void simulateParking(Vehicle v, ParkRate rate) {
        int zonesInspected = 0;
        Queue<Point> targetedQueue = null;

        while (zonesInspected < structuralZones.size()) {
            targetedQueue = structuralZones.get(activeZoneTurn);
            activeZoneTurn = (activeZoneTurn + 1) % structuralZones.size();

            if (!targetedQueue.isEmpty()) {
                break;
            }
            targetedQueue = null;
            zonesInspected++;
        }

        if (targetedQueue == null) return;

        Point reservedPoint = targetedQueue.poll();
        int targetCol = reservedPoint.x;
        int targetRow = reservedPoint.y;
        occupancy[targetCol][targetRow] = v;

        //this is for dynamic coordinate reading (works perfectly on any grid size)
        Bounds stallBounds = spotUI[targetCol][targetRow].getBoundsInParent();
        double cellCenterX = stallBounds.getMinX() + (stallBounds.getWidth() / 2.0);
        double cellCenterY = stallBounds.getMinY() + (stallBounds.getHeight() / 2.0);

        Bounds backgroundBounds = backgroundGrid.getBoundsInParent();
        Bounds firstCellBounds = spotUI[0][0].getBoundsInParent();
        Bounds lastCellBounds = spotUI[cols - 1][rows - 1].getBoundsInParent();

        //This section is for row aisle (necessary for how Vehicles decide on which row to go on before finding their
        //respective cells)
        double chosenAisleY;
        if (targetRow == 0) {
            //row 0 always uses the perimeter track running directly above it
            chosenAisleY = firstCellBounds.getMinY() - 25.0;
        } else {
            //finds the closest odd row serving this specific block
            //if targetRow is 1 or 2 -> uses aisle below row 1
            //if targetRow is 3 or 4 -> uses aisle below row 3
            //if targetRow is 13 or 14 -> uses aisle below row 13
            int servingOddRow = targetRow;

            if (servingOddRow % 2 == 0) {
                servingOddRow = targetRow - 1; //drop back to the companion odd row directly above it
            }

            //grab the precise live bounds of that specific calculated odd row cell
            Bounds oddRowCellBounds = spotUI[targetCol][servingOddRow].getBoundsInParent();
            chosenAisleY = oddRowCellBounds.getMaxY() + 20.0; //target the center of the 40px road gap below it
        }

        //this defines both gateway edge portals (Scales instantly to backgroundGrid size)
        //this is where cars enter/exit from either bottom left or top right of parking lot
        double leftLaneX = firstCellBounds.getMinX() - 25.0;
        double bottomRoadY = lastCellBounds.getMaxY() + 20.0;
        double rightLaneX = lastCellBounds.getMaxX() + 25.0;
        double topRoadY = firstCellBounds.getMinY() - 25.0;
        final boolean assignedToBottomLeft = useBottomLeftGateNext;
        useBottomLeftGateNext = !useBottomLeftGateNext;

        //builds inbound pathways for Vehicles to traverse to their respective cells once found
        Path entryJourneyPath = new Path();
        if (assignedToBottomLeft) {
            entryJourneyPath.getElements().add(new MoveTo(leftLaneX, bottomRoadY));
            entryJourneyPath.getElements().add(new LineTo(leftLaneX, chosenAisleY));
        } else {
            entryJourneyPath.getElements().add(new MoveTo(rightLaneX, topRoadY));
            entryJourneyPath.getElements().add(new LineTo(rightLaneX, chosenAisleY));
        }
        entryJourneyPath.getElements().add(new LineTo(cellCenterX, chosenAisleY));
        entryJourneyPath.getElements().add(new LineTo(cellCenterX, cellCenterY));

        //this is for how the Vehicle objects (or dots) traverse along the path (ENTERING)
        PathTransition entryEngine = new PathTransition(Duration.seconds(2.0), entryJourneyPath, v.getDot());
        layeredCanvas.getChildren().add(v.getDot());

        //saves final state copies for the nested timeline callbacks
        //what this exactly does is once the vehicle exits their cells, it will reference a point where they initially
        //entered from, before they traverse and arrive there
        final Point finalPoint = reservedPoint;
        final boolean finalGateChoice = assignedToBottomLeft;
        final double finalAisleY = chosenAisleY;

        //event toggles once this Vehicle object entered the parking lot to find cell
        entryEngine.setOnFinished(moveEvent -> {

            //this means that once a vehicle finds a spot, update the live dashboard on current occupancy and
            //total cars in a lifetime (throughout how long the application has been running for)
            currentOccupancy.set(currentOccupancy.get() + 1);
            totalCars.set(totalCars.get() + 1);

            //this is for how long a Vehicle object occupies a cell for (as of rn it's between 1 minute to 3 minutes)
            double parkingStayDuration = 60 + (new Random().nextDouble() * 180);
            PauseTransition stayTimer = new PauseTransition(Duration.seconds(parkingStayDuration));

            //event toggles once the Vehicle object is leaving the cell
            stayTimer.setOnFinished(stayEvent -> {
                occupancy[targetCol][targetRow] = null;//sets the cell null so vehicles can check if its actually empty
                currentOccupancy.set(currentOccupancy.get() - 1);

                //builds outbound pathway once the Vehicle object is done parking and left cell
                Path exitJourneyPath = new Path();
                exitJourneyPath.getElements().add(new MoveTo(cellCenterX, cellCenterY));
                exitJourneyPath.getElements().add(new LineTo(cellCenterX, finalAisleY));

                //depending on where this Vehicle arrived from, it follows the same path but backwards
                if (finalGateChoice) {
                    exitJourneyPath.getElements().add(new LineTo(leftLaneX, finalAisleY));
                    exitJourneyPath.getElements().add(new LineTo(leftLaneX, bottomRoadY));
                } else {
                    exitJourneyPath.getElements().add(new LineTo(rightLaneX, finalAisleY));
                    exitJourneyPath.getElements().add(new LineTo(rightLaneX, topRoadY));
                }

                //this is for how the Vehicle objects (or dots) traverse along the path (EXITING)
                PathTransition exitEngine = new PathTransition(Duration.seconds(2.0), exitJourneyPath, v.getDot());

                //event toggles right when this Vehicle is at their designated exits
                exitEngine.setOnFinished(cleanUpEvent -> {
                    Tooltip.uninstall(v.getDot(), (Tooltip) v.getDot().getProperties().get("tooltip")); //saves a bit of memory
                    layeredCanvas.getChildren().remove(v.getDot()); //also saves a bit of memory

                    //this little section is for recording this Vehicle's data
                    v.setTotalDuration(parkingStayDuration); //how long they parked
                    v.setExitTime(LocalTime.now()); //when they exited
                    v.setAmountPaid(rate.processFeeRate(v)); //charges Vehicle for how long they parked
                    totalRevenue.set(totalRevenue.get() + v.getAmountPaid()); //updates and adds onto the revenue count

                    System.out.printf("\n@%s | Vehicle: %s [%s-%s] left garage | PAID: $%.2f | STAYED: %.1fs | ENTRY: %s",
                            v.getExitTime().format(logTimeFormatter), v.getPlate(), v.getModel(), v.getType(),
                            v.getAmountPaid(), v.getTotalDuration(), v.getEntryTime().format(logTimeFormatter));

                    structuralZones.get(targetCol / 5).add(new Point(targetCol, targetRow));
                });

                exitEngine.play();
            });
            view_stayTimer_block: stayTimer.play();
        });

        entryEngine.play();
    }

    public Pane getLayeredPaneCanvas() { return layeredCanvas; }
    public DoubleProperty getRevenueProperty() { return totalRevenue; }
    public IntegerProperty getOccupancyProperty() { return currentOccupancy; }
    public IntegerProperty getTotalCars() { return totalCars; }
    public int getMaxCapacity() { return rows * cols; }
}

