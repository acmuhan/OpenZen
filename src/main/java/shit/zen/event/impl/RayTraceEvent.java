package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import net.minecraft.world.entity.Entity;
import shit.zen.event.EventMarker;

import java.util.Objects;

public class RayTraceEvent
implements EventMarker {
    @Getter @Setter
    public Entity entity;
    @Getter @Setter
    public float range;
    @Getter @Setter
    public float blockRange;

    @Generated
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof RayTraceEvent otherEvent)) {
            return false;
        }
        if (!otherEvent.canEqual(this)) {
            return false;
        }
        if (Float.compare(this.getRange(), otherEvent.getRange()) != 0) {
            return false;
        }
        if (Float.compare(this.getBlockRange(), otherEvent.getBlockRange()) != 0) {
            return false;
        }
        Entity thisEntity = this.getEntity();
        Entity otherEntity = otherEvent.getEntity();
        return !(!Objects.equals(thisEntity, otherEntity));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof RayTraceEvent;
    }

    @Generated
    public int hashCode() {
        int prime = 59;
        int result = 1;
        result = result * 59 + Float.floatToIntBits(this.getRange());
        result = result * 59 + Float.floatToIntBits(this.getBlockRange());
        Entity entity = this.getEntity();
        result = result * 59 + (entity == null ? 43 : entity.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        float blockRange = this.getBlockRange();
        float range = this.getRange();
        String entityStr = String.valueOf(this.getEntity());
        return "RayTraceEvent(entity=" + entityStr + ", yaw=" + range + ", pitch=" + blockRange + ")";
    }

    @Generated
    public RayTraceEvent(Entity entity, float range, float blockRange) {
        this.entity = entity;
        this.range = range;
        this.blockRange = blockRange;
    }
}