package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * Seven world-anchored, gently concave slices of the immortal aperture.
 * Pure visual geometry shared by Minecraft and the offline GPU preview.
 */
public final class ApertureVisionMesh {
    public static final int LAYER_COUNT = 7;
    public static final float MAX_RADIUS = 3.10f;
    private static final int GRID = 12;
    private static final double EXTENT = 1.08;
    // Sky, stars, far mountains, middle mountains, cloud sea, near mountains, heavenly light.
    private static final double[] DEPTH = {4.35, 3.94, 3.53, 3.10, 2.67, 2.22, 1.75};
    private static final double[] RADIUS = {2.91, 2.94, 2.88, 2.98, 3.02, 3.05, 3.10};
    private static final AscensionAuraMesh.Color[] COLORS = {
            new AscensionAuraMesh.Color(.19f, .35f, .43f),
            AscensionAuraMesh.HEAVEN,
            new AscensionAuraMesh.Color(.34f, .64f, .65f),
            new AscensionAuraMesh.Color(.30f, .64f, .55f),
            new AscensionAuraMesh.Color(.70f, .88f, .79f),
            new AscensionAuraMesh.Color(.25f, .49f, .39f),
            new AscensionAuraMesh.Color(.94f, .85f, .58f)
    };

    private ApertureVisionMesh() {}

    /**
     * Emits NEW_ENTITY-compatible quads in player-feet-local WORLD axes.
     * yaw is Minecraft yaw in degrees (zero faces +Z); camera uses the same origin/axes.
     * The effect remains behind the source, rather than turning to face each viewer.
     * The integral part of U identifies the slice; its fractional part stores local U.
     * age and ticks are in ticks, fade is 0..1. No successful-state inference happens here.
     */
    public static void draw(float age, float fade, double ticks, float yaw,
                            double cameraX, double cameraY, double cameraZ, AscensionAuraMesh.Sink sink) {
        Objects.requireNonNull(sink, "sink");
        if (!Float.isFinite(age) || !Float.isFinite(fade) || !Float.isFinite(yaw)
                || !Double.isFinite(ticks) || !Double.isFinite(cameraX)
                || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ)) {
            throw new IllegalArgumentException("Nonfinite aperture visual input");
        }
        float opacity = clamp(fade) * smooth(age / 14.0);
        if (opacity <= .001f) return;
        double opening = smooth(age / 56.0);
        double angle = Math.toRadians(yaw % 360.0);
        double rightX = Math.cos(angle), rightZ = Math.sin(angle);
        double forwardX = -rightZ, forwardZ = rightX;
        double centerY = 1.80 + opening * 1.50;
        // Bounded periodic input keeps the preview deterministic even on long-lived worlds.
        double breath = Math.sin((ticks % 1048576.0) * .017) * .004;

        // Sort complete slices for previews that do not implement Minecraft's quad sorting.
        // The game RenderType additionally sorts individual quads for oblique viewpoints.
        int[] order = {0, 1, 2, 3, 4, 5, 6};
        double[] distance = new double[LAYER_COUNT];
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            double dx = cameraX + forwardX * DEPTH[layer];
            double dy = cameraY - centerY;
            double dz = cameraZ + forwardZ * DEPTH[layer];
            distance[layer] = dx * dx + dy * dy + dz * dz;
        }
        for (int i = 1; i < LAYER_COUNT; i++) {
            int next = order[i], j = i - 1;
            while (j >= 0 && distance[order[j]] < distance[next]) {
                order[j + 1] = order[j--];
            }
            order[j + 1] = next;
        }

        for (int layer : order) {
            float layerAlpha = opacity * smooth((age - layer * 1.4) / 14.0);
            if (layerAlpha <= .001f) continue;
            double radius = RADIUS[layer] * (.16 + .84 * opening) * (1.0 + breath);
            double bend = .10 + layer * .014;
            for (int row = 0; row < GRID; row++) {
                double v0 = row / (double) GRID, v1 = (row + 1.0) / GRID;
                double py0 = (v0 * 2.0 - 1.0) * EXTENT;
                double py1 = (v1 * 2.0 - 1.0) * EXTENT;
                for (int column = 0; column < GRID; column++) {
                    double u0 = column / (double) GRID, u1 = (column + 1.0) / GRID;
                    double px0 = (u0 * 2.0 - 1.0) * EXTENT;
                    double px1 = (u1 * 2.0 - 1.0) * EXTENT;
                    double nearX = Math.max(0.0, Math.max(px0, -px1));
                    double nearY = Math.max(0.0, Math.max(py0, -py1));
                    if (nearX * nearX + nearY * nearY > EXTENT * EXTENT) continue;
                    vertex(sink, layer, u0, v0, px0, py0, radius, bend, centerY,
                            rightX, rightZ, forwardX, forwardZ, layerAlpha);
                    vertex(sink, layer, u1, v0, px1, py0, radius, bend, centerY,
                            rightX, rightZ, forwardX, forwardZ, layerAlpha);
                    vertex(sink, layer, u1, v1, px1, py1, radius, bend, centerY,
                            rightX, rightZ, forwardX, forwardZ, layerAlpha);
                    vertex(sink, layer, u0, v1, px0, py1, radius, bend, centerY,
                            rightX, rightZ, forwardX, forwardZ, layerAlpha);
                }
            }
        }
    }

    private static void vertex(AscensionAuraMesh.Sink sink, int layer, double u, double v,
                               double px, double py, double radius, double bend, double centerY,
                               double rightX, double rightZ, double forwardX, double forwardZ,
                               float alpha) {
        double depth = DEPTH[layer] + bend * (1.0 - px * px - py * py);
        double normalX = -2.0 * bend * px / radius;
        double normalY = -2.0 * bend * py / radius;
        double length = Math.sqrt(1.0 + normalX * normalX + normalY * normalY);
        AscensionAuraMesh.Color color = COLORS[layer];
        sink.vertex((float) (rightX * px * radius - forwardX * depth),
                (float) (centerY + py * radius),
                (float) (rightZ * px * radius - forwardZ * depth),
                (float) (layer + .002 + u * .996), (float) v,
                color.r(), color.g(), color.b(), alpha,
                (float) ((rightX * normalX + forwardX) / length),
                (float) (normalY / length),
                (float) ((rightZ * normalX + forwardZ) / length));
    }

    private static float clamp(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }

    private static float smooth(double value) {
        float unit = clamp(value);
        return unit * unit * (3.0f - 2.0f * unit);
    }
}
