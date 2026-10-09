# Started by name through app_process, never referenced as an entry point from app code.
-keep class com.arslan.prayerbar.statusbar.StatusBarIconInjector {
    public static void main(java.lang.String[]);
}

# Reached through reflection in ShizukuHelper.
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keepclassmembers class rikka.shizuku.ShizukuRemoteProcess {
    public int waitFor();
}
