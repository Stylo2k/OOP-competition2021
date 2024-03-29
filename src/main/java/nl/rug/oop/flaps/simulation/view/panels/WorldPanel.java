package nl.rug.oop.flaps.simulation.view.panels;

import lombok.Getter;
import lombok.extern.java.Log;
import nl.rug.oop.flaps.aircraft_editor.view.panels.aircraft_info.interaction_panels.FuelConfigPanel;
import nl.rug.oop.flaps.simulation.controller.AirportSelectionController;
import nl.rug.oop.flaps.simulation.controller.SpeedRateUp;
import nl.rug.oop.flaps.simulation.model.airport.Airport;
import nl.rug.oop.flaps.simulation.model.map.coordinates.GeographicCoordinates;
import nl.rug.oop.flaps.simulation.model.map.coordinates.PointProvider;
import nl.rug.oop.flaps.simulation.model.map.coordinates.ProjectionMapping;
import nl.rug.oop.flaps.simulation.model.trips.Trip;
import nl.rug.oop.flaps.simulation.model.world.World;
import nl.rug.oop.flaps.simulation.model.world.WorldSelectionModel;
import nl.rug.oop.flaps.simulation.model.world.WorldSelectionModelListener;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays the world map and the airport indicators
 *
 * @author T.O.W.E.R.
 */
@Log
public class WorldPanel extends JPanel implements WorldSelectionModelListener {
    public static final double INDICATOR_SIZE = 8;

    private final BufferedImage worldMapImage;
    private final World world;

    private Image cachedWorldMapImage;

    @Getter
    public static WorldPanel worldPanel;
    @Getter
    private ConcurrentHashMap<Trip, Integer> currentTrips;
    @Getter
    private final JSpinner speedUpRate;

    public WorldPanel(World world) {
        this.world = world;
        try {
            worldMapImage = ImageIO.read(Path.of("images", "map", "world_map_satellite.jpg").toFile());
        } catch (IOException e) {
            log.severe("Could not load world map image.");
            throw new IllegalStateException(e);
        }
        speedUpRate = new JSpinner();

        AirportSelectionController selectionController = new AirportSelectionController(world);
        addMouseMotionListener(selectionController);
        addMouseListener(selectionController);
        this.world.getSelectionModel().addListener(this);

        worldPanel = this;

        this.setLayout(new BorderLayout());
        this.add(speedControlPanel(), BorderLayout.SOUTH);
    }

    /**
     * panel containing the speed controller
     * */
    private JPanel speedControlPanel() {
        JPanel temp = new JPanel();
        GridBagConstraints c = new GridBagConstraints();
        FuelConfigPanel.setLayoutOfDisplayPanel(temp, c);
        temp.add(new JLabel("Speed Rate : "));

        speedUpRate.setModel(new SpinnerNumberModel(1.0, 0.5, 4, 0.5));
        speedUpRate.addChangeListener(new SpeedRateUp(this));
        c.ipadx = 5;
        c.gridx = 1;
        temp.add(speedUpRate);
        return temp;
    }

    /**
     * paints the trips on the world map
     * */
    private void paintTrips(Graphics2D g, Trip trip) {
        var sm = this.world.getSelectionModel();
        if (trip.getIcon() != null) {
            BufferedImage icon = trip.getIcon();
            double s = icon.getWidth() / 2.0;
            if (sm.getSelectedTrip() != null && sm.getSelectedTrip().equals(trip)) {
                icon = upscaleIcon(icon);
                s *= 1.5;
                paintSteps(g, trip);
            }
            int x = (int) (trip.getCurrentPosition().getX() - s);
            int y = (int) (trip.getCurrentPosition().getY() - s);
            g.drawImage(icon,x,y, null);
        } else {
            drawDots(g, trip, sm);
        }
    }

    /**
     * draws normal dots when icon is not found
     * */
    private void drawDots(Graphics2D g, Trip trip, WorldSelectionModel sm) {
        double s;
        s = INDICATOR_SIZE;
        g.setColor(Color.GREEN);
        if (sm.getSelectedTrip() != null && sm.getSelectedTrip().equals(trip)) {
            s *= 1.5;
            paintSteps(g, trip);
            g.setColor(Color.YELLOW);
        }
        Shape marker = new Ellipse2D.Double(trip.getCurrentPosition().getX() - s/2, trip.getCurrentPosition().getY()- s/2, s,s);
        g.fill(marker);
    }

