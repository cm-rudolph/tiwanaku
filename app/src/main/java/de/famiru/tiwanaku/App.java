package de.famiru.tiwanaku;

import de.famiru.dlx.Dlx;
import de.famiru.dlx.DlxBuilder;
import de.famiru.dlx.Stats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class App {
    private static final Logger LOGGER = LogManager.getLogger(App.class);

    private static final List<String> LEVEL = List.of(
            "443334441",
            "442344211",
            "422111214",
            "322411244",
            "333442244"
    );
    private final List<String> level;
    private final int[][] biomeIndices;
    private final Map<Integer, Integer> biomeSizes;
    private final Map<Integer, Integer> biomeIndexOffsets;
    private final int maxBiomeSize;
    private final int width;
    private final int height;
    private final int numberOfFieldFilledConstraints;

    public App(List<String> level) {
        this.level = level;
        width = getWidth();
        height = getHeight();
        biomeIndices = new int[height][width];
        biomeSizes = new HashMap<>();
        biomeIndexOffsets = new HashMap<>();
        maxBiomeSize = findMaxBiomeSize();
        numberOfFieldFilledConstraints = width * height;
        determineBiomeIndicesAndSizes();
    }

    public static void main(String[] args) {
        long start = System.nanoTime();
        new App(LEVEL).run();
        long durationInNs = System.nanoTime() - start;
        LOGGER.info("Took {} ms.", durationInNs / 1_000_000);
    }

    private int findMaxBiomeSize() {
        int maxSize = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int size = findBiomeSize(x, y);
                if (size > maxSize) {
                    maxSize = size;
                }
            }
        }
        return maxSize;
    }

    private int findBiomeSize(int x, int y) {
        boolean[][] visited = new boolean[height][width];
        char biomeType = level.get(y).charAt(x);
        return findBiomeSize(x, y, visited, biomeType);
    }

    private int findBiomeSize(int x, int y, boolean[][] visited, char biomeType) {
        if (visited[y][x]) {
            return 0;
        }
        visited[y][x] = true;
        if (level.get(y).charAt(x) != biomeType) {
            return 0;
        }
        int fields = 1;
        if (x > 0) {
            fields += findBiomeSize(x - 1, y, visited, biomeType);
        }
        if (x < width - 1) {
            fields += findBiomeSize(x + 1, y, visited, biomeType);
        }
        if (y > 0) {
            fields += findBiomeSize(x, y - 1, visited, biomeType);
        }
        if (y < height - 1) {
            fields += findBiomeSize(x, y + 1, visited, biomeType);
        }
        return fields;
    }

    private void determineBiomeIndicesAndSizes() {
        int maxIndex = 0;
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (biomeIndices[y][x] == 0) {
                    maxIndex++;
                    int biomeSize = findBiomeSize(x, y);
                    biomeSizes.put(maxIndex, biomeSize);
                    biomeIndexOffsets.put(maxIndex, offset);
                    offset += biomeSize;
                    markBiomeIndices(x, y, level.get(y).charAt(x), maxIndex);
                }
            }
        }
    }

    private void markBiomeIndices(int x, int y, char biomeType, int biomeIndex) {
        if (level.get(y).charAt(x) != biomeType || biomeIndices[y][x] != 0) {
            return;
        }
        biomeIndices[y][x] = biomeIndex;
        if (x > 0) {
            markBiomeIndices(x - 1, y, biomeType, biomeIndex);
        }
        if (x < width - 1) {
            markBiomeIndices(x + 1, y, biomeType, biomeIndex);
        }
        if (y > 0) {
            markBiomeIndices(x, y - 1, biomeType, biomeIndex);
        }
        if (y < height - 1) {
            markBiomeIndices(x, y + 1, biomeType, biomeIndex);
        }
    }

    private int getWidth() {
        return level.get(0).length();
    }

    private int getHeight() {
        return level.size();
    }

    private void run() {
        int secondaryConstraints = (width - 1) * (height - 1) * maxBiomeSize;
        DlxBuilder<ChoiceInfo> builder = Dlx.builder()
                .maxNumberOfSolutionsToStore(0)
                .countAllSolutions(true)
                .numberOfConstraints(numberOfFieldFilledConstraints * 2, secondaryConstraints)
                .createChoiceBuilder();

        createChoices(builder);
        Dlx<ChoiceInfo> dlx = builder.build();
        dlx.solve();
        Stats stats = dlx.getStats();
        LOGGER.info(stats);
    }

    private void createChoices(DlxBuilder<ChoiceInfo> builder) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int number = 0; number < biomeSizes.get(biomeIndices[y][x]); number++) {
                    List<Integer> constraintIndices = new ArrayList<>(6);

                    // field filled constraint
                    constraintIndices.add(y * width + x);

                    // number filled in biome with corresponding index
                    constraintIndices.add(numberOfFieldFilledConstraints + biomeIndexOffsets.get(biomeIndices[y][x]) + number);

                    // secondary constraints preventing same neighbor numbers
                    if (x > 0) {
                        if (y > 0) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + (y - 1) * (width - 1) * maxBiomeSize + (x - 1) * maxBiomeSize + number);
                        }
                        if (y < height - 1) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + y * (width - 1) * maxBiomeSize + (x - 1) * maxBiomeSize + number);
                        }
                    }
                    if (x < width - 1) {
                        if (y > 0) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + (y - 1) * (width - 1) * maxBiomeSize + x * maxBiomeSize + number);
                        }
                        if (y < height - 1) {
                            constraintIndices.add(2 * numberOfFieldFilledConstraints + y * (width - 1) * maxBiomeSize + x * maxBiomeSize + number);
                        }
                    }
                    constraintIndices.sort(Comparator.naturalOrder());
                    builder.addChoice(new ChoiceInfo(x, y, number + 1), constraintIndices);
                }
            }
        }
    }

    private record ChoiceInfo(int x, int y, int number) {
        @Override
        public String toString() {
            return "(" + x + "|" + y + ") = " + number;
        }
    }
}
