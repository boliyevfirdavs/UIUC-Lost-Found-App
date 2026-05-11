package edu.illinois.cs.cs124.ay2026.project;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class PostItemActivity extends BaseActivity {

    private static final String[] CATEGORIES = {
            "Keys", "ID/Wallet", "Electronics", "Clothing/Bag", "Water Bottle", "Other"
    };

    // Nominatim viewbox biased toward UIUC campus (lon_min,lat_max,lon_max,lat_min)
    private static final String UIUC_VIEWBOX = "-88.25,40.13,-88.20,40.08";

    private TextInputEditText titleField;
    private TextInputEditText descriptionField;
    private Spinner categorySpinner;
    private TextInputEditText locationField;
    private ListView autocompleteList;
    private RadioGroup statusGroup;
    private TextInputEditText contactField;
    private Button submitButton;
    private ProgressBar progressBar;
    private View nearbyCard;
    private TextView nearbySubtitle;
    private RecyclerView nearbyRecycler;
    private final List<Post> nearbyPosts = new ArrayList<>();
    private PostAdapter nearbyAdapter;

    private FusedLocationProviderClient fusedLocationClient;

    // Autocomplete results from Nominatim
    private final List<String> autocompleteLabels = new ArrayList<>();
    private final List<double[]> autocompleteCoords = new ArrayList<>();

    // Coordinates saved when user picks a suggestion or taps My Location
    private double selectedLat = 0;
    private double selectedLng = 0;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private boolean suppressAutocomplete = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Photos
    private final List<Uri> selectedUris = new ArrayList<>();
    private PhotoThumbnailAdapter thumbnailAdapter;
    private Uri cameraOutputUri;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> { /* user can retry after granting */ });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && cameraOutputUri != null) {
                    selectedUris.add(cameraOutputUri);
                    thumbnailAdapter.notifyItemInserted(selectedUris.size() - 1);
                }
            });

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                double lat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0);
                double lng = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0);
                selectedLat = lat;
                selectedLng = lng;
                reverseGeocode(lat, lng);
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        selectedUris.add(data.getClipData().getItemAt(i).getUri());
                    }
                    thumbnailAdapter.notifyDataSetChanged();
                } else if (data.getData() != null) {
                    selectedUris.add(data.getData());
                    thumbnailAdapter.notifyItemInserted(selectedUris.size() - 1);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_item);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            // ic_close is already set as navigationIcon in the layout XML
            actionBar.setTitle("Report Found Item");
        }

        titleField = findViewById(R.id.title_field);
        descriptionField = findViewById(R.id.description_field);
        categorySpinner = findViewById(R.id.category_spinner);
        locationField = findViewById(R.id.location_field);
        autocompleteList = findViewById(R.id.autocomplete_list);
        statusGroup = findViewById(R.id.status_group);
        contactField = findViewById(R.id.contact_field);
        submitButton = findViewById(R.id.submit_button);
        progressBar = findViewById(R.id.progress_bar);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CATEGORIES);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) contactField.setText(user.getEmail());
        contactField.setVisibility(View.GONE);

        statusGroup.setOnCheckedChangeListener((group, checkedId) ->
                contactField.setVisibility(
                        checkedId == R.id.status_taken ? View.VISIBLE : View.GONE));

        RecyclerView photoRecycler = findViewById(R.id.photo_recycler);
        thumbnailAdapter = new PhotoThumbnailAdapter(selectedUris, position -> {
            selectedUris.remove(position);
            thumbnailAdapter.notifyItemRemoved(position);
        });
        photoRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        photoRecycler.setAdapter(thumbnailAdapter);

        nearbyCard = findViewById(R.id.nearby_card);
        nearbySubtitle = findViewById(R.id.nearby_subtitle);
        nearbyRecycler = findViewById(R.id.nearby_recycler);
        nearbyAdapter = new PostAdapter(nearbyPosts, (post, cardView) -> {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra("postId", post.getId());
            startActivity(intent);
        });
        nearbyRecycler.setLayoutManager(new LinearLayoutManager(this));
        nearbyRecycler.setAdapter(nearbyAdapter);

        findViewById(R.id.add_photo_button).setOnClickListener(v -> showPhotoSourceDialog());
        submitButton.setOnClickListener(v -> submitPost());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationAutocomplete();
        findViewById(R.id.my_location_button).setOnClickListener(v -> fetchMyLocation());
        findViewById(R.id.pick_on_map_button).setOnClickListener(v -> openMapPicker());
    }

    // -------------------------------------------------------------------------
    // Location autocomplete via Nominatim (OpenStreetMap) — free, no API key
    // -------------------------------------------------------------------------

    private void setupLocationAutocomplete() {
        locationField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppressAutocomplete) return;
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
            selectedLat = coords[0];
            selectedLng = coords[1];

            suppressAutocomplete = true;
            locationField.setText(label);
            locationField.setSelection(label.length());
            suppressAutocomplete = false;
            hideAutocompleteList();
            loadNearbyPosts(selectedLat, selectedLng);
        });
    }

    /**
     * Queries the Nominatim OpenStreetMap API on a background thread.
     * No API key or billing required. Results are biased toward UIUC campus.
     */
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
                // Nominatim requires a User-Agent header identifying the app
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
    // My Location — uses FusedLocationProvider (free) + Geocoder (built-in)
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
        if (selectedLat != 0 || selectedLng != 0) loadNearbyPosts(selectedLat, selectedLng);
    }

    // -------------------------------------------------------------------------
    // Photos
    // -------------------------------------------------------------------------

    private void showPhotoSourceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Add Photo")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (dialog, which) -> {
                    if (which == 0) launchCamera(); else launchGallery();
                })
                .show();
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
            return;
        }
        try {
            File cameraDir = new File(getCacheDir(), "camera_photos");
            if (!cameraDir.exists()) cameraDir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File photoFile = File.createTempFile("IMG_" + ts, ".jpg", cameraDir);
            cameraOutputUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            cameraLauncher.launch(intent);
        } catch (IOException e) {
            Toast.makeText(this, "Could not start camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGallery() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{perm});
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        galleryLauncher.launch(intent);
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    private void submitPost() {
        String title = titleField.getText() != null
                ? titleField.getText().toString().trim() : "";
        String description = descriptionField.getText() != null
                ? descriptionField.getText().toString().trim() : "";
        String locationLabel = locationField.getText() != null
                ? locationField.getText().toString().trim() : "";
        boolean takenByFinder = statusGroup.getCheckedRadioButtonId() == R.id.status_taken;
        String customContact = (takenByFinder && contactField.getText() != null)
                ? contactField.getText().toString().trim() : "";

        if (title.isEmpty()) { titleField.setError("Title is required"); return; }
        if (locationLabel.isEmpty()) { locationField.setError("Location is required"); return; }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // Always share contact info — use custom contact if provided, otherwise fall back to email
        String contactInfo = !customContact.isEmpty() ? customContact : user.getEmail();

        Map<String, Object> post = new HashMap<>();
        post.put("title", title);
        post.put("description", description);
        post.put("category", categorySpinner.getSelectedItem().toString());
        post.put("locationLabel", locationLabel);
        post.put("latitude", selectedLat);
        post.put("longitude", selectedLng);
        post.put("status", takenByFinder ? "taken_by_finder" : "active");
        post.put("finderUserId", user.getUid());
        post.put("finderName", user.getDisplayName());
        post.put("finderContactInfo", contactInfo);
        post.put("createdAt", Timestamp.now());

        setLoading(true);

        if (selectedUris.isEmpty()) {
            post.put("photoUrls", new ArrayList<>());
            savePost(post);
        } else {
            uploadPhotosAndSave(post);
        }
    }

    // Uploads all selected photos to Cloudinary (sequentially on background thread),
    // then saves the post to Firestore with the returned URLs.
    private void uploadPhotosAndSave(Map<String, Object> post) {
        executor.execute(() -> {
            List<String> photoUrls = new ArrayList<>();
            for (Uri uri : selectedUris) {
                byte[] imageBytes = readUriToBytes(uri);
                if (imageBytes == null) continue;
                try {
                    photoUrls.add(uploadToCloudinary(imageBytes));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Photo upload failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show());
                }
            }
            post.put("photoUrls", photoUrls);
            runOnUiThread(() -> savePost(post));
        });
    }

    // Uploads one image to Cloudinary using an unsigned upload preset and returns the secure URL.
    // Unsigned presets are designed to be public — no secret key is needed for client uploads.
    private String uploadToCloudinary(byte[] imageBytes) throws Exception {
        String cloudName = getString(R.string.cloudinary_cloud_name);
        String uploadPreset = getString(R.string.cloudinary_upload_preset);
        String boundary = "----CloudinaryBoundary" + System.currentTimeMillis();

        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload")
                        .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            // upload_preset field
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n");
            out.writeBytes(uploadPreset + "\r\n");
            // image file field
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n");
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n");
            out.write(imageBytes);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        if (conn.getResponseCode() != 200) {
            throw new IOException("Cloudinary upload failed (HTTP " + conn.getResponseCode() + ")");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        return new JSONObject(sb.toString()).getString("secure_url");
    }

    private byte[] readUriToBytes(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void savePost(Map<String, Object> post) {
        FirebaseFirestore.getInstance().collection("posts").add(post)
                .addOnSuccessListener(ref -> onPostSaved())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to post: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void onPostSaved() {
        Toast.makeText(this, "Item posted!", Toast.LENGTH_SHORT).show();
        finish();
        overridePendingTransition(0, R.anim.slide_down_exit);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
    }

    // -------------------------------------------------------------------------
    // Nearby posts — shown after location is picked
    // -------------------------------------------------------------------------

    private static final int NEARBY_RADIUS_METERS = 500;

    private void loadNearbyPosts(double lat, double lng) {
        FirebaseFirestore.getInstance()
                .collection("posts")
                .whereIn("status", Arrays.asList("active", "taken_by_finder"))
                .get()
                .addOnSuccessListener(snapshots -> {
                    nearbyPosts.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Post post = doc.toObject(Post.class);
                        if (post == null) continue;
                        if (post.getId() == null) post.setId(doc.getId());
                        double dist = haversineMeters(lat, lng,
                                post.getLatitude(), post.getLongitude());
                        if (dist <= NEARBY_RADIUS_METERS) nearbyPosts.add(post);
                    }
                    nearbyAdapter.notifyDataSetChanged();
                    if (nearbyPosts.isEmpty()) {
                        nearbyCard.setVisibility(View.GONE);
                    } else {
                        nearbySubtitle.setText(nearbyPosts.size() == 1
                                ? "1 post found within 500 m of this location"
                                : nearbyPosts.size() + " posts found within 500 m of this location");
                        nearbyCard.setVisibility(View.VISIBLE);
                    }
                });
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void dismissPopup() {
        finish();
        overridePendingTransition(0, R.anim.slide_down_exit);
    }

    @Override
    public void onBackPressed() {
        dismissPopup();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { dismissPopup(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
