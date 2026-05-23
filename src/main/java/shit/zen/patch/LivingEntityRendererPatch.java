package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Slice;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.event.impl.RenderEntityEvent;
import shit.zen.event.impl.RotationAnimationEvent;

@Patch(LivingEntityRenderer.class)
public class LivingEntityRendererPatch {
    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderPre(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        if (!ZenClient.isReady()) return;
        RenderEntityEvent.Post pre = new RenderEntityEvent.Post(renderer, entity, poseStack, bufferSource, partialTick, packedLight);
        ZenClient.getInstance().getEventBus().call(pre);
        if (pre.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.TAIL)
    )
    public static void onRenderPost(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        if (!ZenClient.isReady()) return;
        RenderEntityEvent.Pre post = new RenderEntityEvent.Pre(renderer, entity, poseStack, bufferSource, partialTick, packedLight);
        ZenClient.getInstance().getEventBus().call(post);
    }

    // PatchApplier's wrap matcher is (owner ∥ name) && desc, so the filter accepts every
    // Mth.*(FFF)F call. There are four in render — see onRenderPitchLerp below for the full
    // map. We want the 2nd, the head yaw rotLerp.
    @WrapInvoke(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            target = "net/minecraft/util/Mth/rotLerp",
            targetDesc = "(FFF)F",
            slice = @Slice(startIndex = 2, endIndex = 2)
    )
    public static float onRenderHeadYawLerp(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            Invocation<Object, Float> original) throws Exception {
        float delta = (Float) original.args().get(0);
        float start = (Float) original.args().get(1);
        float end   = (Float) original.args().get(2);
        RotationAnimationEvent event = new RotationAnimationEvent(end, start, 0.0f, 0.0f);
        if (ZenClient.isReady() && entity == ClientBase.mc.player) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return Mth.rotLerp(delta, event.getLastYaw(), event.getYaw());
    }

    @WrapInvoke(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            target = "net/minecraft/util/Mth/lerp",
            targetDesc = "(FFF)F",
            slice = @Slice(startIndex = 4, endIndex = 4)
    )
    public static float onRenderPitchLerp(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            Invocation<Object, Float> original) throws Exception {
        float delta = (Float) original.args().get(0);
        float start = (Float) original.args().get(1);
        float end   = (Float) original.args().get(2);
        RotationAnimationEvent event = new RotationAnimationEvent(0.0f, 0.0f, end, start);
        if (ZenClient.isReady() && entity == ClientBase.mc.player) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return Mth.lerp(delta, event.getLastPitch(), event.getPitch());
    }
}
