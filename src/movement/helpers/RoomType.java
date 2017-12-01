package movement.helpers;

import java.util.HashMap;
import java.util.Map;

public enum RoomType {
    LECTURE_ROOM(1),
    ENTRY_EXIT(3),
    MAGISTRALE(4),
    MENSA(5),
    OTHER(2);

    private int roomNr;

    private static Map<Integer, RoomType> map = new HashMap<Integer, RoomType>();

    static {
        for (RoomType legEnum : RoomType.values()) {
            map.put(legEnum.roomNr, legEnum);
        }
    }

    RoomType(final int roomNr) {
        this.roomNr = roomNr;
    }

    public static RoomType valueOf(int roomNr) {
        return map.get(roomNr);
    }
}
