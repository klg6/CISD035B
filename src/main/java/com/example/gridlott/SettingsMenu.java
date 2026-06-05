package com.example.gridlott;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.util.Locale;

public class SettingsMenu extends StackPane {
    public interface SettingsCallback {
        void onApply();
    }

    private TextField rowsInput, colsInput, floorsInput;
    private TextField minDurInput, maxDurInput, minDelayInput, maxDelayInput;
    private TextField feeInput, perSecInput, speedMultiplier;
    private CheckBox betterVisualsBox, showPathsBox, showDotGlowBox;
    private Button applyButton;
    private SettingsCallback callback;

    //STYLES FOR VALIDATION
    private final String NORMAL_STYLE = "-fx-background-color: #111111; -fx-text-fill: #39FF14; -fx-font-weight: bold; -fx-border-color: #333333; -fx-border-radius: 4; -fx-background-radius: 4;";
    private final String ERROR_STYLE = "-fx-background-color: #111111; -fx-text-fill: #39FF14; -fx-font-weight: bold; -fx-border-color: #FF3333; -fx-border-radius: 4; -fx-background-radius: 4;";

    public SettingsMenu(SettingsCallback callback) {
        this.callback = callback;

        //fullscreen Blocker: dims background and consume clicks
        this.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        this.setOnMouseClicked(e -> e.consume());
        this.setOnScroll(e -> e.consume());

        buildUI();
        attachChangeListeners();
        refreshMenuState();
    }

    private void buildUI() {
        //main menu panel
        VBox menuPanel = new VBox(10);
        menuPanel.setMaxWidth(450);
        menuPanel.setMaxHeight(800);
        menuPanel.setAlignment(Pos.TOP_CENTER);
        menuPanel.setStyle("-fx-background-color: #111111; -fx-border-color: #222222; -fx-border-radius: 10; -fx-background-radius: 10;");
        menuPanel.setPadding(new Insets(20));

        //header
        Label headerLabel = new Label("GRIDLOTT SETTINGS");
        headerLabel.setStyle("-fx-text-fill: #39FF14; -fx-font-weight: bold; -fx-font-size: 16px;");
        HBox header = new HBox(headerLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));

        //initializing TextFields
        rowsInput = new TextField(); colsInput = new TextField(); floorsInput = new TextField();
        minDurInput = new TextField(); maxDurInput = new TextField();
        minDelayInput = new TextField(); maxDelayInput = new TextField();
        feeInput = new TextField(); perSecInput = new TextField(); speedMultiplier = new TextField();

        //build setting rows (field, min, max, step amount, isInteger)
        VBox settingsList = new VBox(8);
        settingsList.getChildren().addAll(
                createSettingRow("Rows", "Number of rows in the grid.", createNumericControl(rowsInput, 1, 50, 1, true)),
                createSettingRow("Cols", "Number of columns in the grid.", createNumericControl(colsInput, 1, 50, 1, true)),
                createSettingRow("Floors", "Number of floors to display.", createNumericControl(floorsInput, 1, 25, 1, true)),
                createSettingRow("Min Duration (s)", "Minimum parking duration.", createNumericControl(minDurInput, 5, 3600, 1, false)),
                createSettingRow("Max Duration (s)", "Maximum parking duration.", createNumericControl(maxDurInput, 5, 3600, 1, false)),
                createSettingRow("Min Delay (s)", "Minimum delay between spawns.", createNumericControl(minDelayInput, 0.01, 10, 0.1, false)),
                createSettingRow("Max Delay (s)", "Maximum delay between spawns.", createNumericControl(maxDelayInput, 0.01, 10, 0.1, false)),
                createSettingRow("Fee ($)", "Fee per vehicle.", createNumericControl(feeInput, 0.01, 999999999, 1, false)),
                createSettingRow("Per Seconds", "Revenue is calculated per this interval.", createNumericControl(perSecInput, 0.01, 999999999, 1, false)),
                createSettingRow("Speed Multiplier", "Dot speed multiplier.", createNumericControl(speedMultiplier, 0.1, 10, 0.1, false))
        );

        //checkboxes
        betterVisualsBox = createCustomCheckBox("Better Dot Visuals", "Shows better floor dot visualization.");
        showPathsBox = createCustomCheckBox("Show Paths", "Displays path connections where dots traverse on.");
        showDotGlowBox = createCustomCheckBox("Show Dot Glow", "Easily distinguishes which dot is exiting. GPU usage will increase!");
        settingsList.getChildren().addAll(betterVisualsBox, showPathsBox, showDotGlowBox);

