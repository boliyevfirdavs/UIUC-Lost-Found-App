package edu.illinois.cs.cs124.ay2026.project;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Button;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;

/**
 * Full-screen map picker using OpenStreetMap (OSMDroid). The user taps a point to drop a pin;
 * tapping again moves it. Returns EXTRA_LAT and EXTRA_LNG via setResult when the user confirms.
 *
 * Callers can optionally pass an existing lat/lng via extras to pre-place the pin.
 */
public class MapPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";

    // UIUC Quad — default map center
    private static final GeoPoint UIUC_CENTER = new GeoPoint(40.1020, -88.2272);

    private MapView mapView;
    private Marker currentMarker;
    private Button confirmButton;

    private double initialLat;
    private double initialLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        org.osmdroid.config.Configuration.getInstance()
                .load(this, PreferenceManager.getDefaultSharedPreferences(this));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Pick Location");
        }

        confirmButton = findViewById(R.id.confirm_button);

        initialLat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
        initialLng = getIntent().getDoubleExtra(EXTRA_LNG, 0);

        mapView = findViewById(R.id.map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);

        if (isDarkMode()) {
            // Invert + 180° hue rotation combined into one matrix.
            // Inversion makes the background dark; the hue rotation cancels the colour shift
            // so blue water stays blue (dark) and green grass stays green (dark).
            ColorMatrix filter = new ColorMatrix(new float[]{
                 1/3f, -2/3f, -2/3f, 0, 255,
                -2/3f,  1/3f, -2/3f, 0, 255,
                -2/3f, -2/3f,  1/3f, 0, 255,
                    0,     0,     0,  1,   0
            });
            mapView.getOverlayManager().getTilesOverlay()
                    .setColorFilter(new ColorMatrixColorFilter(filter));
        }

        boolean hasInitial = initialLat != 0 || initialLng != 0;
        GeoPoint center = hasInitial ? new GeoPoint(initialLat, initialLng) : UIUC_CENTER;
        double zoom = hasInitial ? 17.0 : 15.0;
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(center);

        if (hasInitial) {
            placeMarker(center);
        }

        // Listen for map taps to place/move the pin
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                placeMarker(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        mapView.getOverlays().add(0, eventsOverlay);

        confirmButton.setOnClickListener(v -> {
            if (currentMarker == null) return;
            GeoPoint pos = currentMarker.getPosition();
            Intent result = new Intent();
            result.putExtra(EXTRA_LAT, pos.getLatitude());
            result.putExtra(EXTRA_LNG, pos.getLongitude());
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private boolean isDarkMode() {
        int flags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void placeMarker(GeoPoint geoPoint) {
        if (currentMarker != null) {
            mapView.getOverlays().remove(currentMarker);
        }
        currentMarker = new Marker(mapView);
        currentMarker.setPosition(geoPoint);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentMarker.setTitle("Selected location");
        mapView.getOverlays().add(currentMarker);
        mapView.invalidate();
        confirmButton.setEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
