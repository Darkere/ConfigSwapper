package com.darkere.configswapper;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static List<String> readAvailableModes() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(ConfigSwapper.MODID);
        List<String> list = new ArrayList<>();
        if (!path.toFile().exists()) {
            path.toFile().mkdir();
            return list;
        }

        ConfigSwapper.modes.clear();
        try {
            Files.list(path).forEach(x -> {
                if (x.toFile().isDirectory()) {
                    list.add(x.getFileName().toString());
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static String readWriteModeToJson(String mode) {
        File packModeFile = new File("mode.json");
        JsonParser parser = new JsonParser();

        try {
            if (!packModeFile.exists()) {
                packModeFile.createNewFile();
                FileWriter writer = new FileWriter(packModeFile);
                writer.write("{}");
                writer.flush();
                writer.close();
            }

            JsonObject json = parser.parse(new FileReader(packModeFile)).getAsJsonObject();
            if (json != null) {
                if (mode == null) {
                    JsonElement written = json.get("mode");
                    if (written == null) {
                        mode = "none";
                    } else {
                        mode = written.getAsString();
                    }
                }
                json.addProperty("mode", mode);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = gson.toJson(json);
                FileWriter writer = new FileWriter(packModeFile);
                writer.write(prettyJson);
                writer.flush();
                writer.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return mode;
    }
}
