package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import shit.zen.event.EventMarker;

public class FallFlyingEvent
implements EventMarker {
    @Getter @Setter
    private float speed;
    private static final String TO_STRING_PREFIX = "FallFlyingEvent(pitch=";

    @Generated
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof FallFlyingEvent otherEvent)) {
            return false;
        }
        if (!otherEvent.canEqual(this)) {
            return false;
        }
        return Float.compare(this.getSpeed(), otherEvent.getSpeed()) == 0;
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof FallFlyingEvent;
    }

    @Generated
    public int hashCode() {
        int prime = 59;
        int result = 1;
        result = result * 59 + Float.floatToIntBits(this.getSpeed());
        return result;
    }

    @Generated
    public String toString() {
        return TO_STRING_PREFIX + this.getSpeed() + ")";
    }

    @Generated
    public FallFlyingEvent(float speed) {
        this.speed = speed;
    }
}