import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.WKTReader;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.GeometryModel;
import org.geotools.geometry.jts.JTSFactoryFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

//import javafx.scene.Node;
//import javafx.scene.Parent;

public class Controller {
    /**
     * Identifies value of zooming (e.g 140% -> 1.4)
     */
    private static final double ZOOM_FACTOR = 1.4;
    /**
     * Current level of zooming (0 -> default
     */
    private static int ZOOM_LEVEL = 0;

    @FXML
    private TextArea queryInput;
    @FXML
    private AnchorPane upperPane;
    @FXML
    private VBox vboxLayers;

    private double dragBaseX, dragBaseY;
    private double dragBase2X, dragBase2Y;
    private Stage stage;
    /**
     * Stores geometries with original coordinates
     */
    private final Vector originalGeometries;
    /**
     * Stored all GisVisualizations
     */
    private final Vector<GisVisualization> gisVisualizations;
    private static List<KeyCode> heldDownKeys = new ArrayList<>();

    public Controller() {
        originalGeometries = new Vector(1, 1);
        gisVisualizations = new Vector(1, 1);
    }

    @FXML
    public final void pressed() {
        drawPolygonFromWKT(queryInput.getText());
        upperPane.setOnScroll(getOnScrollEventHandler());

        vboxLayers.getChildren().add(new HBox());
    }

    public final void setStage(final Stage stage) {
        this.stage = stage;
    }

