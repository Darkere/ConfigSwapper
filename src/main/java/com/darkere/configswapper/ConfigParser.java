package com.darkere.configswapper;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigParser {
    private static final Logger LOGGER = LogManager.getLogger();
    private Path path;
    private Map<String, List<ConfigValueRepresentation>> configChanges  = new HashMap<>();



    public ConfigParser(Path path){
        this.path = path;
    }
    public Map<String, List<ConfigValueRepresentation>> readModeFromConfigs() {
        try {
            readFiles(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return configChanges;
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
            ConfigValueRepresentation rep = new ConfigValueRepresentation((prefix.isEmpty() ? "" : prefix + ".") + elements.get(0).trim(), elements.get(1).trim());
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

    //Categories can be stacked, find the end on one.
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
}
