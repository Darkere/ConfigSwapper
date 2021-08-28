package com.darkere.configswapper;

import com.electronwill.nightconfig.core.utils.StringUtils;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

class ConfigValueRepresentation {
    private final String ModID;
    private final ModConfig.Type type;
    private String name;
    private final List<String> categories = new ArrayList<>();
    private String value;
    private int count;
    private static final String SPECIALSTRING = String.valueOf(Character.toChars(0x001D));

    ConfigValueRepresentation(String path, String value) {
        boolean replaced = false;
        int index = path.indexOf("\\");
        while (index != -1) {
            StringBuilder builder = new StringBuilder(path);
            builder.replace(index, index + 2, SPECIALSTRING);
            replaced = true;
            path = builder.toString();
            index = path.indexOf("\\");
        }
        List<String> strings = StringUtils.split(path, '.');
        ModID = strings.get(0);
        strings.remove(0);
        type = ModConfig.Type.valueOf(strings.get(0).toUpperCase());
        strings.remove(0);
        this.name = strings.get(strings.size() - 1);
        strings.remove(strings.size() - 1);
        categories.addAll(strings);
        this.value = value;
        if (replaced) {
            for (int i = 0; i < categories.size(); i++) {
                categories.set(i, categories.get(i).replaceAll(SPECIALSTRING, "."));
            }
            this.name = name.replaceAll(SPECIALSTRING, ".");
            this.value = this.value.replaceAll(SPECIALSTRING, ".");
        }

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

    public List<String> getCategories() {
        return categories;
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
