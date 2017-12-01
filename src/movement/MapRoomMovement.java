package movement;

import core.Coord;
import core.Settings;
import core.SimClock;
import movement.helpers.*;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.MapRoute;

import java.util.List;


/**
 * Map based movement model that uses predetermined paths within the map area.
 * Predetermined paths are generated between point objects found in the map file.
 * Nodes using this model (can) stop on every route waypoint and find their
 * way to next waypoint using {@link DijkstraPathFinder}.
 */
public class MapRoomMovement extends MapBasedMovement {


    public static final String MAP_ROOM_MOVEMENT_NS = "MapRoomMovement";
    public static final String ROUTE_FILE_S = "routeFile";
    public static final String ROUTE_TYPE_S = "routeType";
    public static final String CHANCE_FOR_UBAHN_SETTING = "chanceForUbahn";
    public static final String START_PEAK_ENTER_TIME_DIFFERENCE_SETTING = "startPeakEnterTimeDifference";
    public static final String ENTER_LECTURE_STDDEV_SETTING = "enterLectureStddev";
    public static final String TIME_INTERVALS_PER_MINUTE_SETTING = "timeIntervalsPerMinute";

    private final int StartPeakEnterTimeDifference;
    private final double EnterLectureStddev;
    private final int TimeIntervalsPerMinute;

    private String routeFileName;
    private int routeType;
    private DijkstraPathFinder pathFinder;
    private List<MapRoute> allRoutes = null;

    private Settings modelSettings;
    private Settings groupSettings;

    private List<Room> rooms;
    private RandomHelper randomHelper;
    private RoomHelper roomHelper;
    private EnterExitHelper enterExitHelper;
    private double chanceForUbahn;

    private Schedule schedule;
    private int enterTime;
    private int exitTime;
    private boolean byUbahn;

    private boolean enterNextLectureRoom = false;

    protected MapNode realLastMapNode;

    /**
     * Creates a new movement model based on a Settings object's settings.
     * @param settings The Settings object where the settings are read from
     */
    public MapRoomMovement(Settings settings) {
        super(settings);
        RandomHelper.createInstance(rng);
        randomHelper = RandomHelper.getInstance();

        this.modelSettings = new Settings(MAP_ROOM_MOVEMENT_NS);
        this.groupSettings = settings;

        EnterLectureStddev = settings.getDouble(ENTER_LECTURE_STDDEV_SETTING);
        StartPeakEnterTimeDifference = settings.getInt(START_PEAK_ENTER_TIME_DIFFERENCE_SETTING);
        TimeIntervalsPerMinute = settings.getInt(TIME_INTERVALS_PER_MINUTE_SETTING);

        routeFileName = modelSettings.getSetting(ROUTE_FILE_S);
        routeType = modelSettings.getInt(ROUTE_TYPE_S);
        allRoutes = MapRoute.readRoutes(routeFileName, routeType, getMap());
        RoomHelper.createInstance(modelSettings, allRoutes, getMap());
        roomHelper = RoomHelper.getInstance();
        pathFinder = new DijkstraPathFinder(getOkMapNodeTypes());

        rooms = roomHelper.getAllRooms();
        this.schedule = new Schedule(groupSettings, randomHelper, roomHelper.utilization);

        RandomHelper.createInstance(MovementModel.rng);
        this.randomHelper = RandomHelper.getInstance();

        this.chanceForUbahn = groupSettings.getDouble(CHANCE_FOR_UBAHN_SETTING);
        this.byUbahn = this.randomHelper.getRandomDouble() < this.chanceForUbahn;

        this.enterExitHelper = new EnterExitHelper(settings);
        initEnterExitTime();
    }

