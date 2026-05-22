package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import shit.zen.event.EventMarker;

public class JumpMarkerEvent
implements EventMarker {
    @Getter @Setter
    private float jumpHeight;
    private static final String TO_STRING_PREFIX = "JumpEvent(yaw=";

    @Generated
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof JumpMarkerEvent otherEvent)) {
            return false;
        }
        if (!otherEvent.canEqual(this)) {
            return false;
        }
        return Float.compare(this.getJumpHeight(), otherEvent.getJumpHeight()) == 0;
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof JumpMarkerEvent;
    }

    @Generated
    public int hashCode() {
        int prime = 59;
        int result = 1;
        result = result * 59 + Float.floatToIntBits(this.getJumpHeight());
        return result;
    }

    @Generated
    public String toString() {
        return TO_STRING_PREFIX + this.getJumpHeight() + ")";
    }

    @Generated
    public JumpMarkerEvent(float jumpHeight) {
        this.jumpHeight = jumpHeight;
    }
}