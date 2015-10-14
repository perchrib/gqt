import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import models.GeometryModel;
import models.ModelBoundaries;

import java.util.ArrayList;
import java.util.List;


public class Controller {
    /**
     * Identifies value of zooming (e.g 140% -> 1.4).
     */
    private static final double ZOOM_FACTOR = 1.4;
    private static final int DRAG_SENSITIVITY = 3;
    /**
     * Current level of zooming (0 -> default).
     */
    private int currentZoomLevel = 0;

    private double currentZoom = 1;

    private double currentOffsetX = 0;
    private double currentOffsetY = 0;
    private double mouseMoveOffsetX = 0;
    private double mouseMoveOffsetY = 0;

    @FXML
    private TextArea queryInput;
    @FXML
    private AnchorPane upperPane;
    @FXML
    private VBox vboxLayers;
    @FXML
    private TextField zoomText;
    @FXML
    private Text zoomTextError;
    @FXML
    private Text positionX;
    @FXML
    private Text positionY;

    private double dragBase2X, dragBase2Y;
    private double dragBeginX, dragBeginY;
    private Stage stage;

    /**
     * Stored all GisVisualizations.
     */
    private static List<KeyCode> heldDownKeys = new ArrayList<>();

    public Controller() {

    }


    @FXML
    public final void updateLayer() {
        WktParser wktParser = new WktParser(Layer.getSelectedLayer(), upperPane);
        boolean result = wktParser.parseWktString(queryInput.getText());
        if (result) {
            wktParser.updateLayerGeometries();
            rescaleAllGeometries();
        }
    }

    public final void createEmptyLayer() {
        Layer l = new Layer(null, vboxLayers, "Empty", "", queryInput);
        Layer.getLayers(false).add(l);
        l.addLayerToView();
        //To ensure the latest new layer will be selected.
        l.handleLayerMousePress();
    }

    public final void setStage(final Stage stage) {
        this.stage = stage;
    }

    public final double getZoomScale(final double zoomFactor, final int zoomLevel) {
        return Math.pow(zoomFactor, zoomLevel);
    }


    /**
     * Change coordinates of all geometries by scale of current zoom and move center of view from
     * top-left corner to the middle. (0, 0) coordinate is in the middle
     */
    public final void rescaleAllGeometries() {
        /*if (currentZoom == 0) {
            currentZoom = getZoomScale(ZOOM_FACTOR, currentZoomLevel);
        }*/
        double centerX = upperPane.getWidth() / 2;
        double centerY = upperPane.getHeight() / 2;
        ArrayList<GeometryModel> geometryModelList;
        AnchorPane plotViewGroup = GisVisualization.getGroup();
        //Make sure to reset the GisVisualization, this empties the canvas and tooltips
        GisVisualization.reset();

        zoomText.setText("" + String.format("%.2f", currentZoom * 100));

        // resize and redraw all geometries
        for (int i = Layer.getLayers(true).size() - 1; i >= 0; i--) {
            Layer layer = Layer.getLayers(true).get(i);
            GisVisualization gisVisualization = layer.getGisVis();
            geometryModelList = gisVisualization.getGeometryModelList();
            for (GeometryModel gm : geometryModelList) {
                gm.transformGeometry(currentZoom,
                        this.currentOffsetX + centerX,
                        this.currentOffsetY + centerY);
            }
            // redraw
            layer.redraw2DShape();
            //Move and add all tooltips
            gisVisualization.moveTooltips();
            //Only add them if the corresponding layer is checked and selected.
            if (layer.getIfTooltipsShouldBeDisplayed()) {
                plotViewGroup.getChildren().addAll(gisVisualization.getTooltips());
            }
        }
    }

