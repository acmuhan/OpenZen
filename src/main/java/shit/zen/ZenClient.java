package shit.zen;

import asm.patchify.loader.PatchAgent;
import asm.patchify.loader.PatchRegistry;
import java.io.File;
import java.lang.reflect.Field;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import shit.zen.event.EventBus;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.gui.BuildSourceScreen;
import shit.zen.manager.CommandManager;
import shit.zen.manager.ConfigManager;
import shit.zen.manager.HudManager;
import shit.zen.manager.LagManager;
import shit.zen.manager.ModuleManager;
import shit.zen.manager.TargetManager;
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
import shit.zen.patch.TimerPatch;
import shit.zen.utils.misc.Encryption;
import shit.zen.utils.rotation.RotationHandler;

@Mod(value = "hey")
@Getter
@Setter
public class ZenClient extends ClientBase {
    @Getter
    public static ZenClient instance;
    public static final String CLIENT_NAME = "Zen";
    public static final String VERSION = "1.0";
    public static float serverTickRate;
    public static boolean isReady;
    public static boolean isMCPMapped;
    public static String configDir = System.getProperty("user.home") + File.separator + ".zen";
    public static String username = "";

    private EventBus eventBus;
    private RotationHandler rotationHandler;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private HudManager hudManager;
    private LagManager lagManager;
    private TargetManager targetManager;
    private Encryption encryption;
    private int reconnectAttempts;

    public ZenClient() {
        if (instance == null) {
            instance = this;
            this.init();
        }
    }

    private void init() {
        try {
            username = System.getProperty("user.name", "Player");
            File dir = new File(configDir);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Failed to create config directory at {}", configDir);
            }
            mc = getMcInstance();
            this.eventBus = new EventBus();
            this.rotationHandler = new RotationHandler();
            this.eventBus.register(this.rotationHandler);
            this.moduleManager = new ModuleManager();
            this.hudManager = new HudManager();
            this.commandManager = new CommandManager();
            this.commandManager.initCommands();
            this.configManager = new ConfigManager();
            this.configManager.loadAll();
            this.lagManager = new LagManager();
            this.eventBus.register(this.lagManager);
            this.targetManager = new TargetManager();
            this.eventBus.register(this.targetManager);
            this.eventBus.register(this);
            this.encryption = new Encryption(Encryption.Algorithm.AES);
            registerPatches();
            if (PatchAgent.getInstrumentation() != null) {
                PatchAgent.installPatchesAndRetransform();
            } else {
                logger.warn("PatchAgent not attached. Launch with `./gradlew runClient0` so the agent jvmArg is set.");
            }
            isReady = true;
            logger.info("{} v{} initialized.", CLIENT_NAME, VERSION);
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage(), throwable);
        }
    }

    private boolean moduleInit = false;
    private boolean buildNoticeShown = false;

    @EventTarget
    public void onTick(TickEvent e) {
        if (isReady() && !this.buildNoticeShown) {
            this.buildNoticeShown = true;
            mc.setScreen(new BuildSourceScreen(mc.screen));
        }
        if (isReady() && !moduleInit) {
            moduleInit = true;
            this.moduleManager.initModules();
        }
    }

    public static boolean isReady() {
        return instance != null
                && ZenClient.instance.eventBus != null
                && isReady
                && mc != null
                && mc.player != null
                && !username.isEmpty()
                && mc.player.tickCount > 5;
    }

    public static boolean isOwner(String username) {
        return false;
    }

    public void disconnectFromServer() {
        isReady = false;
        if (this.configManager != null) {
            this.configManager.saveAll();
        }
    }

    private static void registerPatches() {
        PatchRegistry.register(MinecraftPatch.class);
        PatchRegistry.register(LocalPlayerPatch.class);
        PatchRegistry.register(LivingEntityPatch.class);
        PatchRegistry.register(EntityPatch.class);
        PatchRegistry.register(PlayerPatch.class);
        PatchRegistry.register(ClientLevelPatch.class);
        PatchRegistry.register(ConnectionPatch.class);
        PatchRegistry.register(PacketUtilsPatch.class);
        PatchRegistry.register(KeyboardHandlerPatch.class);
        PatchRegistry.register(KeyboardInputPatch.class);
        PatchRegistry.register(ChatScreenPatch.class);
        PatchRegistry.register(EntityRendererPatch.class);
        PatchRegistry.register(LevelRendererPatch.class);
        PatchRegistry.register(GameRendererPatch.class);
        PatchRegistry.register(ItemInHandRendererPatch.class);
        PatchRegistry.register(ItemInHandLayerPatch.class);
        PatchRegistry.register(HumanoidModelPatch.class);
        PatchRegistry.register(LivingEntityRendererPatch.class);
        PatchRegistry.register(ItemPatch.class);
        PatchRegistry.register(PlayerTabOverlayPatch.class);
        PatchRegistry.register(FriendlyByteBufPatch.class);
    }

    public static Minecraft getMcInstance() {
        Minecraft minecraft = null;
        try {
            Class<?> clazz = Minecraft.class;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != clazz) continue;
                field.setAccessible(true);
                minecraft = (Minecraft) field.get(null);
                field.setAccessible(false);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return minecraft != null ? minecraft : Minecraft.getInstance();
    }

}
