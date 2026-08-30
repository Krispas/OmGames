package krispasi.omGames.bank.fortuna;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

final class FortunaMapTileRenderer extends MapRenderer {
    private final FortunaMapDisplay display;
    private final int column;
    private final int row;

    FortunaMapTileRenderer(FortunaMapDisplay display, int column, int row) {
        super(false);
        this.display = display;
        this.column = column;
        this.row = row;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        canvas.drawImage(0, 0, display.getTile(column, row));
    }
}
