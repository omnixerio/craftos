package dev.ultreon.craftos.session;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.ultreon.craftos.session.frames.BrowserFrame;
import dev.ultreon.craftos.session.gui.Button;
import dev.ultreon.craftos.session.gui.Container;
import dev.ultreon.craftos.session.mods.ModInfo;
import dev.ultreon.craftos.session.mods.ModUpdateChecker;
import jmccc.microsoft.MicrosoftAuthenticator;
import jmccc.microsoft.entity.MicrosoftSession;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.lwjgl.glfw.GLFW;
import org.to2mbn.jmccc.auth.AuthenticationException;
import org.to2mbn.jmccc.auth.yggdrasil.core.ProfileService;
import org.to2mbn.jmccc.auth.yggdrasil.core.PropertiesGameProfile;
import org.to2mbn.jmccc.auth.yggdrasil.core.texture.TextureType;
import org.to2mbn.jmccc.auth.yggdrasil.core.yggdrasil.YggdrasilProfileServiceBuilder;
import org.to2mbn.jmccc.launch.LaunchException;
import org.to2mbn.jmccc.launch.Launcher;
import org.to2mbn.jmccc.launch.LauncherBuilder;
import org.to2mbn.jmccc.launch.ProcessListener;
import org.to2mbn.jmccc.mcdownloader.MinecraftDownloader;
import org.to2mbn.jmccc.mcdownloader.MinecraftDownloaderBuilder;
import org.to2mbn.jmccc.mcdownloader.RemoteVersion;
import org.to2mbn.jmccc.mcdownloader.RemoteVersionList;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.CombinedDownloadCallback;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.DownloadCallback;
import org.to2mbn.jmccc.mcdownloader.download.tasks.DownloadTask;
import org.to2mbn.jmccc.option.LaunchOption;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.version.Version;
import org.to2mbn.jmccc.version.parsing.Versions;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class CraftSession extends Container implements ApplicationListener {
    private static final Color BACKGROUND = new Color(0x101010ff);
    public static final String LOADER_VERSION = "0.19.2";
    private static Lwjgl3Application app;
    private static CraftSession instance;
    private final Button back;
    private final MinecraftDownloader minecraftDownloader;
    private final FileHandle sessionFile = new FileHandle(Paths.get(System.getProperty("user.home"), ".minecraft", "msauth.json").toFile());
    private final Cursor cursor;
    private String launchVersion;
    private ModInfo waylandCraftInfo;
    private ModInfo fabricApiInfo;
    private MicrosoftAuthenticator auth;
    private MicrosoftSession session;

    private SpriteBatch batch;
    private ShapeDrawer shapes;
    private Texture white;
    private BitmapFont font;
    private boolean resizing;
    private Graphics.DisplayMode oldDisplayMode;
    private String status;
    private StatusType statusType;
    private final MinecraftDirectory directory = new MinecraftDirectory(Paths.get(System.getProperty("user.home"), ".minecraft").toFile());
    private final MinecraftDirectory alternateDirectory = new MinecraftDirectory(Paths.get(System.getProperty("user.home"), ".craftos").toFile());
    private final GlyphLayout layout = new GlyphLayout();
    private Texture skinTexture;
    private boolean filterSnapshots = true;
    private boolean filterHistoric = true;
    private final Button button = new Button(CraftSession.this, "Desktop", 140, 40, 100, 20);
    private String mcVersion;
    private final GridPoint2 size = new GridPoint2();
    private final GridPoint2 fitSize = new GridPoint2();
    private Texture backgroundTexture;
    private final Color color = new Color();
    private BrowserFrame browserFrame;

    private void run() {
        try {
            if (!sessionFile.parent().exists()) {
                sessionFile.parent().mkdirs();
            }

            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            if (sessionFile.exists()) {
                try {
                    session = json.fromJson(MicrosoftSession.class, sessionFile);
                    auth = MicrosoftAuthenticator.session(session, (microsoftVerification) -> {
                        session = null;
                        setStatus(microsoftVerification.message);
                        setStatusType(StatusType.WARNING);

                        if (browserFrame != null) {
                            setStatus(microsoftVerification.message + " (use browser)");
                            return;
                        }

                        try {
                            browserFrame = new BrowserFrame(microsoftVerification.verificationUri);
                        } catch (UnsupportedPlatformException | CefInitializationException | IOException |
                                 InterruptedException e) {
                            e.printStackTrace();
                        }

                        setStatus(microsoftVerification.message + " (use browser)");
                    });
                    String username = auth.auth().getUsername();
                    browserFrame.dispose();
                    setStatus("Logged in as " + username);
                    setStatusType(StatusType.NORMAL);

                    setupInfo();

                    minecraftDownloader.fetchRemoteVersionList(new VersionDownloadCallback());
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    setStatus("Failed to load session");
                    setStatusType(StatusType.ERROR);
                }
            }

            auth = MicrosoftAuthenticator.login(microsoftVerification -> {
                setStatus(microsoftVerification.message);
                setStatusType(StatusType.WARNING);

                if (browserFrame != null) {
                    setStatus(microsoftVerification.message + " (use browser)");
                    return;
                }

                try {
                    browserFrame = new BrowserFrame(microsoftVerification.verificationUri);
                } catch (UnsupportedPlatformException | CefInitializationException | IOException |
                         InterruptedException e) {
                    e.printStackTrace();
                }

                setStatus(microsoftVerification.message + " (use browser)");
            });
            session = auth.getSession();
            json.toJson(session, sessionFile);

            String username = auth.auth().getUsername();
            browserFrame.dispose();
            setStatus("Logged in as " + username);
            setStatusType(StatusType.NORMAL);

            setupInfo();

            minecraftDownloader.fetchRemoteVersionList(new VersionDownloadCallback());
        } catch (AuthenticationException e) {
            setStatus("Failed to login");
            setStatusType(StatusType.ERROR);
            e.printStackTrace();
        }
    }

    private void setupInfo() {
        try {
            ProfileService profileService = YggdrasilProfileServiceBuilder.buildDefault();
            PropertiesGameProfile gameProfile = profileService.getGameProfile(auth.auth().getUUID());
            Map<TextureType, org.to2mbn.jmccc.auth.yggdrasil.core.texture.Texture> textures = profileService.getTextures(gameProfile);
            byte[] bytes;
            try (InputStream inputStream = textures.get(TextureType.SKIN).openStream()) {
                bytes = inputStream.readAllBytes();
            }

            Gdx.app.postRunnable(() -> {
                Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
                this.skinTexture = new Texture(pixmap);
                this.skinTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                pixmap.dispose();
                setStatus("Logged in as " + gameProfile.getName());
                setStatusType(StatusType.NORMAL);
            });
        } catch (AuthenticationException | IOException e) {
            setStatus("Failed to fetch profile");
            setStatusType(StatusType.ERROR);
            e.printStackTrace();
        }
    }

    private void installMinecraft(String s, MinecraftDirectory alternateDirectory) {

    }

    private int executeCraftProcess(Path resolve, Path tempDirectory) throws IOException, InterruptedException {
        installMinecraft(mcVersion, alternateDirectory);

        String javaCommand = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(javaCommand, "-Djdk.lang.Process.launchMechanism=FORK", "-jar", resolve.toAbsolutePath().toString(),
                "client",
                "-dir",
                alternateDirectory.getAbsolutePath(),
                "-mcversion",
                mcVersion,
                "-loader",
                LOADER_VERSION
        );
        builder.directory(tempDirectory.toFile());
        builder.inheritIO();

        Process exec = builder.start();

        int i = exec.waitFor();
        return i;
    }

    private void launchVersion(String s, Button button) {
        Launcher build = LauncherBuilder.create()
                .nativeFastCheck(true)
                .build();

        try {
            String absolutePath = alternateDirectory.getAbsolutePath();
            try (InputStream inputStream = URI.create(waylandCraftInfo.downloadUrl()).toURL().openStream()) {
                if (inputStream != null) {
                    Path modsDir = Paths.get(absolutePath, "mods");
                    if (!Files.exists(modsDir))
                        Files.createDirectories(modsDir);

                    Path path = Paths.get(absolutePath, "mods", "waylandcraft.jar");
                    if (Files.exists(path))
                        Files.delete(path);
                    Files.copy(inputStream, path);
                }
            }
            try (InputStream inputStream = URI.create(fabricApiInfo.downloadUrl()).toURL().openStream()) {
                if (inputStream != null) {
                    Path modsDir = Paths.get(absolutePath, "mods");
                    if (!Files.exists(modsDir))
                        Files.createDirectories(modsDir);

                    Path path = Paths.get(absolutePath, "mods", "fabric-api.jar");
                    if (Files.exists(path))
                        Files.delete(path);
                    Files.copy(inputStream, path);
                }
            }

            Version version;
            try {
                version = Versions.resolveVersion(alternateDirectory, s);
            } catch (IOException e) {
                version = null;
            }
            if (version != null) {
                version.getJvmArgs().removeIf(arg -> arg.startsWith("--sun-misc-unsafe-memory-access"));
                build.launch(new LaunchOption(version, auth, alternateDirectory), new ProcessListener() {
                    @Override
                    public void onLog(String log) {
                        setStatus(log);
                        setStatusType(StatusType.NORMAL);
                    }

                    @Override
                    public void onErrorLog(String log) {
                        setStatus(log);
                        setStatusType(StatusType.WARNING);
                    }

                    @Override
                    public void onExit(int i) {
                        setStatus("Exited with code " + i);
                        if (i == 0) {
                            setStatusType(StatusType.NORMAL);
                        } else {
                            setStatusType(StatusType.ERROR);
                        }
                        button.enable();
                        if (i != 0) {
                            return;
                        }
                    }
                });
            } else {
                build.launch(new LaunchOption(s, auth, alternateDirectory), new ProcessListener() {
                    @Override
                    public void onLog(String log) {
                        setStatus(log);
                        setStatusType(StatusType.NORMAL);
                    }

                    @Override
                    public void onErrorLog(String log) {
                        setStatus(log);
                        setStatusType(StatusType.WARNING);
                    }

                    @Override
                    public void onExit(int i) {
                        setStatus("Exited with code " + i);
                        if (i == 0) {
                            setStatusType(StatusType.NORMAL);
                        } else {
                            setStatusType(StatusType.ERROR);
                        }
                        button.enable();
                        if (i != 0) {
                            return;
                        }
                    }
                });
            }
        } catch (IOException e) {
            setStatus("Failed to launch version");
            setStatusType(StatusType.ERROR);
            e.printStackTrace();
        } catch (LaunchException e) {
            setStatus("Failed to launch version");
            setStatusType(StatusType.ERROR);
            e.printStackTrace();
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        System.out.println(status);
    }

    public StatusType getStatusType() {
        return statusType;
    }

    public void setStatusType(StatusType statusType) {
        this.statusType = statusType;
    }

    public enum StatusType {
        NORMAL,
        WARNING,
        ERROR
    }

    public CraftSession() {
        super(null, 0, 0, 1, 1);

        this.back = addChild(new Button(this, "Log Out Session", 20, height - 40, 100, 20));
        this.back.setCallback(() -> {
            setStatus("Exiting...");
            setStatusType(StatusType.NORMAL);
            app.exit();
            System.exit(0);
        });

        this.width = Gdx.graphics.getWidth() / 2;
        this.height = Gdx.graphics.getHeight() / 2;

        minecraftDownloader = MinecraftDownloaderBuilder.buildDefault();

        try {
            waylandCraftInfo = ModUpdateChecker.checkLatestVersion("waylandcraft");
            mcVersion = waylandCraftInfo.gameVersion();
            launchVersion = "fabric-loader-" + LOADER_VERSION + "-" + mcVersion;
            fabricApiInfo = ModUpdateChecker.checkLatestVersion("fabric-api", mcVersion);
        } catch (Exception e) {
            waylandCraftInfo = null;
            fabricApiInfo = null;
        }

        CompletableFuture.runAsync(this::run);

        Gdx.input.setInputProcessor(new CraftOSInput());
        new Texture(Gdx.files.internal("cursors/default.png"));
        Pixmap pixmap = new Pixmap(Gdx.files.internal("cursors/default.png"));
        cursor = Gdx.graphics.newCursor(pixmap, 0, 0);
        pixmap.dispose();
        Gdx.graphics.setCursor(cursor);

        button.disable();
        addChild(button);
    }

    public static CraftSession get() {
        return instance;
    }

    @Override
    public void create() {
        instance = this;

        app = (Lwjgl3Application) Gdx.app;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Minecraftia-Regular.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 8;
        parameter.kerning = false;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.genMipMaps = false;
        parameter.borderWidth = 0;
        this.font = generator.generateFont(parameter);
        generator.dispose();

        this.batch = new SpriteBatch();
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();

        this.white = new Texture(pixmap);
        pixmap.dispose();

        shapes = new ShapeDrawer(batch, new TextureRegion(white));
        backgroundTexture = new Texture("img.png");
        Gdx.graphics.setCursor(cursor);
    }

    @Override
    public void resize(int width, int height) {
        if (resizing) {
            resizing = false;
            return;
        }

        batch.setProjectionMatrix(batch.getProjectionMatrix().setToOrtho2D(0, 0, width / 2f, height / 2f));
        this.width = (int) (width / 2f);
        this.height = (int) (height / 2f);

        this.back.setBounds(20, 40, 100, 20);
    }

    @Override
    public void render() {
        Graphics.DisplayMode displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode();
        if (oldDisplayMode == null || !oldDisplayMode.equals(displayMode)) {
            oldDisplayMode = displayMode;
            Lwjgl3Graphics graphics = (Lwjgl3Graphics) Gdx.graphics;
            this.resizing = true;
            graphics.setFullscreenMode(displayMode);
            resize(displayMode.width, displayMode.height);
        }


        batch.begin();

        ScreenUtils.clear(BACKGROUND);

        fill(size.set(width, height), fitSize, backgroundTexture);
        renderBackgroundImage();

        shapes.filledRectangle(0, 0, width, 100, color.set(0, 0, 0, 0.5f));
        shapes.filledRectangle(0, height - 60, width, 60, color.set(0, 0, 0, 0.5f));

        shapes.filledRectangle(0, height - 58, width, 2, color.set(1, 1, 1, 0.3f));
        shapes.filledRectangle(0, 96, width, 2, color.set(1, 1, 1, 0.3f));

        render(batch);
        batch.end();
    }

    private void renderBackgroundImage() {
        int x = (size.x - fitSize.x) / 2;
        int y = (size.y - fitSize.y) / 2;
        batch.draw(backgroundTexture, x, y, fitSize.x, fitSize.y);
    }

    /**
     * Computes the fitted size of a background texture to fill the screen area.
     *
     * @param size    The screen dimensions.
     * @param fitSize Output vector receiving the fitted texture dimensions.
     * @param texture The background texture to fit.
     */
    public static void fill(GridPoint2 size, GridPoint2 fitSize, Texture texture) {
        float scaleX = (float) size.x / texture.getWidth();
        float scaleY = (float) size.y / texture.getHeight();
        float scale = Math.max(scaleX, scaleY);

        fitSize.x = (int) (texture.getWidth() * scale);
        fitSize.y = (int) (texture.getHeight() * scale);
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void dispose() {
        batch.dispose();
        white.dispose();
        font.dispose();
    }

    public static Lwjgl3Application getApp() {
        return app;
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public Texture getWhite() {
        return white;
    }

    public BitmapFont getFont() {
        return font;
    }

    public boolean isResizing() {
        return resizing;
    }

    public Graphics.DisplayMode getOldDisplayMode() {
        return oldDisplayMode;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    @Override
    public void render(Batch batch) {
        super.render(batch);

        font.setColor(color.set(1, 1, 1, 0.5f));
        font.draw(batch, "CraftOS Launcher 2026.06.30", 20, 20);

        if (skinTexture != null) {
            String str = "Logged in as " + auth.auth().getUsername();
            font.setColor(Color.WHITE);
            font.draw(batch, str, skinTexture != null ? 46 : 20, height - layout.height - 26);
            layout.setText(font, str);

            fill(batch, 21, height - 23 - 16, 18, 18, Color.WHITE);
            batch.setColor(Color.WHITE);
            batch.draw(skinTexture, 22, height - 22 - 16, 16, 16, 8, 8, 8, 8, false, false);
        }

        if (getStatus() != null) {
            layout.setText(font, getStatus());
            font.setColor(switch (getStatusType()) {
                case NORMAL -> Color.WHITE;
                case WARNING -> new Color(1.0f, 0.6f, 0.2f, 1.0f);
                case ERROR -> new Color(1.0f, 0.3f, 0.3f, 1.0f);
                case null -> Color.WHITE;
            });
            batch.setColor(1, 1, 1, 1);
            font.draw(batch, getStatus(), width - layout.width - 20, height - layout.height - 26);
        }

        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));

        if (dateTime != null) {
            layout.setText(font, dateTime);
            font.setColor(color.set(1, 1, 1, 0.5f));
            batch.setColor(1, 1, 1, 1);
            font.draw(batch, dateTime, width - layout.width - 20, 20);
        }
    }

    public static void main(String[] args) {
        try {
            Lwjgl3Application ignored = new Lwjgl3Application(new ApplicationListener() {
                private CraftSession craftDM;

                @Override
                public void create() {
                    craftDM = new CraftSession();
                    craftDM.create();
                }

                @Override
                public void resize(int width, int height) {
                    craftDM.resize(width, height);
                }

                @Override
                public void render() {
                    craftDM.render();
                }

                @Override
                public void pause() {
                    craftDM.pause();
                }

                @Override
                public void resume() {
                    craftDM.resume();
                }

                @Override
                public void dispose() {
                    craftDM.dispose();
                }
            }, createConfig());
        } catch (Exception e) {
            e.printStackTrace();
            if (Objects.equals(System.getenv("LIBGL_ALWAYS_SOFTWARE"), "1")) {
                return;
            }
            System.err.println("Retrying with LIBGL_ALWAYS_SOFTWARE=1...");
            if (Boolean.getBoolean("construo")) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("/usr/bin/craft-session");
                    pb.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                    pb.inheritIO();
                    int exitCode = pb.start().waitFor();
                    System.exit(exitCode);
                } catch (IOException | InterruptedException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            } else {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            ProcessHandle.current().info().command().orElse("java"),
                            "-cp", System.getProperty("java.class.path"),
                            "dev.ultreon.craftos.session.CraftSession"
                    );
                    pb.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                    pb.inheritIO();
                    int exitCode = pb.start().waitFor();
                    System.exit(exitCode);
                } catch (IOException | InterruptedException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        }
        System.exit(0);
    }

    private static Lwjgl3ApplicationConfiguration createConfig() {
        final Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();

        configuration.setForegroundFPS(60);
        configuration.setIdleFPS(60);
        configuration.useVsync(true);
        configuration.setTitle("Craft Session");
        configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());

        GLFW.glfwWindowHintString(GLFW.GLFW_X11_CLASS_NAME, "dev.ultreon.CraftSession");

        return configuration;
    }

    private void addVersions() {
        Gdx.app.postRunnable(() -> {
            button.enable();
            button.setCallback(() -> {
                button.disable();

                CompletableFuture.runAsync(() -> {
                    if (alternateDirectory.getVersion(launchVersion).exists()) {
                        launchVersion(launchVersion, button);
                        return;
                    }

                    try {
                        AtomicBoolean failed = new AtomicBoolean(false);
                        setStatus("Installing desktop");
                        setStatusType(StatusType.NORMAL);
                        installVersion(mcVersion, alternateDirectory, () -> {
                            setStatus("Failed to install desktop: " + getStatus());
                            setStatusType(StatusType.ERROR);
                            failed.set(true);
                        }).thenRun(() -> {
                            if (failed.get()) {
                                return;
                            }

                            try (InputStream resourceAsStream = getClass().getResourceAsStream("/fabric-installer-1.1.0.jar")) {
                                Path tempDirectory = Files.createTempDirectory("craftos-");
                                if (resourceAsStream == null) {
                                    setStatus("Failed to install fabric: missing resource");
                                    setStatusType(StatusType.ERROR);
                                    return;
                                }
                                Path resolve = tempDirectory.resolve("fabric-installer-1.1.0.jar");
                                Files.copy(resourceAsStream, resolve);
                                setStatus("Installing fabric");
                                setStatusType(StatusType.NORMAL);
                                String absolutePath = alternateDirectory.getAbsolutePath();
                                Path modsDir = Paths.get(absolutePath, "mods");
                                if (!Files.exists(modsDir))
                                    Files.createDirectories(modsDir);
                                executeCraftProcess(resolve, tempDirectory);

                                Files.deleteIfExists(resolve);
                                Files.deleteIfExists(tempDirectory);

                                setStatus("Launching Desktop");
                                setStatusType(StatusType.NORMAL);
                                launchVersion(launchVersion, button);
                            } catch (InterruptedException e) {
                                setStatus("Failed to install fabric: interrupted");
                                setStatusType(StatusType.ERROR);
                                e.printStackTrace();
                            } catch (IOException e) {
                                setStatus("Failed to install fabric: IO exception");
                                setStatusType(StatusType.ERROR);
                                e.printStackTrace();
                            } catch (Exception e) {
                                setStatus("Failed to install fabric: unknown exception");
                                setStatusType(StatusType.ERROR);
                                e.printStackTrace();
                            }
                        }).exceptionally(throwable -> {
                            setStatus("Failed to install fabric: " + throwable.getMessage());
                            setStatusType(StatusType.ERROR);
                            throwable.printStackTrace();
                            return null;
                        });
                    } catch (Exception e) {
                        setStatus("Failed to install fabric: unknown exception");
                        setStatusType(StatusType.ERROR);
                        e.printStackTrace();
                    }
                });
            });
        });
    }

    private CompletableFuture<Void> installVersion(String version, MinecraftDirectory directory, Runnable onFail) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        MinecraftDownloader downloader = MinecraftDownloaderBuilder.buildDefault();
        downloader.downloadIncrementally(directory, version, new NothingCallback(version, () -> {
            future.completeExceptionally(new Exception(version));
            onFail.run();
        }, () -> {
            future.complete(null);
        }));
        return future;
    }

    private void installAndLaunchVersion(String version, Button button) {
        MinecraftDownloader downloader = MinecraftDownloaderBuilder.buildDefault();
        downloader.downloadIncrementally(directory, version, new LaunchCallback(button, version, version));
        button.setText("Downloading...");
    }

    private class VersionDownloadCallback implements CombinedDownloadCallback<RemoteVersionList> {
        @Override
        public <R> DownloadCallback<R> taskStart(DownloadTask<R> downloadTask) {
            setStatus("Downloading version list");
            setStatusType(StatusType.NORMAL);
            return new DownloadCallback<>() {
                @Override
                public void updateProgress(long l, long l1) {
                    setStatus("Downloading version list: " + (int) (l * 100 / l1) + "%");
                    setStatusType(StatusType.NORMAL);
                }

                @Override
                public void retry(Throwable throwable, int i, int i1) {

                }

                @Override
                public void done(R r) {
                    setStatus("Version list downloaded");
                    setStatusType(StatusType.NORMAL);
                }

                @Override
                public void failed(Throwable throwable) {
                    setStatus("Failed to download version list");
                    setStatusType(StatusType.ERROR);
                }

                @Override
                public void cancelled() {
                    setStatus("Version list download cancelled");
                    setStatusType(StatusType.WARNING);
                }
            };
        }

        @Override
        public void done(RemoteVersionList versionList) {
            Map<String, RemoteVersion> versions = versionList.getVersions();
            Collection<RemoteVersion> values = versions.values();
            addVersions();
        }

        @Override
        public void failed(Throwable throwable) {
            setStatus("Failed to download version list");
            setStatusType(StatusType.ERROR);
        }

        @Override
        public void cancelled() {
            setStatus("Version list download cancelled");
            setStatusType(StatusType.WARNING);
        }

    }

    private class LaunchCallback implements CombinedDownloadCallback<Version> {
        private final Button button;
        private final String str;
        private final String version;

        public LaunchCallback(Button button, String str, String version) {
            this.button = button;
            this.str = str;
            this.version = version;
        }

        @Override
        public void done(Version version) {
            setStatus("Version " + version + " downloaded");
            setStatusType(StatusType.NORMAL);

            button.setText(str);

            version.getJvmArgs().removeIf(arg -> arg.startsWith("--sun-misc-unsafe-memory-access"));

            Launcher build = LauncherBuilder.create()
                    .nativeFastCheck(true)
                    .build();

            try {
                build.launch(new LaunchOption(version, auth, directory), new ProcessListener() {
                    @Override
                    public void onLog(String log) {
                        setStatus(log);
                        setStatusType(StatusType.NORMAL);
                    }

                    @Override
                    public void onErrorLog(String log) {
                        setStatus("Minecraft error log:\n" + log);
                        setStatusType(StatusType.ERROR);
                    }

                    @Override
                    public void onExit(int code) {
                        if (code != 0) {
                            setStatus("Minecraft exited with code " + code);
                            setStatusType(StatusType.ERROR);
                        }
                    }
                });
            } catch (LaunchException e) {
                e.printStackTrace();
                setStatus("Failed to generate launch arguments for version " + version);
                setStatusType(StatusType.ERROR);
            }
        }

        @Override
        public void failed(Throwable throwable) {
            throwable.printStackTrace();
            setStatus("Failed to download version " + version);
            setStatusType(StatusType.ERROR);

            button.setText(str);
        }

        @Override
        public void cancelled() {
            setStatus("Version " + version + " download cancelled");
            setStatusType(StatusType.WARNING);

            button.setText(str);
        }

        @Override
        public <R> DownloadCallback<R> taskStart(DownloadTask<R> downloadTask) {
            return new DownloadCallback<>() {
                @Override
                public void updateProgress(long l, long l1) {

                }

                @Override
                public void retry(Throwable throwable, int i, int i1) {
                    throwable.printStackTrace();
                }

                @Override
                public void done(R r) {

                }

                @Override
                public void failed(Throwable throwable) {
                    throwable.printStackTrace();
                }

                @Override
                public void cancelled() {

                }
            };
        }
    }

    private class NothingCallback implements CombinedDownloadCallback<Version> {
        private final String version;
        private final Runnable onFail;
        private final Runnable onDone;

        public NothingCallback(String version, Runnable onFail, Runnable onDone) {
            this.version = version;
            this.onFail = onFail;
            this.onDone = onDone;
        }

        @Override
        public void done(Version version) {
            setStatus("Version " + version + " downloaded");
            setStatusType(StatusType.NORMAL);

            onDone.run();
        }

        @Override
        public void failed(Throwable throwable) {
            throwable.printStackTrace();
            setStatus("Failed to download version " + version);
            setStatusType(StatusType.ERROR);

            onFail.run();
        }

        @Override
        public void cancelled() {
            setStatus("Version " + version + " download cancelled");
            setStatusType(StatusType.WARNING);

            onFail.run();
        }

        @Override
        public <R> DownloadCallback<R> taskStart(DownloadTask<R> downloadTask) {
            return new DownloadCallback<>() {
                @Override
                public void updateProgress(long l, long l1) {

                }

                @Override
                public void retry(Throwable throwable, int i, int i1) {
                    throwable.printStackTrace();
                }

                @Override
                public void done(R r) {

                }

                @Override
                public void failed(Throwable throwable) {
                    throwable.printStackTrace();
                }

                @Override
                public void cancelled() {

                }
            };
        }
    }

    private class CraftOSInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            CraftSession.this.keyDown(keycode);
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            CraftSession.this.keyUp(keycode);
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            CraftSession.this.keyTyped(character);
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            CraftSession.this.mousePressed();
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            CraftSession.this.mouseReleased();
            CraftSession.this.mouseClicked(button);
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            CraftSession.this.mouseMoved(screenX / 2, height - screenY / 2);
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            CraftSession.this.mouseScrolled(amountY);
            return true;
        }
    }
}
