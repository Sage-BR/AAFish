import com.sun.jna.Native;

import com.sun.jna.platform.win32.Kernel32;

import com.sun.jna.platform.win32.User32;

import com.sun.jna.platform.win32.WinDef;

import com.sun.jna.platform.win32.WinNT;

import com.sun.jna.platform.win32.WinUser;

import org.opencv.core.*;

import org.opencv.imgcodecs.Imgcodecs;

import org.opencv.imgproc.Imgproc;


import java.awt.*;

import java.awt.image.BufferedImage;

import java.awt.image.DataBufferByte;

import java.io.File;

import java.time.LocalTime;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.List;

import java.util.concurrent.atomic.AtomicBoolean;


/**

 * FishingBot — Java 21 (Virtual Threads)

 *

 * Lógica fiel ao Python original:

 *  - Tudo na mesma região (regionSkills)

 *  - up/left/right/pull/release → envia tecla se skill mudou

 *  - target encontrado mas sem skill → aguarda (não reseta lastSkill)

 *  - nada encontrado → reseta lastSkill (peixe sumiu)

 *

 * Teclas:

 *   up      → UP   (2x)

 *   left    → LEFT (1x)

 *   right   → RIGHT(1x)

 *   pull    → DOWN (2x)

 *   release → END  (1x)

 */

public class FishingBot {


    public interface BotListener {

        void onLog(String message);

        void onStatusChanged(boolean running);

    }


    // Virtual-Key codes

    private static final int VK_LEFT  = 0x25;

    private static final int VK_UP    = 0x26;

    private static final int VK_RIGHT = 0x27;

    private static final int VK_DOWN  = 0x28;

    private static final int VK_END   = 0x23;


    private static final int WM_KEYDOWN = 0x0100;

    private static final int WM_KEYUP   = 0x0101;


    private final BotListener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread botThread;

    private final Robot robot;


    // Config — regionSkills é a região principal (skills + target, tudo junto)

    private volatile Rectangle regionSkills  = new Rectangle(700, 0, 400, 150);

    private volatile double    confidence    = 0.40;

    private volatile double    scaleMin      = 0.5;

    private volatile double    scaleMax      = 2.0;

    private volatile boolean   multiScale    = true;

    private volatile String    gameExeName   = "archeage.exe";

    private volatile String    pathUp        = "images/up.png";

    private volatile String    pathLeft      = "images/left.png";

    private volatile String    pathRight     = "images/right.png";

    private volatile String    pathPull      = "images/pull.png";

    private volatile String    pathRelease   = "images/release.png";

    private volatile String    pathTarget    = "images/target.png";


    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");


    public FishingBot(BotListener listener) throws AWTException {

        this.listener = listener;

        this.robot = new Robot();

    }


    // Setters

    public void setRegionSkills(Rectangle r)  { regionSkills = r; }

    public void setRegionTarget(Rectangle r)  { }

    public void setConfidence(double v)        { confidence   = v; }

    public void setScaleMin(double v)          { scaleMin     = v; }

    public void setScaleMax(double v)          { scaleMax     = v; }

    public void setMultiScale(boolean v)       { multiScale   = v; }

    public void setGameExeName(String v)       { gameExeName  = v; }

    public void setPathUp(String v)            { pathUp       = v; }

    public void setPathLeft(String v)          { pathLeft     = v; }

    public void setPathRight(String v)         { pathRight    = v; }

    public void setPathPull(String v)          { pathPull     = v; }

    public void setPathRelease(String v)       { pathRelease  = v; }

    public void setPathTarget(String v)        { pathTarget   = v; }

    public boolean isRunning()                { return running.get(); }


    public void start() {

        if (running.compareAndSet(false, true)) {

            listener.onStatusChanged(true);

            botThread = Thread.ofVirtual().name("fishing-bot").start(this::loop);

        }

    }


    public void stop() {

        if (running.compareAndSet(true, false)) {

            listener.onStatusChanged(false);

            if (botThread != null) botThread.interrupt();

        }

    }


    // ---------------------------------------------------------------

    // Loop principal — fiel ao Python original

    // ---------------------------------------------------------------

