<div align="center">

  # UIUC Lost & Found

  **A campus-only Android app for reporting and reclaiming lost items at the University of Illinois at Urbana-Champaign — with map-based search, photo uploads, and a full claim workflow.**

  ![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=coffeescript)
  ![Android](https://img.shields.io/badge/Android-SDK%2024%2B-green?style=flat-square&logo=android)
  ![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-yellow?style=flat-square&logo=firebase)
  ![OpenStreetMap](https://img.shields.io/badge/Maps-OpenStreetMap-blue?style=flat-square&logo=openstreetmap)
  ![Cloudinary](https://img.shields.io/badge/Images-Cloudinary-purple?style=flat-square)

</div>

---

## The Problem

Lost items on a large university campus rarely make it back to their owners. Campus lost-and-founds are scattered, hard to reach, and don't communicate with each other. Students have no quick way to post what they found, search nearby, or connect with the person who found their item.

## What UIUC Lost & Found Does

A community-driven mobile app where UIUC students post found items with photos and a pinned location, and others can search by proximity and category to identify and claim what they lost — all behind a verified `@illinois.edu` gate.

---

## Features

### Campus-Gated Authentication
- Registration requires an `@illinois.edu` email address — no outsiders
- Email verification enforced before any login is permitted
- User profiles stored in Firestore with optional contact info override

### Location-Smart Search
- Nominatim (OpenStreetMap) autocomplete biased to UIUC's campus bounding box
- Adjustable radius slider from 100 m to 2 km
- Category filter (Keys, ID/Wallet, Electronics, Clothing/Bag, Water Bottle, Other)
- "My Location" GPS button populates the search origin in one tap
- Results sorted by distance using the Haversine formula

### Interactive Map Picker
- Full-screen OSMDroid map — tap anywhere to drop a pin
- Dark mode support via a ColorMatrix inversion filter on the tile layer
- No Google Maps API key required

### Rich Post Creation
- Title, description, and category
- Location set via autocomplete or the map picker
- Attach photos from camera or gallery; previewed in a horizontal thumbnail strip
- Multi-photo Cloudinary upload runs on a background thread sequentially
- Status toggle: "Left in place" or "Taken by finder"
- Nearby posts sidebar (within 500 m) shown while composing, to catch likely duplicates

### Photo Gallery on Detail View
- Swipeable ViewPager2 photo carousel with dot-position indicators
- Tap any photo to view it full-screen in a dialog

### Three-State Item Lifecycle
- **Active** — item is still where it was found (orange badge)
- **Taken by finder** — finder has the item with them (gray badge)
- **Claimed** — item has been returned to its owner (green badge)
- Resolved timestamp recorded when an item moves to Claimed

### Claim Workflow
- "Claim Item" triggers a confirmation bottom sheet before any action
- Claim records the claimer's name, user ID, and contact info on the post
- Finder's contact details revealed through a separate bottom sheet after claiming

### Home Feed with Tabs
- Two tabs via ViewPager2: **Active** posts and **Claimed** posts
- Pull-to-refresh on each tab
- Color-coded status badges and photo thumbnails in the card list

### Account Screen
- **My Posts** tab — all items you have reported
- **Claimed** tab — all items you have successfully claimed

### Dark Mode
- Toggle in the action bar menu, available on every screen
- Preference persisted in SharedPreferences and restored on next launch
- OSMDroid map tiles inverted with a ColorMatrix for a true dark-map experience

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| UI | AppCompat · Material Design · ConstraintLayout |
| Navigation | ViewPager2 + BottomNavigationView |
| Auth | Firebase Authentication (email/password + verification) |
| Database | Firebase Firestore |
| Image upload | Cloudinary (unsigned preset — no backend required) |
| Image loading | Glide 4 |
| Maps | OSMDroid 6 (OpenStreetMap — no API key) |
| Geocoding | Nominatim REST API (free, no key) |
| Location | Google Play Services — FusedLocationProviderClient |
| Background work | Java ExecutorService (photo upload, geocoding queries) |

---

## Screens

| Screen | Purpose |
|---|---|
| `MainActivity` | Entry point — routes to Login or Home based on auth state |
| `LoginActivity` | Email/password sign-in with verified-email check |
| `RegisterActivity` | New account with `@illinois.edu` enforcement |
| `HomeActivity` | Bottom-nav host: Home, Search, Post, Account |
| `PostListFragment` | Reusable feed fragment (Active, Claimed, My Posts, Claimed-by-me) |
| `SearchActivity` | Location + radius + category search with GPS support |
| `PostItemActivity` | Create a found-item post with photos and map location |
| `PostDetailActivity` | Full detail view, photo carousel, claim and contact actions |
| `MapPickerActivity` | Full-screen OSMDroid map with tap-to-place pin |
| `AccountActivity` | User profile, My Posts, and Claimed tabs |

---

## Project Structure

```
app/src/main/
├── java/edu/illinois/cs/cs124/ay2026/project/
│   ├── App.java                   # Dark-mode preference on startup
│   ├── BaseActivity.java          # Shared theme-toggle menu
│   ├── MainActivity.java
│   ├── LoginActivity.java
│   ├── RegisterActivity.java
│   ├── HomeActivity.java
│   ├── PostListFragment.java      # Reusable list with 4 query modes
│   ├── SearchActivity.java        # Geo search with Nominatim + radius
│   ├── PostItemActivity.java      # Post creation + Cloudinary upload
│   ├── PostDetailActivity.java    # Detail, photo carousel, claim flow
│   ├── MapPickerActivity.java     # OSMDroid full-screen picker
│   ├── AccountActivity.java
│   ├── Post.java                  # Firestore data model
│   ├── PostAdapter.java           # RecyclerView card adapter
│   └── PhotoThumbnailAdapter.java # Horizontal photo-strip adapter
└── res/
    ├── layout/        # XML layouts for every screen
    ├── drawable/      # Icons and shape drawables
    ├── values/        # Colors, strings, themes
    └── anim/          # Transition animations
```

---

## Requirements

- Android Studio Hedgehog or later
- Android SDK 35 (min SDK 24 / Android 7.0)
- A Firebase project with **Authentication** (Email/Password) and **Firestore** enabled
- A Cloudinary account with an **unsigned upload preset**

---

## Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/YOUR_USERNAME/uiuc-lost-and-found.git
   cd uiuc-lost-and-found
   ```

2. **Firebase** — download `google-services.json` from your Firebase console and place it at:
   ```
   app/google-services.json
   ```

3. **Cloudinary** — open `app/src/main/res/values/strings.xml` and fill in:
   ```xml
   <string name="cloudinary_cloud_name">your_cloud_name</string>
   <string name="cloudinary_upload_preset">your_unsigned_preset</string>
   ```

4. **Open in Android Studio**, let Gradle sync, then run on an emulator or physical device (API 24+).

> **Note:** No Google Maps API key is required. Maps are served by OpenStreetMap via OSMDroid, and geocoding is handled by the free Nominatim API.