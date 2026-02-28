package com.jrs.gl.components;

import java.util.Locale;

public class CrashHandler {

    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

    public static void propException(String title, String message, Throwable t) {
        String text = message + "\n\n" + (t != null ? stackTrace(t) : "");
        showDialog(title, text);
    }

    public static void propException(String message, Throwable t) {
        String text = message + "\n\n" + (t != null ? stackTrace(t) : "");
        showDialog("Exception", text);
    }

    public static void propException(Throwable t) {
        String text = (t != null ? stackTrace(t) : "--NO-TRACE-FOUND");
        showDialog("Exception", text);
    }

    private static void showDialog(String title, String text){
        if(isWindows()) {
            showWindowsDialog(title, text);
        }
        else if(isMac()) {
            showMacDialog(title, text);
        }
        else if(isUnix()) {
            showLinuxDialog(title, text);
        }
    }

    private static String stackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static boolean isWindows() {
        return OS.contains("win");
    }

    private static boolean isMac() {
        return OS.contains("mac");
    }

    private static boolean isUnix() {
        return OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    }

    private static void showLinuxDialog(String title, String message) {
        if (tryExec("zenity", "--error", "--title", title, "--text", message)) return;
        tryExec("kdialog", "--error", message, "--title", title);
    }

    private static void showWindowsDialog(String title, String message) {
        // Uses WPF MessageBox; works on most modern Windows
        String ps = """
        Add-Type -AssemblyName PresentationFramework;
        [System.Windows.MessageBox]::Show('%s','%s','OK','Error') | Out-Null
        """.formatted(escapePS(message), escapePS(title));
        tryExec("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
    }

    private static String escapePS(String s) {
        return s.replace("'", "''");
    }

    private static void showMacDialog(String title, String message) {
        try {
            String script = "display dialog " + quoteApple(message) +
                    " with title " + quoteApple(title) +
                    " buttons {\"OK\"} default button 1 with icon stop";
            new ProcessBuilder("osascript", "-e", script).start();
        } catch (Exception ignored) {}
    }

    private static String quoteApple(String s) {
        // Escape quotes for AppleScript
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean tryExec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
