package krispasi.omGames.chess;

import org.bukkit.Material;

public record ChessBoardPalette(Material lightBlock, Material darkBlock, Material highlightBlock) {
    public static final ChessBoardPalette DEFAULT = new ChessBoardPalette(
            Material.SMOOTH_QUARTZ,
            Material.COAL_BLOCK,
            Material.SMOOTH_BASALT
    );
}
