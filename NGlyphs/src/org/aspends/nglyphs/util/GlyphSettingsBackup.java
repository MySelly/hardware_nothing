package org.aspends.nglyphs.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.aspends.nglyphs.R;

public final class GlyphSettingsBackup {
    public static final int VERSION = 1;

    private GlyphSettingsBackup() {}

    public static JSONObject exportSettings(Context context) throws Exception {
        SharedPreferences prefs =
                context.getSharedPreferences(context.getString(R.string.pref_file), Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("app", "GlyphManager");

        JSONObject prefJson = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            putPrefValue(prefJson, entry.getKey(), entry.getValue());
        }
        root.put("preferences", prefJson);

        JSONArray imported = new JSONArray();
        for (java.io.File file : CustomRingtoneManager.getImportedRingtones(context)) {
            imported.put(file.getName());
        }
        root.put("imported_ogg", imported);
        return root;
    }

    public static void importSettings(Context context, JSONObject root) throws Exception {
        if (root.optInt("version", 0) != VERSION) {
            throw new IllegalArgumentException("Unsupported backup version");
        }

        JSONObject prefJson = root.getJSONObject("preferences");
        SharedPreferences prefs =
                context.getSharedPreferences(context.getString(R.string.pref_file), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();

        JSONArray names = prefJson.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                Object value = prefJson.get(key);
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    cannotPutLong(editor, key, (Long) value);
                } else if (value instanceof Double || value instanceof Float) {
                    editor.putFloat(key, (float) prefJson.getDouble(key));
                } else if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof JSONArray) {
                    editor.putStringSet(key, jsonArrayToSet((JSONArray) value));
                }
            }
        }
        editor.apply();
    }

    private static void cannotPutLong(SharedPreferences.Editor editor, String key, Long value) {
        if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE) {
            editor.putInt(key, value.intValue());
        } else {
            editor.putLong(key, value);
        }
    }

    private static void putPrefValue(JSONObject json, String key, Object value) throws Exception {
        if (value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) value;
            JSONArray arr = new JSONArray();
            for (String item : set) {
                arr.put(item);
            }
            json.put(key, arr);
        } else if (value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Float) {
            json.put(key, value);
        }
    }

    private static Set<String> jsonArrayToSet(JSONArray arr) throws Exception {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            set.add(arr.getString(i));
        }
        return set;
    }

    public static void writeJsonToStream(OutputStream os, JSONObject root) throws Exception {
        byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
        os.write(bytes);
        os.flush();
    }

    public static JSONObject readJsonFromStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return new JSONObject(sb.toString());
    }
}
