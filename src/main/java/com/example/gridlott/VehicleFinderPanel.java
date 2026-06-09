package com.example.gridlott;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.List;

public class VehicleFinderPanel extends VBox {

    private boolean isExpanded = false;
    private final VBox contentArea;
    private final Button toggleButton;
    private ComboBox<String> categoryBox;
    private TextField searchInput;
    private final Label titleLabel;

    //HIGH PERFORMANCE LISTVIEW ENGINE
    private final ListView<Object> resultsListView;
    private final ObservableList<Object> vehicleData;

    private ParkingLot currentLot;
    private String lastSearchedCategory = "";
    private String lastSearchedQuery = "";

    //PERFORMANCE TRACKERS
    private javafx.animation.Timeline refreshTimeline;
    private double perSecondRefresher = 0.5;
    private Button searchButton;

    // for vehicle status filter buttons
    private ToggleGroup filterGroup;
    private String currentFilter = "All";
    ToggleButton allBtn, transBtn, parkBtn, exitBtn;

    public VehicleFinderPanel(ParkingLot lot) {

        this.currentLot = lot;

        //panel base style
        this.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #2d2d30; -fx-border-radius: 5; -fx-background-radius: 5;");
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.setAlignment(Pos.TOP_LEFT);

        //header (icon + label)
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        toggleButton = new Button("\uD83D\uDD0D"); //magnifying glass icon
        toggleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #8e8e93; -fx-font-size: 16px; -fx-cursor: hand;");
        toggleButton.setOnAction(e -> togglePanel());

        titleLabel = new Label("VEHICLE / CELL FINDER");
        titleLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        titleLabel.setVisible(false);
        titleLabel.setManaged(false);

        header.getChildren().addAll(toggleButton, titleLabel);

        //SETUP VIRTUALIZED LISTVIEW
        vehicleData = FXCollections.observableArrayList();
        resultsListView = new ListView<>(vehicleData);
        resultsListView.setMaxWidth(Double.MAX_VALUE);
        resultsListView.setStyle("-fx-background-color: #1a1a1a; -fx-control-inner-background: #1a1a1a; -fx-border-color: #333333; -fx-border-radius: 5;");
        resultsListView.setPrefHeight(350);
        resultsListView.setPrefWidth(220);
        resultsListView.setVisible(false);
        resultsListView.setManaged(false);

        //cell factory mapping data
        resultsListView.setCellFactory(lv -> new ListCell<Object>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else if (item instanceof Vehicle) {
                    setGraphic(buildResultRow((Vehicle) item, lastSearchedCategory));
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else if (item instanceof CellRef) {
                    CellRef cell = (CellRef) item;
                    setGraphic(buildSelectableCellRow(cell.floor, cell.row, cell.col));
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else if (item instanceof String) {
                    String text = (String) item;
                    Label label = new Label(text);
                    if (text.startsWith(" FLOOR")) {
                        label.setStyle("-fx-text-fill: #39FF14; -fx-font-weight: bold; -fx-padding: 8 0 4 0; -fx-font-family: 'Segoe UI';");
                    } else {
                        label.setStyle("-fx-text-fill: #8e8e93; -fx-font-family: 'Segoe UI'; -fx-padding: 5;");
                    }
                    setGraphic(label);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        applyScrollbarTheme();

        //content area
        contentArea = new VBox(8);
        contentArea.setVisible(false);
        contentArea.setManaged(false);

        //STATUS FILTERS buttons
        HBox filterBar = new HBox(5);
        filterGroup = new ToggleGroup();

        allBtn = new ToggleButton("All");
        transBtn = new ToggleButton("Entering");
        parkBtn = new ToggleButton("Parked");
        exitBtn = new ToggleButton("Exiting");

        ToggleButton[] buttons = {allBtn, transBtn, parkBtn, exitBtn};

        //status themes
        String base = "-fx-font-size: 10px; -fx-background-radius: 4; -fx-padding: 3 8 3 8; -fx-cursor: hand; ";
        String inactive = base + "-fx-background-color: #333333; -fx-text-fill: #aaaaaa;";
        String styleAll = base + "-fx-background-color: #ffffff; -fx-text-fill: black;";
        String styleTrans = base + "-fx-background-color: #28a745; -fx-text-fill: white;"; // Green
        String stylePark = base + "-fx-background-color: #6c757d; -fx-text-fill: white;"; // Gray
        String styleExit = base + "-fx-background-color: #fd7e14; -fx-text-fill: white;"; // Orange

        for (ToggleButton btn : buttons) {
            btn.setToggleGroup(filterGroup);
            btn.setStyle(inactive);
        }
        allBtn.setSelected(true);
        allBtn.setStyle(styleAll);

        filterGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
                return;
            }

            for (ToggleButton b : buttons) b.setStyle(inactive);

            ToggleButton selected = (ToggleButton) newVal;
            currentFilter = selected.getText();

            if (selected == allBtn) selected.setStyle(styleAll);
            else if (selected == transBtn) selected.setStyle(styleTrans);
            else if (selected == parkBtn) selected.setStyle(stylePark);
            else if (selected == exitBtn) selected.setStyle(styleExit);

            //only run a background refresh if the list is currently open and active
            if (resultsListView.isVisible()) {
                executeSearch(lastSearchedCategory, lastSearchedQuery);
            }
        });

        filterBar.getChildren().addAll(allBtn, transBtn, parkBtn, exitBtn);

        double inputWidth = 180.0;

        //ComboBox styling
        categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Ticket#", "Plate", "Model", "Type", "Entry Time", "Cell");
        categoryBox.setPromptText("Select Category");
        categoryBox.setPrefWidth(inputWidth);
        categoryBox.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: #39FF14; -fx-border-color: #39FF14; -fx-background-radius: 5; -fx-border-radius: 5;");

        categoryBox.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("-fx-background-color: #1a1a1a;");
                } else {
                    setText(item);
                    setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: #39FF14;");
                }
            }
        });

        categoryBox.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select Category");
                } else {
                    setText(item);
                    setTextFill(javafx.scene.paint.Color.web("#39FF14"));
                }
            }
        });

        searchInput = new TextField();
        searchInput.setPromptText("Enter value...");
        searchInput.setPrefWidth(inputWidth);
        searchInput.setStyle("-fx-background-color: #111111; -fx-text-fill: white; -fx-border-color: #333333; -fx-border-radius: 3;");

        searchButton = new Button("VIEW ALL");
        searchButton.setStyle("-fx-background-color: #FF00FF; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        searchButton.setMaxWidth(Double.MAX_VALUE);
        searchButton.setOnAction(e -> handleSearchAction()); //only click triggers search

        //only update the button, don't show the list
        categoryBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCell = "Cell".equals(newVal);
            filterBar.setVisible(!isCell);
            filterBar.setManaged(!isCell);

            //let checkButtonState decide if we should restore the results or not.
            checkButtonState();
        });

        //REBOOT
        BooleanBinding isNoCategorySelected = categoryBox.valueProperty().isNull();
        allBtn.disableProperty().bind(isNoCategorySelected);
        transBtn.disableProperty().bind(isNoCategorySelected);
        parkBtn.disableProperty().bind(isNoCategorySelected);
        exitBtn.disableProperty().bind(isNoCategorySelected);

        searchButton.setDisable(true);

        searchInput.textProperty().addListener((obs, oldVal, newVal) -> {
            checkButtonState();
        });

        searchButton.disabledProperty().addListener((obs, wasDisabled, isDisabled) -> {
            if (isDisabled) {
                searchButton.setStyle("-fx-background-color: #333333; -fx-text-fill: #888888; -fx-font-weight: bold; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px;");
            } else {
                boolean isEmpty = (searchInput.getText() == null || searchInput.getText().trim().isEmpty());
                searchButton.setStyle(isEmpty ?
                        "-fx-background-color: #FF00FF; -fx-text-fill: black; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px;" :
                        "-fx-background-color: #39FF14; -fx-text-fill: black; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px;");
            }
        });

        //REFRESH LIVE VEHICLE STATUS
        refreshTimeline = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                Duration.seconds(perSecondRefresher),
                e -> {
                    if (!isExpanded || currentLot == null) return;

                    //only update if the user is actually looking at a visible search
                    if (!resultsListView.isVisible()) return;

                    if (lastSearchedCategory != null && lastSearchedCategory.equals(categoryBox.getValue())) {
                        executeSearch(lastSearchedCategory, lastSearchedQuery);
                    }
                }
        ));
        refreshTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        refreshTimeline.play();

        //ASSEMBLE UI WITH LISTVIEW
        contentArea.getChildren().addAll(resultsListView, filterBar, categoryBox, searchInput, searchButton);
        this.getChildren().addAll(header, contentArea);
    }

    //toggles for the magnifying glass icon (collapses tab or expands it)
    private void togglePanel() {
        isExpanded = !isExpanded;

        contentArea.setVisible(isExpanded);
        contentArea.setManaged(isExpanded);

        titleLabel.setVisible(isExpanded);
        titleLabel.setManaged(isExpanded);

        FadeTransition ft = new FadeTransition(Duration.millis(200), contentArea);
        ft.setFromValue(isExpanded ? 0 : 1);
        ft.setToValue(isExpanded ? 1 : 0);
        ft.play();
    }

    public void clearInputs() {
        categoryBox.getSelectionModel().clearSelection();
        categoryBox.setPromptText("Select Category");
        searchInput.clear();
        searchInput.setPromptText("Enter value...");
    }

    // THIS METHOD ALONE CONTROLS VISIBILITY NOW
    private void handleSearchAction() {
        String currentCat = categoryBox.getValue();
        String currentText = searchInput.getText().trim();

        //force the list to display
        resultsListView.setVisible(true);
        resultsListView.setManaged(true);

        executeSearch(currentCat, currentText);

        //save search query just in case the user comes back to the same category and input
        lastSearchedCategory = currentCat;
        lastSearchedQuery = currentText;

        checkButtonState();
    }

    private void executeSearch(String category, String query) {
        if (currentLot == null) return;

        this.lastSearchedCategory = category;
        this.lastSearchedQuery = query;

        if ("Cell".equals(category)) {
            renderCellStructure(query);
        } else {
            renderVehicleSearch(category, query);
        }
    }

    private void renderCellStructure(String query) {
        int floors = currentLot.getFloorCount();
        int rows = currentLot.getRows();
        int cols = currentLot.getCols();
        String lowerQuery = (query == null) ? "" : query.toLowerCase().trim();

        List<Object> items = new java.util.ArrayList<>();

        for (int f = 0; f < floors; f++) {
            List<Object> floorCells = new java.util.ArrayList<>();
            boolean floorHasMatches = false;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    String coord = (f + 1) + "," + (r + 1) + "," + (c + 1);

                    if (!lowerQuery.isEmpty() && !coord.contains(lowerQuery)) continue;

                    floorCells.add(new CellRef(f + 1, r + 1, c + 1));
                    floorHasMatches = true;
                }
            }

            if (floorHasMatches || lowerQuery.isEmpty()) {
                items.add(" FLOOR " + (f + 1));
                items.addAll(floorCells);
            }
        }

        Platform.runLater(() -> {
            vehicleData.setAll(items);
        });
    }

    private HBox buildSelectableCellRow(int f, int r, int c) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5));

        String defaultStyle = "-fx-background-color: #222222; -fx-background-radius: 3; -fx-border-color: #333333; -fx-border-radius: 3; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: #333333; -fx-background-radius: 3; -fx-border-color: #39FF14; -fx-border-radius: 3; -fx-cursor: hand;";

        row.setStyle(defaultStyle);

        Label infoLabel = new Label("[" + (f) + "," + (r) + "," + (c) + "]");
        infoLabel.setStyle("-fx-text-fill: white;");
        row.getChildren().add(infoLabel);

        boolean isOccupied = currentLot.isCellOccupied(f-1, r-1 , c-1 );

        if (isOccupied) {
            Label occupiedLabel = new Label("(Reserved)");
            occupiedLabel.setStyle("-fx-text-fill: #888888; -fx-font-style: italic; -fx-font-size: 10px;");
            row.getChildren().add(occupiedLabel);
        }

        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e -> row.setStyle(defaultStyle));

        row.setOnMouseClicked(e -> {
            row.setStyle(hoverStyle);
            System.out.println("Selected Structural Cell: " + (f) + "," + (r) + "," + (c));
        });

        return row;
    }

    private void renderVehicleSearch(String category, String query){
        if (currentLot == null) return;

        List<Vehicle> parkedCars = currentLot.getActiveVehicles();
        List<Vehicle> matches = new java.util.ArrayList<>();
        String lowerQuery = (query == null) ? "" : query.toLowerCase().trim();
        boolean isBrowseMode = lowerQuery.isEmpty();

        for (Vehicle car : parkedCars) {
            if (!"Cell".equals(category) && !"All".equals(currentFilter)) {
                if (currentFilter.equals("Entering") && car.getCurrentStatus() != Vehicle.Status.TRANSITING) continue;
                if (currentFilter.equals("Parked") && car.getCurrentStatus() != Vehicle.Status.PARKED) continue;
                if (currentFilter.equals("Exiting") && car.getCurrentStatus() != Vehicle.Status.EXITING) continue;
            }

            if (isBrowseMode) {
                matches.add(car);
            } else {
                String val = getSortableValue(car, category).toLowerCase();
                if (val.contains(lowerQuery)) {
                    matches.add(car);
                }
            }
        }

        matches.sort((v1, v2) -> {
            String s1 = getSortableValue(v1, category).toLowerCase();
            String s2 = getSortableValue(v2, category).toLowerCase();

            if (category.equals("Entry Time")) {
                return v1.getEntryTime().compareTo(v2.getEntryTime());
            }

            if (category.equals("Cells")) {
                String[] nums1 = s1.replaceAll("[^0-9]+", " ").trim().split(" ");
                String[] nums2 = s2.replaceAll("[^0-9]+", " ").trim().split(" ");

                for (int i = 0; i < Math.min(nums1.length, nums2.length); i++) {
                    int n1 = Integer.parseInt(nums1[i]);
                    int n2 = Integer.parseInt(nums2[i]);
                    if (n1 != n2) return Integer.compare(n1, n2);
                }
                return Integer.compare(nums1.length, nums2.length);
            }

            int idx1 = s1.indexOf(lowerQuery);
            int idx2 = s2.indexOf(lowerQuery);
            if (idx1 != idx2) return Integer.compare(idx1, idx2);

            return s1.compareTo(s2);
        });

        List<Object> items = new java.util.ArrayList<>();
        if (matches.isEmpty()) {
            items.add("No vehicles found: '" + query + "'");
        } else {
            items.addAll(matches);
        }

        Platform.runLater(() -> {
            vehicleData.setAll(items);
        });
    }

    private HBox buildResultRow(Vehicle car, String category) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5));
        row.setStyle("-fx-background-color: #222222; -fx-background-radius: 3; -fx-border-color: #333333; -fx-border-radius: 3; -fx-cursor: hand;");

        Circle floorDot = new Circle(4);
        floorDot.setFill(car.getDot().getFill());
        row.getChildren().add(floorDot);

        String displayText;
        switch (category) {
            case "Ticket#": displayText = "#" + car.getTicketNumber(); break;
            case "Plate": displayText = car.getPlate(); break;
            case "Model": displayText = car.getModel().toString(); break;
            case "Type": displayText = car.getType().toString(); break;
            case "Entry Time": displayText = car.getEntryTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")); break;
            case "Cell": displayText = currentLot.getVehicleCoordinates(car); break;
            default: displayText = car.getPlate(); break;
        }

        Label infoLabel = new Label(displayText);
        infoLabel.setStyle("-fx-text-fill: white;");
        infoLabel.setMinWidth(0);
        infoLabel.setMaxWidth(120);
        infoLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        row.getChildren().add(infoLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        HBox rightGroup = new HBox(2);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);

        Integer floor = (Integer) car.getDot().getProperties().getOrDefault("currentPhysicalFloor", 0);
        Label floorLabel = new Label("FL." + (floor + 1) + " ");
        floorLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 10px; -fx-font-family: 'Segoe UI';");

        if (!"Cell".equals(category)) {
            Label statusBox = new Label("🚘");
            statusBox.setAlignment(Pos.CENTER);
            String baseStyle = "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6 2 6; -fx-background-radius: 4; ";

            if (car.getCurrentStatus() == Vehicle.Status.TRANSITING) {
                statusBox.setStyle(baseStyle + "-fx-background-color: #28a745;");
            } else if (car.getCurrentStatus() == Vehicle.Status.PARKED) {
                statusBox.setStyle(baseStyle + "-fx-background-color: #333333; -fx-text-fill: #888888;");
            } else if (car.getCurrentStatus() == Vehicle.Status.EXITING) {
                statusBox.setStyle(baseStyle + "-fx-background-color: #fd7e14;");
            }

            rightGroup.getChildren().addAll(floorLabel, statusBox);
        } else {
            rightGroup.getChildren().add(floorLabel);
        }

        row.getChildren().add(rightGroup);

        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #333333; -fx-background-radius: 3; -fx-border-color: #39FF14; -fx-border-radius: 3; -fx-cursor: hand;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #222222; -fx-background-radius: 3; -fx-border-color: #333333; -fx-border-radius: 3; -fx-cursor: hand;"));

        return row;
    }

    public void updateParkingLot(ParkingLot newLot) {
        this.currentLot = newLot;

        lastSearchedCategory = "";
        lastSearchedQuery = "";

        vehicleData.clear();
        resultsListView.setVisible(false);
        resultsListView.setManaged(false);

        if (searchInput != null) searchInput.clear();
        checkButtonState();
    }

    //only changes styles and interaction rules, ignores visibility)
    private void checkButtonState() {
        if (searchButton == null) return;

        String currentCat = categoryBox.getValue();
        String currentText = searchInput.getText().trim();

        if (currentCat == null) {
            searchButton.setDisable(true);
            searchButton.setText("SEARCH");
            resultsListView.setVisible(false); //only hide if absolutely nothing is selected
            return;
        }

        boolean isNewSearch = !currentCat.equals(lastSearchedCategory) ||
                !currentText.equalsIgnoreCase(lastSearchedQuery);

        searchButton.setDisable(!isNewSearch);

        //if the user navigated back to a previous search, restore the results
        if (!isNewSearch && !lastSearchedCategory.isEmpty()) {
            resultsListView.setVisible(true);
            resultsListView.setManaged(true);

        } else {
            //only hide if the search is actually different/new
            resultsListView.setVisible(false);
            resultsListView.setManaged(false);
            vehicleData.clear();
        }

        //update button visual styles
        if (currentText.isEmpty()) {
            searchButton.setText("VIEW ALL");
            searchButton.setStyle("-fx-background-color: #FF00FF; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            searchButton.setText("SEARCH");
            searchButton.setStyle("-fx-background-color: #39FF14; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    //depending on the category chosen, it will fetch relative data
    private String getSortableValue(Vehicle car, String category) {
        switch (category) {
            case "Ticket#": return String.valueOf(car.getTicketNumber());
            case "Plate": return car.getPlate();
            case "Model": return car.getModel().toString();
            case "Type": return car.getType().toString();
            case "Entry Time": return car.getEntryTime().toString();
            case "Cell": return currentLot.getVehicleCoordinates(car);
            default: return "";
        }
    }

    //LIGHTWEIGHT DATA CLASS FOR CELL RENDERING
    private static class CellRef {
        final int floor, row, col;
        CellRef(int f, int r, int c) {
            this.floor = f;
            this.row = r;
            this.col = c;
        }
    }

    private void applyScrollbarTheme() {
        resultsListView.setStyle("-fx-background-color: #1a1a1a; " +
                "-fx-control-inner-background: #1a1a1a; " +
                "-fx-border-color: #333333; " +
                "-fx-border-radius: 5;");

        Platform.runLater(() -> {
            //style the track
            resultsListView.lookupAll(".scroll-bar").forEach(node -> {
                node.setStyle("-fx-background-color: #1a1a1a; -fx-pref-width: 10;");
            });

            //style the thumb with hover
            resultsListView.lookupAll(".thumb").forEach(node -> {
                String defaultThumbStyle = "-fx-background-color: #333333; -fx-background-radius: 5; -fx-background-insets: 2;";
                String hoverThumbStyle = "-fx-background-color: #39FF14; -fx-background-radius: 5; -fx-background-insets: 2;";

                node.setStyle(defaultThumbStyle);

                node.setOnMouseEntered(e -> node.setStyle(hoverThumbStyle));
                node.setOnMouseExited(e -> node.setStyle(defaultThumbStyle));
            });

            //hide the increment/decrement buttons
            resultsListView.lookupAll(".increment-button, .decrement-button").forEach(node -> {
                node.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            });
        });
    }
}