package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Strict bounded schema-11 property codec. */
public final class XianGuStateCodec {
    private static final String P = "xiangu.";
    private XianGuStateCodec() { }

    public static void write(Properties p, XianGuRuntime state) {
        p.setProperty(P + "version", "1");
        var held = state.held().stream().sorted().toList();
        p.setProperty(P + "held.count", Integer.toString(held.size()));
        for (int i = 0; i < held.size(); i++) p.setProperty(P + "held." + i, held.get(i));
        for (String id : XianGuRuntime.IDS) p.setProperty(P + "cooldown." + id,
                Long.toString(state.cooldowns().getOrDefault(id, 0L)));
        p.setProperty(P + "selected", Integer.toString(state.selectedSlot()));
        p.setProperty(P + "recording", Boolean.toString(state.recording()));
        p.setProperty(P + "sequence", Long.toString(state.sequence()));
        for (int slot = 0; slot < 8; slot++) {
            String prefix = P + "mark." + slot + ".";
            var mark = state.marks().get(slot);
            p.setProperty(prefix + "present", Boolean.toString(mark != null));
            if (mark == null) continue;
            p.setProperty(prefix + "dimension", mark.dimension());
            p.setProperty(prefix + "x", Double.toString(mark.x()));
            p.setProperty(prefix + "y", Double.toString(mark.y()));
            p.setProperty(prefix + "z", Double.toString(mark.z()));
            p.setProperty(prefix + "yaw", Float.toString(mark.yaw()));
            p.setProperty(prefix + "pitch", Float.toString(mark.pitch()));
            p.setProperty(prefix + "scene", mark.scene());
        }
        p.setProperty(P + "use.present", Boolean.toString(state.operation().isPresent()));
        state.operation().ifPresent(use -> {
            p.setProperty(P + "use.id", use.operationId().toString());
            p.setProperty(P + "use.gu", use.guId());
            p.setProperty(P + "use.sequence", Long.toString(use.sequence()));
            p.setProperty(P + "use.stage", use.stage().name());
            p.setProperty(P + "use.tick", Long.toString(use.tick()));
            p.setProperty(P + "use.before", Double.toString(use.essenceBefore()));
            p.setProperty(P + "use.after", Double.toString(use.essenceAfter()));
            p.setProperty(P + "use.cooldown", Long.toString(use.cooldownUntil()));
            p.setProperty(P + "use.payload", Base64.getEncoder().encodeToString(use.payload().getBytes(StandardCharsets.UTF_8)));
        });
    }

    public static XianGuRuntime read(Properties p) throws IOException {
        try {
            if (!"1".equals(required(p, "version"))) throw new IOException("Unsupported immortal Gu state version");
            int count = Integer.parseInt(required(p, "held.count"));
            if (count < 0 || count > 3) throw new IOException("Invalid immortal Gu ownership count");
            Set<String> held = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) if (!held.add(required(p, "held." + i))) throw new IOException("Duplicate Gu ownership");
            Map<String, Long> cooldowns = new LinkedHashMap<>();
            for (String id : XianGuRuntime.IDS) {
                long until = Long.parseLong(required(p, "cooldown." + id));
                if (until < 0) throw new IOException("Negative immortal Gu cooldown");
                if (until > 0) cooldowns.put(id, until);
            }
            Map<Integer, XianGuRuntime.Mark> marks = new LinkedHashMap<>();
            for (int slot = 0; slot < 8; slot++) {
                String prefix = "mark." + slot + ".";
                if (!bool(p, prefix + "present")) continue;
                marks.put(slot, new XianGuRuntime.Mark(required(p, prefix + "dimension"),
                        Double.parseDouble(required(p, prefix + "x")), Double.parseDouble(required(p, prefix + "y")),
                        Double.parseDouble(required(p, prefix + "z")), Float.parseFloat(required(p, prefix + "yaw")),
                        Float.parseFloat(required(p, prefix + "pitch")), required(p, prefix + "scene")));
            }
            Optional<XianGuRuntime.Use> operation = Optional.empty();
            if (bool(p, "use.present")) {
                String encoded = required(p, "use.payload");
                if (encoded.length() > XianGuRuntime.MAX_PAYLOAD * 6) throw new IOException("Oversized Gu effect payload");
                operation = Optional.of(new XianGuRuntime.Use(UUID.fromString(required(p, "use.id")),
                        required(p, "use.gu"), Long.parseLong(required(p, "use.sequence")),
                        XianGuRuntime.Stage.valueOf(required(p, "use.stage")), Long.parseLong(required(p, "use.tick")),
                        Double.parseDouble(required(p, "use.before")), Double.parseDouble(required(p, "use.after")),
                        Long.parseLong(required(p, "use.cooldown")),
                        new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)));
            }
            XianGuRuntime state = new XianGuRuntime(held, cooldowns, marks,
                    Integer.parseInt(required(p, "selected")), bool(p, "recording"),
                    Long.parseLong(required(p, "sequence")), operation);
            Properties canonical = new Properties();
            write(canonical, state);
            Set<String> actual = new LinkedHashSet<>();
            for (String name : p.stringPropertyNames()) if (name.startsWith(P)) actual.add(name);
            if (!actual.equals(canonical.stringPropertyNames())) throw new IOException("Unknown or stray immortal Gu state fields");
            return state;
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            throw new IOException("Invalid immortal Gu runtime state", malformed);
        }
    }
    private static String required(Properties p, String key) throws IOException {
        String value = p.getProperty(P + key);
        if (value == null || value.isEmpty()) throw new IOException("Missing immortal Gu field: " + key);
        return value;
    }
    private static boolean bool(Properties p, String key) throws IOException {
        String value = required(p, key);
        if (!value.equals("true") && !value.equals("false")) throw new IOException("Invalid Gu boolean: " + key);
        return Boolean.parseBoolean(value);
    }
}
