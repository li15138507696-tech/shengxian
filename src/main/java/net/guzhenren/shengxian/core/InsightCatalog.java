package net.guzhenren.shengxian.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Finite, evidence-linked knowledge available during a sustained three-qi交感 window.
 * The catalog contains fixed summaries only; it cannot answer arbitrary player text or invent
 * Gu recipes. Costs are game parameters and each entry can be learned once per session.
 */
public final class InsightCatalog {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("qi_balance", 1, "三气与积累",
                    "天地二气可纳入的量受自身人气积累制约；三气必须维持平衡。",
                    "CANON-002/003; chapter 600"),
            new Entry("aperture_grade", 1, "仙窍底蕴",
                    "普通福地分小、中、上三档；面积与初生仙元按档次范围生成。",
                    "CANON-005/006"),
            new Entry("tribulation_source", 2, "灾劫气机",
                    "天地二气过浓蓄积可引发灾劫；外劫与纳气过程并行。",
                    "CANON-009/011")
    );

    private InsightCatalog() {}

    public record Entry(String id, int cost, String title, String summary, String evidenceRef) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(evidenceRef, "evidenceRef");
            if (id.isBlank() || cost <= 0 || title.isBlank() || summary.isBlank() || evidenceRef.isBlank()) {
                throw new IllegalArgumentException("Invalid insight entry");
            }
        }
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String id) {
        if (id == null) return Optional.empty();
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public static boolean learned(AscensionProgress progress, Entry entry) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(entry, "entry");
        return progress.appliedEffects().contains(effectKey(entry));
    }

    public static List<Entry> available(AscensionProgress progress, int requiredWindowTicks) {
        Objects.requireNonNull(progress, "progress");
        if (requiredWindowTicks <= 0 || progress.window().ticks() < requiredWindowTicks) return List.of();
        int remaining = progress.insightEarned() - progress.insightSpent();
        return ENTRIES.stream().filter(entry -> entry.cost() <= remaining && !learned(progress, entry)).toList();
    }

    public static AscensionProgress grant(AscensionProgress progress, String id, int requiredWindowTicks) {
        Objects.requireNonNull(progress, "progress");
        if (requiredWindowTicks <= 0 || progress.window().ticks() < requiredWindowTicks
                || progress.window().id().isEmpty()) {
            throw new IllegalStateException("No sustained three-qi insight window is active");
        }
        Entry entry = find(id).orElseThrow(() -> new IllegalArgumentException("Unknown insight"));
        if (learned(progress, entry)) throw new IllegalStateException("Insight already learned this session");
        if (progress.insightEarned() - progress.insightSpent() < entry.cost()) {
            throw new IllegalStateException("Not enough insight budget");
        }
        AscensionProgress recorded = progress.withAppliedEffect(effectKey(entry));
        return recorded.withInsight(recorded.insightEarned(), recorded.insightSpent() + entry.cost());
    }

    private static String effectKey(Entry entry) {
        return "knowledge:" + entry.id();
    }
}