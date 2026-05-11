package edu.illinois.cs.cs124.ay2026.project;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.TypedValue;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reusable fragment that shows a filtered, sorted list of posts. */
public class PostListFragment extends Fragment {

    public static final String TYPE_ACTIVE = "active";
    public static final String TYPE_RESOLVED = "resolved";
    public static final String TYPE_MY_POSTS = "my_posts";
    public static final String TYPE_CLAIMED = "claimed_by_me";

    private static final String ARG_TYPE = "type";
    private static final String ARG_CATEGORY = "category";

    public static PostListFragment newInstance(String type) {
        PostListFragment f = new PostListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        f.setArguments(args);
        return f;
    }

    public static PostListFragment newInstance(String type, String category) {
        PostListFragment f = new PostListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        if (category != null) args.putString(ARG_CATEGORY, category);
        f.setArguments(args);
        return f;
    }

    private final List<Post> posts = new ArrayList<>();
    private PostAdapter adapter;
    private TextView emptyView;
    private View progressBar;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_post_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        progressBar = view.findViewById(R.id.progress_bar);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        swipeRefresh.setColorSchemeResources(R.color.orange, R.color.navy);
        // Make the spinner circle use the theme's surface color (dark in dark mode)
        TypedValue surfaceColor = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, surfaceColor, true);
        swipeRefresh.setProgressBackgroundColorSchemeColor(surfaceColor.data);
        swipeRefresh.setOnRefreshListener(this::loadPosts);

        adapter = new PostAdapter(posts, (post, cardView) -> {
            Intent intent = new Intent(requireContext(), PostDetailActivity.class);
            intent.putExtra("postId", post.getId());
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        loadPosts();
    }

    private void loadPosts() {
        if (!swipeRefresh.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        emptyView.setVisibility(View.GONE);

        String type = requireArguments().getString(ARG_TYPE, TYPE_ACTIVE);
        String category = requireArguments().getString(ARG_CATEGORY, null);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query;

        switch (type) {
            case TYPE_RESOLVED:
                query = db.collection("posts").whereEqualTo("status", "claimed");
                break;
            case TYPE_MY_POSTS: {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) { showEmpty(type); return; }
                query = db.collection("posts").whereEqualTo("finderUserId", user.getUid());
                break;
            }
            case TYPE_CLAIMED: {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) { showEmpty(type); return; }
                query = db.collection("posts").whereEqualTo("claimedByUserId", user.getUid());
                break;
            }
            default:
                query = db.collection("posts")
                        .whereIn("status", Arrays.asList("active", "taken_by_finder"));
                break;
        }

        // Optional category filter (used by Home screen chip selection)
        if (category != null && !category.isEmpty()) {
            query = query.whereEqualTo("category", category);
        }

        final String finalType = type;
        query.get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    posts.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) {
                            if (post.getId() == null) post.setId(doc.getId());
                            posts.add(post);
                        }
                    }
                    posts.sort((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    adapter.notifyDataSetChanged();
                    boolean empty = posts.isEmpty();
                    if (empty) emptyView.setText(getEmptyMessage(finalType));
                    emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    swipeRefresh.setRefreshing(false);
                    showEmpty(finalType);
                });
    }

    private void showEmpty(String type) {
        progressBar.setVisibility(View.GONE);
        emptyView.setText(getEmptyMessage(type));
        emptyView.setVisibility(View.VISIBLE);
    }

    private String getEmptyMessage(String type) {
        switch (type) {
            case TYPE_RESOLVED: return "No claimed items yet.";
            case TYPE_MY_POSTS: return "You haven't posted anything yet.";
            case TYPE_CLAIMED: return "You haven't claimed any items yet.";
            default: return "No items yet. Be the first to post!";
        }
    }
}
