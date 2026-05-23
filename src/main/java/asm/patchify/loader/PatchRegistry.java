package asm.patchify.loader;

import asm.patchify.annotation.Patch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.patch.ChatScreenPatch;
import shit.zen.patch.ClientLevelPatch;
import shit.zen.patch.ConnectionPatch;
import shit.zen.patch.EntityPatch;
import shit.zen.patch.EntityRendererPatch;
import shit.zen.patch.FriendlyByteBufPatch;
import shit.zen.patch.GameRendererPatch;
import shit.zen.patch.HumanoidModelPatch;
import shit.zen.patch.ItemInHandLayerPatch;
import shit.zen.patch.ItemInHandRendererPatch;
import shit.zen.patch.ItemPatch;
import shit.zen.patch.KeyboardHandlerPatch;
import shit.zen.patch.KeyboardInputPatch;
import shit.zen.patch.LevelRendererPatch;
import shit.zen.patch.LivingEntityPatch;
import shit.zen.patch.LivingEntityRendererPatch;
import shit.zen.patch.LocalPlayerPatch;
import shit.zen.patch.MinecraftPatch;
import shit.zen.patch.PacketUtilsPatch;
import shit.zen.patch.PlayerPatch;
import shit.zen.patch.PlayerTabOverlayPatch;

/**
 * Runtime registry for {@link Patch}-annotated classes.
 *
 * <p>The original obfuscated client relied on a custom class-load transformer to wire up
 * {@code @Inject} / {@code @Overwrite} / {@code @WrapInvoke} handlers. The transformer is
 * provided by the loader and is not part of this restored source; this registry exposes the
 * patch list so a coremod / launch plugin can drive the transformation, and provides a
 * lightweight no-op fallback when no transformer is installed.</p>
 */
public final class PatchRegistry {
    private static final Logger LOGGER = LogManager.getLogger("PatchRegistry");
    private static final List<Class<?>> PATCHES = new ArrayList<>();

    private PatchRegistry() {
    }

    public static void register(Class<?> patchClass) {
        Patch annotation = patchClass.getAnnotation(Patch.class);
        if (annotation == null) {
            throw new IllegalArgumentException(patchClass.getName() + " is missing @Patch");
        }
        synchronized (PATCHES) {
            if (!PATCHES.contains(patchClass)) {
                PATCHES.add(patchClass);
                LOGGER.debug("Registered patch {} -> {}", patchClass.getName(), annotation.value().getName());
            }
        }
    }

    public static void registerBuiltins() {
        register(MinecraftPatch.class);
        register(LocalPlayerPatch.class);
        register(LivingEntityPatch.class);
        register(EntityPatch.class);
        register(PlayerPatch.class);
        register(ClientLevelPatch.class);
        register(ConnectionPatch.class);
        register(PacketUtilsPatch.class);
        register(KeyboardHandlerPatch.class);
        register(KeyboardInputPatch.class);
        register(ChatScreenPatch.class);
        register(EntityRendererPatch.class);
        register(LevelRendererPatch.class);
        register(GameRendererPatch.class);
        register(ItemInHandRendererPatch.class);
        register(ItemInHandLayerPatch.class);
        register(HumanoidModelPatch.class);
        register(LivingEntityRendererPatch.class);
        register(ItemPatch.class);
        register(PlayerTabOverlayPatch.class);
        register(FriendlyByteBufPatch.class);
    }

    public static List<Class<?>> getPatches() {
        synchronized (PATCHES) {
            return Collections.unmodifiableList(new ArrayList<>(PATCHES));
        }
    }
}
