package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import shit.zen.event.EventMarker;

public class UseItemRayTraceEvent
implements EventMarker {
    @Getter @Setter
    private float range;
    @Getter @Setter
    private float blockRange;

    @Generated
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof UseItemRayTraceEvent otherEvent)) {
            return false;
        }
        if (!otherEvent.canEqual(this)) {
            return false;
        }
        if (Float.compare(this.getRange(), otherEvent.getRange()) != 0) {
            return false;
        }
        return Float.compare(this.getBlockRange(), otherEvent.getBlockRange()) == 0;
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof UseItemRayTraceEvent;
    }

    @Generated
    public int hashCode() {
        int prime = 59;
        int result = 1;
        result = result * 59 + Float.floatToIntBits(this.getRange());
        result = result * 59 + Float.floatToIntBits(this.getBlockRange());
        return result;
    }

    @Generated
    public String toString() {
        return "UseItemRayTraceEvent(yaw=" + this.getRange() + ", pitch=" + this.getBlockRange() + ")";
    }

    @Generated
    public UseItemRayTraceEvent(float range, float blockRange) {
        this.range = range;
        this.blockRange = blockRange;
    }
}