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
    private int rows, cols; //TEST
    private Vehicle[][] occupancy;
    private StackPane[][] spotUI;

    private List<Queue<Point>> structuralZones;
    private int activeZoneTurn = 0;

    private Pane layeredCanvas;
    private GridPane backgroundGrid;

    //track which gateway to use for the next incoming car (alternates which gate they entered from)
    private boolean useBottomLeftGateNext = true;

    private DoubleProperty totalRevenue = new SimpleDoubleProperty(0.0);
    private IntegerProperty currentOccupancy = new SimpleIntegerProperty(0);
    private IntegerProperty totalCars = new SimpleIntegerProperty(0);

    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    //CONSTRUCTOR=======================================================================================================
    public ParkingLot(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.occupancy = new Vehicle[cols][rows];
        this.spotUI = new StackPane[cols][rows];
        this.layeredCanvas = new Pane();
        this.backgroundGrid = new GridPane();
        this.structuralZones = new ArrayList<>();

        /*
            structuralZones are logical sector queues that lessens the congestion for Vehicle objects traversing
            the same path, essentially spreading out incoming vehicles. How it is represented:

                ASSUMING the column count is set to 15 (for the sake of simple math):

                -zone 0 represents cols 0-4 (left)
                -zone 1 represents cols 5-9 (middle)
                -zone 2 represents cols 10-14 (right)

                               ZONE 0            ZONE 1               ZONE 2
           ANY ROW#:     | [0][1][2][3][4] | [5][6][7][8][9] | [10][11][12][13][14]

           let's say 3 cars enter, car #1 is responsible for grabbing a spot only in zone 0 if empty, car #2 can only grab
           a spot in zone 1 if empty and car #3 is obvious. After that, the cycle starts again, so car #4 starts to find
           a spot at zone 0 and so on.

           Let's say that zone 0 is full for any row, it would mean that the car would either have to find available spots
           in zone 1 or 2. It makes it easier for the car to determine which zone has more available spots, and it reduces
           the need for that car to look into zone 0 since its already full.
         */
        for (int i = 0; i < 3; i++) {
            structuralZones.add(new ArrayDeque<>());
        }

        //maps out newly generated spots/cells as Point objects
        List<Point> masterList = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                masterList.add(new Point(c, r));
            }
        }

        //shuffles available spots randomly to imitate cars choosing random spots. Think of it as shuffling cards
        Collections.shuffle(masterList);

        //determines the 3 zones in relation to how big the parking lot's column size
        int zoneWidth = (int) Math.ceil((double) cols / 3);
        for (Point p : masterList) {

            //essentially represents the zones 0-2 in relation to how big the parking lot's column size
            int zoneId = Math.min(p.x / zoneWidth, 2);
            structuralZones.get(zoneId).add(p);
        }

        //this is a layout coordinator that ensures that the parking stalls layer and moving vehicle layer are scaled evenly
        //so that Vehicles don't seem like they are accidentally crossing or overlapping through cells
        backgroundGrid.boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> {
            layeredCanvas.setPrefSize(newBounds.getWidth(), newBounds.getHeight());
            layeredCanvas.setMaxSize(newBounds.getWidth(), newBounds.getHeight());
        });

        layeredCanvas.getChildren().add(backgroundGrid);
    }
    //==================================================================================================================

    //generates individual cells by determined by rows/cols in gridlott class and draws out parking lot
    //stalls represents cells/spots
    public void generateParkingLot() {
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
    }

    /*
        This function is responsible for how a Vehicle behaves in terms of:
            -how/where they enter/exit from
            -uses pathfinding when entering/exiting to either find spots or leaving lot
            -records Vehicle data
    */
    public void simulateParking(Vehicle v, ParkRate rate) {
        int zonesInspected = 0; //a safety counter so that this Vehicle doesn't look for cells/spots forever

        //represents a temporary pointer that will hold the parking section depending on where the Vehicle chooses to look at
        Queue<Point> targetedQueue = null;

        //exactly explains this while loop from line 66-72
        while (zonesInspected < structuralZones.size()) {

            //grabs the section queue that matches the Vehicle's turn index (so by default it starts at zone 0)
            targetedQueue = structuralZones.get(activeZoneTurn);

            //updates the index by 1, so since this Vehicle found a spot at zone 0 in any row, index increments by 1
            //for the next Vehicle to find an available spot in zone 1 and so forth. Then it cycles back to Zone 0 after Zone 2.
            activeZoneTurn = (activeZoneTurn + 1) % structuralZones.size();

            //simply means when this Vehicle found a spot in whatever zone they are assigned to, it will stop the loop
            if (!targetedQueue.isEmpty()) {
                break;
            }

            //if the Vehicle is unsuccessful to find a spot in whatever zone they are assigned to, set the targetQueue
            //to null and increment by 1 for that Vehicle to try again and find another available spot in different zone
            targetedQueue = null;
            zonesInspected++;
        }

        if (targetedQueue == null) return; //this prevents any new cars to find a spot if all zones in all rows are taken

        Point reservedPoint = targetedQueue.poll(); //Vehicle snatches first available spot
        int targetCol = reservedPoint.x;
        int targetRow = reservedPoint.y;

        //Vehicle successfully occupies the spot and is recorded on the occupancy 2D array
        occupancy[targetCol][targetRow] = v;

        //this is for dynamic coordinate reading (works perfectly on any grid size)
        Bounds stallBounds = spotUI[targetCol][targetRow].getBoundsInParent();
        double cellCenterX = stallBounds.getMinX() + (stallBounds.getWidth() / 2.0);
        double cellCenterY = stallBounds.getMinY() + (stallBounds.getHeight() / 2.0);

        Bounds backgroundBounds = backgroundGrid.getBoundsInParent();
        Bounds firstCellBounds = spotUI[0][0].getBoundsInParent();
        Bounds lastCellBounds = spotUI[cols - 1][rows - 1].getBoundsInParent();

        /*
        This section is for row aisle (necessary for how Vehicles decide on which row to go on before finding their
        respective cells)
        */
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

                    int zoneWidth = (int) Math.ceil((double) cols / 3);
                    int returnZoneId = Math.min(targetCol / zoneWidth, 2);

                    structuralZones.get(returnZoneId).add(new Point(targetCol, targetRow));
                });

                exitEngine.play();
            });
            view_stayTimer_block: stayTimer.play();
        });

        entryEngine.play();
    }

    //getters
    public Pane getLayeredPaneCanvas() { return layeredCanvas; }
    public DoubleProperty getRevenueProperty() { return totalRevenue; }
    public IntegerProperty getOccupancyProperty() { return currentOccupancy; }
    public IntegerProperty getTotalCars() { return totalCars; }
    public int getMaxCapacity() { return rows * cols; }
}