    public MapRoomMovement(MapRoomMovement proto) {
        super(proto);
        this.groupSettings = proto.groupSettings;
        this.modelSettings = proto.modelSettings;
        this.schedule = new Schedule(proto.groupSettings, proto.randomHelper, proto.roomHelper.utilization);
        this.randomHelper = proto.randomHelper;
        this.enterExitHelper = proto.enterExitHelper;
        this.chanceForUbahn = proto.chanceForUbahn;
        this.pathFinder = proto.pathFinder;
        this.allRoutes = proto.allRoutes;
        this.rooms = proto.rooms;
        this.roomHelper = proto.roomHelper;

        this.EnterLectureStddev = proto.EnterLectureStddev;
        this.StartPeakEnterTimeDifference = proto.StartPeakEnterTimeDifference;
        this.TimeIntervalsPerMinute = proto.TimeIntervalsPerMinute;

        this.byUbahn = this.randomHelper.getRandomDouble() < this.chanceForUbahn;
        this.initEnterExitTime();
    }

    private void initEnterExitTime() {
        Integer enterTime = this.enterExitHelper.enterTimeForSchedule(schedule, byUbahn);
        this.enterTime = enterTime != null ? enterTime : Integer.MAX_VALUE;
        if (enterTime == null) {
            this.exitTime = this.enterTime;
        } else {
            this.exitTime = this.enterExitHelper.exitTimeForSchedule(schedule, byUbahn, enterTime);
        }
    }

    @Override
    public Path getPath() {
        Path p = new Path(generateSpeed());

        MapNode to;
        final int curTime = SimClock.getIntTime();
        final int timeInsecurity = 10;
        if (curTime + timeInsecurity > this.exitTime) {
            double random = this.randomHelper.getRandomDouble();
            to = RoomHelper.getInstance().getRoomAccordingToProbability(RoomType.ENTRY_EXIT, random).getNode();
        } else {
            ScheduleSlot nextSlot = this.schedule.getNextScheduleSlot(curTime - timeInsecurity);
            ScheduleSlot activeSlot = this.schedule.getActiveScheduleSlot(curTime);
            if (this.enterNextLectureRoom) {
                this.enterNextLectureRoom = false;
                if (activeSlot != null && activeSlot.getStartTime() - curTime < 30 * TimeIntervalsPerMinute) {
                    to = activeSlot.getRoom().getNode();
                } else {
                    to = nextSlot.getRoom().getNode();
                }
            } else {
                final int fiveMinutes = TimeIntervalsPerMinute * 5;
                if (nextSlot != null && nextSlot.getStartTime() - curTime < fiveMinutes) {
                    to = nextSlot.getRoom().getNode();
                } else {
                    if (activeSlot != null) {
                        to = activeSlot.getRoom().getNode();
                    } else {
                        List<Room> otherRooms = this.roomHelper.getRoomsWithType(RoomType.OTHER);
                        otherRooms.addAll(this.roomHelper.getRoomsWithType(RoomType.MAGISTRALE));
                        to = otherRooms.get(this.randomHelper.getRandomIntBetween(0, otherRooms.size())).getNode();
                    }
                }
            }
        }

        if (this.lastMapNode == to) {
            return null;
        }
        Coord realCoord = new Coord(to.getLocation().getX() + randomHelper.getRandomDoubleBetween(-5, 5), to.getLocation().getY() + randomHelper.getRandomDoubleBetween(-5, 5));
        MapNode realTo = new MapNode(realCoord);
        List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, to);

        // this assertion should never fire if the map is checked in read phase
        assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " +
                to + ". The simulation map isn't fully connected";


        List<Room> enterExitRooms = RoomHelper.getInstance().getRoomsWithType(RoomType.ENTRY_EXIT);
        if (!roomListContainsCoord(enterExitRooms, to.getLocation())) {
            nodePath.add(0, realLastMapNode);
            nodePath.add(nodePath.size(), realTo);
        }

        for (MapNode node : nodePath) { // create a Path from the shortest path
            p.addWaypoint(node.getLocation());
        }

        realLastMapNode = realTo;
        lastMapNode = to;

