package com.darkere.configswapper;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;


public class ModeConfig {
    private final Path configPath;
    private static final Logger LOGGER = LogManager.getLogger();
    private final TomlParser parser = new TomlParser();
    private final TomlWriter writer = new TomlWriter();
    private String mode;
    ConcurrentHashMap<String, ModConfig> fileMap;
    Constructor<?> ReloadingEvent;
    Method fireEvent;

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
            realConfigPath = ServerLifecycleHooks.getCurrentServer().func_240776_a_(FolderName.field_237253_i_).resolve(relativePath);
        } else {
            relativePath = configPath.relativize(path);
            realConfigPath = FMLPaths.GAMEDIR.get().resolve(relativePath);
        }

        if (!realConfigPath.toFile().exists()) {
            LOGGER.warn("No config file found for " + path);
            LOGGER.warn("Expected config at " + realConfigPath);
            return;
        }

        CommentedConfig realConfig = parser.parse(realConfigPath, (file, configFormat) -> false);
        CommentedConfig configChanges = parser.parse(path, (file, configFormat) -> false);

        boolean changed = replaceValues(configChanges.valueMap(), realConfig.valueMap(), relativePath, "");
        if (changed) {
            writer.write(realConfig, realConfigPath, WritingMode.REPLACE);
            ModConfig config = fileMap.get(realConfigPath.getFileName().toString());
            if (config != null) {
                if (config.getConfigData() instanceof CommentedFileConfig) {
                    ((CommentedFileConfig) config.getConfigData()).load();
                    if (!config.getSpec().isCorrect(config.getConfigData())) {
                        LOGGER.warn("Config was invalid after applying " + mode + " mode to it. Resetting " + realConfigPath);
                        ConfigFileTypeHandler.backUpConfig((CommentedFileConfig) config.getConfigData());
                        config.getSpec().correct(config.getConfigData());
                        config.save();
                    }
                    config.getSpec().afterReload();
                    try {
                        Class<?> aClass = Class.forName("net.minecraftforge.fml.config.ModConfig$Reloading");
                        ReloadingEvent = aClass.getDeclaredConstructor(ModConfig.class);
                        ReloadingEvent.setAccessible(true);
                        fireEvent.invoke(config, ReloadingEvent.newInstance(config));
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            }
            LOGGER.info("Applied " + mode + " mode to " + realConfigPath.getFileName());
        }
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


    public void applyMode(boolean onlyServer) {
        try {
            GetConfigFileMapViaReflection();
        } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        forAllFiles(onlyServer ? configPath.resolve("serverconfig") : configPath, this::applyConfigs, onlyServer);
        fileMap = null;


    }
    @SuppressWarnings("Unchecked cast")
    private void GetConfigFileMapViaReflection() throws NoSuchMethodException, IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
        Field field = ConfigTracker.class.getDeclaredField("fileMap");
        field.setAccessible(true);
        fileMap = (ConcurrentHashMap<String, ModConfig>) field.get(ConfigTracker.INSTANCE);
        fireEvent = ModConfig.class.getDeclaredMethod("fireEvent", ModConfig.ModConfigEvent.class);
        fireEvent.setAccessible(true);
    }


}
