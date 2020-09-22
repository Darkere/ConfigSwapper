package com.darkere.configswapper;

import com.electronwill.nightconfig.core.utils.StringUtils;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigReloader {
    Map<String, List<ConfigValueRep>> configChanges = new HashMap<>();
    Path configPath;
    Path backupPath;
    List<String> backupText = new ArrayList<>();
    private static final Logger LOGGER = LogManager.getLogger();

    public ConfigReloader(String mode, String backup) {
        configPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + mode);
        if (backup != null) {
            backupPath = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID + "/" + backup);
            if (!backupPath.toFile().exists()) {
                backupPath.toFile().mkdir();
            }
            backupPath = backupPath.resolve("backup.txt");
        }
    }

    public void setConfigs() {
        ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>> modconfigs = getModConfigsWithReflection();
        if (modconfigs == null) {
            LOGGER.error("Cannot load Mod configs");
            return;
        }

        //go through configChanges
        for (List<ConfigValueRep> configuration : configChanges.values()) {
            //Get config
            ConfigValueRep first = configuration.get(0);
            Map<ModConfig.Type, ModConfig> modsconfig = modconfigs.get(first.getModID());
            if (modsconfig == null) {
                LOGGER.warn("Cannot find config for mod " + first.getModID());
                continue;
            }
            ModConfig config = modsconfig.get(first.getType());
            if (config == null) {
                LOGGER.warn(first.getModID() + "does not have a config of type " + first.getType());
                continue;
            }

            //read config
            List<String> lines = readAllLinesInConfig(config.getFullPath());
            if (lines == null) continue;


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

    private void replaceConfig(List<String> lines, ConfigValueRep configChange) {
        int depth = configChange.categories.size();
        String category = configChange.getNextCategory();
        int start = 0;
        while (category != null) {
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
            category = configChange.getNextCategory();
        }
        boolean found = false;
        for (int i = start; i < lines.size(); i++) {
            String sub = lines.get(i).trim();
            if (sub.startsWith("#")) continue;
            if (sub.startsWith(configChange.getName())) {
                backupText.add(configChange.getModID() + "." + configChange.getType() + "." + String.join(".", configChange.categories) + "." + sub);
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

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>> getModConfigsWithReflection() {
        Field configsByMod = null;
        try {
            configsByMod = ConfigTracker.class.getDeclaredField("configsByMod");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        if (configsByMod != null) {
            configsByMod.setAccessible(true);
            try {
                return (ConcurrentHashMap<String, Map<ModConfig.Type, ModConfig>>) configsByMod.get(ConfigTracker.INSTANCE);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    //----------------------- Reading configChanges-------------------
    public void readMode() {
        try {
            readFiles(configPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readFiles(Path folderPath) throws IOException {
        Files.list(folderPath).forEach(path -> {
            if (path.toFile().isDirectory()) {
                try {
                    readFiles(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                List<String> lines = null;
                try {
                    lines = Files.readAllLines(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (lines != null) {
                    readLines(lines, "");
                }
            }
        });
    }

    private void readLines(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("#")) continue;
            if (line.startsWith("$")) {
                int x = findEndOfCategory(lines, i);
                if (x != 0) {
                    String originalPrefix = prefix;
                    readLines(lines.subList(i + 1, x), prefix + readPrefix(line));
                    prefix = originalPrefix;
                    i = x + 1;
                }
                continue;
            }
            if (!line.contains("=")) continue;
            List<String> elements = StringUtils.split(line, '=');
            ConfigValueRep rep = new ConfigValueRep((prefix.isEmpty() ? "" : prefix + ".") + elements.get(0).trim(), elements.get(1).trim());
            if (!configChanges.containsKey(rep.getModID() + rep.getType())) {
                configChanges.put(rep.getModID() + rep.getType(), new ArrayList<>());
            }
            configChanges.get(rep.getModID() + rep.getType()).add(rep);
        }
    }

    private String readPrefix(String fullLine) {
        String line = fullLine.substring(fullLine.indexOf("$") + 1);
        String value;
        if (!line.contains("{")) {
            LOGGER.warn("Missing bracket on line \"" + fullLine + "\"");
            value = line;
        } else {
            value = line.substring(0, line.indexOf("{"));
        }

        return value.trim();
    }

    private int findEndOfCategory(List<String> lines, int i) {
        int opened = 0;
        for (int j = i + 1; j < lines.size(); j++) {
            if (lines.get(j).contains("{")) {
                opened++;
            }
            if (lines.get(j).contains("}")) {
                if (opened == 0) {
                    return j;
                } else {
                    opened--;
                }
            }
        }
        LOGGER.warn("Could not find end of Category started on line " + i + " " + lines.get(i));
        return 0;
    }

    static class ConfigValueRep {
        private final String ModID;
        private final ModConfig.Type type;
        private final String name;
        private final List<String> categories = new ArrayList<>();
        private final String value;
        private int count;

        ConfigValueRep(String path, String value) {
            List<String> strings = StringUtils.split(path, '.');
            ModID = strings.get(0);
            strings.remove(0);
            type = ModConfig.Type.valueOf(strings.get(0).toUpperCase());
            strings.remove(0);
            this.name = strings.get(strings.size() - 1);
            strings.remove(strings.size() - 1);
            categories.addAll(strings);
            this.value = value;
        }

        public String getModID() {
            return ModID;
        }

        public ModConfig.Type getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getNextCategory() {
            if (count < categories.size()) {
                String ret = categories.get(count);
                count++;
                return ret;
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return ModID + type + String.join(".", categories) + name;
        }
    }
}