    private void loop() {

        log("[BOT] Iniciado. Aguardando target do peixe...");

        String lastSkill = null;


        while (running.get() && !Thread.currentThread().isInterrupted()) {

            try {

                // Captura única da região (igual ao Python)

                BufferedImage screen = captureRegion(regionSkills);


                if (detect(screen, pathUp, Channel.RG)) {

                    if (!"up".equals(lastSkill)) {

                        pressKey(VK_UP); pressKey(VK_UP);

                        log("[" + now() + "] ->> SKILL seta pra CIMA");

                        lastSkill = "up";

                    }


                } else if (detect(screen, pathLeft, Channel.RG)) {

                    if (!"left".equals(lastSkill)) {

                        pressKey(VK_LEFT);

                        log("[" + now() + "] ->> SKILL seta pra ESQUERDA");

                        lastSkill = "left";

                    }


                } else if (detect(screen, pathRight, Channel.RG)) {

                    if (!"right".equals(lastSkill)) {

                        pressKey(VK_RIGHT);

                        log("[" + now() + "] ->> SKILL seta pra DIREITA");

                        lastSkill = "right";

                    }


                } else if (detect(screen, pathPull, Channel.HSV_S)) {

                    if (!"pull".equals(lastSkill)) {

                        pressKey(VK_DOWN); pressKey(VK_DOWN);

                        log("[" + now() + "] ->> SKILL PUXAR peixe");

                        lastSkill = "pull";

                    }


                } else if (detect(screen, pathRelease, Channel.HSV_S)) {

                    if (!"release".equals(lastSkill)) {

                        pressKey(VK_END);

                        log("[" + now() + "] ->> SKILL SOLTAR peixe");

                        lastSkill = "release";

                    }


                } else if (detect(screen, pathTarget, Channel.GRAY)) {

                    // Peixe fissgado mas sem skill visível — aguarda

                    if (lastSkill == null) {

                        log("[" + now() + "] ->> AGUARDANDO SKILL");

                        sleep(100);

                    }

                    // lastSkill NÃO é resetado (igual ao Python)


                } else {

                    // Nada encontrado — peixe sumiu

                    log("[" + now() + "] ->> Aguardando target do peixe");

                    lastSkill = null;

                    sleep(10);

                }


            } catch (Exception e) {

                log("[ERRO] " + e.getMessage());

                sleep(500);

            }

        }


        log("[BOT] Parado.");

    }


    // ---------------------------------------------------------------

    // Template Matching

    // ---------------------------------------------------------------

    private enum Channel { GRAY, RG, HSV_S }


    private boolean detect(BufferedImage screen, String templatePath, Channel channel) {

        File f = new File(templatePath);

        if (!f.exists()) return false;

        Mat tmpl = Imgcodecs.imread(f.getAbsolutePath());

        if (tmpl.empty()) return false;

        Mat src    = toMat(screen);

        Mat srcCh  = extract(src,  channel);

        Mat tmplCh = extract(tmpl, channel);

        double score = multiScale ? matchMultiScale(srcCh, tmplCh) : matchSingle(srcCh, tmplCh);

        return score >= confidence;

    }


    private double detectScore(BufferedImage screen, String templatePath, Channel channel) {

        File f = new File(templatePath);

        if (!f.exists()) return 0.0;

        Mat tmpl = Imgcodecs.imread(f.getAbsolutePath());

        if (tmpl.empty()) return 0.0;

        Mat src    = toMat(screen);

        Mat srcCh  = extract(src,  channel);

        Mat tmplCh = extract(tmpl, channel);

        return multiScale ? matchMultiScale(srcCh, tmplCh) : matchSingle(srcCh, tmplCh);

    }


    private Mat extract(Mat bgr, Channel ch) {

        return switch (ch) {

            case GRAY  -> { Mat g = new Mat(); Imgproc.cvtColor(bgr, g, Imgproc.COLOR_BGR2GRAY); yield g; }

            case RG    -> rgChannel(bgr);

            case HSV_S -> hsvSChannel(bgr);

        };

    }


    private Mat rgChannel(Mat bgr) {

        List<Mat> ch = new ArrayList<>();

        Core.split(bgr, ch);

        Mat diff = new Mat();

        Core.subtract(ch.get(2), ch.get(1), diff);

        Core.normalize(diff, diff, 0, 255, Core.NORM_MINMAX);

        return diff;

    }


    private Mat hsvSChannel(Mat bgr) {

        Mat hsv = new Mat();

        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);

        List<Mat> ch = new ArrayList<>();

        Core.split(hsv, ch);

