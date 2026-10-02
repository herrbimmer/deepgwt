package com.havalh6.viewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import br.com.opengw.R;

final class DockAppOverrides {
    static final String GWM_HUB_PKG = "__gwm_hub";
    static final String PREF_KEY = "dock_app_overrides";
    private static final String TAG = "DockAppOverrides";
    final JSONObject map;
    static final String[][] SUBSTITUTE_ICONS = {new String[]{"youtube", "YouTube"}, new String[]{"youtube_music", "YT Music"}, new String[]{"gwm", "GWM"}, new String[]{"nav", "Navegação"}, new String[]{"music", "Música"}, new String[]{"video", "Vídeo"}, new String[]{"radio", "Rádio"}, new String[]{"phone", "Telefone"}, new String[]{"chat", "Chat"}, new String[]{"map_alt", "Mapa"}, new String[]{"browser", "Navegador"}, new String[]{"camera", "Câmera"}, new String[]{"photos", "Fotos"}, new String[]{"settings", "Configurações"}, new String[]{"haval", "Carro"}, new String[]{"bolt", "Carga"}, new String[]{"weather", "Clima"}, new String[]{"calendar", "Agenda"}, new String[]{"clock", "Relógio"}, new String[]{"folder", "Arquivos"}, new String[]{"download", "Download"}, new String[]{"store", "Loja"}, new String[]{"game", "Jogo"}, new String[]{"star", "Estrela"}, new String[]{"tv", "TV"}, new String[]{"mail", "Email"}, new String[]{"wifi", "Rede"}, new String[]{"speaker", "Caixa"}};
    static final String[] COLORS = {"#FFFFFF", "#9AA3AD", "#3D4650", "#111111", "#E53935", "#FB8C00", "#F5C542", "#43A047", "#1E88E5", "#26C6DA"};
    static final String COLOR_DEFAULT = COLORS[0];
    static final String BG_DEFAULT = COLORS[2];

    DockAppOverrides(JSONObject jSONObject) {
        this.map = jSONObject != null ? jSONObject : new JSONObject();
    }

