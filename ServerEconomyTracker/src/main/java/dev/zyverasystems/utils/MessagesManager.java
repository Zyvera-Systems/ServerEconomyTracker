package dev.zyverasystems.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessagesManager {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private File file;
    private YamlConfiguration config;
    private final LegacyComponentSerializer legacySerializer;

    public MessagesManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.builder()
                .character('&')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");

        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);

        InputStream inputStream = plugin.getResource("messages.yml");
        if (inputStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }
    }

    public void reload() {
        load();
    }

    public String getRaw(String path) {
        if (config == null) {
            throw new IllegalStateException("MessagesManager wurde noch nicht geladen.");
        }

        String value = config.getString(path, "<red>Missing message: " + path);
        return applyPrefix(value);
    }

    public @NotNull Component get(String path) {
        return parse(getRaw(path));
    }

    public @NotNull Component get(String path, Placeholder... placeholders) {
        String text = getRaw(path);

        for (Placeholder placeholder : placeholders) {
            text = text.replace("{" + placeholder.key() + "}", placeholder.value());
        }

        return parse(text);
    }

    private @NotNull Component parse(String text) {
        // Unterstützt MiniMessage direkt
        if (text.contains("<")) {
            return miniMessage.deserialize(text);
        }

        // Unterstützt &a, &#ffffff und &x&f&f&f&f&f&f
        return legacySerializer.deserialize(text);
    }

    private String applyPrefix(String text) {
        String prefix = config.getString("prefix", "");
        return text.replace("<prefix>", prefix);
    }

    public record Placeholder(String key, String value) {
        public static Placeholder of(String key, String value) {
            return new Placeholder(key, value);
        }
    }
}