package edu.illinois.cs.cs124.ay2026.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Drives the RecyclerView on the home feed. Each item is a post card. */
public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    interface OnPostClickListener {
        void onPostClick(Post post, View cardView);
    }

    private List<Post> posts;
    private final OnPostClickListener listener;

    PostAdapter(List<Post> posts, OnPostClickListener listener) {
        this.posts = posts;
        this.listener = listener;
    }

    /** Called when the tab switches — swap the backing list and refresh. */
    void swapList(List<Post> newList) {
        this.posts = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post);
        View card = holder.itemView.findViewById(R.id.card_view);
        View sharedView = card != null ? card : holder.itemView;
        holder.itemView.setOnClickListener(v -> listener.onPostClick(post, sharedView));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView categoryView;
        private final TextView locationView;
        private final TextView statusView;
        private final TextView finderView;
        private final TextView finderAvatarView;
        private final TextView timeView;
        private final ImageView thumbnailView;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.post_title);
            categoryView = itemView.findViewById(R.id.post_category);
            locationView = itemView.findViewById(R.id.post_location);
            statusView = itemView.findViewById(R.id.post_status);
            finderView = itemView.findViewById(R.id.post_finder);
            finderAvatarView = itemView.findViewById(R.id.finder_avatar);
            timeView = itemView.findViewById(R.id.post_time);
            thumbnailView = itemView.findViewById(R.id.post_thumbnail);
        }

        void bind(Post post) {
            titleView.setText(post.getTitle());
            categoryView.setText(post.getCategory());
            locationView.setText(post.getLocationLabel());

            boolean isClaimed = "claimed".equals(post.getStatus());
            boolean isTaken = "taken_by_finder".equals(post.getStatus());

            // Color-code the status badge
            int badgeColor;
            if (isClaimed) {
                badgeColor = ContextCompat.getColor(itemView.getContext(), R.color.status_claimed);
            } else if (isTaken) {
                badgeColor = ContextCompat.getColor(itemView.getContext(), R.color.status_taken);
            } else {
                badgeColor = ContextCompat.getColor(itemView.getContext(), R.color.status_active);
            }
            // Mutate so recycled views don't share drawable state
            DrawableCompat.setTint(statusView.getBackground().mutate(), badgeColor);

            // Avatar: first letter of whoever is highlighted (claimer or finder)
            String avatarName = post.getFinderName();
            if (isClaimed && post.getClaimedByName() != null
                    && !post.getClaimedByName().isEmpty()) {
                avatarName = post.getClaimedByName();
            }
            if (avatarName != null && !avatarName.isEmpty()) {
                finderAvatarView.setText(String.valueOf(Character.toUpperCase(avatarName.charAt(0))));
            } else {
                finderAvatarView.setText("?");
            }

            if (isClaimed) {
                statusView.setText("Claimed");
                // Show who claimed it and when
                String claimer = post.getClaimedByName();
                if (claimer != null && !claimer.isEmpty()) {
                    finderView.setText(itemView.getContext().getString(
                            R.string.claimed_by, claimer));
                } else {
                    finderView.setText(itemView.getContext().getString(
                            R.string.found_by, post.getFinderName()));
                }
                if (post.getResolvedAt() != null) {
                    String resolved = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            .format(post.getResolvedAt().toDate());
                    timeView.setText("Claimed " + resolved);
                }
            } else {
                statusView.setText(isTaken
                        ? itemView.getContext().getString(R.string.status_taken)
                        : itemView.getContext().getString(R.string.status_left));
                finderView.setText(itemView.getContext().getString(
                        R.string.found_by, post.getFinderName()));
                if (post.getCreatedAt() != null) {
                    String formatted = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            .format(post.getCreatedAt().toDate());
                    timeView.setText(formatted);
                }
            }

            List<String> photoUrls = post.getPhotoUrls();
            if (photoUrls != null && !photoUrls.isEmpty()) {
                thumbnailView.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(photoUrls.get(0))
                        .centerCrop()
                        .into(thumbnailView);
            } else {
                thumbnailView.setVisibility(View.GONE);
            }
        }
    }
}
