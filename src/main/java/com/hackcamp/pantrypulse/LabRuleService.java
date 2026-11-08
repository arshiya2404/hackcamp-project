package com.hackcamp.pantrypulse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabRuleService {

    public static final Set<String> SUPPORTED_FOCUS = Set.of(
        "WATCH_SATURATED_FAT",
        "WATCH_ADDED_SUGAR",
        "DISCUSS_IRON_FOODS"
    );

    private static final Set<String> ALLOWED_FLAGS = Set.of(
        "HIGH", "LOW", "NORMAL", "UNKNOWN"
    );

    public FocusProfile buildAutomatic(LabExtraction extraction) {
        if (extraction == null || extraction.markers() == null) {
            throw badRequest("The extracted report was incomplete.");
        }

        return createProfile(
            extraction.markers(),
            true,
            "Findings reflect extracted report flags, not diagnoses. "
                + "Check accuracy and follow your clinician's advice."
        );
    }

    // Preserves compatibility with the existing confirmation endpoint.
    public FocusProfile build(LabConfirmation request) {
        if (request == null
                || !Boolean.TRUE.equals(request.confirmed())
                || request.conditionOrMedication() == null
                || request.enableFoodFocus() == null) {
            throw badRequest("The confirmation request was incomplete.");
        }

        boolean enabled =
            Boolean.TRUE.equals(request.enableFoodFocus())
                && !Boolean.TRUE.equals(
                    request.conditionOrMedication()
                );

        return createProfile(
            request.markers(),
            enabled,
            enabled
                ? "Food preferences reflect reviewed report flags."
                : "Automatic food preferences are off."
        );
    }

    private FocusProfile createProfile(
            List<LabMarker> markers,
            boolean enabled,
            String message) {

        if (markers == null || markers.size() > 30) {
            throw badRequest("A report may contain at most 30 results.");
        }

        Map<String, Integer> counts = new HashMap<>();

        for (LabMarker marker : markers) {
            if (!usable(marker)) {
                continue;
            }

            String canonical = canonicalName(marker.name());

            if (!canonical.equals("unsupported")) {
                counts.merge(canonical, 1, Integer::sum);
            }
        }

        List<LabCard> cards = new ArrayList<>();
        Set<String> focusAreas = new LinkedHashSet<>();

        for (LabMarker marker : markers) {
            if (!usable(marker)) {
                continue;
            }

            String canonical = canonicalName(marker.name());

            if (canonical.equals("unsupported")) {
                continue;
            }

            String flag = normalizeFlag(marker.flag());

            // Keep duplicate findings visible, but do not automatically
            // choose a food preference from potentially conflicting results.
            boolean canCreateFocus =
                enabled && counts.getOrDefault(canonical, 0) == 1;

            String explanation = switch (canonical) {
                case "ldl" ->
                    "LDL cholesterol result.";

                case "hdl" ->
                    "HDL cholesterol result.";

                case "total_cholesterol" ->
                    "Total cholesterol result.";

                case "non_hdl" ->
                    "Non-HDL cholesterol result.";

                case "cholesterol_ratio" ->
                    "Cholesterol ratio result.";

                case "glucose" ->
                    "Blood glucose result.";

                case "hba1c" ->
                    "HbA1c result.";

                case "ferritin" ->
                    "Ferritin result, related to stored iron.";

                case "iron" ->
                    "Iron result.";

                case "vitamin_a" ->
                    "Vitamin A result.";

                case "vitamin_b12" ->
                    "Vitamin B12 result.";

                case "folate" ->
                    "Folate / vitamin B9 result.";

                case "vitamin_b" ->
                    "B-vitamin result.";

                case "vitamin_d" ->
                    "Vitamin D result.";

                default ->
                    "Report result.";
            };

            String discussion = switch (flag) {
                case "HIGH" ->
                    "Flagged high on the uploaded report.";

                case "LOW" ->
                    "Flagged low on the uploaded report.";

                case "NORMAL" ->
                    "Marked normal on the uploaded report.";

                default ->
                    "No explicit high or low flag was extracted.";
            };

            if (canonical.equals("ldl")
                    && flag.equals("HIGH")
                    && canCreateFocus) {
                focusAreas.add("WATCH_SATURATED_FAT");

                discussion +=
                    " Saturated-fat choices will be considered "
                    + "when suggesting recipes.";
            }

            if (canonical.equals("ferritin")
                    && flag.equals("LOW")
                    && canCreateFocus) {
                focusAreas.add("DISCUSS_IRON_FOODS");

                discussion +=
                    " Iron-containing foods will be considered "
                    + "when suggesting recipes.";
            }

            if (canonical.equals("ferritin")
                    && flag.equals("HIGH")) {
                discussion +=
                    " An increase-iron preference has not been added.";
            }

            if (flag.equals("HIGH") || flag.equals("LOW")) {
                discussion +=
                    " Discuss the result with your clinician.";
            }

            cards.add(new LabCard(
                marker.name().trim(),
                flag,
                explanation,
                discussion,
                sourceFor(canonical)
            ));
        }

        return new FocusProfile(
            List.copyOf(focusAreas),
            List.copyOf(cards),
            message
        );
    }

    private boolean usable(LabMarker marker) {
        return marker != null
            && marker.name() != null
            && !marker.name().isBlank()
            && marker.value() != null
            && marker.value().signum() >= 0;
    }

    private String normalizeFlag(String flag) {
        if (flag == null) {
            return "UNKNOWN";
        }

        String normalized =
            flag.trim().toUpperCase(Locale.ROOT);

        normalized = switch (normalized) {
            case "H" -> "HIGH";
            case "L" -> "LOW";
            default -> normalized;
        };

        return ALLOWED_FLAGS.contains(normalized)
            ? normalized
            : "UNKNOWN";
    }

    private String canonicalName(String name) {
        if (name == null) {
            return "unsupported";
        }

        String normalized = name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");

        // Check specific cholesterol names before broader names.
        if (normalized.contains("nonhdl")) {
            return "non_hdl";
        }

        if (normalized.contains("ratio")
                && (normalized.contains("cholesterol")
                    || normalized.contains("hdl"))) {
            return "cholesterol_ratio";
        }

        if (normalized.equals("ldl")
                || normalized.equals("ldlc")
                || normalized.contains("ldlcholesterol")
                || normalized.contains("cholesterolldl")) {
            return "ldl";
        }

        if (normalized.equals("hdl")
                || normalized.equals("hdlc")
                || normalized.contains("hdlcholesterol")
                || normalized.contains("cholesterolhdl")) {
            return "hdl";
        }

        if (normalized.equals("cholesterol")
                || normalized.contains("totalcholesterol")
                || normalized.contains("cholesteroltotal")) {
            return "total_cholesterol";
        }

        if (normalized.contains("hba1c")
                || normalized.equals("a1c")
                || normalized.contains("hemoglobina1c")
                || normalized.contains("haemoglobina1c")
                || normalized.contains("glycatedhemoglobin")) {
            return "hba1c";
        }

        if (normalized.contains("glucose")) {
            return "glucose";
        }

        if (normalized.contains("ferritin")) {
            return "ferritin";
        }

        if (Set.of(
                "iron",
                "serumiron",
                "ironserum",
                "plasmairon",
                "ironplasma",
                "fetotal",
                "fe"
            ).contains(normalized)) {
            return "iron";
        }

        if (normalized.contains("vitaminb12")
                || normalized.contains("cobalamin")
                || normalized.equals("b12")) {
            return "vitamin_b12";
        }

        if (normalized.contains("folate")
                || normalized.contains("folicacid")
                || normalized.contains("vitaminb9")) {
            return "folate";
        }

        if (normalized.contains("vitamind")
                || normalized.contains("hydroxyvitamind")
                || normalized.contains("25ohd")) {
            return "vitamin_d";
        }

        if (normalized.contains("vitamina")
                || normalized.contains("retinol")) {
            return "vitamin_a";
        }

        if (normalized.contains("vitaminb")
                || normalized.contains("thiamine")
                || normalized.contains("riboflavin")
                || normalized.contains("niacin")
                || normalized.contains("pyridoxine")
                || normalized.contains("biotin")) {
            return "vitamin_b";
        }

        return "unsupported";
    }

    private String sourceFor(String canonical) {
        return switch (canonical) {
            case "ldl", "hdl", "total_cholesterol",
                    "non_hdl", "cholesterol_ratio" ->
                "https://medlineplus.gov/lab-tests/cholesterol-levels/";

            case "glucose" ->
                "https://medlineplus.gov/lab-tests/blood-glucose-test/";

            case "hba1c" ->
                "https://www.niddk.nih.gov/health-information/"
                    + "diagnostic-tests/a1c-test";

            case "ferritin" ->
                "https://medlineplus.gov/lab-tests/ferritin-blood-test/";

            case "iron" ->
                "https://medlineplus.gov/lab-tests/iron-tests/";

            case "vitamin_d" ->
                "https://medlineplus.gov/lab-tests/vitamin-d-test/";

            case "vitamin_b", "vitamin_b12", "folate" ->
                "https://medlineplus.gov/lab-tests/vitamin-b-test/";

            default ->
                "https://medlineplus.gov/lab-tests/";
        };
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        );
    }
}