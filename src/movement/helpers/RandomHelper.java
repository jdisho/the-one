package movement.helpers;

import java.util.Random;

public class RandomHelper {
    private static RandomHelper instance = null;
    private Random random;

    private RandomHelper(Random rng) {
        this.random = rng;
    }

    public static RandomHelper getInstance() {
        return RandomHelper.instance;
    }

    public static void createInstance(Random rng) {
        if (RandomHelper.instance == null) {
            RandomHelper.instance = new RandomHelper(rng);
        }
    }

    public int getRandomIntBetween(int from, int until) {
        return random.nextInt(until - from) + from;
    }

    public int getRandomInt() {
        return random.nextInt();
    }

    public double getRandomDoubleBetween(double from, double until) {
        return random.nextDouble() * (until - from) + from;
    }

    public double getRandomDouble() {
        return random.nextDouble();
    }

    public double getNormalRandomWithMeanAndStddev(double mean, double stddev) {
        return random.nextGaussian() * stddev + mean;
    }
}
