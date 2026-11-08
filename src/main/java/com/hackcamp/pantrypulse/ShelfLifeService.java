package com.hackcamp.pantrypulse;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class ShelfLifeService {

    private final Map<String, ShelfLifeEntry> lookup = new HashMap<>();

    // Convenience assumptions when a receipt omits the product type.
    private static final Map<String, String> ASSUMED_NAMES = Map.ofEntries(
            Map.entry("chickpeas", "canned chickpeas"),
            Map.entry("chickpea", "canned chickpeas"),
            Map.entry("garbanzo beans", "canned chickpeas"),
            Map.entry("kidney beans", "canned kidney beans"),
            Map.entry("red kidney beans", "canned kidney beans"),
            Map.entry("black beans", "canned black beans"),
            Map.entry("corn", "canned corn"),
            Map.entry("sweetcorn", "canned corn"),
            Map.entry("sweet corn", "canned corn"),
            Map.entry("tuna", "canned tuna"),
            Map.entry("soup", "canned soup"),
            Map.entry("coconut milk", "canned coconut milk"));

    public ShelfLifeService(ObjectMapper mapper) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/shelf-life.json")) {

            if (input == null) {
                throw new IOException("Missing shelf-life.json");
            }

            ShelfLifeEntry[] entries = mapper.readValue(input, ShelfLifeEntry[].class);

            if (entries == null) {
                throw new IOException("Invalid shelf-life.json");
            }

            for (ShelfLifeEntry entry : entries) {
                if (entry == null
                        || entry.name() == null
                        || entry.name().isBlank()
                        || entry.storage() == null
                        || entry.storage().isBlank()
                        || entry.days() < 0
                        || entry.tip() == null) {
                    throw new IOException("Invalid shelf-life entry");
                }

                register(entry.name(), entry);

                List<String> aliases = entry.aliases();

                if (aliases != null) {
                    for (String alias : aliases) {
                        if (alias == null || alias.isBlank()) {
                            throw new IOException(
                                    "Blank alias for " + entry.name());
                        }

                        register(alias, entry);
                    }
                }
            }
        }
    }

    private void register(
            String name,
            ShelfLifeEntry entry) throws IOException {

        String key = normalize(name);
        ShelfLifeEntry existing = lookup.putIfAbsent(key, entry);

        if (existing != null && !existing.equals(entry)) {
            throw new IOException(
                    "Duplicate food name or alias: " + name);
        }
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public FoodItem createItem(
            String name,
            LocalDate purchaseDate) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Food name is required");
        }

        if (purchaseDate == null) {
            throw new IllegalArgumentException(
                    "Purchase date is required");
        }

        String query = normalize(name);

        // Exact names and aliases take priority over assumptions.
        ShelfLifeEntry entry = lookup.get(query);
        boolean assumed = false;

        if (entry == null) {
            String assumedName = ASSUMED_NAMES.get(query);

            if (assumedName != null) {
                entry = lookup.get(normalize(assumedName));
                assumed = entry != null;
            }
        }

        if (entry == null) {
            return new FoodItem(
                    name.trim(),
                    purchaseDate,
                    5,
                    "review",
                    "Suggested location: fridge. Check the package for storage "
                            + "instructions and its printed date. "
                            + "The five-day reminder is provisional.",
                    true);
        }

        String tip = entry.tip();

        if (assumed) {
            tip = "Suggested match: " + entry.name()
                    + ". Check the package label. " + tip;
        }

        return new FoodItem(
                name.trim(),
                purchaseDate,
                entry.days(),
                entry.storage(),
                tip,
                assumed);
    }
}