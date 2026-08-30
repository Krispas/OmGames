package krispasi.omGames.random;

import java.awt.image.BufferedImage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

final class RandomGifMapRenderer extends MapRenderer {
    private final RandomGifManager manager;
    private final int mapId;

    RandomGifMapRenderer(RandomGifManager manager, int mapId) {
        super(false);
        this.manager = manager;
        this.mapId = mapId;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        BufferedImage frame = manager.getCurrentTileFrame(mapId);
        if (frame != null) {
            canvas.drawImage(0, 0, frame);
        }
    }
}
