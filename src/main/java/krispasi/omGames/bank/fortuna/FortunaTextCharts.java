package krispasi.omGames.bank.fortuna;

import java.util.ArrayList;
import java.util.List;

final class FortunaTextCharts {
    private static final char[] LEVELS = {'_', '.', '-', '~', '^'};

    private FortunaTextCharts() {
    }

    static String sparkline(List<Double> values, int maxWidth) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        int width = Math.max(2, maxWidth);
        List<Double> sampled = sample(values, width);
        double min = sampled.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = sampled.stream().mapToDouble(Double::doubleValue).max().orElse(min);
        if (Math.abs(max - min) < 0.0001) {
            return String.valueOf('-').repeat(sampled.size());
        }
        StringBuilder builder = new StringBuilder();
        for (double value : sampled) {
            double normalized = (value - min) / (max - min);
            int index = (int) Math.round(normalized * (LEVELS.length - 1));
            index = Math.max(0, Math.min(LEVELS.length - 1, index));
            builder.append(LEVELS[index]);
        }
        return builder.toString();
    }

    private static List<Double> sample(List<Double> values, int width) {
        if (values.size() <= width) {
            return values;
        }
        List<Double> sampled = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            int sourceIndex = (int) Math.round(i * (values.size() - 1) / (double) (width - 1));
            sampled.add(values.get(sourceIndex));
        }
        return sampled;
    }
}
