package movement.helpers;

import movement.map.MapNode;


public class Room {
    private int id;
    private int capacity;
    private MapNode node;
    private RoomType type;
    private double probability;

    public Room(int id, MapNode node, int capacity, RoomType type) {
        this.node = node;
        this.capacity = capacity;
        this.type = type;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public MapNode getNode() {
        return node;
    }

    public void setNode(MapNode node) {
        this.node = node;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public double getProbability() {
        return probability;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }
}
