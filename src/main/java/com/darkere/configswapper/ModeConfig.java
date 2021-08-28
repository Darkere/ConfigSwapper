package com.darkere.configswapper;

import com.electronwill.nightconfig.core.utils.StringUtils;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
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
    Path backupPath;
    List<String> backupText = new ArrayList<>();
    private static final Logger LOGGER = LogManager.getLogger();

    public ModeConfig(String mode, String backup) {
        configPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + mode);
        if (backup != null) {
            backupPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + backup);
            if (!backupPath.toFile().exists()) {
                backupPath.toFile().mkdir();
            }
            backupPath = backupPath.resolve("backup.txt");
        }
        ConfigParser parser = new ConfigParser(configPath);
        configChanges = parser.readModeFromConfigs();
    }

    public void applyMode() {
        ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>> modConfigs = Utils.getModConfigsWithReflection();
        if (modConfigs == null) {
            LOGGER.error("Cannot load Mod configs");
            return;
        }

        //go through configChanges
        for (List<ConfigValueRepresentation> configuration : configChanges.values()) {

            //Get the first, to read out which config file to apply this change too
            ConfigValueRepresentation first = configuration.get(0);

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

            //read config
            List<String> lines = readAllLinesInConfig(config.getFullPath());
            if (lines == null) continue;


            //Apply Changes to Config
            configuration.forEach(configChange -> replaceConfig(lines, configChange));


            //save config
            try {
                Files.write(config.getFullPath(), lines, StandardOpenOption.WRITE);
                if (backupPath != null) {
                    backupPath.toFile().createNewFile();
                    Files.write(backupPath, backupText, StandardOpenOption.WRITE);
                }
            } catch (IOException e) {
                LOGGER.warn("Could not write to Config file" + config.getFullPath());
                e.printStackTrace();
            }
            backupPath = null;
        }
    }

    private void replaceConfig(List<String> lines, ConfigValueRepresentation configChange) {
        int depth = configChange.getCategories().size();
        String category = configChange.getNextCategory();
        int start = 0;
        while (category != null) {

            //Find Specified Category in config
            for (int i = start; i < lines.size(); i++) {
                if (lines.get(i).startsWith("[" + category) || lines.get(i).startsWith("[\"" + category)) {
                    start = i;
                    break;
                }
                if (i == lines.size() - 1) {
                    LOGGER.warn("Could not find category" + category + " for config change " + configChange);
                    return;
                }
            }
            //Go to next if not found
            category = configChange.getNextCategory();
        }
        boolean found = false;
        for (int i = start; i < lines.size(); i++) {
            String sub = lines.get(i).trim();
            if (sub.startsWith("#")) continue;
            if (sub.startsWith(configChange.getName())) {
                backupText.add(configChange.getModID() + "." + configChange.getType() + "." + String.join(".", configChange.getCategories()) + "." + sub);
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
