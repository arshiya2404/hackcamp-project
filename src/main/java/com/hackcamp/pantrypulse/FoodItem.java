package com.hackcamp.pantrypulse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class FoodItem {
    private String name;
    private LocalDate purchaseDate;
    private int shelfLifeDays;
    private String storage;
    private String tip;
    private boolean needsReview;

    // Constructs a new FoodItem with full details
    public FoodItem(String name, LocalDate purchaseDate, int shelfLifeDays, String storage, String tip,
            boolean needsReview) {
        this.name = name;
        this.purchaseDate = purchaseDate;
        this.shelfLifeDays = shelfLifeDays;
        this.storage = storage;
        this.tip = tip;
        this.needsReview = needsReview;
    }

    // Computes the expiry date by adding shelf life days to the purchase date
    public LocalDate getExpiryDate() {
        return purchaseDate.plusDays(shelfLifeDays);
    }

    // Calculates the number of days remaining until expiry from today
    public long getDaysLeft() {
        return ChronoUnit.DAYS.between(LocalDate.now(), getExpiryDate());
    }

    // Returns RED (0-2 days or expired), AMBER (3-5 days), or GREEN (>5 days)
    public String getColor() {
        long daysLeft = getDaysLeft();
        if (daysLeft <= 2) {
            return "RED";
        } else if (daysLeft <= 5) {
            return "AMBER";
        } else {
            return "GREEN";
        }
    }

    // Returns the name of the food item
    public String getName() {
        return name;
    }

    // Returns the purchase date of the food item
    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    // Returns the shelf life duration in days
    public int getShelfLifeDays() {
        return shelfLifeDays;
    }

    // Returns the recommended storage location
    public String getStorage() {
        return storage;
    }

    // Returns the storage advice tip
    public String getTip() {
        return tip;
    }

    // Checks if the item requires manual user review
    public boolean isNeedsReview() {
        return needsReview;
    }

    // Updates whether the item requires review
    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    // Returns a string representation of the food item
    @Override
    public String toString() {
        return "FoodItem{" +
                "name='" + name + '\'' +
                ", purchaseDate=" + purchaseDate +
                ", shelfLifeDays=" + shelfLifeDays +
                ", storage='" + storage + '\'' +
                ", tip='" + tip + '\'' +
                ", needsReview=" + needsReview +
                ", expiryDate=" + getExpiryDate() +
                ", daysLeft=" + getDaysLeft() +
                ", color=" + getColor() +
                '}';
    }
}
