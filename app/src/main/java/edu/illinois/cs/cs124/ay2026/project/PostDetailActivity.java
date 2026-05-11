package edu.illinois.cs.cs124.ay2026.project;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PostDetailActivity extends BaseActivity {

    private TextView titleView;
    private View descriptionCard;
    private TextView descriptionView;
    private TextView categoryView;
    private TextView locationView;
    private View locationRow;
    private TextView statusView;
    private View finderRow;
    private TextView finderAvatarView;
    private TextView finderNameView;
    private View claimerDivider;
    private View claimerRow;
    private TextView claimerAvatarView;
    private TextView claimerNameView;
    private TextView timeView;
    private TextView resolvedDateView;
    private View contactSection;
    private TextView contactView;
    private View photoCard;
    private ViewPager2 photoPager;
    private LinearLayout photoDots;
    private MaterialButton resolveButton;

    private String postId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // ic_close is set as navigationIcon in layout XML; just enable home-up
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        titleView = findViewById(R.id.post_title);
        descriptionCard = findViewById(R.id.description_card);
        descriptionView = findViewById(R.id.post_description);
        categoryView = findViewById(R.id.post_category);
        locationView = findViewById(R.id.post_location);
        locationRow = findViewById(R.id.location_row);
        statusView = findViewById(R.id.post_status);
        finderRow = findViewById(R.id.finder_row);
        finderAvatarView = findViewById(R.id.finder_avatar);
        finderNameView = findViewById(R.id.post_finder);
        claimerDivider = findViewById(R.id.claimer_divider);
        claimerRow = findViewById(R.id.claimer_row);
        claimerAvatarView = findViewById(R.id.claimer_avatar);
        claimerNameView = findViewById(R.id.claimer_name);
        timeView = findViewById(R.id.post_time);
        resolvedDateView = findViewById(R.id.post_resolved_date);
        contactSection = findViewById(R.id.contact_section);
        contactView = findViewById(R.id.post_contact);
        photoCard = findViewById(R.id.photo_card);
        photoPager = findViewById(R.id.photo_pager);
        photoDots = findViewById(R.id.photo_dots);
        resolveButton = findViewById(R.id.resolve_button);

        postId = getIntent().getStringExtra("postId");
        if (postId != null) loadPost(postId);

    }

    private void loadPost(String id) {
        FirebaseFirestore.getInstance()
                .collection("posts")
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Post post = doc.toObject(Post.class);
                    if (post == null) return;
                    if (post.getId() == null) post.setId(doc.getId());

                    titleView.setText(post.getTitle());

                    // Hide description card entirely when there is no description
                    String description = post.getDescription();
                    if (description != null && !description.isEmpty()) {
                        descriptionView.setText(description);
                        descriptionCard.setVisibility(View.VISIBLE);
                    } else {
                        descriptionCard.setVisibility(View.GONE);
                    }

                    categoryView.setText(post.getCategory());
                    locationView.setText(post.getLocationLabel());

                    // Location row — tap to open in maps
                    locationRow.setOnClickListener(v -> openLocationOnMap(
                            post.getLatitude(), post.getLongitude(), post.getLocationLabel()));

                    // Finder avatar + name — tap to see contact info
                    String finderName = post.getFinderName();
                    finderNameView.setText(finderName != null ? finderName : "Unknown");
                    if (finderName != null && !finderName.isEmpty()) {
                        finderAvatarView.setText(
                                String.valueOf(Character.toUpperCase(finderName.charAt(0))));
                    } else {
                        finderAvatarView.setText("?");
                    }
                    finderRow.setOnClickListener(v -> showFinderContact(post));

                    boolean isClaimed = "claimed".equals(post.getStatus());
                    boolean takenByFinder = "taken_by_finder".equals(post.getStatus());

                    // Status badge text + color (consistent with the list cards)
                    int badgeColor;
                    if (isClaimed) {
                        statusView.setText("Claimed");
                        badgeColor = ContextCompat.getColor(this, R.color.status_claimed);
                    } else if (takenByFinder) {
                        statusView.setText(getString(R.string.status_taken));
                        badgeColor = ContextCompat.getColor(this, R.color.status_taken);
                    } else {
                        statusView.setText(getString(R.string.status_left));
                        badgeColor = ContextCompat.getColor(this, R.color.status_active);
                    }
                    DrawableCompat.setTint(statusView.getBackground().mutate(), badgeColor);

                    // Show who claimed it — full row matching the "Posted by" row
                    if (isClaimed && post.getClaimedByName() != null
                            && !post.getClaimedByName().isEmpty()) {
                        String claimerName = post.getClaimedByName();
                        claimerNameView.setText(claimerName);
                        claimerAvatarView.setText(
                                String.valueOf(Character.toUpperCase(claimerName.charAt(0))));
                        claimerDivider.setVisibility(View.VISIBLE);
                        claimerRow.setVisibility(View.VISIBLE);
                        claimerRow.setOnClickListener(v -> showClaimerContact(post));

                        if (post.getResolvedAt() != null) {
                            String resolvedFormatted = new SimpleDateFormat(
                                    "MMM d, yyyy", Locale.getDefault())
                                    .format(post.getResolvedAt().toDate());
                            resolvedDateView.setText(resolvedFormatted);
                            resolvedDateView.setVisibility(View.VISIBLE);
                        }
                    }

                    // Show contact info if taken by finder
                    if (takenByFinder
                            && post.getFinderContactInfo() != null
                            && !post.getFinderContactInfo().isEmpty()) {
                        contactSection.setVisibility(View.VISIBLE);
                        contactView.setText(post.getFinderContactInfo());
                    }

                    // Show photos in a swipeable pager
                    List<String> photoUrls = post.getPhotoUrls();
                    if (photoUrls != null && !photoUrls.isEmpty()) {
                        photoCard.setVisibility(View.VISIBLE);
                        photoPager.setAdapter(new PhotoPagerAdapter(photoUrls));
                        setupPhotoDots(photoUrls);
                    }

                    if (post.getCreatedAt() != null) {
                        String formatted = new SimpleDateFormat(
                                "MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                                .format(post.getCreatedAt().toDate());
                        timeView.setText(formatted);
                    }

                    // Show "Claim" button to any logged-in user while the post is unclaimed
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    String currentUid = currentUser != null ? currentUser.getUid() : null;
                    if (!isClaimed && currentUid != null) {
                        resolveButton.setVisibility(View.VISIBLE);
                        resolveButton.setOnClickListener(v -> confirmClaim(currentUser));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load post.", Toast.LENGTH_SHORT).show());
    }

    private void confirmClaim(FirebaseUser claimant) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.sheet_claim, null);
        sheetView.findViewById(R.id.sheet_confirm).setOnClickListener(v -> {
            sheet.dismiss();
            claimPost(claimant);
        });
        sheetView.findViewById(R.id.sheet_cancel).setOnClickListener(v -> sheet.dismiss());
        sheet.setContentView(sheetView);
        sheet.show();
    }

    private void claimPost(FirebaseUser claimant) {
        resolveButton.setEnabled(false);
        String displayName = claimant.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = claimant.getEmail();
        }
        final String finalName = displayName;

        // Look up the claimer's stored contact info from their user profile
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(claimant.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {
                    String contactInfo = null;
                    if (userDoc.exists()) {
                        contactInfo = userDoc.getString("contactInfo");
                    }
                    // Fall back to email if no contact info was found
                    if (contactInfo == null || contactInfo.isEmpty()) {
                        contactInfo = claimant.getEmail();
                    }

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "claimed");
                    update.put("resolvedAt", Timestamp.now());
                    update.put("claimedByUserId", claimant.getUid());
                    update.put("claimedByName", finalName);
                    update.put("claimedByContactInfo", contactInfo);

                    FirebaseFirestore.getInstance()
                            .collection("posts")
                            .document(postId)
                            .update(update)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Item claimed!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                resolveButton.setEnabled(true);
                                Toast.makeText(this, "Failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    resolveButton.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void openLocationOnMap(double lat, double lng, String label) {
        Uri geoUri;
        if (lat != 0 || lng != 0) {
            String query = lat + "," + lng + "(" + Uri.encode(label != null ? label : "") + ")";
            geoUri = Uri.parse("geo:" + lat + "," + lng + "?q=" + query);
        } else if (label != null && !label.isEmpty()) {
            geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(label));
        } else {
            Toast.makeText(this, "No location available.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, geoUri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No map app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showContactSheet(String personName, String contactInfo) {
        String message = (contactInfo != null && !contactInfo.isEmpty())
                ? contactInfo
                : "No contact information available.";

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.sheet_contact, null);
        ((TextView) sheetView.findViewById(R.id.sheet_title))
                .setText("Contact " + personName);
        ((TextView) sheetView.findViewById(R.id.sheet_contact))
                .setText(message);
        sheetView.findViewById(R.id.sheet_close).setOnClickListener(v -> sheet.dismiss());
        sheet.setContentView(sheetView);
        sheet.show();
    }

    private void showFinderContact(Post post) {
        showContactSheet(post.getFinderName(), post.getFinderContactInfo());
    }

    private void showClaimerContact(Post post) {
        showContactSheet(post.getClaimedByName(), post.getClaimedByContactInfo());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ── Photo pager ─────────────────────────────────────────────────────────

    private class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.VH> {
        private final List<String> urls;

        PhotoPagerAdapter(List<String> urls) { this.urls = urls; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String url = urls.get(position);
            Glide.with(holder.imageView.getContext()).load(url).centerCrop().into(holder.imageView);
            holder.imageView.setOnClickListener(v -> showFullScreenPhoto(url));
        }

        @Override
        public int getItemCount() { return urls.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView imageView;
            VH(ImageView iv) { super(iv); imageView = iv; }
        }
    }

    private void setupPhotoDots(List<String> urls) {
        if (urls.size() <= 1) {
            photoDots.setVisibility(View.GONE);
            return;
        }
        photoDots.setVisibility(View.VISIBLE);
        View[] dots = new View[urls.size()];
        int size = Math.round(8 * getResources().getDisplayMetrics().density);
        int margin = Math.round(4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < urls.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackground(dotDrawable(i == 0));
            dots[i] = dot;
            photoDots.addView(dot);
        }
        photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < dots.length; i++) {
                    dots[i].setBackground(dotDrawable(i == position));
                }
            }
        });
    }

    private GradientDrawable dotDrawable(boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(active
                ? ContextCompat.getColor(this, R.color.orange)
                : ContextCompat.getColor(this, R.color.dot_inactive));
        return d;
    }

    private void showFullScreenPhoto(String url) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(url).into(iv);
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(iv);
        dialog.show();
    }
}
