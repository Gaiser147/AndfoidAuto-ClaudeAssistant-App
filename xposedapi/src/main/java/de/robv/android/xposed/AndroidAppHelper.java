package de.robv.android.xposed;

import android.app.Application;

/** Stub: liefert zur Laufzeit (über LSPosed) die aktuelle Application der gehookten App. */
public final class AndroidAppHelper {
    private AndroidAppHelper() {}
    public static Application currentApplication() { return null; }
    public static String currentPackageName() { return null; }
}