        return ch.get(1);

    }


    private double matchSingle(Mat src, Mat tmpl) {

        if (tmpl.rows() > src.rows() || tmpl.cols() > src.cols()) return 0.0;

        Mat result = new Mat();

        Imgproc.matchTemplate(src, tmpl, result, Imgproc.TM_CCOEFF_NORMED);

        return Core.minMaxLoc(result).maxVal;

    }


    private double matchMultiScale(Mat src, Mat tmpl) {

        double best = 0.0;

        int steps = 12;

        for (int i = 0; i <= steps; i++) {

            double scale = scaleMin + i * (scaleMax - scaleMin) / steps;

            int nw = (int)(tmpl.cols() * scale);

            int nh = (int)(tmpl.rows() * scale);

            if (nw < 4 || nh < 4 || nw > src.cols() || nh > src.rows()) continue;

            Mat resized = new Mat();

            Imgproc.resize(tmpl, resized, new Size(nw, nh));

            double score = matchSingle(src, resized);

            if (score > best) best = score;

            if (best >= 0.99) break;

        }

        return best;

    }


    // ---------------------------------------------------------------

    // Captura e conversão

    // ---------------------------------------------------------------

    private BufferedImage captureRegion(Rectangle r) {

        return robot.createScreenCapture(r);

    }


    private Mat toMat(BufferedImage img) {

        BufferedImage bgr = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);

        Graphics2D g2 = bgr.createGraphics();

        g2.drawImage(img, 0, 0, null);

        g2.dispose();

        byte[] data = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();

        Mat mat = new Mat(bgr.getHeight(), bgr.getWidth(), CvType.CV_8UC3);

        mat.put(0, 0, data);

        return mat;

    }


    // ---------------------------------------------------------------

    // Envio de teclado

    // ---------------------------------------------------------------

    private void pressKey(int vk) {

        WinDef.HWND hwnd = findGameWindow();

        if (hwnd != null) {

            postKey(hwnd, vk);

        } else {

            sendInputKey(vk);

        }

    }


    private WinDef.HWND findGameWindow() {

        final String target = gameExeName.toLowerCase();

        WinDef.HWND[] found = {null};

        User32.INSTANCE.EnumWindows((hWnd, data) -> {

            com.sun.jna.ptr.IntByReference pidRef = new com.sun.jna.ptr.IntByReference();

            User32.INSTANCE.GetWindowThreadProcessId(hWnd, pidRef);

            int pid = pidRef.getValue();

            if (pid == 0) return true;

            try {

                WinNT.HANDLE hProc = Kernel32.INSTANCE.OpenProcess(0x1000, false, pid);

                if (hProc != null) {

                    char[] buf = new char[1024];

                    WinDef.DWORDByReference sz = new WinDef.DWORDByReference(new WinDef.DWORD(1024));

                    boolean ok = Kernel32Ext.INSTANCE.QueryFullProcessImageNameW(hProc, 0, buf, sz);

                    Kernel32.INSTANCE.CloseHandle(hProc);

                    if (ok) {

                        String name = new String(buf, 0, sz.getValue().intValue()).toLowerCase();

                        if (name.endsWith(target)) {

                            found[0] = hWnd;

                            return false;

                        }

                    }

                }

            } catch (Exception ignored) {}

            return true;

        }, null);

        return found[0];

    }


    private void postKey(WinDef.HWND hwnd, int vk) {

        User32.INSTANCE.PostMessage(hwnd, WM_KEYDOWN, new WinDef.WPARAM(vk), new WinDef.LPARAM(0));

        sleep(30);

        User32.INSTANCE.PostMessage(hwnd, WM_KEYUP,   new WinDef.WPARAM(vk), new WinDef.LPARAM(0));

    }


    private void sendInputKey(int vk) {

        WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);

        inputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);

        inputs[0].input.setType("ki");

        inputs[0].input.ki.wVk    = new WinDef.WORD(vk);

        inputs[0].input.ki.dwFlags = new WinDef.DWORD(0);

        inputs[1].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);

        inputs[1].input.setType("ki");

        inputs[1].input.ki.wVk    = new WinDef.WORD(vk);

        inputs[1].input.ki.dwFlags = new WinDef.DWORD(2);

        User32.INSTANCE.SendInput(new WinDef.DWORD(2), inputs, inputs[0].size());

    }


    interface Kernel32Ext extends com.sun.jna.Library {

        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

        boolean QueryFullProcessImageNameW(WinNT.HANDLE hProcess, int dwFlags,

                                           char[] lpExeName,

                                           WinDef.DWORDByReference lpdwSize);

    }


    // ---------------------------------------------------------------

    // Debug — F1

    // ---------------------------------------------------------------

    public void saveDebugCapture() {

        Thread.ofVirtual().name("debug").start(() -> {

            try {

                new File("debug").mkdirs();

                String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

                BufferedImage cap = captureRegion(regionSkills);

                Mat mat = toMat(cap);

                Imgcodecs.imwrite("debug/capture_" + ts + ".png", mat);


                log("[DEBUG] ---- Scores (" + ts + ") ----");

                log(String.format("[DEBUG] %-10s score=%.4f  canal=RG",    "up",      detectScore(cap, pathUp,      Channel.RG)));

                log(String.format("[DEBUG] %-10s score=%.4f  canal=RG",    "left",    detectScore(cap, pathLeft,    Channel.RG)));

                log(String.format("[DEBUG] %-10s score=%.4f  canal=RG",    "right",   detectScore(cap, pathRight,   Channel.RG)));

                log(String.format("[DEBUG] %-10s score=%.4f  canal=HSV-S", "pull",    detectScore(cap, pathPull,    Channel.HSV_S)));

                log(String.format("[DEBUG] %-10s score=%.4f  canal=HSV-S", "release", detectScore(cap, pathRelease, Channel.HSV_S)));

                log(String.format("[DEBUG] %-10s score=%.4f  canal=GRAY",  "target",  detectScore(cap, pathTarget,  Channel.GRAY)));

                log(String.format("[DEBUG] threshold=%.2f — captura salva em debug/capture_%s.png", confidence, ts));

            } catch (Exception e) {

                log("[DEBUG] Erro: " + e.getMessage());

            }

        });

    }


    // ---------------------------------------------------------------

    // Helpers

    // ---------------------------------------------------------------

    private void log(String msg) { listener.onLog(msg); }

    private String now() { return LocalTime.now().format(TIME_FMT); }

    private void sleep(long ms) {

        try { Thread.sleep(ms); }

        catch (InterruptedException e) { Thread.currentThread().interrupt(); }

    }

}


https://imgur.com/a/xZF3Rfr