    static Drawable drawableFor(Context context, String str, String str2) {
        Integer numResIdForSlug;
        if (context == null || str == null || str.isEmpty() || (numResIdForSlug = resIdForSlug(str)) == null) {
            return null;
        }
        try {
            Drawable drawable = context.getDrawable(numResIdForSlug.intValue());
            if (drawable == null) {
                return null;
            }
            Drawable drawableMutate = drawable.getConstantState() != null ? drawable.getConstantState().newDrawable().mutate() : drawable.mutate();
            if (!isBrandIcon(str)) {
                drawableMutate.setColorFilter(parseColor(str2, -1), PorterDuff.Mode.SRC_IN);
            }
            return drawableMutate;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isBrandIcon(String str) {
        return "youtube".equals(str) || "youtube_music".equals(str) || "gwm".equals(str);
    }

    static boolean isDarkPlateSlug(String str) {
        return "gwm".equals(str);
    }

    static DockAppOverrides load(SharedPreferences sharedPreferences) {
        String string = sharedPreferences != null ? sharedPreferences.getString("dock_app_overrides", "") : "";
        if (string == null || string.isEmpty()) {
            return new DockAppOverrides(new JSONObject());
        }
        try {
            return new DockAppOverrides(new JSONObject(string));
        } catch (JSONException e) {
            Log.w("DockAppOverrides", "Bad dock_app_overrides JSON", e);
            return new DockAppOverrides(new JSONObject());
        }
    }

    static int parseColor(String str, int i) {
        if (str == null || str.isEmpty()) {
            return i;
        }
        try {
            return Color.parseColor(str);
        } catch (Exception e) {
            return i;
        }
    }

    static Integer resIdForSlug(String str) {
        if (str == null) {
            return null;
        }
        switch (str) {
            case "youtube":
                return Integer.valueOf(R.drawable.ic_youtube_default);
            case "youtube_music":
                return Integer.valueOf(R.drawable.ic_youtube_music_default);
            case "gwm":
                return Integer.valueOf(R.drawable.ic_gwm_viewer);
            case "nav":
                return Integer.valueOf(R.drawable.ic_sub_nav);
            case "music":
                return Integer.valueOf(R.drawable.ic_sub_music);
            case "video":
                return Integer.valueOf(R.drawable.ic_sub_video);
            case "radio":
                return Integer.valueOf(R.drawable.ic_sub_radio);
            case "settings":
                return Integer.valueOf(R.drawable.ic_sub_settings);
            case "haval":
                return Integer.valueOf(R.drawable.ic_sub_haval);
            case "game":
                return Integer.valueOf(R.drawable.ic_sub_game);
            case "tv":
                return Integer.valueOf(R.drawable.ic_sub_tv);
            case "phone":
                return Integer.valueOf(R.drawable.ic_sub_phone);
            case "chat":
                return Integer.valueOf(R.drawable.ic_sub_chat);
            case "map_alt":
                return Integer.valueOf(R.drawable.ic_sub_map_alt);
            case "browser":
                return Integer.valueOf(R.drawable.ic_sub_browser);
            case "camera":
                return Integer.valueOf(R.drawable.ic_sub_camera);
            case "photos":
                return Integer.valueOf(R.drawable.ic_sub_photos);
            case "bolt":
                return Integer.valueOf(R.drawable.ic_sub_bolt);
            case "weather":
                return Integer.valueOf(R.drawable.ic_sub_weather);
            case "calendar":
                return Integer.valueOf(R.drawable.ic_sub_calendar);
            case "clock":
                return Integer.valueOf(R.drawable.ic_sub_clock);
            case "folder":
                return Integer.valueOf(R.drawable.ic_sub_folder);
            case "download":
                return Integer.valueOf(R.drawable.ic_sub_download);
            case "store":
                return Integer.valueOf(R.drawable.ic_sub_store);
            case "star":
                return Integer.valueOf(R.drawable.ic_sub_star);
            case "mail":
                return Integer.valueOf(R.drawable.ic_sub_mail);
            case "wifi":
                return Integer.valueOf(R.drawable.ic_sub_wifi);
            case "speaker":
                return Integer.valueOf(R.drawable.ic_sub_speaker);
            default:
                return null;
        }
    }

    String bg(String str) {
        JSONObject jSONObjectEntry = entry(str);
        if (jSONObjectEntry == null) {
            return null;
        }
        String strTrim = jSONObjectEntry.optString("bg", "").trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    void clear(String str) {
        if (str != null) {
            this.map.remove(str);
        }
    }

    String color(String str) {
        JSONObject jSONObjectEntry = entry(str);
        if (jSONObjectEntry == null) {
            return null;
        }
        String strTrim = jSONObjectEntry.optString("color", "").trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    JSONObject entry(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        return this.map.optJSONObject(str);
    }

    String icon(String str) {
        JSONObject jSONObjectEntry = entry(str);
        if (jSONObjectEntry == null) {
            return null;
        }
        String strTrim = jSONObjectEntry.optString("icon", "").trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    String name(String str) {
        JSONObject jSONObjectEntry = entry(str);
        if (jSONObjectEntry == null) {
            return null;
        }
        String strTrim = jSONObjectEntry.optString("name", "").trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    void put(String str, String str2, String str3, String str4, String str5) {
        if (str == null || str.isEmpty()) {
            return;
        }
        boolean z = (str2 == null || str2.trim().isEmpty()) ? false : true;
        boolean z2 = (str3 == null || str3.trim().isEmpty()) ? false : true;
        if (!z && !z2) {
            this.map.remove(str);
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject();
            if (z) {
                jSONObject.put("name", str2.trim());
            }
            if (z2) {
                jSONObject.put("icon", str3.trim());
                String str6 = (str4 == null || str4.isEmpty()) ? COLOR_DEFAULT : str4;
                String str7 = (str5 == null || str5.isEmpty()) ? BG_DEFAULT : str5;
                jSONObject.put("color", str6);
                jSONObject.put("bg", str7);
            }
            this.map.put(str, jSONObject);
        } catch (JSONException e) {
        }
    }

    void save(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return;
        }
        sharedPreferences.edit().putString("dock_app_overrides", this.map.toString()).apply();
    }
}
