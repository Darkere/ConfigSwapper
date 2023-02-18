package com.darkere.configswapper;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;


public class ModeConfig {
    private final Path configPath;
    private static final Logger LOGGER = LogManager.getLogger();
    private final TomlParser parser = new TomlParser();
    private final TomlWriter writer = new TomlWriter();
    private String mode;
    private static final int MAX_ATTEMPTS = 5;
    List<Runnable> runnables = new ArrayList<>();

    public ModeConfig(String mode) {
        this.mode = mode;
        configPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + mode);
    }

    private void forAllFiles(Path folderPath, BiConsumer<Path, Boolean> consumer, boolean serverconfig) {
        try {
            Files.list(folderPath).forEach(path -> {
                if (path.toFile().isDirectory()) {
                    if (path.equals(configPath.resolve("serverconfig"))) {
                        if (ServerLifecycleHooks.getCurrentServer() != null) {
                            forAllFiles(path, consumer, true);
                        }
                    } else {
                        forAllFiles(path, consumer, serverconfig);
                    }
                } else {
                    consumer.accept(path, serverconfig);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void applyConfigs(Path path, boolean serverconfig) {
        Path relativePath;
        Path realConfigPath;

        if (serverconfig) {
            relativePath = configPath.relativize(path);
            realConfigPath = ServerLifecycleHooks.getCurrentServer().getWorldPath(LevelResource.ROOT).resolve(relativePath);
        } else {
            relativePath = configPath.relativize(path);
            realConfigPath = FMLPaths.GAMEDIR.get().resolve(relativePath);
        }

        if (!realConfigPath.toFile().exists()) {
            if(ConfigSwapper.afterConstructor){
                LOGGER.warn("No config file found for " + path);
                LOGGER.warn("Expected config at " + realConfigPath);
            }
            return;
        }
        if(!realConfigPath.getFileName().toString().endsWith(".toml")){
            LOGGER.info(realConfigPath + " is not a .toml config!");
            LOGGER.info("Using fallback file replacement for " + realConfigPath);
            try {
                Files.copy(path, realConfigPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.error("Failed to replace " + realConfigPath);
                LOGGER.error(e);
            }
            return;
        }
        CommentedConfig realConfig = parser.parse(realConfigPath, (file, configFormat) -> false);
        CommentedConfig configChanges = parser.parse(path, (file, configFormat) -> false);

        if (ConfigSwapper.afterConstructor)
            if (!isCorrect(realConfigPath))
                return;


        LOGGER.info("Checking if " + realConfigPath.getFileName() + " requires Changes");
        boolean changed = replaceValues(configChanges.valueMap(), realConfig.valueMap(), relativePath, "");

        if (changed) {
            if (writeAndUpdateConfig(realConfigPath, realConfig, realConfig, 1))
                LOGGER.info("Applied " + mode + " mode to " + realConfigPath.getFileName());
        } else {
            LOGGER.info(realConfigPath.getFileName() + " is up to date!");
        }
    }

    private boolean isCorrect(Path realConfigPath) {
        ModConfig config = findModConfig(realConfigPath.getFileName().toString());
        if (config != null) {
            if (config.getConfigData() instanceof CommentedFileConfig) {
                if (!config.getSpec().isCorrecting()) {
                    try {
                        ((CommentedFileConfig) config.getConfigData()).load();
                        if (!config.getSpec().isCorrect(config.getConfigData())) {
                            LOGGER.warn("Config was invalid before applying " + mode + " mode to it. Resetting " + realConfigPath);
                            ConfigFileTypeHandler.backUpConfig((CommentedFileConfig) config.getConfigData());
                            config.getSpec().correct(config.getConfigData());
                            config.save();
                            return false;
                        }
                    } catch (ParsingException e) {
                        LOGGER.error("Config swap failed for " + realConfigPath + " Config was incorrect before swapping!");
                        return false;
                    }
                }
            }
        } else {
            LOGGER.warn("Cannot find config in forge config registry " + realConfigPath + " reloading may not work for this file!");
            return true;
        }

        return true;
    }

    private ModConfig findModConfig(String configFileName) {
        for (Map.Entry<String, ModConfig> entry : ConfigTracker.INSTANCE.fileMap().entrySet()) {
            if (entry.getKey().endsWith(configFileName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean writeAndUpdateConfig(Path realConfigPath, CommentedConfig realConfig, CommentedConfig backupConfig, int attempt) {
        if (attempt > MAX_ATTEMPTS) {
            LOGGER.error("Failed to apply config swap to " + realConfigPath + " after " + MAX_ATTEMPTS + " Attempts. Giving up");
            return false;
        }
        writer.write(realConfig, realConfigPath, WritingMode.REPLACE);
        if(!ConfigSwapper.afterConstructor)
            return true;

        ModConfig config = findModConfig(realConfigPath.getFileName().toString());
        if (config != null) {
            if (config.getConfigData() instanceof CommentedFileConfig) {
                if (!config.getSpec().isCorrecting()) {
                    try {
                        ((CommentedFileConfig) config.getConfigData()).load();
                        if (!config.getSpec().isCorrect(config.getConfigData())) {
                            LOGGER.warn("Config was invalid after applying " + mode + " mode to it. Resetting " + realConfigPath);
                            ConfigFileTypeHandler.backUpConfig((CommentedFileConfig) config.getConfigData());
                            config.getSpec().correct(config.getConfigData());
                            config.save();
                        }
                    } catch (ParsingException e) {
                        LOGGER.error("Config swap failed for " + realConfigPath);
                        LOGGER.error("Attempt Number " + attempt + " out of " + MAX_ATTEMPTS);
                        LOGGER.error("Trying to revert to original config");
                        writer.write(backupConfig, realConfigPath, WritingMode.REPLACE);
                        runnables.add(() -> {
                            LOGGER.info("Reattempting overwrite for " + realConfigPath.getFileName());
                            if (writeAndUpdateConfig(realConfigPath, realConfig, backupConfig, attempt + 1))
                                LOGGER.info("Applied " + mode + " mode to " + realConfigPath.getFileName() + " after " + attempt + " Attempts");
                        });
                        return false;

                    }

                    //reload config
                    config.getSpec().afterReload();
                    var event = new ModConfigEvent.Reloading(config);
                    var container = ModList.get().getModContainerById(config.getModId());
                    container.ifPresent(c->c.dispatchConfigEvent(event));
                } else {
                    runnables.add(() -> {
                        LOGGER.info("Reattempting overwrite for " + realConfigPath.getFileName());
                        if (writeAndUpdateConfig(realConfigPath, realConfig, backupConfig, attempt + 1))
                            LOGGER.info("Applied " + mode + " mode to " + realConfigPath.getFileName() + " after " + attempt + " Attempts");
                    });
                }

            }
        }

        return true;
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

            } else if (!realObject.equals(entry.getValue())) {
                LOGGER.info("Updating entry " + entry.getKey() + " from " + realObject + " to " + entry.getValue());
                reals.put(entry.getKey(), entry.getValue());
                changed = true;
            }

        }

        return changed;
    }


    public void applyMode() {


        forAllFiles(configPath, this::applyConfigs, false);
        while (!runnables.isEmpty())
            runRunnables();
    }

    private void runRunnables() {
        List<Runnable> runnableList = new ArrayList<>(runnables);
        runnables.clear();
        runnableList.forEach(Runnable::run);
        runnableList.clear();
        if (!runnables.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }



}
