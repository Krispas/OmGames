package krispasi.omGames.bank.fortuna;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class FortunaMapDisplay {
    public static final int MAP_COLUMNS = 3;
    public static final int MAP_ROWS = 2;
    public static final int TILE_SIZE = 128;
    public static final int DISPLAY_WIDTH = MAP_COLUMNS * TILE_SIZE;
    public static final int DISPLAY_HEIGHT = MAP_ROWS * TILE_SIZE;
    public static final List<Integer> DEFAULT_MAP_IDS = List.of(1459, 1460, 1461, 1462, 1463, 1464);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM", Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final Color BACKGROUND_TOP = new Color(5, 26, 20);
    private static final Color BACKGROUND_BOTTOM = new Color(31, 10, 34);
    private static final Color PANEL = new Color(16, 20, 23, 225);
    private static final Color PANEL_DARK = new Color(7, 9, 12, 230);
    private static final Color GOLD = new Color(245, 186, 52);
    private static final Color GREEN = new Color(31, 210, 116);
    private static final Color RED = new Color(234, 77, 85);
    private static final Color DRAW = new Color(92, 190, 255);
    private static final Color WHITE = new Color(245, 247, 241);
    private static final Color MUTED = new Color(166, 175, 171);

    private final FortunaManager manager;
    private List<Integer> mapIds = DEFAULT_MAP_IDS;
    private BufferedImage cachedImage;

    public FortunaMapDisplay(FortunaManager manager) {
        this.manager = manager;
    }

    public void setMapIds(List<Integer> mapIds) {
        if (mapIds == null || mapIds.size() != MAP_COLUMNS * MAP_ROWS) {
            this.mapIds = DEFAULT_MAP_IDS;
            return;
        }
        this.mapIds = List.copyOf(mapIds);
    }

    public List<Integer> getMapIds() {
        return mapIds;
    }

    public void installRenderers() {
        for (int index = 0; index < mapIds.size(); index++) {
            int mapId = mapIds.get(index);
            MapView view = Bukkit.getMap(mapId);
            if (view == null) {
                manager.getPlugin().getLogger().warning("Fortuna display map id " + mapId
                        + " does not exist. Create the map in game before using the display.");
                continue;
            }
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.setLocked(false);
            view.addRenderer(new FortunaMapTileRenderer(this, index % MAP_COLUMNS, index / MAP_COLUMNS));
        }
        invalidate();
    }

    public void invalidate() {
        cachedImage = null;
        sendMaps();
    }

    public void cleanBoard() {
        cachedImage = renderCleanArtwork();
        sendMaps();
    }

    BufferedImage getTile(int column, int row) {
        BufferedImage image = getImage();
        return image.getSubimage(column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
    }

    private BufferedImage getImage() {
        if (cachedImage == null) {
            cachedImage = renderArtwork();
        }
        return cachedImage;
    }

    private void sendMaps() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int mapId : mapIds) {
                MapView view = Bukkit.getMap(mapId);
                if (view != null) {
                    player.sendMap(view);
                }
            }
        }
    }

    private BufferedImage renderArtwork() {
        FortunaMatch match = manager.getDisplayMatch();
        if (match == null) {
            return renderCleanArtwork();
        }

        BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawBackground(g);
            drawHeader(g);
            drawMatch(g, match);
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage renderCleanArtwork() {
        BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawBackground(g);
            drawHeader(g);
            drawCleanState(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void drawBackground(Graphics2D g) {
        Paint paint = new GradientPaint(0, 0, BACKGROUND_TOP, DISPLAY_WIDTH, DISPLAY_HEIGHT, BACKGROUND_BOTTOM);
        g.setPaint(paint);
        g.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

        g.setColor(new Color(255, 255, 255, 13));
        for (int x = -DISPLAY_HEIGHT; x < DISPLAY_WIDTH; x += 24) {
            Path2D stripe = new Path2D.Double();
            stripe.moveTo(x, 0);
            stripe.lineTo(x + 10, 0);
            stripe.lineTo(x + DISPLAY_HEIGHT + 10, DISPLAY_HEIGHT);
            stripe.lineTo(x + DISPLAY_HEIGHT, DISPLAY_HEIGHT);
            stripe.closePath();
            g.fill(stripe);
        }

        g.setColor(new Color(0, 0, 0, 85));
        g.fillRect(0, 0, DISPLAY_WIDTH, 9);
        g.fillRect(0, DISPLAY_HEIGHT - 9, DISPLAY_WIDTH, 9);
    }

    private void drawHeader(Graphics2D g) {
        Rectangle header = new Rectangle(16, 12, DISPLAY_WIDTH - 32, 44);
        g.setColor(PANEL_DARK);
        g.fillRoundRect(header.x, header.y, header.width, header.height, 8, 8);
        g.setColor(GREEN);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(header.x + 10, header.y + header.height - 1,
                header.x + header.width - 10, header.y + header.height - 1);

        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.setColor(WHITE);
        g.drawString("FORTUNA", 28, 43);

        g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 36));
        g.fillRoundRect(195, 26, 44, 18, 7, 7);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.1f));
        g.drawRoundRect(195, 26, 44, 18, 7, 7);
        drawCentered(g, "BANK", new Font("SansSerif", Font.BOLD, 11), 195, 26, 44, 18, GOLD);
    }

    private void drawCleanState(Graphics2D g) {
        Rectangle body = new Rectangle(26, 78, DISPLAY_WIDTH - 52, 118);
        g.setColor(PANEL);
        g.fillRoundRect(body.x, body.y, body.width, body.height, 10, 10);
        g.setColor(new Color(255, 255, 255, 30));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(body.x, body.y, body.width, body.height, 10, 10);

        drawCentered(g, "NO ACTIVE MATCH", new Font("SansSerif", Font.BOLD, 27),
                body.x + 16, body.y + 25, body.width - 32, 34, WHITE);
        drawCentered(g, "No upcoming game", new Font("SansSerif", Font.BOLD, 16),
                body.x + 16, body.y + 61, body.width - 32, 24, MUTED);
        drawCentered(g, "Next match coming soon", new Font("SansSerif", Font.BOLD, 15),
                body.x + 16, body.y + 86, body.width - 32, 24, GOLD);
    }

    private void drawMatch(Graphics2D g, FortunaMatch match) {
        drawStatusBadge(g, match);
        if (match.getStatus() == FortunaMatchStatus.ACTIVE) {
            drawLiveMatchPanel(g, match);
            drawFutureMatchStrip(g, manager.getNextUpcomingMatch().orElse(null));
            return;
        }
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            java.util.Optional<FortunaMatch> nextMatch = manager.getNextUpcomingMatch();
            if (nextMatch.isPresent()) {
                drawUpcomingMatchPanel(g, nextMatch.get());
            } else {
                drawOddsCards(g, match);
            }
            drawResultStrip(g, match);
            return;
        }
        drawOddsCards(g, match);
        drawScheduleStrip(g, match, "NEXT GAME");
    }

    private void drawStatusBadge(Graphics2D g, FortunaMatch match) {
        Color color = switch (match.getStatus()) {
            case ACTIVE -> GREEN;
            case FINISHED -> GOLD;
            case UPCOMING -> DRAW;
        };
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
        Rectangle badge = new Rectangle(DISPLAY_WIDTH - 111, 24, 88, 20);
        g.fillRoundRect(badge.x, badge.y, badge.width, badge.height, 7, 7);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(badge.x, badge.y, badge.width, badge.height, 7, 7);
        drawCentered(g, match.getStatus().displayName().toUpperCase(Locale.ROOT),
                new Font("SansSerif", Font.BOLD, 11), badge.x + 4, badge.y, badge.width - 8, badge.height, color);
    }

    private void drawOddsCards(Graphics2D g, FortunaMatch match) {
        Rectangle main = new Rectangle(18, 68, DISPLAY_WIDTH - 36, 102);
        g.setColor(PANEL);
        g.fillRoundRect(main.x, main.y, main.width, main.height, 10, 10);
        g.setColor(new Color(255, 255, 255, 30));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(main.x, main.y, main.width, main.height, 10, 10);

        drawOddsCard(g, new Rectangle(31, 82, 98, 72),
                resultTitle(match, FortunaOutcome.HOME), match.getHomeName(), match.getHomeOdds(), GREEN);
        drawOddsCard(g, new Rectangle(143, 82, 98, 72),
                resultTitle(match, FortunaOutcome.DRAW), "X", match.getDrawOdds(), DRAW);
        drawOddsCard(g, new Rectangle(255, 82, 98, 72),
                resultTitle(match, FortunaOutcome.AWAY), match.getAwayName(), match.getAwayOdds(), RED);
    }

    private void drawOddsCard(Graphics2D g, Rectangle rect, String title, String name, double odds, Color accent) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 9, 9);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 9, 9);
        int nameY = rect.y + 18;
        if (title != null && !title.isBlank()) {
            drawCentered(g, title.toUpperCase(Locale.ROOT), new Font("SansSerif", Font.BOLD, 11),
                    rect.x + 6, rect.y + 7, rect.width - 12, 14, resultTitleColor(title));
            nameY = rect.y + 26;
        }
        drawCentered(g, name, new Font("SansSerif", Font.BOLD, 15),
                rect.x + 7, nameY, rect.width - 14, 17, WHITE);
        drawCentered(g, formatOdds(odds), new Font("SansSerif", Font.BOLD, 24),
                rect.x + 7, rect.y + 43, rect.width - 14, 22, WHITE);
    }

    private void drawLiveMatchPanel(Graphics2D g, FortunaMatch match) {
        drawMatchHeaderPanel(g, match, "LIVE MATCH", GREEN);
    }

    private void drawUpcomingMatchPanel(Graphics2D g, FortunaMatch match) {
        drawMatchHeaderPanel(g, match, "NEXT MATCH", DRAW);
    }

    private void drawMatchHeaderPanel(Graphics2D g, FortunaMatch match, String title, Color titleColor) {
        Rectangle main = new Rectangle(18, 68, DISPLAY_WIDTH - 36, 102);
        g.setColor(PANEL);
        g.fillRoundRect(main.x, main.y, main.width, main.height, 10, 10);
        g.setColor(new Color(255, 255, 255, 30));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(main.x, main.y, main.width, main.height, 10, 10);

        drawCentered(g, title, new Font("SansSerif", Font.BOLD, 13),
                main.x + 16, main.y + 10, main.width - 32, 17, titleColor);
        drawCentered(g, match.getHomeName(), new Font("SansSerif", Font.BOLD, 24),
                main.x + 18, main.y + 34, 126, 30, WHITE);
        drawCentered(g, "VS", new Font("SansSerif", Font.BOLD, 18),
                main.x + 160, main.y + 39, 26, 20, MUTED);
        drawCentered(g, match.getAwayName(), new Font("SansSerif", Font.BOLD, 24),
                main.x + main.width - 144, main.y + 34, 126, 30, WHITE);

        drawCentered(g, "1 " + formatOdds(match.getHomeOdds()), new Font("SansSerif", Font.BOLD, 16),
                main.x + 18, main.y + 73, 126, 20, GREEN);
        drawCentered(g, "X " + formatOdds(match.getDrawOdds()), new Font("SansSerif", Font.BOLD, 16),
                main.x + 154, main.y + 73, 76, 20, DRAW);
        drawCentered(g, "2 " + formatOdds(match.getAwayOdds()), new Font("SansSerif", Font.BOLD, 16),
                main.x + main.width - 144, main.y + 73, 126, 20, RED);
    }

    private String resultTitle(FortunaMatch match, FortunaOutcome outcome) {
        if (match.getStatus() != FortunaMatchStatus.FINISHED || match.getResult() == null) {
            return "";
        }
        if (match.getResult() == FortunaOutcome.DRAW) {
            return outcome == FortunaOutcome.DRAW ? "Remiza" : "";
        }
        if (match.getResult() == outcome) {
            return "Vyhra";
        }
        return outcome == FortunaOutcome.DRAW ? "" : "Prohra";
    }

    private Color resultTitleColor(String title) {
        return switch (title.toLowerCase(Locale.ROOT)) {
            case "vyhra" -> GREEN;
            case "prohra" -> RED;
            case "remiza" -> DRAW;
            default -> MUTED;
        };
    }

    private void drawFutureMatchStrip(Graphics2D g, FortunaMatch match) {
        if (match == null) {
            drawEmptyFutureStrip(g);
            return;
        }
        drawScheduleStrip(g, match, "FUTURE MATCH");
    }

    private void drawResultStrip(Graphics2D g, FortunaMatch match) {
        Rectangle strip = new Rectangle(18, 178, DISPLAY_WIDTH - 36, 56);
        g.setColor(PANEL_DARK);
        g.fillRoundRect(strip.x, strip.y, strip.width, strip.height, 10, 10);
        g.setColor(new Color(255, 255, 255, 25));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(strip.x, strip.y, strip.width, strip.height, 10, 10);

        drawCentered(g, "RESULT", new Font("SansSerif", Font.BOLD, 9),
                strip.x + 16, 186, strip.width - 32, 11, MUTED);
        drawCentered(g, match.resultLabel(), new Font("SansSerif", Font.BOLD, 18),
                strip.x + 16, 201, strip.width - 32, 22, WHITE);
        drawCentered(g, "Winning odds " + formatOdds(winningOdds(match)), new Font("SansSerif", Font.BOLD, 12),
                strip.x + 16, 221, strip.width - 32, 12, GOLD);
    }

    private void drawEmptyFutureStrip(Graphics2D g) {
        Rectangle strip = new Rectangle(18, 178, DISPLAY_WIDTH - 36, 56);
        g.setColor(PANEL_DARK);
        g.fillRoundRect(strip.x, strip.y, strip.width, strip.height, 10, 10);
        g.setColor(new Color(255, 255, 255, 25));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(strip.x, strip.y, strip.width, strip.height, 10, 10);
        drawCentered(g, "FUTURE MATCH", new Font("SansSerif", Font.BOLD, 9),
                strip.x + 16, 187, strip.width - 32, 11, MUTED);
        drawCentered(g, "No upcoming game", new Font("SansSerif", Font.BOLD, 18),
                strip.x + 16, 203, strip.width - 32, 22, WHITE);
    }

    private void drawScheduleStrip(Graphics2D g, FortunaMatch match, String centerTitle) {
        Rectangle strip = new Rectangle(18, 178, DISPLAY_WIDTH - 36, 56);
        g.setColor(PANEL_DARK);
        g.fillRoundRect(strip.x, strip.y, strip.width, strip.height, 10, 10);
        g.setColor(new Color(255, 255, 255, 25));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(strip.x, strip.y, strip.width, strip.height, 10, 10);

        drawCentered(g, "DATE", new Font("SansSerif", Font.BOLD, 9), 30, 185, 66, 11, MUTED);
        drawCentered(g, DATE_FORMAT.format(match.getScheduledAt()), new Font("SansSerif", Font.BOLD, 15),
                30, 198, 66, 19, WHITE);

        drawCentered(g, centerTitle, new Font("SansSerif", Font.BOLD, 9), 108, 185, 168, 11, MUTED);
        drawCentered(g, match.label(), new Font("SansSerif", Font.BOLD, 16), 100, 199, 184, 20, WHITE);

        drawCentered(g, "TIME", new Font("SansSerif", Font.BOLD, 9), 288, 185, 66, 11, MUTED);
        drawCentered(g, TIME_FORMAT.format(match.getScheduledAt()), new Font("SansSerif", Font.BOLD, 15),
                288, 198, 66, 19, WHITE);

        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            drawCentered(g, "RESULT: " + match.resultLabel(), new Font("SansSerif", Font.BOLD, 13),
                    strip.x + 16, 219, strip.width - 32, 14, GOLD);
        } else if (match.getStatus() == FortunaMatchStatus.ACTIVE) {
            drawCentered(g, "LIVE MARKET", new Font("SansSerif", Font.BOLD, 12),
                    strip.x + 16, 220, strip.width - 32, 13, GOLD);
        } else {
            drawCentered(g, "1 " + formatOdds(match.getHomeOdds()), new Font("SansSerif", Font.BOLD, 11),
                    47, 220, 72, 13, GREEN);
            drawCentered(g, "X " + formatOdds(match.getDrawOdds()), new Font("SansSerif", Font.BOLD, 11),
                    156, 220, 72, 13, DRAW);
            drawCentered(g, "2 " + formatOdds(match.getAwayOdds()), new Font("SansSerif", Font.BOLD, 11),
                    265, 220, 72, 13, RED);
        }
    }

    private double winningOdds(FortunaMatch match) {
        if (match.getResult() == FortunaOutcome.HOME) {
            return match.getHomeOdds();
        }
        if (match.getResult() == FortunaOutcome.DRAW) {
            return match.getDrawOdds();
        }
        if (match.getResult() == FortunaOutcome.AWAY) {
            return match.getAwayOdds();
        }
        return 0.0;
    }

    private void drawCentered(Graphics2D g,
                              String text,
                              Font preferredFont,
                              int x,
                              int y,
                              int width,
                              int height,
                              Color color) {
        String safeText = text == null || text.isBlank() ? "-" : text;
        Font font = fitFont(g, preferredFont, safeText, width);
        FontMetrics metrics = g.getFontMetrics(font);
        int drawX = x + Math.max(0, (width - metrics.stringWidth(safeText)) / 2);
        int drawY = y + Math.max(0, (height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setFont(font);
        g.setColor(color);
        g.drawString(safeText, drawX, drawY);
    }

    private Font fitFont(Graphics2D g, Font font, String text, int width) {
        int size = font.getSize();
        while (size > 7) {
            Font candidate = font.deriveFont((float) size);
            if (g.getFontMetrics(candidate).stringWidth(text) <= width) {
                return candidate;
            }
            size--;
        }
        return font.deriveFont(7.0f);
    }

    private String formatOdds(double odds) {
        return String.format(Locale.US, "%.2f", odds);
    }
}
