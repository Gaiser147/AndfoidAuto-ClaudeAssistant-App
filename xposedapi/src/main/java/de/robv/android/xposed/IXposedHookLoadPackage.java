package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Minimaler Stub der Xposed-API. Nur zum Kompilieren vorhanden; zur Laufzeit stellt
 * das LSPosed-Framework die echten Klassen bereit, deshalb compileOnly und nicht im APK.
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(LoadPackageParam lpparam) throws Throwable;
}
