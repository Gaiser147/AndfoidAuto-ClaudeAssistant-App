package de.robv.android.xposed;

public final class XposedBridge {
    private XposedBridge() {}
    public static void log(String text) {}
    public static void log(Throwable t) {}
    public static java.util.Set<Object> hookAllMethods(Class<?> hookClass, String methodName, Object callback) { return null; }
}
