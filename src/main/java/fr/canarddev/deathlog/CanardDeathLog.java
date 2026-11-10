package fr.canarddev.deathlog;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * CanardDeathLog - keeps the history of every player's deaths
 * (cause, killer, place, date) and shows it with /deaths.
 */
public final class CanardDeathLog extends JavaPlugin implements Listener, TabExecutor {

    private Lang lang;
    private Storage storage;
    private File logFile;

    private int maxDeaths, perPage;
    private boolean logToFile;
    private ZoneId zone;
    private DateTimeFormatter dateFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storage = new Storage(this);
        logFile = new File(getDataFolder(), "deaths.log");
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = getCommand("deaths");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.close();
    }

    private void load() {
        reloadConfig();
        FileConfiguration c = getConfig();
        lang = new Lang(this, c.getString("language", "en"));
        maxDeaths = Math.max(1, c.getInt("max-deaths-per-player", 100));
        perPage = Math.max(1, Math.min(50, c.getInt("per-page", 10)));
        logToFile = c.getBoolean("log-file", true);
        try {
            zone = ZoneId.of(c.getString("timezone", "Europe/Paris"));
        } catch (Exception e) {
            getLogger().warning("Unknown timezone in config.yml, using the server's.");
            zone = ZoneId.systemDefault();
        }
        try {
            dateFormat = DateTimeFormatter.ofPattern(c.getString("date-format", "dd/MM/yyyy HH:mm:ss"));
        } catch (Exception e) {
            dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        }
    }

    // ================================================================== recording

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        storage.remember(e.getPlayer().getName(), e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        EntityDamageEvent last = v.getLastDamageCause();
        String cause = last != null ? last.getCause().name() : "UNKNOWN";

        // who did it?
        String killerType = null, killerName = null;
        Player pk = v.getKiller();
        if (pk != null && !pk.equals(v)) {
            killerType = "player";
            killerName = pk.getName();
        } else if (last instanceof EntityDamageByEntityEvent ev) {
            Entity d = ev.getDamager();
            if (d instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) d = shooter;
            if (d instanceof Player p && !p.equals(v)) {
                killerType = "player";
                killerName = p.getName();
            } else if (!d.equals(v)) {
                killerType = d.getType().name();
                killerName = d.getCustomName();
            }
        }

        UUID id = v.getUniqueId();
        YamlConfiguration cfg = storage.get(id);
        int n = cfg.getInt("total", 0) + 1;
        cfg.set("total", n);
        cfg.set("name", v.getName());
        Location l = v.getLocation();
        String base = "deaths." + n;
        long now = System.currentTimeMillis();
        cfg.set(base + ".time", now);
        cfg.set(base + ".cause", cause);
        if (killerType != null) cfg.set(base + ".killer-type", killerType);
        if (killerName != null) cfg.set(base + ".killer-name", killerName);
        cfg.set(base + ".world", l.getWorld() != null ? l.getWorld().getName() : "?");
        cfg.set(base + ".x", l.getBlockX());
        cfg.set(base + ".y", l.getBlockY());
        cfg.set(base + ".z", l.getBlockZ());
        // keep only the newest ones (the total keeps counting)
        int oldest = n - maxDeaths;
        ConfigurationSection s = cfg.getConfigurationSection("deaths");
        if (s != null && oldest >= 1) {
            for (String k : new ArrayList<>(s.getKeys(false))) {
                try {
                    if (Integer.parseInt(k) <= oldest) cfg.set("deaths." + k, null);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        storage.remember(v.getName(), id);
        storage.save(id);

        if (logToFile) {
            ConfigurationSection d = cfg.getConfigurationSection(base);
            storage.append(logFile, v.getName() + " | " + date(now) + " | " + strip(describe(d)) + " | "
                    + d.getString("world") + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        }
    }

    // ================================================================== display

    private String date(long millis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(dateFormat);
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    /** "FALL_DAMAGE" -> "Fall damage" */
    private static String pretty(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Cause text, with the killer if there is one. */
    private String describe(ConfigurationSection d) {
        String causeKey = d.getString("cause", "UNKNOWN");
        String cause = lang.file().getString("causes." + causeKey, pretty(causeKey));
        String type = d.getString("killer-type");
        if (type == null) return Lang.color(cause);
        String name = d.getString("killer-name");
        if (type.equals("player")) return lang.get("by-player", "cause", cause, "killer", name);
        String mob = lang.file().getString("mobs." + type, pretty(type));
        if (name != null) return lang.get("by-named-mob", "cause", cause, "name", strip(Lang.color(name)), "mob", mob);
        return lang.get("by-mob", "cause", cause, "mob", mob);
    }

    // ================================================================== commands

    private UUID resolve(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        UUID id = storage.byName(name);
        if (id != null) return id;
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null ? op.getUniqueId() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (!sender.hasPermission("deathlog.view")) {
            lang.send(sender, "no-permission");
            return true;
        }
        if (a.length == 0) {
            lang.send(sender, sender.hasPermission("deathlog.clear") ? "usage-admin" : "usage", "label", label);
            return true;
        }
        if (a[0].equalsIgnoreCase("reload") && sender.hasPermission("deathlog.clear")) {
            load();
            lang.send(sender, "reloaded");
            return true;
        }
        if (a[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("deathlog.clear")) {
                lang.send(sender, "no-permission");
                return true;
            }
            if (a.length < 2) {
                lang.send(sender, "usage-clear", "label", label);
                return true;
            }
            UUID id = resolve(a[1]);
            if (id == null || !storage.exists(id) || storage.get(id).getInt("total", 0) == 0) {
                lang.send(sender, "no-deaths", "player", a[1]);
                return true;
            }
            int total = storage.get(id).getInt("total", 0);
            storage.delete(id);
            lang.send(sender, "cleared", "count", total, "player", a[1]);
            if (logToFile) storage.append(logFile, sender.getName() + " cleared the " + total + " deaths of " + a[1]);
            return true;
        }

        UUID id = resolve(a[0]);
        if (id == null || !storage.exists(id)) {
            lang.send(sender, "no-deaths", "player", a[0]);
            return true;
        }
        YamlConfiguration cfg = storage.get(id);
        int total = cfg.getInt("total", 0);
        if (total == 0) {
            lang.send(sender, "no-deaths", "player", a[0]);
            return true;
        }
        String name = cfg.getString("name", a[0]);
        int available = Math.min(total, maxDeaths);
        int pages = (available + perPage - 1) / perPage;
        int page = 1;
        if (a.length > 1) {
            try {
                page = Integer.parseInt(a[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        page = Math.max(1, Math.min(page, pages));

        sender.sendMessage(lang.get("header", "player", name, "page", page, "pages", pages, "total", total));
        int top = total - (page - 1) * perPage;
        for (int i = top; i > top - perPage && i >= 1; i--) {
            ConfigurationSection d = cfg.getConfigurationSection("deaths." + i);
            if (d == null) break; // older ones were trimmed
            sender.sendMessage(lang.get("line", "n", i, "date", date(d.getLong("time")), "cause", describe(d),
                    "world", d.getString("world", "?"), "x", d.getInt("x"), "y", d.getInt("y"), "z", d.getInt("z")));
        }
        if (page < pages) sender.sendMessage(lang.get("next-page", "label", label, "player", name, "page", page + 1));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("deathlog.view")) return out;
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("deathlog.clear");
        if (a.length == 1) {
            if (admin) for (String s : List.of("clear", "reload")) if (s.startsWith(last)) out.add(s);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("clear") && admin) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            UUID id = resolve(a[0]);
            if (id != null && storage.exists(id)) {
                int available = Math.min(storage.get(id).getInt("total", 0), maxDeaths);
                for (int i = 1; i <= (available + perPage - 1) / perPage; i++) out.add(String.valueOf(i));
            }
        }
        return out;
    }
}