        //scrollPane
        ScrollPane scrollPane = new ScrollPane(settingsList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.getStylesheets().add(createScrollbarCSS());

        //action buttons
        Button resetButton = new Button("↺  RESET");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setStyle("-fx-background-color: transparent; -fx-border-color: #888888; -fx-border-radius: 5; -fx-text-fill: #888888; -fx-font-weight: bold; -fx-padding: 10; -fx-cursor: hand;");
        resetButton.setOnAction(e -> refreshMenuState());

        applyButton = new Button("💾  APPLY CHANGES");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setStyle("-fx-background-color: transparent; -fx-border-color: #39FF14; -fx-border-radius: 5; -fx-text-fill: #39FF14; -fx-font-weight: bold; -fx-padding: 10; -fx-cursor: hand;");
        applyButton.setOnAction(e -> applySettings());

        HBox actionButtons = new HBox(10, resetButton, applyButton);
        HBox.setHgrow(resetButton, Priority.ALWAYS);
        HBox.setHgrow(applyButton, Priority.ALWAYS);

        menuPanel.getChildren().addAll(header, scrollPane, actionButtons);
        this.getChildren().add(menuPanel);
    }

    //UI BUILDER HELPERS
    private BorderPane createSettingRow(String title, String description, Region control) {
        BorderPane row = new BorderPane();
        row.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #2a2a2a; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");

        String icon = "■";
        String lowerTitle = title.toLowerCase();

        if (lowerTitle.contains("row"))             icon = "↕";
        else if (lowerTitle.contains("col"))        icon = "↔";
        else if (lowerTitle.contains("floor"))      icon = "▤";
        else if (lowerTitle.contains("min duration")) icon = "⏱";
        else if (lowerTitle.contains("max duration")) icon = "⏱";
        else if (lowerTitle.contains("min delay"))    icon = "📥";
        else if (lowerTitle.contains("max delay"))    icon = "📥";
        else if (lowerTitle.contains("fee"))        icon = "💲";
        else if (lowerTitle.contains("per second"))  icon = "⟳";
        else if (lowerTitle.contains("speed"))      icon = "⚡";

        Text iconText = new Text(icon + " ");
        iconText.setFill(Color.web("#39FF14"));

        iconText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        Text titleText = new Text(title);
        titleText.setFill(Color.WHITE);
        titleText.setFont(Font.font("System", FontWeight.BOLD, 13));

        TextFlow titleFlow = new TextFlow(iconText, titleText);

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        VBox leftSide = new VBox(3, titleFlow, descLabel);
        leftSide.setAlignment(Pos.CENTER_LEFT);

        row.setLeft(leftSide);
        row.setRight(control);
        BorderPane.setAlignment(control, Pos.CENTER_RIGHT);

        return row;
    }

