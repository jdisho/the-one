package movement.helpers;

import core.Settings;
import core.SettingsError;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


public class Schedule implements ScheduleInterface{

    public static final String SCHEDULE_TIME_SETTING = "scheduleTime";
    public static final String AVERAGE_COURSES_PER_DAY_SETTING = "averageCoursesPerDaySetting";
    public static final String FIXED_SCHEDULE_FILE_SETTING = "fixedScheduleFile";
    public static final String LUNCH_TIME_SETTING = "lunchTime";
    public static final String LUNCH_TIME_LENGTH_MEAN_SETTING = "lunchTimeLengthMean";
    public static final String LUNCH_TIME_LENGTH_STDDEV_SETTING = "lunchTimeLengthStddev";
    public static final String TIME_INTERVALS_PER_MINUTE_SETTING = "timeIntervalsPerMinute";
    public static final String CHANCE_FOR_MENSA_SETTING = "ChanceForMensa";

    private List<ScheduleSlot> timeSlots;

    public Schedule(Settings settings, RandomHelper random, Map<Room, Map<ScheduleSlot, Integer>> roomCapacities) {

        timeSlots = new ArrayList<>();

        final double CoursesPerDay = settings.getInt(AVERAGE_COURSES_PER_DAY_SETTING);
        final int[] Times = settings.getCsvInts(SCHEDULE_TIME_SETTING);
        final int[] LunchTimes = settings.getCsvInts(LUNCH_TIME_SETTING);
        final int LunchTimeLengthMean = settings.getInt(LUNCH_TIME_LENGTH_MEAN_SETTING);
        final int LunchTimeLengthStddev = settings.getInt(LUNCH_TIME_LENGTH_STDDEV_SETTING);
        final int TimeIntervalsPerMinute = settings.getInt(TIME_INTERVALS_PER_MINUTE_SETTING);
        final double ChanceForMensa = settings.getDouble(CHANCE_FOR_MENSA_SETTING);

        final int LunchTimeStart = LunchTimes[0];
        final int LunchTimeEnd = LunchTimes[1];

        List<Integer> startTimes = new ArrayList<>(Times.length);

        for (int time: Times) {
            startTimes.add(time);
        }

        List<Integer> endTimes = new ArrayList<>(startTimes);
        startTimes.remove(startTimes.size()-1);
        endTimes.remove(0);

        if (settings.contains(FIXED_SCHEDULE_FILE_SETTING)) {
            timeSlots.addAll(
                    parseScheduleFile(settings.getSetting(FIXED_SCHEDULE_FILE_SETTING), roomCapacities));
        } else {

            for (int i = 0; i < CoursesPerDay; i++) {
                int randomSlot = random.getRandomIntBetween(0, startTimes.size());
                int startTime = startTimes.get(randomSlot);
                int endTime = endTimes.get(randomSlot);

                ScheduleSlot newSlot = new ScheduleSlot(startTime, endTime, null);

                setRandomRoomForSlot(newSlot, roomCapacities, random);

                timeSlots.add(newSlot);
                startTimes.remove(randomSlot);
                endTimes.remove(randomSlot);
            }
        }

        double lunchTimeLength = random.getNormalRandomWithMeanAndStddev(LunchTimeLengthMean, LunchTimeLengthStddev);
        int normalizedLunchTimeLength = (int) lunchTimeLength;
        if (normalizedLunchTimeLength > LunchTimeEnd - LunchTimeStart) {
            normalizedLunchTimeLength = LunchTimeEnd - LunchTimeStart;
        }
        if (normalizedLunchTimeLength < 10*TimeIntervalsPerMinute) {
            normalizedLunchTimeLength = 10*TimeIntervalsPerMinute;
        }
        List<ScheduleSlot> freeSlots = getFreeTimesBetween(LunchTimeStart, LunchTimeEnd);
        List<ScheduleSlot> fittingSlots = freeSlots.stream().filter(slot -> slot.getDuration() > lunchTimeLength)
                .collect(Collectors.toList());
        if (fittingSlots.size() > 0) {
            ScheduleSlot randSlot = fittingSlots.get(random.getRandomIntBetween(0, fittingSlots.size()));
            int randStartLunchTime = random.getRandomIntBetween(randSlot.getStartTime(), randSlot.getEndTime()-normalizedLunchTimeLength);
            RoomHelper roomHelper = RoomHelper.getInstance();
            if (random.getRandomDouble() < ChanceForMensa) {
                timeSlots.add(new ScheduleSlot(randStartLunchTime, randStartLunchTime+normalizedLunchTimeLength, roomHelper.getMensaRoom(), true));
            } else {
                timeSlots.add(new ScheduleSlot(randStartLunchTime, randStartLunchTime+normalizedLunchTimeLength, roomHelper.getRandomEatingRoom(), true));
            }
        }

        timeSlots.sort(Comparator.comparing(ScheduleSlot::getStartTime));
    }

