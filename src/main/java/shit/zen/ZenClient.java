package shit.zen;

import asm.patchify.loader.PatchAgent;
import asm.patchify.loader.PatchRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import shit.zen.event.EventBus;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.gui.AgentWarningScreen;
import shit.zen.gui.BuildSourceScreen;
import shit.zen.gui.IntroAnimation;
import shit.zen.manager.CommandManager;
import shit.zen.manager.ConfigManager;
import shit.zen.manager.HudManager;
import shit.zen.manager.LagManager;
import shit.zen.manager.ModuleManager;
import shit.zen.manager.TargetManager;
import shit.zen.asm.Bootstrap;
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

    private static final String[] CLOUD_ASSET_NAMES = { "panel.png", "ptr.png", "lie.wav", "truth.wav" };

    private EventBus eventBus;
    private RotationHandler rotationHandler;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private HudManager hudManager;
    private LagManager lagManager;
    private TargetManager targetManager;
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
            this.configManager = new ConfigManager();
            this.extractCloudAssets();
            this.lagManager = new LagManager();
            this.targetManager = new TargetManager();
            this.eventBus.register(this.hudManager);
            this.eventBus.register(this.lagManager);
            this.eventBus.register(this.targetManager);
            this.eventBus.register(this);
            this.commandManager.initCommands();
            this.eventBus.register(new IntroAnimation());
            Bootstrap.init();
            registerPatches();
            boolean agentAttached = PatchAgent.getInstrumentation() != null;
            if (!agentAttached) {
                agentAttached = PatchAgent.trySelfAttach();
            }
            if (agentAttached) {
                PatchAgent.installPatchesAndRetransform();
            } else if (PatchAgent.isLaunchPluginActive()) {
                logger.info("PatchAgent is not attached; OpenZen is using the ModLauncher patch plugin instead.");
            } else {
                logger.warn("OpenZen patches are NOT active. Launch with `-javaagent:<OpenZen jar>` or use OpenZenLoader.exe.");
            }
            isReady = true;
            logger.info("{} v{} initialized.", CLIENT_NAME, VERSION);
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage(), throwable);
        }
    }

    private boolean moduleInit = false;
    private boolean buildNoticeShown = false;
    private boolean agentWarningShown = false;

    @EventTarget
    public void onTick(TickEvent e) {
        if (isReady() && !this.agentWarningShown && !PatchAgent.isPatchingAvailable()) {
            this.agentWarningShown = true;
            mc.setScreen(new AgentWarningScreen(mc.screen));
            return;
        }
        if (isReady() && !this.buildNoticeShown) {
            this.buildNoticeShown = true;
            mc.setScreen(new BuildSourceScreen(mc.screen));
        }
        if (isReady() && !moduleInit) {
            moduleInit = true;
            this.moduleManager.initModules();
            this.configManager.loadAll();
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

    public void shutdown() {
        isReady = false;
        if (this.configManager != null) {
            this.configManager.saveAll();
        }
    }

    private void extractCloudAssets() {
        File targetDir = ConfigManager.CONFIG_DIR;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            logger.warn("Failed to create config directory at {}", targetDir);
            return;
        }
        for (String name : CLOUD_ASSET_NAMES) {
            File outFile = new File(targetDir, name);
            if (outFile.exists()) continue;
            try (InputStream in = openCloudAsset(name)) {
                if (in == null) {
                    logger.warn("Cloud asset missing on classpath: {}", name);
                    continue;
                }
                try (OutputStream out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            } catch (IOException ioException) {
                logger.error("Failed to extract cloud asset {}", name, ioException);
            }
        }
    }

    private static InputStream openCloudAsset(String name) {
        String classpath = "/assets/zen/cloud_assets/" + name;
        InputStream is = ZenClient.class.getResourceAsStream(classpath);
        if (is != null) return is;
        String dir = System.getProperty("openzen.resources");
        if (dir != null) {
            File f = new File(dir, "assets/zen/cloud_assets/" + name);
            if (f.isFile()) {
                try {
                    return new java.io.FileInputStream(f);
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    public static void registerPatches() {
        PatchRegistry.registerBuiltins();
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
