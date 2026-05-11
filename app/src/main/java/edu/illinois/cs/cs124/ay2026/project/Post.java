package edu.illinois.cs.cs124.ay2026.project;

import com.google.firebase.Timestamp;
import java.util.List;

/** Represents a found-item post stored in Firestore. */
public class Post {

    private String id;
    private String title;
    private String description;
    private String category;
    private String locationLabel;
    private String status; // active | taken_by_finder | claimed | archived
    private String finderUserId;
    private String finderName;
    private String finderContactInfo;
    private Timestamp createdAt;
    private Timestamp resolvedAt;
    private List<String> photoUrls;
    private double latitude;
    private double longitude;
    private String claimedByUserId;
    private String claimedByName;
    private String claimedByContactInfo;

    // Required no-arg constructor for Firestore deserialization
    public Post() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLocationLabel() { return locationLabel; }
    public String getStatus() { return status; }
    public String getFinderUserId() { return finderUserId; }
    public String getFinderName() { return finderName; }
    public String getFinderContactInfo() { return finderContactInfo; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getResolvedAt() { return resolvedAt; }
    public List<String> getPhotoUrls() { return photoUrls; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getClaimedByUserId() { return claimedByUserId; }
    public String getClaimedByName() { return claimedByName; }
    public String getClaimedByContactInfo() { return claimedByContactInfo; }
}