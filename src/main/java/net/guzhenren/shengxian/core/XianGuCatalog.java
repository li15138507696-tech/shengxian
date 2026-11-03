package net.guzhenren.shengxian.core;

import java.util.List;
import java.util.Optional;

/**
 * Immortal gu this module knows about.
 *
 * <p>Only entries with a verified chapter reference are listed. The essence cost is a balance
 * parameter, not a canon quantity. Acquisition (refining) is not implemented: the text read so
 * far does not describe it, so gu are handed out by an operator command only.</p>
 */
public final class XianGuCatalog {
    private static final List<XianGu> ENTRIES = List.of(
            new XianGu("pai_nan", "排难仙蛊", 5.0, XianGu.Effect.CARRIER_STRIKE, "CANON-009"),
            new XianGu("ding_xian_you", "定仙游", 3.0, XianGu.Effect.FIXED_TRAVEL, "XG-001", true),
            new XianGu("ren_ru_gu_immortal", "人如故仙蛊", 2.0, XianGu.Effect.RECENT_RESTORE, "XG-002", true),
            new XianGu("jiang_shan_ru_gu", "江山如故仙蛊", 5.0, XianGu.Effect.LANDSCAPE_RESTORE, "XG-003", true),
            // CANON-013 只描述"余气把凡蛊升炼成另一只仙蛊"，未给名称与效果，故效果标为
            // UNSPECIFIED，条目也做成通用的"余气仙蛊"而非某个人的专属蛊。
            // 它只能由余气升炼得到，不能催动——补一个效果等于编造原著。
            new XianGu(LeftoverRefinement.LEFTOVER_GU, "余气仙蛊（效果未核）", 1.0,
                    XianGu.Effect.UNSPECIFIED, "CANON-013"));

    private XianGuCatalog() {}

    public static List<XianGu> all() {
        return ENTRIES;
    }

    public static Optional<XianGu> find(String id) {
        if (id == null) return Optional.empty();
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }
}