    /**
     * @return the icon image but in 1.5 size
     * */
    private BufferedImage upscaleIcon(BufferedImage icon) {
        int newWidth = (int) (icon.getWidth(null) * 1.5);
        int newHeight = (int) (icon.getHeight(null) * 1.5);
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight,BufferedImage.TYPE_INT_ARGB);
        AffineTransform at = new AffineTransform();
        at.scale(1.5, 1.5);
        AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        icon = scaleOp.filter(icon, scaledImage);
        return icon;
    }

    /**
     * paints the steps of the aircraft
     * */
    private void paintSteps(Graphics2D g, Trip trip) {
        var start = ProjectionMapping.mercatorToWorld(this.world.getDimensions())
                .map(trip.getOriginAirport().getLocation());
        var end = trip.getCurrentPosition();
        g.setColor(Color.CYAN);
        Line2D.Double line = new Line2D.Double(start.getPointX(), start.getPointY(), end.x, end.y);
        g.draw(line);
    }

    private void drawAirportIndicator(Graphics2D g, Airport airport) {
        double s = INDICATOR_SIZE;
        Color c = Color.RED;
        var p = ProjectionMapping.mercatorToWorld(this.world.getDimensions())
                .map(airport.getGeographicCoordinates()).asPoint();

        var sm = this.world.getSelectionModel();
        if (sm.getSelectedAirport() != null && sm.getSelectedAirport().equals(airport)) {
            c = Color.CYAN;
            s *= 2;
        } else if (sm.getSelectedDestinationAirport() != null && sm.getSelectedDestinationAirport().equals(airport)) {
            c = Color.GREEN;
            s *= 1.5;
        }
        g.setColor(c);
        Shape marker = new Ellipse2D.Double(p.x - s/2, p.y - s/2, s, s);
        g.fill(marker);
    }

    private void drawTrajectory(Graphics2D g) {
        g.setColor(Color.WHITE);
        var sm = this.world.getSelectionModel();
        // here the user is trying to select a trip as destination (•_•)
        if (sm.getSelectedAirport() == null) {
            sm.setSelectingDestination(false);
            return;
        }
        var start = ProjectionMapping.mercatorToWorld(this.world.getDimensions())
                .map(sm.getSelectedAirport().getLocation());
        var end = new Point2D.Double(sm.getDestinationSelectionCursorX(), sm.getDestinationSelectionCursorY());
        var endM = ProjectionMapping.worldToMercator(this.world.getDimensions())
                .map(PointProvider.ofPoint(end));
        var endGeo = new GeographicCoordinates(endM.getPointX(), endM.getPointY());
        double distance = sm.getSelectedAirport().getLocation().distanceTo(endGeo);
        Color c = distance / 1000 > sm.getSelectedAircraft().getType().getRange() ? Color.RED : Color.WHITE;
        g.setColor(c);
        Line2D.Double line = new Line2D.Double(start.getPointX(), start.getPointY(), end.x, end.y);
        g.draw(line);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3));
        if (this.cachedWorldMapImage == null) { // Use cached world map image to avoid expensive scaling with each repaint.
            this.cachedWorldMapImage = this.worldMapImage.getScaledInstance(
                    this.world.getDimensions().getMapWidth(),
                    this.world.getDimensions().getMapHeight(),
                    Image.SCALE_SMOOTH
            );
        }
        g2d.drawImage(this.cachedWorldMapImage, 0, 0, null);
        var sm = this.world.getSelectionModel();
        if (sm.isSelectingDestination()) {
            drawTrajectory(g2d);
        }
        if (sm.getSelectedDestinationAirport() != null && sm.getSelectedAirport() != null && sm.getSelectedAircraft() != null) {
           drawPlannedRoute(g2d, sm.getSelectedAirport(), sm.getSelectedDestinationAirport());
        }
        this.world.getAirports().values().forEach(airport -> drawAirportIndicator(g2d, airport));

        if (currentTrips != null) {
            for (Trip trip : currentTrips.keySet()) {
                paintTrips(g2d, trip);
            }
        }
    }

    private void drawPlannedRoute(Graphics2D g, Airport selectedAirport, Airport selectedDestinationAirport) {
        var projectionMapping = ProjectionMapping.mercatorToWorld(this.world.getDimensions());
        var start = projectionMapping.map(selectedAirport.getGeographicCoordinates()).asPoint();
        var end = projectionMapping.map(selectedDestinationAirport.getGeographicCoordinates()).asPoint();
        g.setColor(Color.WHITE);
        g.draw(new Line2D.Double(start, end));
    }

    /**
     * adds a trip to the trips list
     * @param trip the trip to be added
     * */
    public void addTrip(Trip trip) {
        if (currentTrips == null) {
            currentTrips = new ConcurrentHashMap<>();
        }
        currentTrips.put(trip, 0);
    }

    /**
     * removes a trip from the trips list
     * @param trip the trip to be removed
     * */
    public void removeTrip(Trip trip) {
        currentTrips.remove(trip);
    }


    @Override
    public void airportSelected(Airport selectedAirport) {
        this.repaint();
    }

    @Override
    public void destinationAirportSelected(Airport destinationAirport) {
        this.repaint();
    }

    @Override
    public void destinationSelectionUpdated() {
        this.repaint();
    }
}
