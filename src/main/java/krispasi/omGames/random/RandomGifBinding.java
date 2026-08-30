package krispasi.omGames.random;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

final class RandomGifBinding {
    private final String fileName;
    private final int baseMapId;
    private final int width;
    private final int height;
    private final List<Integer> mapIds;
    private final RandomGifImage image;
    private int frameIndex;
    private long nextFrameTick;
    private boolean active;

    RandomGifBinding(String fileName, int baseMapId, int width, int height, RandomGifImage image) {
        this.fileName = fileName;
        this.baseMapId = baseMapId;
        this.width = width;
        this.height = height;
        this.mapIds = buildMapIds(baseMapId, width, height);
        this.image = image;
    }

    String fileName() {
        return fileName;
    }

    int baseMapId() {
        return baseMapId;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    String sizeLabel() {
        return width + "x" + height;
    }

    List<Integer> mapIds() {
        return mapIds;
    }

    boolean containsMapId(int mapId) {
        return mapIds.contains(mapId);
    }

    int frameCount() {
        return image.frameCount();
    }

    BufferedImage currentFrame() {
        return image.frame(frameIndex);
    }

    BufferedImage tileFrame(int mapId) {
        int offset = mapId - baseMapId;
        if (offset < 0 || offset >= mapIds.size()) {
            return null;
        }
        int column = offset % width;
        int row = offset / width;
        return currentFrame().getSubimage(
                column * RandomGifImage.MAP_SIZE,
                row * RandomGifImage.MAP_SIZE,
                RandomGifImage.MAP_SIZE,
                RandomGifImage.MAP_SIZE
        );
    }

    boolean activate(long currentTick) {
        if (active) {
            return false;
        }
        active = true;
        frameIndex = 0;
        nextFrameTick = currentTick + image.delayTicks(frameIndex);
        return true;
    }

    void deactivate() {
        active = false;
        frameIndex = 0;
        nextFrameTick = 0L;
    }

    boolean advanceIfDue(long currentTick) {
        if (!active || image.frameCount() <= 1 || currentTick < nextFrameTick) {
            return false;
        }
        frameIndex = (frameIndex + 1) % image.frameCount();
        nextFrameTick = currentTick + image.delayTicks(frameIndex);
        return true;
    }

    private static List<Integer> buildMapIds(int baseMapId, int width, int height) {
        List<Integer> ids = new ArrayList<>();
        for (int index = 0; index < width * height; index++) {
            ids.add(baseMapId + index);
        }
        return List.copyOf(ids);
    }
}
