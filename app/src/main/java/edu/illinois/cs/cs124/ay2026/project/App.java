package edu.illinois.cs.cs124.ay2026.project;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

/** Application class — restores the saved dark/light mode preference at startup. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        int nightMode = getSharedPreferences("prefs", MODE_PRIVATE)
                .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