    /**
     * Changes coordinates of all geometries.
     * @param offsetX change of X coordinates
     * @param offsetY change of Y coordinates
     */
    public final void moveAllGeometries(final double offsetX, final double offsetY) {
        AnchorPane plotViewGroup = GisVisualization.getGroup();

        //Make sure to reset the GisVisualization, this empties the canvas and tooltips
        GisVisualization.reset();
        // resize and redraw all geometries
        for (int i = Layer.getLayers(true).size() - 1; i >= 0; i--) {
            Layer layer = Layer.getLayers(true).get(i);
            GisVisualization gisVisualization = layer.getGisVis();

            // moving geometries to a List decreased dragging performance
            for (GeometryModel gm : gisVisualization.getGeometryModelList()) {
                gm.moveGeometry(offsetX, offsetY);
            }
            // redraw
            layer.redraw2DShape();
            //Move and add all tooltips
            gisVisualization.moveTooltips();
            //Only add them if the corresponding layer is checked and selected.
            if (layer.getIfTooltipsShouldBeDisplayed()) {
                plotViewGroup.getChildren().addAll(gisVisualization.getTooltips());
            }
        }
    }

    private double logZoomFactor(final double x) {
        return Math.log(x) / Math.log(ZOOM_FACTOR);
    }

    private ModelBoundaries fillBoundariesWithLayers(final ArrayList<Layer> layers) {
        ModelBoundaries boundaries = new ModelBoundaries();
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            GisVisualization gisVisualization = layer.getGisVis();

            for (GeometryModel gm : gisVisualization.getGeometryModelList()) {
                boundaries.includeGeometry(gm.getOriginalGeometry());
            }
        }
        return boundaries;
    }

    public final void zoomToFitAll() {
        ModelBoundaries modelBoundaries = fillBoundariesWithLayers(Layer.getLayers(true));
        zoomToFit(modelBoundaries);
    }

    public final void zoomToFitSelected() {
        ModelBoundaries modelBoundaries =
                fillBoundariesWithLayers(Layer.getAllSelectedLayers(true));
        zoomToFit(modelBoundaries);
    }

    public final void zoomToFitVisible() {
        ModelBoundaries modelBoundaries =
                fillBoundariesWithLayers(Layer.getAllVisibleLayers(true));
        zoomToFit(modelBoundaries);
    }

    private int getBestZoomLevel(final ModelBoundaries modelBoundaries) {
        double scaleX = upperPane.getWidth() / modelBoundaries.getWidth();
        double scaleY = upperPane.getHeight() / modelBoundaries.getHeight();
        double scaleMin = Math.min(scaleX, scaleY);
        double zoomLevel;
        int bestZoomLevel;

        zoomLevel = logZoomFactor(scaleMin);
        // correction because of truncating negative numbers in a way that doesn't fit this purpose
        if (zoomLevel < 0) {
            zoomLevel--;
        }
        bestZoomLevel = (int) zoomLevel;
        return bestZoomLevel;
    }
    /**
     * Scales geometries to fit to current view.
     */
    public final void zoomToFit(final ModelBoundaries modelBoundaries) {
        if (modelBoundaries.isNull()) {
            return;
        }
        currentZoomLevel = getBestZoomLevel(modelBoundaries);

        double zoomScale = getZoomScale(ZOOM_FACTOR, currentZoomLevel);
        this.currentOffsetX = -(modelBoundaries.getMiddleX() * zoomScale);
        this.currentOffsetY = -(modelBoundaries.getMiddleY() * zoomScale);

        setZoomLevel();
        rescaleAllGeometries();
    }

    private void setZoomLevel() {
        currentZoom = getZoomScale(ZOOM_FACTOR, currentZoomLevel);
    }
    public final void resetView() {
        currentZoomLevel = 0;
        this.currentOffsetX = 0;
        this.currentOffsetY = 0;

        setZoomLevel();
        rescaleAllGeometries();
    }
    public final void zoomIn() {
        currentZoomLevel++;
        setZoomLevel();
        rescaleAllGeometries();
    }

    public final void zoomOut() {
        currentZoomLevel--;
        setZoomLevel();
        rescaleAllGeometries();
    }

    public final void handleUpperPaneKeyPresses(final KeyEvent event) {
        switch (event.getText()) {
            case "+":
                zoomIn();
                break;
            case "-":
                zoomOut();
                break;
            case "*":
                //zoomToFitSelected();
                zoomToFitVisible();
                break;
            case "/":
                resetView();
                break;
            default:
                break;
        }
    }

    public final void mouseScrollEvent(final ScrollEvent event) {
        // scroll down
        if (event.getDeltaY() < 0) {
            zoomOut();
        } else { // scroll up
            zoomIn();
        }
    }

    /**
     * Called when the mouse press the upperPane.
     * This pane will receive focus and save the coordinates of the press to be used for dragging.
     * @param event MouseEvent to react to.
     */
    public final void upperPaneMousePressed(final MouseEvent event) {
        upperPane.requestFocus();
        dragBase2X = event.getSceneX();
        dragBase2Y = event.getSceneY();
        dragBeginX = dragBase2X;
        dragBeginY = dragBase2Y;
    }

    /**
     * Called when upperPane is being dragged and drag it accordingly.
     * Also sets cursor to Cursor.Move.
     * @param event MouseEvent to react to.
     */
    public final void upperPaneMouseDragged(final MouseEvent event) {
        this.stage.getScene().setCursor(Cursor.MOVE);
        mouseMoveOffsetX += (event.getSceneX() - dragBase2X);
        dragBase2X = event.getSceneX();
        mouseMoveOffsetY += (event.getSceneY() - dragBase2Y);
        dragBase2Y = event.getSceneY();
        // performance improvement
        if (Math.abs(mouseMoveOffsetX) > DRAG_SENSITIVITY
                || Math.abs(mouseMoveOffsetY) > DRAG_SENSITIVITY) {
            moveAllGeometries(mouseMoveOffsetX, mouseMoveOffsetY);
            mouseMoveOffsetX = 0;
            mouseMoveOffsetY = 0;
        }
    }

    /**
     * Called when mouse releases on upperPane. Makes sure cursor goes back to normal.
     */
    public final void upperPaneMouseReleased(final MouseEvent event) {
        this.stage.getScene().setCursor(Cursor.DEFAULT);
        this.currentOffsetX += event.getSceneX() - dragBeginX;
        this.currentOffsetY += event.getSceneY() - dragBeginY;
        moveAllGeometries(mouseMoveOffsetX, mouseMoveOffsetY);
        mouseMoveOffsetX = 0;
        mouseMoveOffsetY = 0;
        //calculateBoundaries();
    }

    public final void wktAreaKeyPressed(final KeyEvent event) {
        if (event.isAltDown() && event.getCode() == KeyCode.ENTER) {
            updateLayer();
        }
    }

    public final void onAnyKeyPressed(final KeyEvent event) {
        if (!heldDownKeys.contains(event.getCode())) {
            heldDownKeys.add(event.getCode());
        }
    }


    public final void handleSceneScrollEvent(final ScrollEvent event) {
        if (event.isControlDown()) {
            mouseScrollEvent(event);
        }
    }

    public final void handleSceneKeyEvent(final KeyEvent event) {
        if (event.isControlDown()) {
            handleUpperPaneKeyPresses(event);
        }
    }

    public final void upperPaneMouseMoved(final MouseEvent event) {
        double centerX = upperPane.getWidth() / 2;
        double centerY = upperPane.getHeight() / 2;
        double sceneX = event.getSceneX();
        double sceneY = event.getSceneY();
        double positionX = (sceneX - currentOffsetX - centerX) / currentZoom;
        double positionY = -(sceneY - currentOffsetY - centerY) / currentZoom;
        this.positionX.setText("X: " + (int) positionX);
        this.positionY.setText("Y: " + (int) positionY);
    }

    public final void zoomTextKeyPressed(final KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            try {
                double zoomFactor = Double.parseDouble(zoomText.getText());
                zoomTextError.setVisible(false);
                zoomFactor /= 100;
                currentZoom = zoomFactor;
                rescaleAllGeometries();
                currentZoomLevel = (int)logZoomFactor(zoomFactor);
            } catch (NumberFormatException e) {
                zoomTextError.setVisible(true);
            }

        }
    }


    public final void onAnyKeyReleased(final KeyEvent event) {
        heldDownKeys.remove(event.getCode());
    }

    public static boolean isKeyHeldDown(final KeyCode code) {
        return heldDownKeys.contains(code);
    }
}
