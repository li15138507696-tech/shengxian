package net.guzhenren.shengxian.core;

/** Frozen destination and use duration; movement and incoming damage are deliberately not cancellation inputs. */
public record TravelChannel(long startedTick, int duration, String dimension, String hand, XianGuRuntime.Mark mark) {
    public enum Status { PENDING, READY, CANCELLED }
    public TravelChannel {
        if (startedTick < 0 || duration < 1 || duration > 1200 || dimension == null || mark == null
                || !("MAIN_HAND".equals(hand) || "OFF_HAND".equals(hand))) {
            throw new IllegalArgumentException("Invalid fixed travel channel");
        }
    }
    public Status status(long tick, String currentDimension, String currentHand,
                          boolean usingItem, boolean alive, boolean riding) {
        if (tick < startedTick || !dimension.equals(currentDimension) || !hand.equals(currentHand)
                || !usingItem || !alive || riding) return Status.CANCELLED;
        return tick - startedTick >= duration ? Status.READY : Status.PENDING;
    }
}