        return p;
    }

    private boolean roomListContainsCoord(List<Room> rooms, Coord coord) {
        for (Room room : rooms) {
            if (room.getNode().getLocation().equals(coord)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first stop on the route which is one of the lecture rooms
     */
    @Override
    public Coord getInitialLocation() {
        if (nextPathAvailable() == Double.MAX_VALUE) {
            return new Coord(0,0);
        }
        if (lastMapNode == null) {
            lastMapNode = RoomHelper.getInstance().getRoomAccordingToProbability(RoomType.ENTRY_EXIT, randomHelper.getRandomDouble()).getNode();
            realLastMapNode = lastMapNode;
        }

        return lastMapNode.getLocation().clone();
    }

    @Override
    public Coord getLastLocation() {
        if (realLastMapNode != null) {
            return realLastMapNode.getLocation().clone();
        } else {
            return null;
        }
    }

    @Override
    public MapRoomMovement replicate() {
        return new MapRoomMovement(this);
    }

    @Override
    public boolean isActive() {
        ScheduleSlot lunchSlot = this.schedule.getLunchTimeSlot();
        int curTime = SimClock.getIntTime();
        if (lunchSlot != null) {
            return (curTime >= this.enterTime - 5 * TimeIntervalsPerMinute && curTime <= lunchSlot.getStartTime() + 5 * TimeIntervalsPerMinute) || (curTime >= lunchSlot.getEndTime() - 5 * TimeIntervalsPerMinute &&
                    curTime <= this.exitTime + 30 * TimeIntervalsPerMinute);
        }
        return curTime >= this.enterTime - 5 * TimeIntervalsPerMinute &&
                curTime <= this.exitTime + 5 * TimeIntervalsPerMinute;
    }

    @Override
    public double nextPathAvailable() {
        final double curTime = SimClock.getTime();
        if ( curTime < this.enterTime ) {
            return this.enterTime;
        } else if ( curTime > this.exitTime ) {
            return Double.MAX_VALUE;
        }

        ScheduleSlot activeSlot = this.schedule.getActiveScheduleSlot((int)curTime - 1);
        if (activeSlot != null) {
            return activeSlot.getEndTime();
        }

        ScheduleSlot nextSlot = this.schedule.getNextScheduleSlot((int)curTime);
        if (nextSlot != null) {
            if (nextSlot.getRoom().getNode() == this.lastMapNode) {
                return nextSlot.getEndTime();
            }

            if (nextSlot.isLunchSlot()) {
                return nextSlot.getStartTime() < curTime ? curTime : nextSlot.getStartTime();
            }

            if (nextSlot.getStartTime() > curTime + 30 * this.TimeIntervalsPerMinute) {
                double nextPath = this.randomHelper.getNormalRandomWithMeanAndStddev((nextSlot.getStartTime() + curTime)/2, (nextSlot.getStartTime() - curTime)/2);
                if (nextPath < nextSlot.getStartTime() - this.StartPeakEnterTimeDifference && nextPath > curTime + this.StartPeakEnterTimeDifference) {
                    this.enterNextLectureRoom = true;
                    return nextPath;
                }
            }
            double enterNextLectureTime = this.randomHelper.getNormalRandomWithMeanAndStddev(
                    nextSlot.getStartTime() - this.StartPeakEnterTimeDifference, this.EnterLectureStddev);
            if (enterNextLectureTime < curTime) {
                enterNextLectureTime = curTime;
            }
            if (enterNextLectureTime < nextSlot.getStartTime() - this.StartPeakEnterTimeDifference*2) {
                enterNextLectureTime = nextSlot.getStartTime() - this.StartPeakEnterTimeDifference*2;
            }
            if (enterNextLectureTime > nextSlot.getStartTime() + this.StartPeakEnterTimeDifference) {
                enterNextLectureTime = nextSlot.getStartTime() + this.StartPeakEnterTimeDifference;
            }
            this.enterNextLectureRoom = true;
            return enterNextLectureTime;
        }
        return this.exitTime;
    }
}