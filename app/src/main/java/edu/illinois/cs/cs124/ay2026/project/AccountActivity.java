package edu.illinois.cs.cs124.ay2026.project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AccountActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        TextView avatarView = findViewById(R.id.account_avatar);
        TextView nameView = findViewById(R.id.account_name);
        TextView emailView = findViewById(R.id.account_email);

        if (user != null) {
            String displayName = user.getDisplayName();
            String name = (displayName != null && !displayName.isEmpty())
                    ? displayName : "No name set";
            nameView.setText(name);
            emailView.setText(user.getEmail());
            // Show first letter of name in the avatar circle
            avatarView.setText(String.valueOf(name.charAt(0)).toUpperCase());
        }

        MaterialButton logoutButton = findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabs = findViewById(R.id.account_tabs);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return PostListFragment.newInstance(
                        position == 0 ? PostListFragment.TYPE_MY_POSTS
                                      : PostListFragment.TYPE_CLAIMED);
            }

            @Override
            public int getItemCount() { return 2; }
        });


        new TabLayoutMediator(tabs, viewPager,
                (tab, position) -> tab.setText(
                        position == 0 ? "My Posts" : "Claimed"))
                .attach();

        setupBottomNav();
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_account);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                navigateTab(HomeActivity.class);
                return true;
            } else if (id == R.id.nav_search) {
                navigateTab(SearchActivity.class);
                return true;
            } else if (id == R.id.nav_post) {
                startActivity(new Intent(this, PostItemActivity.class));
                overridePendingTransition(R.anim.slide_up_enter, R.anim.none);
                return false;
            } else if (id == R.id.nav_account) {
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
}
