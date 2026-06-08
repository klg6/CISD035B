package com.example.gridlott;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.util.Duration;
import javafx.beans.binding.BooleanBinding;

import java.util.List;

public class VehicleFinderPanel extends VBox {

    private boolean isExpanded = false;
    private final VBox contentArea;
    private final Button toggleButton;
    private ComboBox<String> categoryBox;
    private TextField searchInput;
    private final Label titleLabel;
    private final VBox resultsArea;
    private final ScrollPane scrollPane;
    private ParkingLot currentLot;
    private String lastSearchedCategory = "";
    private String lastSearchedQuery = "";
    private javafx.animation.Timeline refreshTimeline; // The background poller
    private double perSecondRefresher = 0.5; //update live feed refresher -> the lower it is, the faster the update
    private Button searchButton; // Ensure this is still at the top!

    //for vehicle status filter buttons
    private ToggleGroup filterGroup;
    private String currentFilter = "All";
    ToggleButton allBtn, transBtn, parkBtn, exitBtn;

    public VehicleFinderPanel(ParkingLot lot) {

        this.currentLot = lot;

        //panel Base Style
        this.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #2d2d30; -fx-border-radius: 5; -fx-background-radius: 5;");
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.setAlignment(Pos.TOP_LEFT);

        //header (Icon + Label)
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        toggleButton = new Button("\uD83D\uDD0D"); //magnifying Glass
        toggleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #8e8e93; -fx-font-size: 16px; -fx-cursor: hand;");
        toggleButton.setOnAction(e -> togglePanel());

        titleLabel = new Label("VEHICLE / CELL FINDER");
        titleLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        titleLabel.setVisible(false); //hidden until expanded
        titleLabel.setManaged(false); //also hidden until expanded

        header.getChildren().addAll(toggleButton, titleLabel);

        //create the container for results
        resultsArea = new VBox(5);
        resultsArea.setStyle("-fx-background-color: #1a1a1a;");

        //wrap it in a ScrollPane so it doesn't overflow the screen
        scrollPane = new ScrollPane(resultsArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1a1a1a; -fx-background-color: #1a1a1a; -fx-border-color: #333333; -fx-border-radius: 5;");
        scrollPane.setPrefHeight(350); //set a default height for the results list
        scrollPane.setPrefWidth(180);
        scrollPane.setVisible(false);
        scrollPane.setManaged(false);

        //content Area
        contentArea = new VBox(8);
        contentArea.setVisible(false);
        contentArea.setManaged(false);

        //STATUS FILTERS
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

        //set initial state and group
        for (ToggleButton btn : buttons) {
            btn.setToggleGroup(filterGroup);
            btn.setStyle(inactive);
        }
        allBtn.setSelected(true);
        allBtn.setStyle(styleAll);

        //logic + theme switcher
        filterGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            //prevents accidental deselection
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

            executeSearch(lastSearchedCategory, lastSearchedQuery);
        });

        filterBar.getChildren().addAll(allBtn, transBtn, parkBtn, exitBtn);

        double inputWidth = 180.0;
        //comboBox Styling
        categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Ticket#", "Plate", "Model", "Type", "Entry Time", "Cell");
        categoryBox.setPromptText("Select Category");
        categoryBox.setPrefWidth(inputWidth);
        categoryBox.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: #39FF14; -fx-border-color: #39FF14; -fx-background-radius: 5; -fx-border-radius: 5;");

        //dropdown cell styling
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

        //selected item styling
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

        //textField
        searchInput = new TextField();
        searchInput.setPromptText("Enter value...");
        searchInput.setPrefWidth(inputWidth);
        searchInput.setStyle("-fx-background-color: #111111; -fx-text-fill: white; -fx-border-color: #333333; -fx-border-radius: 3;");

