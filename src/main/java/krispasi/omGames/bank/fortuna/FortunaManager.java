package krispasi.omGames.bank.fortuna;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class FortunaManager {
    private static final String CONFIG_FILE_NAME = "fortuna.yml";
    private static final int MAX_NAME_LENGTH = 28;
    private static final List<DateTimeFormatter> FULL_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd.MM.yy HH:mm", Locale.ROOT)
    );
    private static final List<DateTimeFormatter> SHORT_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.ROOT)
    );

    private final JavaPlugin plugin;
    private final FortunaMapDisplay mapDisplay;
    private final List<FortunaMatch> matches = new ArrayList<>();
    private final Map<UUID, FortunaPromptSession> prompts = new LinkedHashMap<>();
    private int nextMatchId = 1;

    public FortunaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mapDisplay = new FortunaMapDisplay(this);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public FortunaMapDisplay getMapDisplay() {
        return mapDisplay;
    }

    public void load() {
        matches.clear();
        prompts.clear();
        ensureConfigFile();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(getConfigFile());
        List<Integer> mapIds = config.getIntegerList("map-display.map-ids");
        if (mapIds.size() != FortunaMapDisplay.MAP_COLUMNS * FortunaMapDisplay.MAP_ROWS) {
            mapIds = FortunaMapDisplay.DEFAULT_MAP_IDS;
        }
        mapDisplay.setMapIds(mapIds);

        int maxId = 0;
        for (Map<?, ?> rawMatch : config.getMapList("matches")) {
            FortunaMatch match = parseMatch(rawMatch);
            if (match == null) {
                continue;
            }
            matches.add(match);
            maxId = Math.max(maxId, match.getId());
        }
        nextMatchId = Math.max(config.getInt("next-match-id", maxId + 1), maxId + 1);
        mapDisplay.installRenderers();
        plugin.getLogger().info("Loaded " + matches.size() + " Fortuna match" + (matches.size() == 1 ? "" : "es") + ".");
    }

    public void shutdown() {
        prompts.clear();
    }

    public void openFortunaMenu(Player player) {
        if (player == null) {
            return;
        }
        new FortunaMainMenu(this).open(player);
    }

    public List<FortunaMatch> getMatchesForMenu() {
        List<FortunaMatch> sorted = new ArrayList<>(matches);
        sorted.sort(this::compareForMenu);
        return sorted;
    }

    public FortunaMatch getMatch(int id) {
        for (FortunaMatch match : matches) {
            if (match.getId() == id) {
                return match;
            }
        }
        return null;
    }

    public int countMatches(FortunaMatchStatus status) {
        int count = 0;
        for (FortunaMatch match : matches) {
            if (match.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    public FortunaMatch getDisplayMatch() {
        return matches.stream()
                .filter(match -> match.getStatus() == FortunaMatchStatus.ACTIVE)
                .max(Comparator.comparing(match -> fallbackTime(match.getStartedAt(), match.getScheduledAt())))
                .or(() -> matches.stream()
                        .filter(match -> match.getStatus() == FortunaMatchStatus.UPCOMING)
                        .min(Comparator.comparing(FortunaMatch::getScheduledAt)))
                .orElse(null);
    }

    public void beginNewMatchPrompt(Player player) {
        if (player == null) {
            return;
        }
        prompts.put(player.getUniqueId(), FortunaPromptSession.newMatch());
        sendPrompt(player, "Zadej prvni jmeno nebo team. Napis cancel pro zruseni.");
    }

    public Result beginOddsPrompt(Player player, int matchId) {
        if (player == null) {
            return Result.fail("Only players can edit Fortuna odds.");
        }
        FortunaMatch match = getMatch(matchId);
        if (match == null) {
            player.sendMessage(Component.text("Match not found.", NamedTextColor.RED));
            return Result.fail("Match not found.");
        }
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            player.sendMessage(Component.text("Finished matches cannot change odds.", NamedTextColor.RED));
            return Result.fail("Finished matches cannot change odds.");
        }
        prompts.put(player.getUniqueId(), FortunaPromptSession.oddsUpdate(matchId));
        sendPrompt(player, "Zadej nove kurzy jako: <vyhra> <remiza> <prohra>, napr. 1.80 3.20 2.10");
        return Result.ok("Odds prompt started.");
    }

    public boolean hasPrompt(Player player) {
        return player != null && prompts.containsKey(player.getUniqueId());
    }

    public void cancelPrompt(Player player) {
        if (player != null) {
            prompts.remove(player.getUniqueId());
        }
    }

    public void handlePromptInput(Player player, String message) {
        if (player == null) {
            return;
        }
        FortunaPromptSession session = prompts.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("zrusit")) {
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text("Fortuna input cancelled.", NamedTextColor.YELLOW));
            openFortunaMenu(player);
            return;
        }
        if (session.mode() == FortunaPromptSession.Mode.NEW_MATCH) {
            handleNewMatchPrompt(player, session, input);
        } else if (session.mode() == FortunaPromptSession.Mode.ODDS_UPDATE) {
            handleOddsPrompt(player, session, input);
        }
    }

    public Result startMatch(int matchId) {
        FortunaMatch match = getMatch(matchId);
        if (match == null) {
            return Result.fail("Match not found.");
        }
        if (match.getStatus() == FortunaMatchStatus.ACTIVE) {
            return Result.fail("That match is already active.");
        }
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            return Result.fail("Finished matches cannot be activated.");
        }
        match.setStatus(FortunaMatchStatus.ACTIVE);
        match.setStartedAt(LocalDateTime.now());
        match.setResult(null);
        match.setFinishedAt(null);
        save();
        refreshDisplay();
        return Result.ok("Match #" + match.getId() + " moved to active games.");
    }

    public Result finishMatch(int matchId, FortunaOutcome outcome) {
        FortunaMatch match = getMatch(matchId);
        if (match == null) {
            return Result.fail("Match not found.");
        }
        if (match.getStatus() != FortunaMatchStatus.ACTIVE) {
            return Result.fail("Only active games can be ended.");
        }
        if (outcome == null) {
            return Result.fail("Choose a winner or draw.");
        }
        match.setStatus(FortunaMatchStatus.FINISHED);
        match.setResult(outcome);
        match.setFinishedAt(LocalDateTime.now());
        save();
        refreshDisplay();
        return Result.ok("Match #" + match.getId() + " ended. Result: " + match.resultLabel() + ".");
    }

    public Result deleteMatch(int matchId) {
        FortunaMatch match = getMatch(matchId);
        if (match == null) {
            return Result.fail("Match not found.");
        }
        matches.remove(match);
        prompts.entrySet().removeIf(entry -> entry.getValue().mode() == FortunaPromptSession.Mode.ODDS_UPDATE
                && entry.getValue().matchId() == matchId);
        save();
        refreshDisplay();
        return Result.ok("Deleted Fortuna match #" + matchId + ".");
    }

    public Result giveDisplayMaps(Player player) {
        if (player == null) {
            return Result.fail("Only players can receive display maps.");
        }
        int index = 0;
        for (int mapId : mapDisplay.getMapIds()) {
            ItemStack item = new ItemStack(Material.FILLED_MAP);
            ItemMeta rawMeta = item.getItemMeta();
            if (rawMeta instanceof MapMeta meta) {
                meta.setMapId(mapId);
                meta.displayName(Component.text("Fortuna Display Map " + (index + 1) + "/6", NamedTextColor.GOLD));
                meta.lore(List.of(
                        Component.text("Map id: " + mapId, NamedTextColor.GRAY),
                        Component.text("Place as 3 columns x 2 rows.", NamedTextColor.DARK_GRAY)
                ));
                item.setItemMeta(meta);
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            index++;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        return Result.ok("Gave Fortuna display maps " + mapDisplay.getMapIds() + ".");
    }

    public void refreshDisplay() {
        mapDisplay.invalidate();
    }

    public Result cleanDisplay() {
        mapDisplay.cleanBoard();
        return Result.ok("Fortuna display board cleaned.");
    }

    private void handleNewMatchPrompt(Player player, FortunaPromptSession session, String input) {
        switch (session.step()) {
            case HOME_NAME -> {
                String name = parseName(input);
                if (name == null) {
                    sendPrompt(player, "Jmeno musi mit 1-" + MAX_NAME_LENGTH + " znaku. Zadej prvni jmeno znovu.");
                    return;
                }
                session.setHomeName(name);
                session.setStep(FortunaPromptSession.Step.AWAY_NAME);
                sendPrompt(player, "Zadej druhe jmeno nebo team.");
            }
            case AWAY_NAME -> {
                String name = parseName(input);
                if (name == null) {
                    sendPrompt(player, "Jmeno musi mit 1-" + MAX_NAME_LENGTH + " znaku. Zadej druhe jmeno znovu.");
                    return;
                }
                session.setAwayName(name);
                session.setStep(FortunaPromptSession.Step.SCHEDULED_AT);
                sendPrompt(player, "Zadej datum a cas. Format: dd.MM HH:mm nebo yyyy-MM-dd HH:mm");
            }
            case SCHEDULED_AT -> {
                LocalDateTime scheduledAt = parseUserDateTime(input);
                if (scheduledAt == null) {
                    sendPrompt(player, "Neplatny datum. Pouzij napr. 22.04 18:33 nebo 2026-04-22 18:33");
                    return;
                }
                session.setScheduledAt(scheduledAt);
                session.setStep(FortunaPromptSession.Step.HOME_ODDS);
                sendPrompt(player, "Zadej kurz na vyhru " + session.homeName() + ".");
            }
            case HOME_ODDS -> {
                Double odds = parseOdds(input);
                if (odds == null) {
                    sendPrompt(player, "Kurz musi byt cislo vetsi nez 1.00. Zadej kurz na vyhru znovu.");
                    return;
                }
                session.setHomeOdds(odds);
                session.setStep(FortunaPromptSession.Step.DRAW_ODDS);
                sendPrompt(player, "Zadej kurz na remizu.");
            }
            case DRAW_ODDS -> {
                Double odds = parseOdds(input);
                if (odds == null) {
                    sendPrompt(player, "Kurz musi byt cislo vetsi nez 1.00. Zadej kurz na remizu znovu.");
                    return;
                }
                session.setDrawOdds(odds);
                session.setStep(FortunaPromptSession.Step.AWAY_ODDS);
                sendPrompt(player, "Zadej kurz na prohru / vyhru " + session.awayName() + ".");
            }
            case AWAY_ODDS -> {
                Double odds = parseOdds(input);
                if (odds == null) {
                    sendPrompt(player, "Kurz musi byt cislo vetsi nez 1.00. Zadej kurz na prohru znovu.");
                    return;
                }
                createMatchFromPrompt(player, session, odds);
            }
            case ODDS_LINE -> sendPrompt(player, "Tento input patri zmene kurzu. Zadej hodnoty ve tvaru 1.80 3.20 2.10");
        }
    }

    private void handleOddsPrompt(Player player, FortunaPromptSession session, String input) {
        double[] odds = parseOddsLine(input);
        if (odds == null) {
            sendPrompt(player, "Zadej tri kurzy jako cisla vetsi nez 1.00, napr. 1.80 3.20 2.10");
            return;
        }
        FortunaMatch match = getMatch(session.matchId());
        if (match == null) {
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text("Match not found.", NamedTextColor.RED));
            openFortunaMenu(player);
            return;
        }
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text("Finished matches cannot change odds.", NamedTextColor.RED));
            openFortunaMenu(player);
            return;
        }
        match.setOdds(odds[0], odds[1], odds[2], LocalDateTime.now());
        prompts.remove(player.getUniqueId());
        save();
        refreshDisplay();
        player.sendMessage(Component.text("Odds updated for match #" + match.getId() + ".", NamedTextColor.GREEN));
        new FortunaMatchMenu(this, match.getId()).open(player);
    }

    private void createMatchFromPrompt(Player player, FortunaPromptSession session, double awayOdds) {
        LocalDateTime now = LocalDateTime.now();
        FortunaMatch match = new FortunaMatch(
                nextMatchId++,
                session.homeName(),
                session.awayName(),
                session.scheduledAt(),
                session.homeOdds(),
                session.drawOdds(),
                awayOdds,
                now
        );
        match.addOddsPoint(new FortunaOddsPoint(now, session.homeOdds(), session.drawOdds(), awayOdds));
        matches.add(match);
        prompts.remove(player.getUniqueId());
        save();
        refreshDisplay();
        player.sendMessage(Component.text("Created Fortuna match #" + match.getId() + ": " + match.label() + ".", NamedTextColor.GREEN));
        new FortunaMatchMenu(this, match.getId()).open(player);
    }

    private int compareForMenu(FortunaMatch first, FortunaMatch second) {
        int statusCompare = Integer.compare(statusOrder(first), statusOrder(second));
        if (statusCompare != 0) {
            return statusCompare;
        }
        if (first.getStatus() == FortunaMatchStatus.FINISHED) {
            return fallbackTime(second.getFinishedAt(), second.getScheduledAt())
                    .compareTo(fallbackTime(first.getFinishedAt(), first.getScheduledAt()));
        }
        return first.getScheduledAt().compareTo(second.getScheduledAt());
    }

    private int statusOrder(FortunaMatch match) {
        return switch (match.getStatus()) {
            case ACTIVE -> 0;
            case UPCOMING -> 1;
            case FINISHED -> 2;
        };
    }

    private void ensureConfigFile() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create Fortuna data folder.");
            return;
        }
        File configFile = getConfigFile();
        if (configFile.exists()) {
            return;
        }
        try (InputStream input = plugin.getResource(CONFIG_FILE_NAME)) {
            if (input != null) {
                Files.copy(input, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save default Fortuna config: " + ex.getMessage());
        }
        save();
    }

    private void save() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create Fortuna data folder.");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("map-display.width", FortunaMapDisplay.MAP_COLUMNS);
        config.set("map-display.height", FortunaMapDisplay.MAP_ROWS);
        config.set("map-display.map-ids", mapDisplay.getMapIds());
        config.set("next-match-id", nextMatchId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FortunaMatch match : matches) {
            rows.add(serializeMatch(match));
        }
        config.set("matches", rows);
        try {
            config.save(getConfigFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save Fortuna config.", ex);
        }
    }

    private Map<String, Object> serializeMatch(FortunaMatch match) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", match.getId());
        row.put("home-name", match.getHomeName());
        row.put("away-name", match.getAwayName());
        row.put("scheduled-at", formatStorageTime(match.getScheduledAt()));
        row.put("odds-home", match.getHomeOdds());
        row.put("odds-draw", match.getDrawOdds());
        row.put("odds-away", match.getAwayOdds());
        row.put("status", match.getStatus().key());
        row.put("result", match.getResult() == null ? null : match.getResult().key());
        row.put("created-at", formatStorageTime(match.getCreatedAt()));
        row.put("started-at", formatStorageTime(match.getStartedAt()));
        row.put("finished-at", formatStorageTime(match.getFinishedAt()));

        List<Map<String, Object>> history = new ArrayList<>();
        for (FortunaOddsPoint point : match.getOddsHistory()) {
            Map<String, Object> historyRow = new LinkedHashMap<>();
            historyRow.put("changed-at", formatStorageTime(point.changedAt()));
            historyRow.put("home", point.homeOdds());
            historyRow.put("draw", point.drawOdds());
            historyRow.put("away", point.awayOdds());
            history.add(historyRow);
        }
        row.put("odds-history", history);
        return row;
    }

    private FortunaMatch parseMatch(Map<?, ?> raw) {
        Integer id = asInt(raw.get("id"));
        String homeName = asString(raw.get("home-name"));
        String awayName = asString(raw.get("away-name"));
        LocalDateTime scheduledAt = parseStorageTime(asString(raw.get("scheduled-at")));
        Double homeOdds = asDouble(raw.get("odds-home"));
        Double drawOdds = asDouble(raw.get("odds-draw"));
        Double awayOdds = asDouble(raw.get("odds-away"));
        if (id == null || homeName == null || awayName == null || scheduledAt == null
                || homeOdds == null || drawOdds == null || awayOdds == null) {
            return null;
        }
        FortunaMatch match = new FortunaMatch(
                id,
                homeName,
                awayName,
                scheduledAt,
                homeOdds,
                drawOdds,
                awayOdds,
                parseStorageTime(asString(raw.get("created-at")))
        );
        match.setStatus(FortunaMatchStatus.fromKey(asString(raw.get("status"))));
        match.setResult(FortunaOutcome.fromKey(asString(raw.get("result"))));
        match.setStartedAt(parseStorageTime(asString(raw.get("started-at"))));
        match.setFinishedAt(parseStorageTime(asString(raw.get("finished-at"))));

        Object historyObject = raw.get("odds-history");
        if (historyObject instanceof List<?> historyRows) {
            for (Object historyObjectRow : historyRows) {
                if (!(historyObjectRow instanceof Map<?, ?> historyRow)) {
                    continue;
                }
                LocalDateTime changedAt = parseStorageTime(asString(historyRow.get("changed-at")));
                Double historyHome = asDouble(historyRow.get("home"));
                Double historyDraw = asDouble(historyRow.get("draw"));
                Double historyAway = asDouble(historyRow.get("away"));
                if (changedAt != null && historyHome != null && historyDraw != null && historyAway != null) {
                    match.addOddsPoint(new FortunaOddsPoint(changedAt, historyHome, historyDraw, historyAway));
                }
            }
        }
        if (match.getOddsHistory().isEmpty()) {
            match.addOddsPoint(new FortunaOddsPoint(match.getCreatedAt(), match.getHomeOdds(), match.getDrawOdds(), match.getAwayOdds()));
        }
        return match;
    }

    private File getDataFolder() {
        return new File(new File(plugin.getDataFolder(), "Bank"), "Fortuna");
    }

    private File getConfigFile() {
        return new File(getDataFolder(), CONFIG_FILE_NAME);
    }

    private String parseName(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank() || trimmed.length() > MAX_NAME_LENGTH) {
            return null;
        }
        return trimmed;
    }

    private LocalDateTime parseUserDateTime(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().replace(',', '.');
        for (DateTimeFormatter formatter : FULL_DATE_FORMATS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted player input format.
            }
        }
        for (DateTimeFormatter formatter : SHORT_DATE_FORMATS) {
            try {
                java.time.temporal.TemporalAccessor parsed = formatter.parse(normalized);
                int year = LocalDate.now().getYear();
                LocalDate date = LocalDate.of(
                        year,
                        parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                        parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                );
                LocalTime time = LocalTime.of(
                        parsed.get(java.time.temporal.ChronoField.HOUR_OF_DAY),
                        parsed.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR)
                );
                return LocalDateTime.of(date, time);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted player input format.
            }
        }
        return null;
    }

    private Double parseOdds(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(input.trim().replace(',', '.'));
            return value > 1.0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double[] parseOddsLine(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String cleaned = input.trim()
                .replace(',', '.')
                .replace(';', ' ')
                .replace('|', ' ');
        String[] parts = cleaned.split("\\s+");
        List<Double> values = new ArrayList<>();
        for (String part : parts) {
            String normalized = part
                    .replace("1=", "")
                    .replace("1:", "")
                    .replace("x=", "")
                    .replace("x:", "")
                    .replace("X=", "")
                    .replace("X:", "")
                    .replace("2=", "")
                    .replace("2:", "");
            Double value = parseOdds(normalized);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() != 3) {
            return null;
        }
        return new double[]{values.get(0), values.get(1), values.get(2)};
    }

    private LocalDateTime parseStorageTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, FortunaMatch.STORAGE_TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String formatStorageTime(LocalDateTime value) {
        return value == null ? null : FortunaMatch.STORAGE_TIME_FORMAT.format(value);
    }

    private LocalDateTime fallbackTime(LocalDateTime primary, LocalDateTime fallback) {
        return primary != null ? primary : fallback;
    }

    private String asString(Object value) {
        return value instanceof String string && !string.isBlank() ? string.trim() : null;
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void sendPrompt(Player player, String message) {
        player.sendMessage(Component.text("[Fortuna] " + message, NamedTextColor.YELLOW));
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
