package net.guzhenren.shengxian.core;

import java.util.Objects;

/** The outward-wound bounds of a purely visual source-local cloud volume. */
public final class AscensionCloudMesh {
    public static final float HALF_WIDTH = 10.0f;
    public static final float MIN_Y = 10.0f;
    public static final float MAX_Y = 16.0f;
    public static final int VERTEX_COUNT = 24;

    private AscensionCloudMesh() {}

    /** Emits exactly six outward-wound quads, or none when strength is zero. */
    public static void draw(float strength, AscensionAuraMesh.Sink sink) {
        Objects.requireNonNull(sink, "sink");
        if (!Float.isFinite(strength)) throw new IllegalArgumentException("Nonfinite cloud strength");
        float alpha = Math.max(0, Math.min(1, strength));
        if (alpha <= .001f) return;
        float s = HALF_WIDTH, lo = MIN_Y, hi = MAX_Y;
        face(sink, alpha, 1, 0, 0, s, lo, -s, s, hi, -s, s, hi, s, s, lo, s);
        face(sink, alpha, -1, 0, 0, -s, lo, s, -s, hi, s, -s, hi, -s, -s, lo, -s);
        face(sink, alpha, 0, 1, 0, -s, hi, -s, -s, hi, s, s, hi, s, s, hi, -s);
        face(sink, alpha, 0, -1, 0, -s, lo, s, -s, lo, -s, s, lo, -s, s, lo, s);
        face(sink, alpha, 0, 0, 1, -s, lo, s, s, lo, s, s, hi, s, -s, hi, s);
        face(sink, alpha, 0, 0, -1, s, lo, -s, -s, lo, -s, -s, hi, -s, s, hi, -s);
    }

    private static void face(AscensionAuraMesh.Sink sink, float alpha, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        put(sink, ax, ay, az, 0, 0, alpha, nx, ny, nz);
        put(sink, bx, by, bz, 1, 0, alpha, nx, ny, nz);
        put(sink, cx, cy, cz, 1, 1, alpha, nx, ny, nz);
        put(sink, dx, dy, dz, 0, 1, alpha, nx, ny, nz);
    }

    private static void put(AscensionAuraMesh.Sink sink, float x, float y, float z, float u, float v,
                            float alpha, float nx, float ny, float nz) {
        var color = AscensionAuraMesh.HEAVEN;
        sink.vertex(x, y, z, u, v, color.r(), color.g(), color.b(), alpha, nx, ny, nz);
    }
}
