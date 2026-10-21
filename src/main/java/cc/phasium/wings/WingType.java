package cc.phasium.wings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One kind of wings. The wings are no longer built into the mod: the server reads them from
 * config/phasium_wings/wings/&lt;key&gt;.json (+ &lt;key&gt;.png) and sends them to every client on join, so new wings
 * need no modpack update. The json holds the German name, how they move, their resting pose, the particles and the
 * geometry (atlas size, planes, optional block halo, optional blocks on other body parts), the png is the atlas.
 * Besides wings the same files describe other cosmetics (hair, hats, glasses, belts, jackets), see {@link #SLOTS}.
 */
public final class WingType {
    public enum Style { FEATHER, BUTTERFLY }

    /**
     * Where a cosmetic is worn; one piece per slot, all slots at the same time. Set with "slot" in the json,
     * definitions without it are wings.
     */
    public static final java.util.List<String> SLOTS = java.util.List.of("fluegel", "haare", "muetze", "brille", "guertel", "jacke");

    /** All wings by key, in the order of the server's list (filled on the server from the config, on the client from the server). */
    private static final Map<String, WingType> REGISTRY = new LinkedHashMap<>();

    public final String key;
    public final String name;
    /** One of {@link #SLOTS}. */
    public final String slot;
    public final Style style;
    public final float spread, lift;
    /** Feather colour (0xRRGGBB) for wings that lose feathers, or -1. */
    public final int feathers;
    /** Sparkle colours (0xRRGGBB); empty for wings without sparkles. */
    public final int[] sparks;
    private final String permission;
    /** The full definition as sent to the clients, and the atlas png. */
    public final JsonObject json;
    public final byte[] png;
    /** Client only: where the atlas was registered as a texture. */
    public ResourceLocation texture;

    private WingType(String key, JsonObject json, byte[] png) {
        this.key = key;
        this.json = json;
        this.png = png;
        this.name = json.has("name") ? json.get("name").getAsString() : key;
        String slot = json.has("slot") ? json.get("slot").getAsString().toLowerCase(java.util.Locale.ROOT) : "fluegel";
        this.slot = SLOTS.contains(slot) ? slot : "fluegel";
        this.style = json.has("style") && json.get("style").getAsString().equalsIgnoreCase("butterfly") ? Style.BUTTERFLY : Style.FEATHER;
        this.spread = json.has("spread") ? json.get("spread").getAsFloat() : 20;
        this.lift = json.has("lift") ? json.get("lift").getAsFloat() : 0;
        this.feathers = json.has("feathers") && !json.get("feathers").isJsonNull() ? colour(json.get("feathers").getAsString()) : -1;
        JsonArray sp = json.has("sparks") ? json.getAsJsonArray("sparks") : new JsonArray();
        this.sparks = new int[sp.size()];
        for (int i = 0; i < sp.size(); i++) sparks[i] = colour(sp.get(i).getAsString());
        this.permission = json.has("permission") ? json.get("permission").getAsString() : "phasium.fluegel." + key;
    }

    private static int colour(String hex) {
        return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
    }

    public static WingType create(String key, JsonObject json, byte[] png) {
        return new WingType(key, json, png);
    }

    /** The geometry part of the definition (atlas, planes, halo). */
    public JsonObject geometry() {
        return json.getAsJsonObject("geometry");
    }

    public boolean sparkles() {
        return sparks.length > 0;
    }

    public String permission() {
        return permission;
    }

    public static synchronized void replaceAll(Collection<WingType> types) {
        REGISTRY.clear();
        for (WingType t : types) REGISTRY.put(t.key, t);
    }

    public static synchronized void put(WingType type) {
        REGISTRY.put(type.key, type);
    }

    public static synchronized void clear() {
        REGISTRY.clear();
    }

    public static synchronized WingType byKey(String key) {
        return REGISTRY.get(key);
    }

    public static synchronized java.util.List<WingType> all() {
        return java.util.List.copyOf(REGISTRY.values());
    }

    /** Small helper for the json read from the config: an element or null. */
    static JsonElement opt(JsonObject o, String k) {
        return o.has(k) ? o.get(k) : null;
    }
}