    private HBox createNumericControl(TextField field, double min, double max, double step, boolean isInteger) {
        field.setPrefWidth(90);
        field.setAlignment(Pos.CENTER);
        field.setStyle(NORMAL_STYLE);

        //snaps big input to max value regarding to the textField
        field.setTextFormatter(isInteger ? createIntegerFormatter((int)max) : createDoubleFormatter(max));

        //lets the user press ESC if user is typing in TextField
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                field.getParent().requestFocus();
            }
        });

        Button minusBtn = createStepperButton("-");
        Button plusBtn = createStepperButton("+");

        //when the user actually clicks the +,- buttons
        minusBtn.setOnAction(e -> stepValue(field, -step, min, max, isInteger));
        plusBtn.setOnAction(e -> stepValue(field, step, min, max, isInteger));

        HBox container = new HBox(5, minusBtn, field, plusBtn);
        container.setAlignment(Pos.CENTER);
        return container;
    }

    private Button createStepperButton(String text) {
        Button btn = new Button(text);
        btn.setPrefSize(30, 30);
        btn.setStyle("-fx-background-color: #222222; -fx-text-fill: #888888; -fx-border-color: #2a2a2a; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #333333; -fx-text-fill: white; -fx-border-color: #2a2a2a; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #222222; -fx-text-fill: #888888; -fx-border-color: #2a2a2a; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;"));
        return btn;
    }

    private void stepValue(TextField field, double step, double min, double max, boolean isInteger) {
        try {
            double current = field.getText().isEmpty() ? min : Double.parseDouble(field.getText());
            double newVal = current + step;

            //clamp stepper buttons so users don't easily click themselves into an error state
            if (newVal < min) newVal = min;
            if (newVal > max) newVal = max;

            if (isInteger) {
                field.setText(String.valueOf((int) Math.round(newVal)));
            } else {
                field.setText(String.format(Locale.US, "%.2f", newVal));
            }
            evaluateModificationState();
        } catch (Exception ex) {}
    }

    //checkbox placeholder for showpaths/better dot visualization
    private CheckBox createCustomCheckBox(String title, String description) {
        CheckBox box = new CheckBox();
        box.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        VBox textLayout = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        textLayout.getChildren().addAll(titleLabel, descLabel);

        box.setGraphic(textLayout);
        return box;
    }

    private String createScrollbarCSS() {
        return "data:text/css," +
                ".scroll-pane > .viewport { -fx-background-color: transparent; } " +
                ".scroll-pane { -fx-background-color: transparent; -fx-padding: 0; }";
    }

    //HYBRID FORMATTERS (Blocks letters & snaps to Max, ignores Min)

    private TextFormatter<String> createIntegerFormatter(int max) {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) return change;
            if (newText.length() > 1 && newText.startsWith("0")) return null;
            if (!newText.matches("\\d*")) return null;

            try {
                int val = Integer.parseInt(newText);
                if (val > max) {
                    change.setText(String.valueOf(max));
                    change.setRange(0, change.getControlText().length());
                    // Push cursor to the end
                    Platform.runLater(() -> {
                        if (change.getControl() instanceof TextField) {
                            ((TextField) change.getControl()).positionCaret(((TextField) change.getControl()).getText().length());
                        }
                    });
                }
                return change;
            } catch (NumberFormatException e) { return null; }
        });
    }

    private TextFormatter<String> createDoubleFormatter(double max) {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.equals(".")) return change;
            if (newText.length() > 1 && newText.startsWith("0") && !newText.startsWith("0.")) return null;
            if (!newText.matches("\\d*(\\.\\d{0,2})?")) return null;

            try {
                double val = Double.parseDouble(newText);
                if (val > max) {
                    // Cleanly format the max string (e.g. 3600 instead of 3600.00 if it's a whole number)
                    String maxStr = max == (int) max ? String.valueOf((int) max) : String.valueOf(max);
                    change.setText(maxStr);
                    change.setRange(0, change.getControlText().length());
                    // Push cursor to the end
                    Platform.runLater(() -> {
                        if (change.getControl() instanceof TextField) {
                            ((TextField) change.getControl()).positionCaret(((TextField) change.getControl()).getText().length());
                        }
                    });
                }
                return change;
            } catch (NumberFormatException e) { return null; }
        });
    }

    //LIVE VALIDATION HELPERS

    //basically makes box highlighted in red if invalid inputs
    private boolean validateBounds(TextField field, double min, double max) {
        try {
            double val = Double.parseDouble(field.getText());
            if (val < min || val > max) {
                field.setStyle(ERROR_STYLE);
                return false;
            }
            field.setStyle(NORMAL_STYLE);
            return true;
        } catch (NumberFormatException e) {
            field.setStyle(ERROR_STYLE); //empty box or isolated "." triggers red
            return false;
        }
    }

    //this is for min/max delay and min/max duration logic checks
    private boolean validateMinMaxPair(TextField minField, TextField maxField, double absoluteMin, double absoluteMax) {
        boolean minValid = validateBounds(minField, absoluteMin, absoluteMax);
        boolean maxValid = validateBounds(maxField, absoluteMin, absoluteMax);

        //if both fields have numbers inside them, check if min is crossing max
        if (minValid && maxValid) {
            double min = Double.parseDouble(minField.getText());
            double max = Double.parseDouble(maxField.getText());
            if (min > max) {
                minField.setStyle(ERROR_STYLE);
                maxField.setStyle(ERROR_STYLE);
                return false;
            }
        }
        return minValid && maxValid;
    }

    //LISTENERS (when a user changes settings)
    private void attachChangeListeners() {
        rowsInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        colsInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        floorsInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        minDurInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        maxDurInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        minDelayInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        maxDelayInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        feeInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        perSecInput.textProperty().addListener((o, old, n) -> evaluateModificationState());
        speedMultiplier.textProperty().addListener((o, old, n) -> evaluateModificationState());
        betterVisualsBox.selectedProperty().addListener((o, old, n) -> evaluateModificationState());
        showPathsBox.selectedProperty().addListener((o, old, n) -> evaluateModificationState());
        showDotGlowBox.selectedProperty().addListener((o, old, n) -> evaluateModificationState());
    }

    public void refreshMenuState() {
        rowsInput.setText(String.valueOf(Config.rows));
        colsInput.setText(String.valueOf(Config.cols));
        floorsInput.setText(String.valueOf(Config.floors));
        minDurInput.setText(String.valueOf(Config.minDuration));
        maxDurInput.setText(String.valueOf(Config.maxDuration));
        minDelayInput.setText(String.valueOf(Config.minDelay));
        maxDelayInput.setText(String.valueOf(Config.maxDelay));
        feeInput.setText(String.valueOf(Config.fee));
        perSecInput.setText(String.valueOf(Config.perSeconds));
        speedMultiplier.setText(String.valueOf(Config.speedMultiplier));
        betterVisualsBox.setSelected(Config.betterDotVisuals);
        showPathsBox.setSelected(Config.showPaths);
        showDotGlowBox.setSelected(Config.showDotGlow);

        evaluateModificationState();
    }

    private void evaluateModificationState() {
        //validate every field first based on absolute bounds
        boolean allValid = true;
        allValid &= validateBounds(rowsInput, 1, 50);
        allValid &= validateBounds(colsInput, 1, 50);
        allValid &= validateBounds(floorsInput, 1, 25);
        allValid &= validateBounds(feeInput, 0.01, 999999999);
        allValid &= validateBounds(perSecInput, 0.01, 999999999);
        allValid &= validateBounds(speedMultiplier, 0.1, 10.0);

        //validate the pairs (Enforces bounds AND min <= max)
        allValid &= validateMinMaxPair(minDurInput, maxDurInput, 5.0, 3600);
        allValid &= validateMinMaxPair(minDelayInput, maxDelayInput, 0.01, 10);

        //if ANY box is red (invalid), disable the apply button instantly and stop checking
        if (!allValid) {
            applyButton.setDisable(true);
            applyButton.setOpacity(0.5);
            return;
        }

        //if all boxes are green, check if values actually changed from the Config file
        boolean valuesUnchanged =
                Integer.parseInt(rowsInput.getText()) == Config.rows &&
                        Integer.parseInt(colsInput.getText()) == Config.cols &&
                        Integer.parseInt(floorsInput.getText()) == Config.floors &&
                        Double.parseDouble(minDurInput.getText()) == Config.minDuration &&
                        Double.parseDouble(maxDurInput.getText()) == Config.maxDuration &&
                        Double.parseDouble(minDelayInput.getText()) == Config.minDelay &&
                        Double.parseDouble(maxDelayInput.getText()) == Config.maxDelay &&
                        Double.parseDouble(feeInput.getText()) == Config.fee &&
                        Double.parseDouble(perSecInput.getText()) == Config.perSeconds &&
                        Double.parseDouble(speedMultiplier.getText()) == Config.speedMultiplier &&
                        betterVisualsBox.isSelected() == Config.betterDotVisuals &&
                        showPathsBox.isSelected() == Config.showPaths &&
                        showDotGlowBox.isSelected() == Config.showDotGlow;

        //turn apply button green only if they have pending valid changes!
        applyButton.setDisable(valuesUnchanged);
        applyButton.setOpacity(valuesUnchanged ? 0.5 : 1.0);
    }

    private void applySettings() {//basically lets the user click the apply changes button to confirm changes
        try {
            double newMinDur = Double.parseDouble(minDurInput.getText());
            double newMaxDur = Double.parseDouble(maxDurInput.getText());
            double newMinDelay = Double.parseDouble(minDelayInput.getText());
            double newMaxDelay = Double.parseDouble(maxDelayInput.getText());

            new RandomizeDuration(newMinDur, newMaxDur);
            new RandomizeDuration(newMinDelay, newMaxDelay);

            //saves your inputs even after changing it many times
            Config.rows = Integer.parseInt(rowsInput.getText());
            Config.cols = Integer.parseInt(colsInput.getText());
            Config.floors = Integer.parseInt(floorsInput.getText());
            Config.fee = Double.parseDouble(feeInput.getText());
            Config.perSeconds = Double.parseDouble(perSecInput.getText());
            Config.speedMultiplier = Double.parseDouble(speedMultiplier.getText());

            Config.minDuration = newMinDur;
            Config.maxDuration = newMaxDur;
            Config.minDelay = newMinDelay;
            Config.maxDelay = newMaxDelay;

            Config.betterDotVisuals = betterVisualsBox.isSelected();
            Config.showPaths = showPathsBox.isSelected();
            Config.showDotGlow = showDotGlowBox.isSelected();

            if (callback != null) callback.onApply();
            refreshMenuState();

        } catch (IllegalArgumentException e) {
            System.out.println("Apply blocked: " + e.getMessage());
        }
    }
}