    private void setRandomRoomForSlot(ScheduleSlot slot, Map<Room, Map<ScheduleSlot, Integer>> roomCapacities,
                                      RandomHelper random) {
        Room retRoom = null;
        while (retRoom == null) {
            int roomIndex = random.getRandomIntBetween(0, roomCapacities.size());
            Room room = (Room) roomCapacities.keySet().toArray()[roomIndex];
            if (reserveRoomInSlot(room, slot, roomCapacities)) {
                retRoom = room;
            }
        }
        slot.setRoom(retRoom);
    }

    private boolean reserveRoomInSlot(Room room, ScheduleSlot slot, Map<Room, Map<ScheduleSlot, Integer>> roomCapacities) {
        Map<ScheduleSlot, Integer> roomSchedule = roomCapacities.get(room);
        if (roomSchedule == null) {
            Map<ScheduleSlot, Integer> newRoomSchedule = new HashMap<>();
            newRoomSchedule.put(new ScheduleSlot(slot), 1);
            roomCapacities.put(room, newRoomSchedule);
            return true;
        }

        Integer slotCap = roomSchedule.get(slot);
        if (slotCap == null) {
            roomSchedule.put(new ScheduleSlot(slot), 1);
            return true;
        } else if (slotCap < room.getCapacity()) {
            roomSchedule.put(new ScheduleSlot(slot), slotCap + 1);
            return true;
        }
        return false;

    }

    public int[] getStartTimesSorted(){
        return timeSlots.stream().mapToInt(ScheduleSlot::getStartTime).toArray();
    }


    public int[] getEndTimesSorted(){
        return timeSlots.stream().mapToInt(ScheduleSlot::getEndTime).toArray();
    }

    public ScheduleSlot getLunchTimeSlot() {
        Optional<ScheduleSlot> lunchSlot = this.timeSlots.stream().filter(ScheduleSlot::isLunchSlot).findFirst();
        if (lunchSlot.isPresent()) {
            return lunchSlot.get();
        }
        return null;
    }

    public ScheduleSlot getNextScheduleSlot(int curTime) {
        int curDif = -1;
        ScheduleSlot nextSlot = null;
        for (ScheduleSlot slot: timeSlots) {
            int timeDif = slot.getStartTime() - curTime;
            if (timeDif > 0 && (curDif > timeDif || nextSlot == null)) {
                curDif = timeDif;
                nextSlot = slot;
            }
        }
        return nextSlot;
    }

    public ScheduleSlot getActiveScheduleSlot(int curTime) {
        for (ScheduleSlot slot: timeSlots) {
            if (slot.getStartTime() < curTime && slot.getEndTime() > curTime) {
                return slot;
            }
        }
        return null;
    }

    public List<ScheduleSlot> getFreeTimesBetween(int start, int end) {
        List<ScheduleSlot> ret = new ArrayList<>();
        int testTime = start;
        while (testTime < end) {
            ScheduleSlot actualSlot = getActiveScheduleSlot(testTime);
            if (actualSlot == null) {
                ScheduleSlot nextSlot = getNextScheduleSlot(testTime);
                if (nextSlot == null || nextSlot.getStartTime() >= end) {
                    ret.add(new ScheduleSlot(testTime, end, null));
                    break;
                }
                ret.add(new ScheduleSlot(testTime, nextSlot.getStartTime(), null));
                testTime = nextSlot.getEndTime();
            } else {
                testTime = actualSlot.getEndTime();
            }
        }
        return ret;
    }

    public List<ScheduleSlot> parseScheduleFile(String fileName,Map<Room, Map<ScheduleSlot, Integer>> roomCapacities) {
        List<String> schedules = new ArrayList<>();
        try
        {
            File file = new File(fileName);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                schedules.add(line);
            }
            reader.close();
        }
        catch (IOException ioe) {
            throw new SettingsError("Couldn't read schedule file " +
                    fileName + " (cause: " + ioe.getMessage() + ")");
        }

        List<ScheduleSlot> slots = new ArrayList<>();
        String[] timeIntervals, timeValues;
        int startTime, endTime;
        ScheduleSlot newSlot;


        timeIntervals = schedules.get(0).split(",");

        for (String token : timeIntervals) {
            timeValues = token.split("-");

            startTime = (Integer.parseInt(timeValues[0])-6)*3600;
            endTime = (Integer.parseInt(timeValues[1])-6)*3600;


            newSlot = new ScheduleSlot(startTime, endTime, null);

            RoomHelper roomHelper = RoomHelper.getInstance();
            Room room = roomHelper.getRoomById(Integer.parseInt(timeValues[2]));
            if (!this.reserveRoomInSlot(room, newSlot, roomCapacities)) {
                throw new SettingsError("To much nodes for room with id " + room.getId());
            }
            newSlot.setRoom(room);

            slots.add(newSlot);
        }
        return slots;
    }
}
