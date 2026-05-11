package edu.illinois.cs.cs124.ay2026.project;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class HomeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return PostListFragment.newInstance(
                        position == 0 ? PostListFragment.TYPE_ACTIVE
                                      : PostListFragment.TYPE_RESOLVED);
            }

            @Override
            public int getItemCount() { return 2; }
        });

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Active" : "Claimed"))
                .attach();

        setupBottomNav();
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_search) {
                navigateTab(SearchActivity.class);
                return true;
            }
            if (id == R.id.nav_post) {
                startActivity(new Intent(this, PostItemActivity.class));
                overridePendingTransition(R.anim.slide_up_enter, R.anim.none);
                return false;
            }
            if (id == R.id.nav_account) {
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
}
