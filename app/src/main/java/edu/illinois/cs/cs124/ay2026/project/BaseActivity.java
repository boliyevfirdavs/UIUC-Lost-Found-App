package edu.illinois.cs.cs124.ay2026.project;

import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Base activity that provides the theme-toggle menu item to all screens.
 * The icon shows a moon when in light mode (tap to go dark) and a sun
 * when in dark mode (tap to go light).
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem toggleItem = menu.findItem(R.id.action_toggle_theme);
        if (toggleItem != null) {
            boolean isDark = AppCompatDelegate.getDefaultNightMode()
                    == AppCompatDelegate.MODE_NIGHT_YES;
            // Show the moon when in light mode (tap to switch to dark)
            // Show the sun when in dark mode (tap to switch to light)
            toggleItem.setIcon(isDark ? R.drawable.ic_theme_toggle : R.drawable.ic_theme_moon);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_theme) {
            boolean isDark = AppCompatDelegate.getDefaultNightMode()
                    == AppCompatDelegate.MODE_NIGHT_YES;
            int newMode = isDark
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_YES;
            AppCompatDelegate.setDefaultNightMode(newMode);
            getSharedPreferences("prefs", MODE_PRIVATE)
                    .edit().putInt("night_mode", newMode).apply();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
