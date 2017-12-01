package movement.helpers;

import core.Coord;
import core.Settings;
import core.SettingsError;
import input.WKTReader;
import movement.map.MapNode;
import movement.map.MapRoute;
import movement.map.SimMap;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RoomHelper {

    public static final String ROOM_PREFIX_S = "room";
    public static final String ROOM_COUNT_S = "roomCount";
    public static final String ROUTE_FILE_S = "routeFile";
    public static final String ROOM_CAPACITY_OTHER_I = "roomCapacityOther";

    public static final int ROOM_CONFIG_ID = 0;
    public static final int ROOM_CONFIG_POINT_X = 1;
    public static final int ROOM_CONFIG_POINT_Y = 2;
    public static final int ROOM_CONFIG_CAPACITY = 3;
    public static final int ROOM_CONFIG_ROOMTYPE = 4;
    public static final int ROOM_CONFIG_PROBABILITY = 5;

    private Set<MapNode> roomNodes;
    private List<Room> rooms;
    private static RoomHelper instance = null;
    public Map<Room, Map<ScheduleSlot, Integer>> utilization;

    public RoomHelper(Settings modelSettings, List<MapRoute> routes, SimMap map) {
        this.readAllRooms(modelSettings, routes, map);
        this.utilization = new HashMap<>();
        for (Room room: this.getRoomsWithType(RoomType.LECTURE_ROOM)) {
            this.utilization.put(room, null);
        }
    }

    public static RoomHelper getInstance() {
        return RoomHelper.instance;
    }

    public static void createInstance(Settings modelSettings, List<MapRoute> routes, SimMap map) {
        if (RoomHelper.instance == null) {
            RoomHelper.instance = new RoomHelper(modelSettings, routes, map);
        }
    }

    public List<Room> getAllRooms() {
        return rooms;
    }

    public List<Room> getRoomsWithType(RoomType type){
        return rooms.stream().filter(x -> x.getType() == type).collect(Collectors.toList());
    }

    public Room getRoomById(int id) {
        return rooms.stream()
                .filter(x -> x.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public Room getRoomAccordingToProbability(RoomType type, double random) {
        double cumulativeProbability = 0;
        for (Room room : getRoomsWithType(type)) {
            cumulativeProbability += room.getProbability();
            if (random <= cumulativeProbability) {
                return room;
            }
        }
        return null;
    }

    public Room getRandomEatingRoom() {
        List<Room> eatingRooms = getRoomsWithType(RoomType.MAGISTRALE);
        if (eatingRooms.size() > 0) {
            return eatingRooms.get(RandomHelper.getInstance().getRandomIntBetween(0, eatingRooms.size()));
        }
        return null;
    }

    public Room getMensaRoom() {
        List<Room> mensaRooms = getRoomsWithType(RoomType.MENSA);
        if (mensaRooms.size() > 0) {
            return mensaRooms.get(RandomHelper.getInstance().getRandomIntBetween(0, mensaRooms.size()));
        }
        return null;
    }

    private void readAllRooms(Settings settings, List<MapRoute> routes, SimMap map) {
        rooms = new ArrayList<>();
        roomNodes = getNodesForAllPointsInMap(settings, routes, map);
        rooms.addAll(readConfiguredRooms(settings, map));
        int firstId = rooms.get(rooms.size()-1).getId() + 1;
        rooms.addAll(readOtherRooms(settings, firstId));
    }

    private Set<MapNode> getNodesForAllPointsInMap(Settings settings, List<MapRoute> routes, SimMap map) {
        WKTReader reader = new WKTReader();
        String fileName = settings.getSetting(ROUTE_FILE_S);
        List<Coord> points;
        try {
            File routeFile = new File(fileName);
            points = reader.readPoints(routeFile);
        }
        catch (IOException ioe){
            throw new SettingsError("Couldn't read MapRoute-data file " +
                    fileName + 	" (cause: " + ioe.getMessage() + ")");
        }

        for (Coord point : points) {
            pointToMapCoord(point, map);
        }

        Set<MapNode> roomNodes = new HashSet<>();
        for (MapRoute route : routes) {
            for (MapNode node : route.getStops()) {
                for (Coord point : points) {
                    if (node.getLocation().equals(point)) {
                        roomNodes.add(node);
                    }
                }
            }
        }
        return roomNodes;
    }

    private List<Room> readConfiguredRooms(Settings settings, SimMap map) {

        List<Room> rooms = new ArrayList<>();

        int count = settings.getInt(ROOM_COUNT_S);

        for (int i=1; i <= count; i++) {
            double[] raw = settings.getCsvDoubles(ROOM_PREFIX_S + i);
            Coord point = pointToMapCoord(raw[ROOM_CONFIG_POINT_X], raw[ROOM_CONFIG_POINT_Y], map);
            for (MapNode node : roomNodes) {
                if (node.getLocation().equals(point)) {
                    Room room = new Room((int)raw[ROOM_CONFIG_ID], node, (int)raw[ROOM_CONFIG_CAPACITY], RoomType.valueOf((int)raw[ROOM_CONFIG_ROOMTYPE]));
                    if (room.getType() == RoomType.ENTRY_EXIT) {
                        room.setProbability(raw[ROOM_CONFIG_PROBABILITY]);
                    }
                    rooms.add(room);
                    roomNodes.remove(node);
                    break;
                }
            }
        }

        normalizeProbabilities(rooms);

        return rooms;
    }

    private void normalizeProbabilities(List<Room> rooms) {
        double cumulativeProbability = 0.0;
        for (Room r : rooms) {
            cumulativeProbability += r.getProbability();
        }

        for (Room r : rooms) {
            r.setProbability(r.getProbability() / cumulativeProbability);
        }
    }

    private List<Room> readOtherRooms(Settings settings, int firstId) {
        List<Room> otherRooms = new ArrayList<>();
        for (MapNode node : roomNodes) {
            otherRooms.add(new Room(firstId++, node,  settings.getInt(ROOM_CAPACITY_OTHER_I), RoomType.OTHER));
        }
        return otherRooms;
    }

    private static Coord pointToMapCoord(Coord point, SimMap map) {

        boolean mirror = map.isMirrored();
        double xOffset = map.getOffset().getX();
        double yOffset = map.getOffset().getY();

        if (mirror) {
            point.setLocation(point.getX(), -point.getY());
        }
        point.translate(xOffset, yOffset);

        return point;
    }

    private Coord pointToMapCoord(double x, double y, SimMap map) {
        return pointToMapCoord(new Coord(x,y), map);
    }
}