    /**
     * Adds geometry or geometry collection into vector
     * @param  geom geometry that has to be saved
     */
    public void saveOriginalGeometries(Geometry geom) {
        if (geom instanceof GeometryCollection) {
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                originalGeometries.add(geom.getGeometryN(i));
            }
        }
        else {
            originalGeometries.add(geom);
        }
    }

    /**
     * Creates and saves geometry. Calls methods to create new layer and visualization
     * @param poly Well Known Text from user input
     */
    public final void drawPolygonFromWKT(final String poly) {
        try {
            //Create a WKT parser for reading WKT input
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
            WKTReader reader = new WKTReader(geometryFactory);
            Geometry geom = reader.read(poly);

            drawPolygon(geom);

            // clone geometry to save 2 different objects. One with original coordinates, the other with actual (scaled) coordinates
            Geometry geomClone = (Geometry) geom.clone();
            saveOriginalGeometries(geomClone);
            if (ZOOM_LEVEL != 0) {
                double scale = Math.pow(ZOOM_FACTOR, ZOOM_LEVEL);
                resizeGeometryModel(geomClone, gisVisualizations.lastElement(), scale);
            }
        } catch (com.vividsolutions.jts.io.ParseException e) {
            e.printStackTrace();
        }
    }

    public final void drawPolygon(final Geometry geom) {
        if (geom instanceof GeometryCollection) {
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                refineGeometryClass(geom.getGeometryN(i));
            }
        } else {
            refineGeometryClass(geom);
        }
    }
    /**
     * Delegates the task of creating the layer for this geometry. Whether it is a plain WKT object,
     * or a composite such as a MultiPolygon.
     * @param geometry geometry to consider.
     */
    private void refineGeometryClass(final Geometry geometry) {
        if (geometry instanceof GeometryCollection) {
            createLayersFromMultiples(geometry);
        } else {
            createLayer(geometry);
        }
    }

    /**
     * Assumes the given geometry is of a multiple type, and creates a layer for each.
     * @param geometry geometry to consider.
     */
    private void createLayersFromMultiples(final Geometry geometry) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            createLayer(geometry.getGeometryN(i));
        }
    }

    /**
     * Creates a layer for the given geometry.
     * @param geometry geometry to draw to a layer.
     */
    private void createLayer(final Geometry geometry) {
        GisVisualization gv = GisVisualization.createVisualization(
                this.stage.getWidth(),
                this.stage.getHeight(),
                geometry,
                upperPane);
        gisVisualizations.add(gv);
        Layer hb = new Layer(gv, vboxLayers, geometry.getGeometryType());
        Layer.getLayers().add(hb);
        hb.reorderLayers();
        upperPane.requestFocus();
    }


    private void zoom(final double d) {
        upperPane.setScaleX(upperPane.scaleXProperty().get() * d);
        upperPane.setScaleY(upperPane.scaleYProperty().get() * d);
    }

    public final void handleUpperPaneKeyPresses(final KeyEvent event) {
        switch (event.getText()) {
            case "+":
                zoom(ZOOM_FACTOR);
                break;
            case "-":
                zoom(1 / ZOOM_FACTOR);
                break;
            default:
                break;
        }
    }

    // TODO
    // Concerns: dragging only works when clicking the canvas,
    // areas of pane not filled with canvas does not react
    // Possible solutions: Make a really huge canvas and translate
    // 0,0 to middle of screen. Or find another node and listener to move canvas

    public final EventHandler<ScrollEvent> getOnScrollEventHandler() {
        return onScrollEventHandler;
    }

    public void zoomByChangingGeometries() {
        double current_zoom = Math.pow(ZOOM_FACTOR, ZOOM_LEVEL); // ZOOM_FACTOR ^ ZOOM_LEVEL;

        Geometry geom;
        GeometryModel gm;
        Coordinate coord_orig[];
        Coordinate coord[];

        // resize and redraw all geometries
        for (int i = 0; i < originalGeometries.size(); i++) {
            geom = (Geometry) originalGeometries.get(i);
            resizeGeometryModel(geom, gisVisualizations.get(i), current_zoom);

            //gisVisualizations.get(i).getGeometryModel().drawAndCreateToolTips(gisVisualizations.get(i).getGraphicsContext());
        }
    }

    public final void resizeGeometryModel(Geometry originalGeometry, GisVisualization gisVisualization, double scale) {
        Coordinate coord_orig[];
        Coordinate coord[];
        GeometryModel gm = gisVisualization.getGeometryModel();
        // get original coordinates
        coord_orig = originalGeometry.getCoordinates();
        // get actual (scaled) coordinates
        coord = gm.getGeometry().getCoordinates();

        for (int j = 0; j < coord_orig.length; j++) {
            coord[j].x = coord_orig[j].x * scale;
            coord[j].y = coord_orig[j].y * scale;
        }

        // remove tooltips' circles from canvas
       // gm.removeTooltips();
        // redraw
        gisVisualization.reDraw();
    }
    /**
     * Mouse wheel handler: zoom to pivot point.
     */
    private EventHandler<ScrollEvent> onScrollEventHandler = new EventHandler<ScrollEvent>() {

        @Override
        public void handle(final ScrollEvent event) {
            if (event.getDeltaY() < 0) {
                ZOOM_LEVEL--;
                zoomByChangingGeometries();
            } else {
                ZOOM_LEVEL++;
                zoomByChangingGeometries();

            }
        }

    };



    //TODO Concerns: dragging only works when clicking the canvas,
        //areas of pane not filled with canvas does not react
    //TODO possible solutions: Make a really huge canvas and translate 0,0 to middle of screen.
        // Or find another node and listener to move canvas

    /**
     * Called when the mouse press the upperPane.
     * This pane will receive focus and save the coordinates of the press to be used for dragging.
     * @param event MouseEvent to react to.
     */
    public final void upperPaneMousePressed(final MouseEvent event) {
        upperPane.requestFocus();

        dragBaseX = upperPane.translateXProperty().get();
        dragBaseY = upperPane.translateYProperty().get();
        dragBase2X = event.getSceneX();
        dragBase2Y = event.getSceneY();
    }

    /**
     * Called when upperPane is being dragged and drag it accordingly.
     * Also sets cursor to Cursor.Move.
     * @param event MouseEvent to react to.
     */
    public final void upperPaneMouseDragged(final MouseEvent event) {
        this.stage.getScene().setCursor(Cursor.MOVE);
        upperPane.setTranslateX(dragBaseX + (event.getSceneX() - dragBase2X));
        upperPane.setTranslateY(dragBaseY + (event.getSceneY() - dragBase2Y));
    }

    /**
     * Called when mouse releases on upperPane. Makes sure cursor goes back to normal.
     */
    public final void upperPaneMouseReleased() {
        this.stage.getScene().setCursor(Cursor.DEFAULT);
    }



    /*private Node isChildFocused(final Parent parent) {
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (node.isFocused()) {
                return node;
            } else if (node instanceof Parent) {
                if (isChildFocused((Parent) node) != null) {
                    return node;
                }
            }
        }
        return null;
    }*/



    public final void WktAreaKeyPressed(final KeyEvent event) {
        if (event.isAltDown() && event.getCode() == KeyCode.ENTER) {
            pressed();
        }
    }

    public final void onAnyKeyPressed(final KeyEvent event) {
        if (!heldDownKeys.contains(event.getCode())) {
            heldDownKeys.add(event.getCode());
        }
    }

    public final void onAnyKeyReleased(final KeyEvent event) {
        heldDownKeys.remove(event.getCode());
    }

    public static boolean isKeyHeldDown(final KeyCode code) {
        return heldDownKeys.contains(code);
    }
}
