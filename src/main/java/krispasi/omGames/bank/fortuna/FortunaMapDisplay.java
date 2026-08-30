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
        g.setColor(PANEL_DARK);
        g.fillRoundRect(14, 12, DISPLAY_WIDTH - 28, 42, 10, 10);
        g.setColor(GREEN);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(24, 54, DISPLAY_WIDTH - 24, 54);

        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.setColor(WHITE);
        g.drawString("FORTUNA", 28, 43);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(GOLD);
        g.drawString("BANK", 180, 42);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(MUTED);
        g.drawString("Sazkova kancelar", DISPLAY_WIDTH - 143, 38);
    }

    private void drawCleanState(Graphics2D g) {
        Rectangle body = new Rectangle(34, 72, DISPLAY_WIDTH - 68, 118);
        g.setColor(PANEL);
        g.fillRoundRect(body.x, body.y, body.width, body.height, 12, 12);
        drawCentered(g, "Board clean", new Font("SansSerif", Font.BOLD, 28), body.x, body.y + 20,
                body.width, 40, WHITE);
        drawCentered(g, "No active or upcoming match", new Font("SansSerif", Font.BOLD, 17), body.x, body.y + 62,
                body.width, 26, MUTED);
        drawCentered(g, "Use /bank fortuna", new Font("SansSerif", Font.BOLD, 15), body.x, body.y + 88,
                body.width, 30, GOLD);
    }

    private void drawMatch(Graphics2D g, FortunaMatch match) {
        drawStatusBadge(g, match);
        drawOddsCards(g, match);
        drawScheduleStrip(g, match);
        drawOddsChart(g, match);
    }

    private void drawStatusBadge(Graphics2D g, FortunaMatch match) {
        Color color = switch (match.getStatus()) {
            case ACTIVE -> GREEN;
            case FINISHED -> GOLD;
            case UPCOMING -> DRAW;
        };
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
        g.fillRoundRect(250, 22, 92, 23, 9, 9);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(250, 22, 92, 23, 9, 9);
        drawCentered(g, match.getStatus().displayName().toUpperCase(Locale.ROOT),
                new Font("SansSerif", Font.BOLD, 12), 250, 22, 92, 23, color);
    }

    private void drawOddsCards(Graphics2D g, FortunaMatch match) {
        Rectangle main = new Rectangle(20, 69, DISPLAY_WIDTH - 40, 88);
        g.setColor(PANEL);
        g.fillRoundRect(main.x, main.y, main.width, main.height, 12, 12);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawRoundRect(main.x, main.y, main.width, main.height, 12, 12);

        drawOddsCard(g, new Rectangle(34, 83, 94, 58), "Vyhra", match.getHomeName(), match.getHomeOdds(), GREEN);
        drawOddsCard(g, new Rectangle(145, 83, 94, 58), "Remiza", "X", match.getDrawOdds(), DRAW);
        drawOddsCard(g, new Rectangle(256, 83, 94, 58), "Prohra", match.getAwayName(), match.getAwayOdds(), RED);
    }

    private void drawOddsCard(Graphics2D g, Rectangle rect, String title, String name, double odds, Color accent) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
        drawCentered(g, title.toUpperCase(Locale.ROOT), new Font("SansSerif", Font.BOLD, 11),
                rect.x + 4, rect.y + 5, rect.width - 8, 16, accent);
        drawCentered(g, name, new Font("SansSerif", Font.BOLD, 13),
                rect.x + 6, rect.y + 22, rect.width - 12, 17, WHITE);
        drawCentered(g, formatOdds(odds), new Font("SansSerif", Font.BOLD, 22),
                rect.x + 6, rect.y + 36, rect.width - 12, 22, WHITE);
    }

    private void drawScheduleStrip(Graphics2D g, FortunaMatch match) {
        Rectangle strip = new Rectangle(20, 165, DISPLAY_WIDTH - 40, 31);
        g.setColor(PANEL_DARK);
        g.fillRoundRect(strip.x, strip.y, strip.width, strip.height, 9, 9);

        drawCentered(g, "DATE", new Font("SansSerif", Font.BOLD, 9), 34, 168, 72, 11, MUTED);
        drawCentered(g, DATE_FORMAT.format(match.getScheduledAt()), new Font("SansSerif", Font.BOLD, 15),
                34, 178, 72, 18, WHITE);

        drawCentered(g, "NEXT GAME", new Font("SansSerif", Font.BOLD, 9), 120, 168, 144, 11, MUTED);
        drawCentered(g, match.label(), new Font("SansSerif", Font.BOLD, 14), 110, 181, 164, 15, WHITE);

        drawCentered(g, "TIME", new Font("SansSerif", Font.BOLD, 9), 278, 168, 72, 11, MUTED);
        drawCentered(g, TIME_FORMAT.format(match.getScheduledAt()), new Font("SansSerif", Font.BOLD, 15),
                278, 178, 72, 18, WHITE);

        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            drawCentered(g, "Result: " + match.resultLabel(), new Font("SansSerif", Font.BOLD, 11),
                    120, 153, 144, 14, GOLD);
        }
    }

    private void drawOddsChart(Graphics2D g, FortunaMatch match) {
        Rectangle chart = new Rectangle(20, 205, DISPLAY_WIDTH - 40, 38);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(chart.x, chart.y, chart.width, chart.height, 8, 8);
        g.setColor(new Color(255, 255, 255, 25));
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 1; i <= 2; i++) {
            int y = chart.y + i * chart.height / 3;
            g.drawLine(chart.x + 8, y, chart.x + chart.width - 8, y);
        }

        List<FortunaOddsPoint> points = new ArrayList<>(match.getOddsHistory());
        if (points.isEmpty()) {
            points.add(new FortunaOddsPoint(match.getCreatedAt(), match.getHomeOdds(), match.getDrawOdds(), match.getAwayOdds()));
        }
        if (points.size() == 1) {
            FortunaOddsPoint point = points.getFirst();
            points.add(new FortunaOddsPoint(point.changedAt().plusMinutes(1), point.homeOdds(), point.drawOdds(), point.awayOdds()));
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (FortunaOddsPoint point : points) {
            min = Math.min(min, Math.min(point.homeOdds(), Math.min(point.drawOdds(), point.awayOdds())));
            max = Math.max(max, Math.max(point.homeOdds(), Math.max(point.drawOdds(), point.awayOdds())));
        }
        if (Math.abs(max - min) < 0.0001) {
            max = min + 1.0;
        }
        double padding = (max - min) * 0.12;
        min -= padding;
        max += padding;

        Rectangle plot = new Rectangle(chart.x + 10, chart.y + 6, chart.width - 20, chart.height - 12);
        drawChartLine(g, points.stream().map(FortunaOddsPoint::homeOdds).toList(), plot, min, max, GREEN);
        drawChartLine(g, points.stream().map(FortunaOddsPoint::drawOdds).toList(), plot, min, max, DRAW);
        drawChartLine(g, points.stream().map(FortunaOddsPoint::awayOdds).toList(), plot, min, max, RED);

        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        g.setColor(GREEN);
        g.drawString("1", chart.x + 13, chart.y + 10);
        g.setColor(DRAW);
        g.drawString("X", chart.x + 27, chart.y + 10);
        g.setColor(RED);
        g.drawString("2", chart.x + 41, chart.y + 10);
    }

    private void drawChartLine(Graphics2D g, List<Double> values, Rectangle plot, double min, double max, Color color) {
        if (values == null || values.size() < 2) {
            return;
        }
        Path2D path = new Path2D.Double();
        for (int i = 0; i < values.size(); i++) {
            double x = plot.x + i * (plot.width / (double) (values.size() - 1));
            double normalized = (values.get(i) - min) / (max - min);
            double y = plot.y + plot.height - normalized * plot.height;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
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
