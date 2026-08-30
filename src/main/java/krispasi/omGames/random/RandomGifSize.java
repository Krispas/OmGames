package krispasi.omGames.random;

import java.util.List;

record RandomGifSize(int width, int height) {
    static final List<RandomGifSize> MENU_SIZES = List.of(
            new RandomGifSize(1, 1),
            new RandomGifSize(2, 1),
            new RandomGifSize(1, 2),
            new RandomGifSize(2, 2),
            new RandomGifSize(3, 1),
            new RandomGifSize(3, 2),
            new RandomGifSize(3, 3),
            new RandomGifSize(4, 1),
            new RandomGifSize(4, 2),
            new RandomGifSize(4, 3),
            new RandomGifSize(4, 4)
    );

    RandomGifSize {
        if (width < 1 || width > 4 || height < 1 || height > 4) {
            throw new IllegalArgumentException("GIF map size must be between 1x1 and 4x4.");
        }
    }

    int mapCount() {
        return width * height;
    }

    String label() {
        return width + "x" + height;
    }
}
