package dev.ultreon.craftos.dm;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.ultreon.craftos.dm.gui.Button;
import dev.ultreon.craftos.dm.gui.Container;
import dev.ultreon.craftos.dm.gui.ScrollContainer;
import jmccc.microsoft.MicrosoftAuthenticator;
import jmccc.microsoft.entity.MicrosoftSession;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class CraftDM extends Container implements ApplicationListener {
    private static final Color BACKGROUND = new Color(0x101010ff);
    public static final String MC_VERSION = "1.21.8";
    public static final String LOADER_VERSION = "0.17.2";
    public static final String LAUNCH_VERSION = "fabric-loader-" + LOADER_VERSION + "-" + MC_VERSION;
    private static Lwjgl3Application app;
    private static CraftDM instance;
    private final Button back;
    private final List<Button> versionButtons;
    private final MinecraftDownloader minecraftDownloader;
    private final FileHandle sessionFile = new FileHandle(Paths.get(System.getProperty("user.home"), ".minecraft", "msauth.json").toFile());
    private final Cursor cursor;
    private final Button snapshotFilter;
    private final Button historicFilter;
    private MicrosoftAuthenticator auth;
    private MicrosoftSession session;

    private SpriteBatch batch;
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
    private final ScrollContainer versionContainer = new ScrollContainer(this, 20, 60, 100, height - 120);
    private final List<RemoteVersion> allVersions = new ArrayList<>();
    private final List<RemoteVersion> versions = new ArrayList<>();
    private boolean filterSnapshots = true;
    private boolean filterHistoric = true;
    private final Button button = new Button(CraftDM.this, "Desktop OS", 140, 40, 100, 20);

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
                        status = microsoftVerification.message;
                        statusType = StatusType.WARNING;
                    });
                    String username = auth.auth().getUsername();
                    status = "Logged in as " + username;
                    statusType = StatusType.NORMAL;

                    setupInfo();

                    minecraftDownloader.fetchRemoteVersionList(new VersionDownloadCallback());
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    status = "Failed to load session";
                    statusType = StatusType.ERROR;
                }
            }

            auth = MicrosoftAuthenticator.login(microsoftVerification -> {
                status = microsoftVerification.message;
                statusType = StatusType.WARNING;
            });
            session = auth.getSession();
            json.toJson(session, sessionFile);

            String username = auth.auth().getUsername();
            status = "Logged in as " + username;
            statusType = StatusType.NORMAL;

            setupInfo();

            minecraftDownloader.fetchRemoteVersionList(new VersionDownloadCallback());
        } catch (AuthenticationException e) {
            status = "Failed to login";
            statusType = StatusType.ERROR;
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
                status = "Logged in as " + gameProfile.getName();
                statusType = StatusType.NORMAL;
            });
        } catch (AuthenticationException | IOException e) {
            status = "Failed to fetch profile";
            statusType = StatusType.ERROR;
            e.printStackTrace();
        }
    }

    private void installMinecraft(String s, MinecraftDirectory alternateDirectory) {

    }

    private int executeCraftProcess(Path resolve, Path tempDirectory) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("java", "-jar", resolve.toAbsolutePath().toString(),
                "client",
                "-dir",
                alternateDirectory.getAbsolutePath(),
                "-mcversion",
                MC_VERSION,
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
            try (InputStream inputStream = getClass().getResourceAsStream("/craftmod.jar")) {
                if (inputStream != null) {
                    Path modsDir = Paths.get(absolutePath, "mods");
                    if (!Files.exists(modsDir))
                        Files.createDirectories(modsDir);
                    Path path = Paths.get(absolutePath, "mods", "craftmod.jar");
                    if (Files.exists(path))
                        Files.delete(path);
                    Files.copy(inputStream, path);
                }
            }
            build.launch(new LaunchOption(s, auth, alternateDirectory), new ProcessListener() {
                @Override
                public void onLog(String log) {
                    status = log;
                    statusType = StatusType.NORMAL;
                }

                @Override
                public void onErrorLog(String log) {
                    status = log;
                    statusType = StatusType.WARNING;
                }

                @Override
                public void onExit(int i) {
                    status = "Exited with code " + i;
                    if (i == 0) {
                        statusType = StatusType.NORMAL;
                    } else {
                        statusType = StatusType.ERROR;
                    }
                    button.enable();
                    if (i != 0) {
                        return;
                    }
                }
            });
        } catch (IOException e) {
            status = "Failed to launch version";
            statusType = StatusType.ERROR;
            e.printStackTrace();
        } catch (LaunchException e) {
            status = "Failed to launch version";
            statusType = StatusType.ERROR;
            e.printStackTrace();
        }
    }

    public enum StatusType {
        NORMAL,
        WARNING,
        ERROR
    }

    public CraftDM() {
        super(null, 0, 0, 1, 1);

        addChild(versionContainer);

        this.back = addChild(new Button(this, "Exit", 20, height - 40, 100, 20));
        this.back.setCallback(() -> {
            status = "Exiting...";
            statusType = StatusType.NORMAL;
            app.exit();
            System.exit(0);
        });

        this.snapshotFilter = addChild(new Button(this, "Snapshots: Hide", 260, height - 40, 100, 20));
        this.snapshotFilter.setCallback(() -> {
            this.filterSnapshots = !this.filterSnapshots;
            this.snapshotFilter.setText(this.filterSnapshots ? "Snapshots: Hide" : "Snapshots: Show");
            this.addVersions();
        });

        this.historicFilter = addChild(new Button(this, "Historic: Hide", 380, height - 40, 100, 20));
        this.historicFilter.setCallback(() -> {
            this.filterHistoric = !this.filterHistoric;
            this.historicFilter.setText(this.filterHistoric ? "Historic: Hide" : "Historic: Show");
            this.addVersions();
        });

        this.width = Gdx.graphics.getWidth() / 2;
        this.height = Gdx.graphics.getHeight() / 2;

        this.versionButtons = new ArrayList<>();
        minecraftDownloader = MinecraftDownloaderBuilder.buildDefault();

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

    public static CraftDM get() {
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
        this.snapshotFilter.setBounds(260, 40, 100, 20);
        this.historicFilter.setBounds(380, 40, 100, 20);
        this.versionContainer.setBounds(20, 80, 100, height / 2 - 120);
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
        render(batch);
        batch.end();
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
        ScreenUtils.clear(BACKGROUND);

        font.setColor(Color.DARK_GRAY);
        font.draw(batch, "CraftOS Launcher 2025.08.27", 20, 20);

        if (skinTexture != null) {
            String str = "Logged in as " + auth.auth().getUsername();
            font.setColor(Color.WHITE);
            font.draw(batch, str, skinTexture != null ? 46 : 20, height - layout.height - 20);
            layout.setText(font, str);

            fill(batch, 21, height - 23 - 10, 18, 18, Color.WHITE);
            batch.setColor(Color.WHITE);
            batch.draw(skinTexture, 22, height - 22 - 10, 16, 16, 8, 8, 8, 8, false, false);
        }

        if (status != null) {
            layout.setText(font, status);
            font.setColor(switch (statusType) {
                case NORMAL -> Color.WHITE;
                case WARNING -> new Color(1.0f, 0.6f, 0.2f, 1.0f);
                case ERROR -> new Color(1.0f, 0.3f, 0.3f, 1.0f);
                case null -> Color.WHITE;
            });
            font.draw(batch, status, width - layout.width - 20, height - layout.height - 20);
        }

        super.render(batch);
    }

    public static void main(String[] args) {
        Lwjgl3Application ignored = new Lwjgl3Application(new ApplicationListener() {
            private CraftDM craftDM;

            @Override
            public void create() {
                craftDM = new CraftDM();
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
        System.exit(0);
    }

    private static Lwjgl3ApplicationConfiguration createConfig() {
        final Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();

        configuration.setForegroundFPS(60);
        configuration.setIdleFPS(60);
        configuration.useVsync(true);
        configuration.setTitle("CraftDM");
        configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());

        return configuration;
    }

    private void addVersions() {
        for (Button b : versionButtons) {
            versionContainer.removeChild(b);
        }

        versionButtons.clear();

        for (RemoteVersion version : CraftDM.this.versions) {
            if (Objects.equals(version.getType(), "snapshot") && filterSnapshots) {
                continue;
            }
            if ((Objects.equals(version.getType(), "old_alpha") || Objects.equals(version.getType(), "old_beta")) && filterHistoric) {
                continue;
            }
            Button button = new Button(CraftDM.this, version.getVersion(), width - 120, height - 60 - versionButtons.size() * 20, 100, 20);
            button.setCallback(() -> {
                for (Button b : versionButtons) {
                    b.disable();
                }

                status = "Downloading version " + version + "...";

                installAndLaunchVersion(version.getVersion(), button);
            });
            versionButtons.add(button);
        }

        Gdx.app.postRunnable(() -> {
            button.enable();
            button.setCallback(() -> {
                button.disable();

                CompletableFuture.runAsync(() -> {
                    if (alternateDirectory.getVersion(LAUNCH_VERSION).exists()) {
                        launchVersion(LAUNCH_VERSION, button);
                        return;
                    }

                    try {
                        AtomicBoolean failed = new AtomicBoolean(false);
                        status = "Installing desktop";
                        statusType = StatusType.NORMAL;
                        installVersion(MC_VERSION, alternateDirectory, () -> {
                            status = "Failed to install desktop: " + status;
                            statusType = StatusType.ERROR;
                            failed.set(true);
                        }).thenRun(() -> {
                            if (failed.get()) {
                                return;
                            }

                            try (InputStream resourceAsStream = getClass().getResourceAsStream("/fabric-installer-1.1.0.jar")) {
                                Path tempDirectory = Files.createTempDirectory("craftos-");
                                if (resourceAsStream == null) {
                                    status = "Failed to install fabric: missing resource";
                                    statusType = StatusType.ERROR;
                                    return;
                                }
                                Path resolve = tempDirectory.resolve("fabric-installer-1.1.0.jar");
                                Files.copy(resourceAsStream, resolve);
                                status = "Installing fabric";
                                statusType = StatusType.NORMAL;
                                String absolutePath = alternateDirectory.getAbsolutePath();
                                Path modsDir = Paths.get(absolutePath, "mods");
                                if (!Files.exists(modsDir))
                                    Files.createDirectories(modsDir);
                                executeCraftProcess(resolve, tempDirectory);

                                Files.deleteIfExists(resolve);
                                Files.deleteIfExists(tempDirectory);

                                status = "Launching Desktop";
                                statusType = StatusType.NORMAL;
                                launchVersion(LAUNCH_VERSION, button);
                            } catch (InterruptedException e) {
                                status = "Failed to install fabric: interrupted";
                                statusType = StatusType.ERROR;
                                e.printStackTrace();
                            } catch (IOException e) {
                                status = "Failed to install fabric: IO exception";
                                statusType = StatusType.ERROR;
                                e.printStackTrace();
                            } catch (Exception e) {
                                status = "Failed to install fabric: unknown exception";
                                statusType = StatusType.ERROR;
                                e.printStackTrace();
                            }
                        }).exceptionally(throwable -> {
                            status = "Failed to install fabric: " + throwable.getMessage();
                            statusType = StatusType.ERROR;
                            throwable.printStackTrace();
                            return null;
                        });
                    } catch (Exception e) {
                        status = "Failed to install fabric: unknown exception";
                        statusType = StatusType.ERROR;
                        e.printStackTrace();
                    }
                });
            });

            for (int i = 0; i < versionButtons.size(); i++) {
                versionButtons.get(i).setBounds(0, versionContainer.height - 20 - i * 20, 100, 20);
                versionContainer.addChild(versionButtons.get(i));
            }
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
            status = "Downloading version list";
            statusType = StatusType.NORMAL;
            return new DownloadCallback<>() {
                @Override
                public void updateProgress(long l, long l1) {
                    status = "Downloading version list: " + (int) (l * 100 / l1) + "%";
                    statusType = StatusType.NORMAL;
                }

                @Override
                public void retry(Throwable throwable, int i, int i1) {

                }

                @Override
                public void done(R r) {
                    status = "Version list downloaded";
                    statusType = StatusType.NORMAL;
                }

                @Override
                public void failed(Throwable throwable) {
                    status = "Failed to download version list";
                    statusType = StatusType.ERROR;
                }

                @Override
                public void cancelled() {
                    status = "Version list download cancelled";
                    statusType = StatusType.WARNING;
                }
            };
        }

        @Override
        public void done(RemoteVersionList versionList) {
            Map<String, RemoteVersion> versions = versionList.getVersions();
            Collection<RemoteVersion> values = versions.values();
            CraftDM.this.versions.clear();
            CraftDM.this.versions.addAll(values);
            CraftDM.this.allVersions.clear();
            CraftDM.this.allVersions.addAll(values);
            addVersions();
        }

        @Override
        public void failed(Throwable throwable) {
            status = "Failed to download version list";
            statusType = StatusType.ERROR;
        }

        @Override
        public void cancelled() {
            status = "Version list download cancelled";
            statusType = StatusType.WARNING;
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
            status = "Version " + version + " downloaded";
            statusType = StatusType.NORMAL;

            for (Button b : versionButtons) {
                b.enable();
            }

            button.setText(str);

            Launcher build = LauncherBuilder.create()
                    .nativeFastCheck(true)
                    .build();

            try {
                build.launch(new LaunchOption(version, auth, directory), new ProcessListener() {
                    @Override
                    public void onLog(String log) {
                        status = log;
                        statusType = StatusType.NORMAL;
                    }

                    @Override
                    public void onErrorLog(String log) {
                        status = "Minecraft error log:\n" + log;
                        statusType = StatusType.ERROR;
                    }

                    @Override
                    public void onExit(int code) {
                        if (code != 0) {
                            status = "Minecraft exited with code " + code;
                            statusType = StatusType.ERROR;
                        }
                    }
                });
            } catch (LaunchException e) {
                e.printStackTrace();
                status = "Failed to generate launch arguments for version " + version;
                statusType = StatusType.ERROR;
            }
        }

        @Override
        public void failed(Throwable throwable) {
            throwable.printStackTrace();
            status = "Failed to download version " + version;
            statusType = StatusType.ERROR;

            for (Button b : versionButtons) {
                b.enable();
            }

            button.setText(str);
        }

        @Override
        public void cancelled() {
            status = "Version " + version + " download cancelled";
            statusType = StatusType.WARNING;

            for (Button b : versionButtons) {
                b.enable();
            }

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
            status = "Version " + version + " downloaded";
            statusType = StatusType.NORMAL;

            onDone.run();
        }

        @Override
        public void failed(Throwable throwable) {
            throwable.printStackTrace();
            status = "Failed to download version " + version;
            statusType = StatusType.ERROR;

            onFail.run();
        }

        @Override
        public void cancelled() {
            status = "Version " + version + " download cancelled";
            statusType = StatusType.WARNING;

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
            CraftDM.this.keyDown(keycode);
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            CraftDM.this.keyUp(keycode);
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            CraftDM.this.keyTyped(character);
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            CraftDM.this.mousePressed();
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            CraftDM.this.mouseReleased();
            CraftDM.this.mouseClicked(button);
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            CraftDM.this.mouseMoved(screenX / 2, height - screenY / 2);
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            CraftDM.this.mouseScrolled(amountY);
            return true;
        }
    }
}
