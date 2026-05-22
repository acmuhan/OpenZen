package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import shit.zen.event.EventMarker;

public class HeadTurnEvent
implements EventMarker {
    @Getter @Setter
    private float yaw;
    @Getter @Setter
    private float pitch;

    @Generated
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof HeadTurnEvent otherEvent)) {
            return false;
        }
        if (!otherEvent.canEqual(this)) {
            return false;
        }
        if (Float.compare(this.getYaw(), otherEvent.getYaw()) != 0) {
            return false;
        }
        return Float.compare(this.getPitch(), otherEvent.getPitch()) == 0;
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof HeadTurnEvent;
    }

    @Generated
    public int hashCode() {
        int prime = 59;
        int result = 1;
        result = result * 59 + Float.floatToIntBits(this.getYaw());
        result = result * 59 + Float.floatToIntBits(this.getPitch());
        return result;
    }

    @Generated
    public String toString() {
        return "HeadTurnEvent(yaw=" + this.getYaw() + ", lastYaw=" + this.getPitch() + ")";
    }

    @Generated
    public HeadTurnEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}