package movement.helpers;

import core.Settings;

public class EnterExitHelper {

    // Settings for enter exit helper
    public static final String LECTURE_PARTICIPATION_CHANCE_SETTING = "LectureParticipationChance";
    public static final String START_PEAK_ENTER_TIME_DIFFERENCE_SETTING = "StartPeakEnterTimeDifference";
    public static final String ENTER_LECTURE_STDDEV_SETTING = "EnterLectureStddev";
    public static final String EXIT_LECTURE_STDDEV_SETTING = "ExitLectureStddev";
    public static final String END_ENTER_LECTURE_DIFFERENCE_SETTING = "EndEnterLectureDifference";
    public static final String ENTER_EXIT_STDDEV_SETTING = "EnterExitStddev";
    public static final String EXIT_END_TIME_SETTING = "ExitEndTime";
    public static final String EXIT_START_TIME_SETTING = "ExitStartTime";
    public static final String ENTER_END_TIME_SETTING = "EnterEndTime";
    public static final String ENTER_START_TIME_SETTING = "EnterStartTime";
    public static final String ENTER_CHANCE_WITHOUT_SCHEDULE_SETTING = "EnterChanceWithoutSchedule";
    public static final String TIME_INTERVALLS_PER_MINUTE_SETTING = "TimeInterallsPerMinute";

    private final double LectureParticipationChance;
    private final int StartPeakEnterTimeDifference;
    private final double EnterLectureStddev;
    private final double ExitLectureStddev;
    private final int EndEnterLectureDifference;
    private final double EnterChanceWithoutSchedule;
    private final int EnterStartTime;
    private final int EnterEndTime;
    private final int ExitStartTime;
    private final int ExitEndTime;
    private final double EnterExitStddev;
    private final int TimeIntervallsPerMinute;

    private RandomHelper random = RandomHelper.getInstance();

    public EnterExitHelper(Settings settings) {
        LectureParticipationChance = settings.getDouble(LECTURE_PARTICIPATION_CHANCE_SETTING);
        StartPeakEnterTimeDifference = settings.getInt(START_PEAK_ENTER_TIME_DIFFERENCE_SETTING);
        EnterLectureStddev = settings.getDouble(ENTER_LECTURE_STDDEV_SETTING);
        ExitLectureStddev = settings.getDouble(EXIT_LECTURE_STDDEV_SETTING);
        EndEnterLectureDifference = settings.getInt(END_ENTER_LECTURE_DIFFERENCE_SETTING);
        EnterChanceWithoutSchedule = settings.getDouble(ENTER_CHANCE_WITHOUT_SCHEDULE_SETTING);
        EnterStartTime = settings.getInt(ENTER_START_TIME_SETTING);
        EnterEndTime = settings.getInt(ENTER_END_TIME_SETTING);
        ExitStartTime = settings.getInt(EXIT_START_TIME_SETTING);
        ExitEndTime = settings.getInt(EXIT_END_TIME_SETTING);
        EnterExitStddev = settings.getDouble(ENTER_EXIT_STDDEV_SETTING);
        TimeIntervallsPerMinute = settings.getInt(TIME_INTERVALLS_PER_MINUTE_SETTING);
    }

    public Integer enterTimeForSchedule(ScheduleInterface schedule, boolean byUbahn) {
        int[] startTimes = schedule.getStartTimesSorted();
        double normalEnterTime = random.getNormalRandomWithMeanAndStddev(
                (double)(this.EnterStartTime-this.EnterEndTime), this.EnterExitStddev);
        if (normalEnterTime < this.EnterStartTime) {
            normalEnterTime = this.EnterStartTime;
        }
        if (normalEnterTime > this.EnterEndTime) {
            return null;
        }
        for (int startTime: startTimes) {
            if (random.getRandomDouble() < LectureParticipationChance) {
                double enterTime = random.getNormalRandomWithMeanAndStddev(
                        (double)(startTime - this.StartPeakEnterTimeDifference),
                        this.EnterLectureStddev);
                if (enterTime > normalEnterTime) {
                    break;
                }
                if (enterTime - this.EndEnterLectureDifference > startTime) {
                    continue;
                }
                if (byUbahn) {
                    return (int) (Math.round(enterTime / (double)(10 * TimeIntervallsPerMinute)) * (10 * TimeIntervallsPerMinute));
                } else {
                    return (int) Math.round(enterTime);
                }
            }
        }
        if (random.getRandomDouble() < EnterChanceWithoutSchedule) {
            if (byUbahn) {
                return (int) (Math.round(normalEnterTime / (double)(10 * TimeIntervallsPerMinute)) * (10 * TimeIntervallsPerMinute));
            } else {
                return (int) Math.round(normalEnterTime);
            }
        }
        return null;
    }

    public Integer exitTimeForSchedule(ScheduleInterface schedule, boolean byUbahn, int minExitTime) {
        int[] endTimes = schedule.getEndTimesSorted();
        double normalExitTime = random.getNormalRandomWithMeanAndStddev(
                (double)(this.ExitStartTime-this.ExitEndTime), this.EnterExitStddev);
        if (normalExitTime < this.ExitStartTime || normalExitTime > this.EnterEndTime) {
            normalExitTime = this.EnterStartTime;
        }
        if (endTimes.length > 0) {
            int lastEndTime = endTimes[endTimes.length - 1];
            double exitTime = random.getNormalRandomWithMeanAndStddev(
                    (double)(lastEndTime + this.StartPeakEnterTimeDifference), this.ExitLectureStddev
            );
            if (normalExitTime < exitTime) {
                if (byUbahn) {
                    return (int) (Math.round(exitTime / (double)(10 * TimeIntervallsPerMinute)) * (10 * TimeIntervallsPerMinute));
                } else {
                    return (int) Math.round(exitTime);
                }
            }
        }

        if (normalExitTime < minExitTime) {
            normalExitTime = minExitTime;
        }

        if (byUbahn) {
            return (int) (Math.round(normalExitTime / (double)(10 * TimeIntervallsPerMinute)) * (10 * TimeIntervallsPerMinute));
        } else {
            return (int) Math.round(normalExitTime);
        }
    }
}