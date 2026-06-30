package dev.ultreon.craftos.session.frames;

import me.friwi.jcefmaven.*;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefFocusHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class BrowserFrame extends JFrame {
    private CefBrowser browser;
    private boolean browserFocused = true;
    private boolean cefInstanceDisposed;

    public BrowserFrame(String url) {
        CefAppBuilder builder = new CefAppBuilder();
        builder.getCefSettings().windowless_rendering_enabled = false;

        CefApp app;
        try {
            app = builder.build();
        } catch (IOException | CefInitializationException | InterruptedException | UnsupportedPlatformException e) {
            dispose();
            return;
        }
        CefClient client = app.createClient();

        browser = client.createBrowser(url, false, false);
        Component browerComponent = browser.getUIComponent();

        client.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public void onGotFocus(CefBrowser browser) {
                if (browserFocused) return;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                browser.setFocus(true);
                browserFocused = true;
            }

            @Override
            public void onTakeFocus(CefBrowser browser, boolean next) {
                browserFocused = false;
            }
        });

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new AbstractAction("Reset Page") {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser.loadURL(url);
            }
        });
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
        getContentPane().add(browerComponent, BorderLayout.CENTER);
        pack();

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreenDevice = ge.getDefaultScreenDevice();
        DisplayMode displayMode = defaultScreenDevice.getDisplayMode();
        setSize(displayMode.getWidth(), displayMode.getHeight() - 320);
        setLocation(0, 120);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!cefInstanceDisposed) {
                    CefApp.getInstance().dispose();
                    cefInstanceDisposed = true;
                }
                dispose();
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                super.windowLostFocus(e);

                e.getWindow().requestFocus();
            }
        });
    }

    @Override
    public void dispose() {
        if (!cefInstanceDisposed) {
            CefApp.getInstance().dispose();
            cefInstanceDisposed = true;
        }
        super.dispose();
    }
}