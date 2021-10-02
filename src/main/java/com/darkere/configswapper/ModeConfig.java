package com.darkere.configswapper;

import net.minecraft.world.storage.FolderName;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModeConfig {
    Map<String, List<ConfigValueRepresentation>> configChanges;
    Path configPath;
    private static final Logger LOGGER = LogManager.getLogger();

    public ModeConfig(String mode) {
        configPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + mode);
        ConfigParser parser = new ConfigParser(configPath);
        configChanges = parser.readModeFromConfigs();
    }

    public void applyMode() {
        ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>> modConfigs = Utils.getModConfigsWithReflection();
        if (modConfigs == null) {
            LOGGER.error("Cannot load Mod configs");
            return;
        }

        //go through configChanges for each file
        for (List<ConfigValueRepresentation> configuration : configChanges.values()) {

            //Get the first, to read out which config file to apply this change too
            ConfigValueRepresentation first = configuration.get(0);
            Path configPath;
            if (first.getCustomPath() == null) {
                //Get config
                Map<ModConfig.Type, ModConfig> modConfig = modConfigs.get(first.getModID());
                if (modConfig == null) {
                    LOGGER.warn("Cannot find config for mod " + first.getModID());
                    continue;
                }

                ModConfig config = modConfig.get(first.getType());
                if (config == null) {
                    LOGGER.warn(first.getModID() + "does not have a config of type " + first.getType());
                    continue;
                }

                if (config.getConfigData() == null) {
                    LOGGER.debug(first.getModID() + " " + first.getType() + " has no data. Client config on server?");
                    continue;
                }

                configPath = config.getFullPath();
            } else {
                configPath = getConfigPath(first.getCustomPath());
                if (configPath == null) {
                    LOGGER.warn("Could not find config for custom file path " + first.getCustomPath());
                    continue; //go to next config
                }

            }


            //read config
            List<String> lines = readAllLinesInConfig(configPath);
            if (lines == null) continue;


            //Apply Changes to Config
            configuration.forEach(configChange -> replaceConfig(lines, configChange));


            //save config
            try {
                Files.write(configPath, lines, StandardOpenOption.WRITE);
            } catch (IOException e) {
                LOGGER.warn("Could not write to Config file" + configPath);
                e.printStackTrace();
            }
        }
    }

    private Path getConfigPath(String customPath) {
        Path path = FMLPaths.GAMEDIR.get().resolve(customPath);
        if (path.toFile().exists() && path.toFile().isFile()) {
            return path;
        }

        Path worldPath = ServerLifecycleHooks.getCurrentServer().func_240776_a_(FolderName.field_237253_i_);
        path = worldPath.resolve(customPath);
        if (path.toFile().exists() && path.toFile().isFile()) {
            return path;
        }

        return null;
    }

    private void replaceConfig(List<String> lines, ConfigValueRepresentation configChange) {
        int depth = configChange.getCategories().size();
        String category = configChange.getNextCategory();
        int start = 0;
        while (category != null) {

            //Find Specified Category in config
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("[" + category) || line.startsWith("[\"" + category)) {
                    start = i;
                    break;
                }
                if (i == lines.size() - 1) {
                    LOGGER.warn("Could not find category " + category + " for config change " + configChange);
                    return;
                }
            }
            //Go to next
            category = configChange.getNextCategory();
        }
        boolean found = false;
        for (int i = start; i < lines.size(); i++) {
            String sub = lines.get(i).trim();
            if (sub.startsWith("#")) continue;
            if (sub.startsWith(configChange.getName())) {
                lines.set(i, getTabs(depth) + configChange.getName() + " = " + configChange.getValue());
                found = true;
                break;
            }
        }
        if (!found) {
            LOGGER.warn("Could not find entry for" + configChange);
        }
    }

    private String getTabs(int depth) {
        StringBuilder tabs = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            tabs.append("\t");
        }
        return tabs.toString();
    }

    private List<String> readAllLinesInConfig(Path fullPath) {
        try {
            return Files.readAllLines(fullPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
