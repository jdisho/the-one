package movement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import core.SettingsError;
import input.WKTReader;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.MapRoute;
import core.Coord;
import core.Settings;
import movement.map.SimMap;

import static movement.map.MapRoute.CIRCULAR;
import static movement.map.MapRoute.PINGPONG;

/**
 * Map based movement model that uses predetermined paths within the map area.
 * Predetermined paths are generated between point objects found in the map file.
 * Nodes using this model (can) stop on every route waypoint and find their
 * way to next waypoint using {@link DijkstraPathFinder}. There can be
 * different type of routes; see {@link #ROUTE_TYPE_S}.
 */
public class MapRoomMovement extends MapRouteMovement {

    /**
     * Creates a new movement model based on a Settings object's settings.
     * @param settings The Settings object where the settings are read from
     */
    public MapRoomMovement(Settings settings) {
        super(settings);
        allRoutes = generatePointRoutes(allRoutes, routeFileName, routeType, getMap());
        initFirstIndex(settings);
    }

    private List<MapRoute> generatePointRoutes(List<MapRoute> tempRoutes, String fileName, int type, SimMap map) {

        List<MapRoute> routes = new ArrayList<>();
        WKTReader reader = new WKTReader();
        List<Coord> points;
        File routeFile;

        boolean mirror = map.isMirrored();
        double xOffset = map.getOffset().getX();
        double yOffset = map.getOffset().getY();

        if (type != CIRCULAR && type != PINGPONG) {
            throw new SettingsError("Invalid route type (" + type + ")");
        }

        try {
            routeFile = new File(fileName);
            points = reader.readPoints(routeFile);
        }
        catch (IOException ioe){
            throw new SettingsError("Couldn't read MapRoute-data file " +
                    fileName + 	" (cause: " + ioe.getMessage() + ")");
        }

        for (Coord point : points) {
            if (mirror) {
                point.setLocation(point.getX(), -point.getY());
            }
            point.translate(xOffset, yOffset);
        }

        HashMap<Coord, MapNode> roomMapNode = new HashMap<>();
        for (MapRoute route : tempRoutes) {
            for (MapNode node : route.getStops()) {
                for (Coord point : points) {
                    if (node.getLocation().equals(point)) {
                        roomMapNode.put(point, node);
                    }
                }
            }
        }

        for (MapNode nOutside : roomMapNode.values()){
            for (MapNode nInside : roomMapNode.values()){
                if (!nOutside.equals(nInside)) {
                    routes.add(new MapRoute(type, pathFinder.getShortestPath(nOutside, nInside)));
                }
            }
        }
        return routes;
    }
}