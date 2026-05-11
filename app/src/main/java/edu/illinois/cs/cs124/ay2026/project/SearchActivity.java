package edu.illinois.cs.cs124.ay2026.project;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class SearchActivity extends BaseActivity {

    private static final String[] CATEGORIES = {
            "All Categories", "Keys", "ID/Wallet", "Electronics",
            "Clothing/Bag", "Water Bottle", "Other"
    };

    // Nominatim viewbox biased toward UIUC campus (lon_min,lat_max,lon_max,lat_min)
    private static final String UIUC_VIEWBOX = "-88.25,40.13,-88.20,40.08";

    // Radius steps in meters: index 0–8
    private static final int[] RADIUS_STEPS = {100, 200, 300, 500, 750, 1000, 1250, 1500, 2000};

    private TextInputEditText locationField;
    private ListView autocompleteList;
    private TextView radiusLabel;
    private SeekBar radiusSeekBar;
    private Spinner categorySpinner;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private PostAdapter adapter;

    private final List<Post> searchResults = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;

    // Autocomplete state
    private final List<String> autocompleteLabels = new ArrayList<>();
    private final List<double[]> autocompleteCoords = new ArrayList<>();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private boolean suppressAutocomplete = false;

    // Exact coordinates saved when the user picks a suggestion or uses GPS
    private double selectedLat = 0;
    private double selectedLng = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> { /* user can retry after granting */ });

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                double lat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0);
                double lng = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0);
                selectedLat = lat;
                selectedLng = lng;
                reverseGeocode(lat, lng);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        locationField = findViewById(R.id.location_field);
        autocompleteList = findViewById(R.id.autocomplete_list);
        radiusLabel = findViewById(R.id.radius_label);
        radiusSeekBar = findViewById(R.id.radius_seekbar);
        categorySpinner = findViewById(R.id.category_spinner);
        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORIES);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);

        adapter = new PostAdapter(searchResults, (post, cardView) -> {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra("postId", post.getId());
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusLabel.setText(formatRadius(RADIUS_STEPS[progress]));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationAutocomplete();

        findViewById(R.id.my_location_button).setOnClickListener(v -> fetchMyLocation());
        findViewById(R.id.pick_on_map_button).setOnClickListener(v -> openMapPicker());
        findViewById(R.id.search_button).setOnClickListener(v -> runSearch());

        setupBottomNav();
    }

    // -------------------------------------------------------------------------
    // Location autocomplete via Nominatim
    // -------------------------------------------------------------------------

    private void setupLocationAutocomplete() {
        locationField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppressAutocomplete) return;
                // Clear saved coords when the user manually edits the field
                selectedLat = 0;
                selectedLng = 0;
                if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
                String query = s.toString().trim();
                if (query.length() < 2) { hideAutocompleteList(); return; }
                debounceRunnable = () -> queryNominatim(query);
                debounceHandler.postDelayed(debounceRunnable, 400);
            }
        });

        autocompleteList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= autocompleteLabels.size()) return;
            String label = autocompleteLabels.get(position);
            double[] coords = autocompleteCoords.get(position);
            // Save exact coordinates
            selectedLat = coords[0];
            selectedLng = coords[1];

            suppressAutocomplete = true;
            locationField.setText(label);
            locationField.setSelection(label.length());
            suppressAutocomplete = false;
            hideAutocompleteList();
        });
    }

    private void queryNominatim(String query) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String urlStr = "https://nominatim.openstreetmap.org/search"
                        + "?format=json"
                        + "&q=" + encoded
                        + "&limit=5"
                        + "&viewbox=" + UIUC_VIEWBOX
                        + "&bounded=0";

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "UIUCLostAndFound/1.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    runOnUiThread(this::hideAutocompleteList);
                    conn.disconnect();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }
                conn.disconnect();

                JSONArray results = new JSONArray(sb.toString());
                List<String> labels = new ArrayList<>();
                List<double[]> coords = new ArrayList<>();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String name = obj.optString("name", "").trim();
                    String displayName = obj.optString("display_name", "");
                    String[] parts = displayName.split(",\\s*");
                    // Build a Google Maps-style label: "Building, Street, City"
                    StringBuilder labelBuilder = new StringBuilder();
                    if (!name.isEmpty()) {
                        labelBuilder.append(name);
                        for (int j = 1; j < Math.min(parts.length, 3); j++) {
                            String part = parts[j].trim();
                            if (!part.isEmpty()) labelBuilder.append(", ").append(part);
                        }
                    } else {
                        for (int j = 0; j < Math.min(parts.length, 3); j++) {
                            if (j > 0) labelBuilder.append(", ");
                            labelBuilder.append(parts[j].trim());
                        }
                    }
                    double lat = Double.parseDouble(obj.getString("lat"));
                    double lon = Double.parseDouble(obj.getString("lon"));
                    labels.add(labelBuilder.toString());
                    coords.add(new double[]{lat, lon});
                }

                runOnUiThread(() -> {
                    autocompleteLabels.clear();
                    autocompleteLabels.addAll(labels);
                    autocompleteCoords.clear();
                    autocompleteCoords.addAll(coords);

                    if (labels.isEmpty()) { hideAutocompleteList(); return; }
                    autocompleteList.setAdapter(new ArrayAdapter<>(
                            this, android.R.layout.simple_list_item_1, labels));
                    autocompleteList.setVisibility(View.VISIBLE);
                });

            } catch (Exception e) {
                runOnUiThread(this::hideAutocompleteList);
            }
        });
    }

    private void hideAutocompleteList() {
        autocompleteList.setVisibility(View.GONE);
        autocompleteLabels.clear();
        autocompleteCoords.clear();
    }

    // -------------------------------------------------------------------------
    // My Location
    // -------------------------------------------------------------------------

    private void fetchMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        Toast.makeText(this, "Could not get location. Try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedLat = location.getLatitude();
                    selectedLng = location.getLongitude();
                    reverseGeocode(location.getLatitude(), location.getLongitude());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Location error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void openMapPicker() {
        Intent intent = new Intent(this, MapPickerActivity.class);
        if (selectedLat != 0 || selectedLng != 0) {
            intent.putExtra(MapPickerActivity.EXTRA_LAT, selectedLat);
            intent.putExtra(MapPickerActivity.EXTRA_LNG, selectedLng);
        }
        mapPickerLauncher.launch(intent);
    }

    private void reverseGeocode(double lat, double lng) {
        if (!Geocoder.isPresent()) {
            setLocationText(String.format(Locale.US, "%.5f, %.5f", lat, lng));
            return;
        }
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                setLocationText(buildFullAddress(addr, lat, lng));
            } else {
                setLocationText(String.format(Locale.US, "%.5f, %.5f", lat, lng));
            }
        } catch (IOException e) {
            setLocationText(String.format(Locale.US, "%.5f, %.5f", lat, lng));
        }
    }

    /** Builds a full address string from a Geocoder Address, similar to Google Maps style. */
    private String buildFullAddress(Address addr, double lat, double lng) {
        StringBuilder sb = new StringBuilder();
        String feature = addr.getFeatureName();
        String houseNumber = addr.getSubThoroughfare(); // e.g. "1401"
        String street = addr.getThoroughfare();         // e.g. "West Green Street"
        String city = addr.getLocality();
        String state = addr.getAdminArea();

        // Add building name only if it isn't just a number
        if (feature != null && !feature.matches("\\d+.*")) sb.append(feature);

        // Street address: "1401 West Green Street"
        if (street != null) {
            if (sb.length() > 0) sb.append(", ");
            if (houseNumber != null) sb.append(houseNumber).append(" ");
            sb.append(street);
        }
        if (city != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (state != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        return sb.length() > 0 ? sb.toString()
                : String.format(Locale.US, "%.5f, %.5f", lat, lng);
    }

    private void setLocationText(String text) {
        suppressAutocomplete = true;
        locationField.setText(text);
        locationField.setSelection(text.length());
        suppressAutocomplete = false;
        hideAutocompleteList();
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    private void runSearch() {
        if (selectedLat == 0 && selectedLng == 0) {
            locationField.setError("Select a location from the suggestions");
            return;
        }
        locationField.setError(null);

        int radiusMeters = RADIUS_STEPS[radiusSeekBar.getProgress()];
        String selectedCategory = categorySpinner.getSelectedItem().toString();

        FirebaseFirestore.getInstance()
                .collection("posts")
                .whereIn("status", Arrays.asList("active", "taken_by_finder"))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    searchResults.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Post post = doc.toObject(Post.class);
                        if (post == null) continue;
                        if (post.getId() == null) post.setId(doc.getId());

                        // Distance filter
                        double distance = haversineMeters(
                                selectedLat, selectedLng,
                                post.getLatitude(), post.getLongitude());
                        if (distance > radiusMeters) continue;

                        // Category filter
                        if (!"All Categories".equals(selectedCategory)
                                && !selectedCategory.equals(post.getCategory())) continue;

                        searchResults.add(post);
                    }
                    searchResults.sort((a, b) -> Double.compare(
                            haversineMeters(selectedLat, selectedLng,
                                    a.getLatitude(), a.getLongitude()),
                            haversineMeters(selectedLat, selectedLng,
                                    b.getLatitude(), b.getLongitude())));
                    adapter.notifyDataSetChanged();
                    updateResultsView(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Search failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private String formatRadius(int meters) {
        if (meters >= 1000) {
            int km = meters / 1000;
            return "Radius: " + km + " km";
        }
        return "Radius: " + meters + " m";
    }

    /** Haversine formula — returns distance in meters between two lat/lng points. */
    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000; // Earth radius in metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void updateResultsView(boolean searchRan) {
        if (searchResults.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(searchRan
                    ? "No items found. Try a larger radius or different category."
                    : "Enter a location and tap Search.");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Bottom nav
    // -------------------------------------------------------------------------

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_search);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                navigateTab(HomeActivity.class);
                return true;
            } else if (id == R.id.nav_search) {
                return true;
            } else if (id == R.id.nav_post) {
                startActivity(new Intent(this, PostItemActivity.class));
                overridePendingTransition(R.anim.slide_up_enter, R.anim.none);
                return false;
            } else if (id == R.id.nav_account) {
                navigateTab(AccountActivity.class);
                return true;
            }
            return false;
        });
    }

    private void navigateTab(Class<?> dest) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, dest)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
            finish();
        }, 200);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
