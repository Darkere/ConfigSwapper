package com.darkere.configswapper;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class ModeConfig {
    private final Path configPath;
    private static final Logger LOGGER = LogManager.getLogger();
    private final TomlParser parser = new TomlParser();
    private final TomlWriter writer = new TomlWriter();

    public ModeConfig(String mode) {
        configPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + mode);
    }

    private void forAllFiles(Path folderPath, Consumer<Path> consumer) {
        try {
            Files.list(folderPath).forEach(path -> {
                if (path.toFile().isDirectory()) {
                    if (path.equals(configPath.resolve("serverconfig"))) {
                        if (ServerLifecycleHooks.getCurrentServer() != null) {
                            path = ServerLifecycleHooks.getCurrentServer().func_240776_a_(FolderName.field_237253_i_).resolve("serverconfig");
                        } else {
                            return;
                        }
                    }
                    forAllFiles(path, consumer);
                } else {
                    consumer.accept(path);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void applyConfigs(Path path) {
        Path relativePath = configPath.relativize(path);
        Path realConfigPath = FMLPaths.GAMEDIR.get().resolve(relativePath);
        if (!realConfigPath.toFile().exists()) {
            LOGGER.warn("Not config file found for " + path);
            return;
        }
        CommentedConfig realConfig = parser.parse(realConfigPath, (file, configFormat) -> false);
        CommentedConfig configChanges = parser.parse(path, (file, configFormat) -> false);

        boolean changed = replaceValues(configChanges.valueMap(), realConfig.valueMap(), relativePath, "");
        if (changed)
            writer.write(realConfig, realConfigPath, WritingMode.REPLACE);

    }

    private boolean replaceValues(Map<String, Object> changes, Map<String, Object> reals, Path filePath, String configPath) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            Object realObject = reals.get(entry.getKey());
            if (realObject == null) {
                LOGGER.warn("Config Swapper: Error in " + filePath + ". Real config does not have an entry called " + configPath + "." + entry.getKey());
                continue;
            }
            if (entry.getValue() instanceof UnmodifiableConfig) {
                if (!(realObject instanceof UnmodifiableConfig)) {
                    LOGGER.warn("Config Swapper: Error in " + filePath + ". Real config differs in entry called " + configPath + "." + entry.getKey());
                    continue;
                }
                if (replaceValues(((UnmodifiableConfig) entry.getValue()).valueMap(), ((UnmodifiableConfig) reals.get(entry.getKey())).valueMap(), filePath, configPath + (configPath.isEmpty() ? "" : ".") + entry.getKey()))
                    changed = true;
            } else {
                reals.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        return changed;
    }


    public void applyMode() {
        forAllFiles(configPath, this::applyConfigs);
    }
}
