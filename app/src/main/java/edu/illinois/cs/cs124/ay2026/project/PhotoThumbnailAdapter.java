package edu.illinois.cs.cs124.ay2026.project;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

/** Adapter that shows a horizontal strip of selected photo thumbnails in PostItemActivity. */
public class PhotoThumbnailAdapter extends RecyclerView.Adapter<PhotoThumbnailAdapter.ViewHolder> {

    interface OnRemoveListener {
        void onRemove(int position);
    }

    private final List<Uri> uris;
    private final OnRemoveListener removeListener;

    public PhotoThumbnailAdapter(List<Uri> uris, OnRemoveListener removeListener) {
        this.uris = uris;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_thumbnail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Glide.with(holder.thumbnailImage.getContext())
                .load(uris.get(position))
                .centerCrop()
                .into(holder.thumbnailImage);
        holder.removeButton.setOnClickListener(v -> removeListener.onRemove(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnailImage;
        final ImageButton removeButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.thumbnail_image);
            removeButton = itemView.findViewById(R.id.remove_button);
        }
    }
}