        //button (Start with VIEW ALL)
        searchButton = new Button("VIEW ALL");
        searchButton.setStyle("-fx-background-color: #FF00FF; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        searchButton.setMaxWidth(Double.MAX_VALUE);
        searchButton.setOnAction(e -> {
            //reveal the results area when they hit the search or view all button
            scrollPane.setVisible(true);
            scrollPane.setManaged(true);

            handleSearchAction();
        });

        //SMART LISTENERS
        categoryBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCell = "Cell".equals(newVal);

            //hide/show filters based on mode
            filterBar.setVisible(!isCell);
            filterBar.setManaged(!isCell);

            //SMART VISIBILITY MEMORY
            String currentText = searchInput.getText().trim();
            if (newVal != null && newVal.equals(lastSearchedCategory) && currentText.equalsIgnoreCase(lastSearchedQuery)) {
                //if they switch back to their active search, instantly reveal the live results
                scrollPane.setVisible(true);
                scrollPane.setManaged(true);
                executeSearch(lastSearchedCategory, lastSearchedQuery);
            } else {
                //if they switch to an unsearched category, hide the old results so they aren't confusing to reference data
                scrollPane.setVisible(false);
                scrollPane.setManaged(false);
                resultsArea.getChildren().clear();
            }

            //re-check state so the button updates immediately when switching categories
            checkButtonState();
        });

        //REBOOT
        //disables bindings; gray out until category selected
        BooleanBinding isNoCategorySelected = categoryBox.valueProperty().isNull();
        allBtn.disableProperty().bind(isNoCategorySelected);
        transBtn.disableProperty().bind(isNoCategorySelected);
        parkBtn.disableProperty().bind(isNoCategorySelected);
        exitBtn.disableProperty().bind(isNoCategorySelected);

        //lets checkButtonState() handle the search button manually
        searchButton.setDisable(true);

        //SEARCH INPUT LISTENER; switch VIEW ALL vs SEARCH button
        searchInput.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean isEmpty = (newVal == null || newVal.trim().isEmpty());
            if (isEmpty) {
                searchButton.setText("VIEW ALL");
                //change color immediately as long as it's not currently disabled
                if (!searchButton.isDisabled()) {
                    searchButton.setStyle("-fx-background-color: #FF00FF; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
                }
            } else {
                searchButton.setText("SEARCH");
                if (!searchButton.isDisabled()) {
                    searchButton.setStyle("-fx-background-color: #39FF14; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
                }
            }
            checkButtonState();
        });

        //visual styling when disabled vs enabled
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

        //BACKGROUND POLLING ENGINE; refreshes the active search every half a second
        refreshTimeline = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                Duration.seconds(perSecondRefresher),
                e -> {
                    if (!isExpanded || "Cell".equals(categoryBox.getValue())) {
                        return;
                    }

                    //only run background refreshes if the user is looking at the active search category
                    if (lastSearchedCategory != null && lastSearchedCategory.equals(categoryBox.getValue())) {
                        executeSearch(lastSearchedCategory, lastSearchedQuery);
                    }
                }
        ));
        refreshTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        refreshTimeline.play();

        //ASSEMBLE UI (fixes vehicle finder panel automatically popping)
        contentArea.getChildren().addAll(scrollPane, filterBar, categoryBox, searchInput, searchButton);

        //ONLY the header and contentArea inside the root panel
        this.getChildren().addAll(header, contentArea);
    }

    private void togglePanel() {

        isExpanded = !isExpanded;

        //toggle content visibility
        contentArea.setVisible(isExpanded);
        contentArea.setManaged(isExpanded);

        //toggle title visibility
        titleLabel.setVisible(isExpanded);
        titleLabel.setManaged(isExpanded);

        scrollPane.setVisible(isExpanded);
        scrollPane.setManaged(isExpanded);

        FadeTransition ft = new FadeTransition(Duration.millis(200), contentArea);
        ft.setFromValue(isExpanded ? 0 : 1);
        ft.setToValue(isExpanded ? 1 : 0);
        ft.play();
    }

    public void clearInputs() {
        //reset the dropdown
        categoryBox.getSelectionModel().clearSelection();
        categoryBox.setPromptText("Select Category");

        //clear the text field
        searchInput.clear();
        searchInput.setPromptText("Enter value...");
    }

    private void handleSearchAction() {
        String currentCat = categoryBox.getValue();
        String currentText = searchInput.getText().trim();

        //perform the search immediately
        executeSearch(currentCat, currentText);

        //set the "Active" state so the timeline knows what to keep alive
        lastSearchedCategory = currentCat;
        lastSearchedQuery = currentText;

        //refresh button state so it disables/grays out to prevent the user from re-clicking while the live refresh is working.
        checkButtonState();
    }

    private void executeSearch(String category, String query) {
        if (currentLot == null) return;
        resultsArea.getChildren().clear();

        if ("Cell".equals(category)) {
            renderCellStructure(query); //(No vehicles referenced)
        } else {
            renderVehicleSearch(category, query); //standard vehicle search for other remaining categories
        }
    }

    private void renderCellStructure(String query) {
        int floors = currentLot.getFloorCount();
        int rows = currentLot.getRows();
        int cols = currentLot.getCols();
        String lowerQuery = (query == null) ? "" : query.toLowerCase().trim();

        for (int f = 0; f < floors; f++) {
            //floor header
            Label floorHeader = new Label(" FLOOR " + (f + 1));
            floorHeader.setStyle("-fx-text-fill: #39FF14; -fx-font-weight: bold; -fx-padding: 10 0 5 0;");
            resultsArea.getChildren().add(floorHeader);

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    String coord = (f + 1) + "," + (r + 1) + "," + (c + 1);

                    //filter logic
                    if (!lowerQuery.isEmpty() && !coord.contains(lowerQuery)) continue;

                    //create the row that handles the hover/click behavior
                    resultsArea.getChildren().add(buildSelectableCellRow(f + 1, r + 1, c + 1));
                }
            }
        }
    }

    private HBox buildSelectableCellRow(int f, int r, int c) { //similar to buildResultRow but for cell category
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5));

        String defaultStyle = "-fx-background-color: #222222; -fx-background-radius: 3; -fx-border-color: #333333; -fx-border-radius: 3; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: #333333; -fx-background-radius: 3; -fx-border-color: #39FF14; -fx-border-radius: 3; -fx-cursor: hand;";

        row.setStyle(defaultStyle);

        //sisplay the grid coordinate
        Label infoLabel = new Label("[" + (f) + "," + (r) + "," + (c) + "]");
        infoLabel.setStyle("-fx-text-fill: white;");
        row.getChildren().add(infoLabel);

        //hover effects
        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e -> row.setStyle(defaultStyle));

        //selection Click Logic
        row.setOnMouseClicked(e -> {
            row.setStyle(hoverStyle); //keep the highlight on click
            System.out.println("Selected Structural Cell: " + (f+1) + "," + (r+1) + "," + (c+1));
        });

        return row;
    }


    private void renderVehicleSearch(String category, String query){
        if (currentLot == null) return;

        List<Vehicle> parkedCars = currentLot.getActiveVehicles();
        List<Vehicle> matches = new java.util.ArrayList<>();
        String lowerQuery = (query == null) ? "" : query.toLowerCase().trim();
        boolean isBrowseMode = lowerQuery.isEmpty();

        //filter the matches
        for (Vehicle car : parkedCars) {

            //ONLY APPLY STATUS FILTER IF NOT IN CELL MODE
            if (!"Cell".equals(category) && !"All".equals(currentFilter)) {
                if (currentFilter.equals("Entering") && car.getCurrentStatus() != Vehicle.Status.TRANSITING) continue;
                if (currentFilter.equals("Parked") && car.getCurrentStatus() != Vehicle.Status.PARKED) continue;
                if (currentFilter.equals("Exiting") && car.getCurrentStatus() != Vehicle.Status.EXITING) continue;
            }

            //SEARCH/BROWSE
            if (isBrowseMode) {
                matches.add(car);
            } else {
                String val = getSortableValue(car, category).toLowerCase();
                if (val.contains(lowerQuery)) {
                    matches.add(car);
                }
            }
        }

        //sort the matches depending on search query
        matches.sort((v1, v2) -> {
            String s1 = getSortableValue(v1, category).toLowerCase();
            String s2 = getSortableValue(v2, category).toLowerCase();

            //handles time identifier
            if (category.equals("Entry Time")) {
                return v1.getEntryTime().compareTo(v2.getEntryTime());
            }

            //handle numerical sort for cells category
            if (category.equals("Cells")) {
                //extract numbers from strings like "[F0][R1][C6]"
                //replace non-digits with spaces, trim, and split
                String[] nums1 = s1.replaceAll("[^0-9]+", " ").trim().split(" ");
                String[] nums2 = s2.replaceAll("[^0-9]+", " ").trim().split(" ");

                for (int i = 0; i < Math.min(nums1.length, nums2.length); i++) {
                    int n1 = Integer.parseInt(nums1[i]);
                    int n2 = Integer.parseInt(nums2[i]);
                    if (n1 != n2) return Integer.compare(n1, n2);
                }
                return Integer.compare(nums1.length, nums2.length);
            }

            //standard positional relevance Sort (for model, plate, ticket#)
            int idx1 = s1.indexOf(lowerQuery);
            int idx2 = s2.indexOf(lowerQuery);
            if (idx1 != idx2) return Integer.compare(idx1, idx2);

            return s1.compareTo(s2);
        });

        //build UI
        List<javafx.scene.Node> newResults = new java.util.ArrayList<>();
        if (matches.isEmpty()) {
            Label noResults = new Label("No vehicles found: '" + query + "'");
            noResults.setStyle("-fx-text-fill: #8e8e93; -fx-font-family: 'Segoe UI'; -fx-padding: 5;");
            newResults.add(noResults);
        } else {
            for (Vehicle car : matches) {
                newResults.add(buildResultRow(car, category));
            }
        }

        resultsArea.getChildren().setAll(newResults);
    }


    //helper to build the visual row for other categories except cell
    private HBox buildResultRow(Vehicle car, String category) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5));
        row.setStyle("-fx-background-color: #222222; -fx-background-radius: 3; -fx-border-color: #333333; -fx-border-radius: 3; -fx-cursor: hand;");

        //colored dot
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
            case "Cell": displayText = currentLot.getVehicleCoordinates(car); break; // Updated to match your string
            default: displayText = car.getPlate(); break;
        }

        Label infoLabel = new Label(displayText);
        infoLabel.setStyle("-fx-text-fill: white;");
        row.getChildren().add(infoLabel);

        //this pushes everything that follows to the far right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        //the live status emoji Box
        if (!"Cell".equals(category)) {
            Label statusBox = new Label("🚘");
            statusBox.setAlignment(Pos.CENTER);

            String baseStyle = "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 2 6 2 6; -fx-background-radius: 4; ";

            //determines the vehicle status dependent on the status filter button chosen
            if (car.getCurrentStatus() == Vehicle.Status.TRANSITING) {
                statusBox.setStyle(baseStyle + "-fx-background-color: #28a745;"); // Green
            } else if (car.getCurrentStatus() == Vehicle.Status.PARKED) {
                statusBox.setStyle(baseStyle + "-fx-background-color: #333333; -fx-text-fill: #888888;"); //gray
            } else if (car.getCurrentStatus() == Vehicle.Status.EXITING) {
                statusBox.setStyle(baseStyle + "-fx-background-color: #fd7e14;"); // Orange
            }
            row.getChildren().add(statusBox);
        }

        //hover effects
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #333333; -fx-background-radius: 3; -fx-border-color: #39FF14; -fx-border-radius: 3; -fx-cursor: hand;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #222222; -fx-background-radius: 3; -fx-border-color: #333333; -fx-border-radius: 3; -fx-cursor: hand;"));

        return row;
    }

    //updates the reference to the parking lot after a simulation reboot
    public void updateParkingLot(ParkingLot newLot) {
        this.currentLot = newLot;

        //wipe the old search data clean
        lastSearchedCategory = "";
        lastSearchedQuery = "";
        resultsArea.getChildren().clear();
        scrollPane.setVisible(false);
        scrollPane.setManaged(false);

        if (searchInput != null) searchInput.clear();
        checkButtonState();
    }

    private void checkButtonState() { //search button state changes depending if input and category are changed
        if (searchButton == null) return;

        String currentCat = categoryBox.getValue();
        String currentText = searchInput.getText().trim();

        //if no category is selected, disable the button and hide results
        if (currentCat == null) {
            searchButton.setDisable(true);
            searchButton.setText("SEARCH");
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            resultsArea.getChildren().clear();
            return;
        }

        //determines if the user is about to perform a new search
        boolean isNewSearch = !currentCat.equals(lastSearchedCategory) ||
                !currentText.equalsIgnoreCase(lastSearchedQuery);

        //if it's a new search, enable the button so they can click it
        searchButton.setDisable(!isNewSearch);

        //MASTER VISIBILITY LINK
        if (!isNewSearch && lastSearchedCategory != null && !lastSearchedCategory.isEmpty()) {
            //if inputs perfectly match the active live search, automatically show the results area
            scrollPane.setVisible(true);
            scrollPane.setManaged(true);
            executeSearch(lastSearchedCategory, lastSearchedQuery);
        } else {
            //if they are mid-typing or looking at an unsearched category, hide the old results
            //so they don't look at inaccurate or misleading data.
            scrollPane.setVisible(false);
            scrollPane.setManaged(false);
            resultsArea.getChildren().clear();
        }

        //conditional Styling for the confirm button
        if (currentText.isEmpty()) {
            searchButton.setText("VIEW ALL");
            searchButton.setStyle("-fx-background-color: #FF00FF; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            searchButton.setText("SEARCH");
            searchButton.setStyle("-fx-background-color: #39FF14; -fx-text-fill: black; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    private String getSortableValue(Vehicle car, String category) { //fetch proper data depending on category
        switch (category) {
            case "Ticket#": return String.valueOf(car.getTicketNumber());
            case "Plate": return car.getPlate();
            case "Model": return car.getModel().toString();
            case "Type": return car.getType().toString();
            case "Entry Time": return car.getEntryTime().toString(); //sorts naturally
            case "Cell": return currentLot.getVehicleCoordinates(car);
            default: return "";
        }
    }
}