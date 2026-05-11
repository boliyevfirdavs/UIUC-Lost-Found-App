# UIUC Lost & Found

An Android app for reporting and claiming lost items on the University of Illinois at Urbana-Champaign campus. Users can post items they have found, browse the feed, search by keyword, pick a location on a map, and contact the finder to claim their belongings.

## Features

### Posts
- Create a lost-item post with a title, description, and status
- Attach up to multiple photos (uploaded to Cloudinary)
- Pin the exact find location using an interactive OpenStreetMap picker
- Mark an item as "Left in place" or "Taken by finder"

### Discovery
- Scrollable home feed showing all posted items with photos and location
- Search screen to filter posts by keyword in real time
- Pull-to-refresh to get the latest posts

### Claiming
- View full post details including photo gallery and map location
- Claim an item through a bottom sheet form
- Contact the finder directly via a contact sheet

### Accounts
- Register with name, email, and password
- Login / logout with Firebase Authentication
- Account screen showing user profile and posted items

## Screens

| Screen | Description |
|---|---|
| `MainActivity` | Entry point — redirects to Login or Home |
| `LoginActivity` | Email/password login |
| `RegisterActivity` | New account registration |
| `HomeActivity` | Bottom-nav host with Home, Search, Post, Account tabs |
| `PostListFragment` | Scrollable feed of all posts |
| `SearchActivity` | Keyword search across posts |
| `PostItemActivity` | Create a new post with photos and map location |
| `PostDetailActivity` | Full detail view with claim and contact actions |
| `MapPickerActivity` | Interactive map to pin a location |
| `AccountActivity` | User profile and their posts |

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| UI | AppCompat, Material Design Components, ConstraintLayout |
| Navigation | ViewPager2 + BottomNavigationView |
| Auth | Firebase Authentication |
| Database | Firebase Firestore |
| Image upload | Cloudinary (unsigned upload preset) |
| Image loading | Glide |
| Maps | OpenStreetMap via osmdroid (no API key required) |
| Location | Google Play Services Location |

## Requirements

- Android Studio Hedgehog or later
- Android SDK 35 (min SDK 24)
- A Firebase project with Authentication and Firestore enabled

## Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
   ```

2. Get `google-services.json` from your Firebase project console and place it at:
   ```
   app/google-services.json
   ```

3. Add your Google Maps API key to `local.properties` (create this file if it doesn't exist):
   ```
   sdk.dir=/path/to/your/Android/sdk
   MAPS_API_KEY=your_key_here
   ```

4. Open the project in Android Studio, let Gradle sync, then run on an emulator or device (API 24+).

## Project Structure

```
app/src/main/
├── java/edu/illinois/cs/cs124/ay2026/project/
│   ├── MainActivity.java
│   ├── LoginActivity.java
│   ├── RegisterActivity.java
│   ├── HomeActivity.java
│   ├── PostListFragment.java
│   ├── SearchActivity.java
│   ├── PostItemActivity.java
│   ├── PostDetailActivity.java
│   ├── MapPickerActivity.java
│   ├── AccountActivity.java
│   ├── BaseActivity.java
│   ├── Post.java
│   ├── PostAdapter.java
│   └── PhotoThumbnailAdapter.java
└── res/
    ├── layout/        # XML layouts for each screen
    ├── drawable/      # Icons and shape drawables
    ├── values/        # Colors, strings, themes
    └── anim/          # Transition animations
```