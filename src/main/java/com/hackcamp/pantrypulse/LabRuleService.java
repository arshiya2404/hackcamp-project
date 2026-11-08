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
            "DISCUSS_IRON_FOODS");

    private static final String A1C_SOURCE = "https://www.niddk.nih.gov/health-information/diagnostic-tests/a1c-test";

    private static final String LIPID_SOURCE = "https://www.healthlinkbc.ca/healthlinkbc-files/"
            + "healthy-eating-lower-high-cholesterol-and-triglycerides";

    private static final String FERRITIN_SOURCE = "https://medlineplus.gov/lab-tests/ferritin-blood-test/";

    private static final String GENERAL_SOURCE = "https://medlineplus.gov/lab-tests/";

    public FocusProfile buildAutomatic(LabExtraction extraction) {
        if (extraction == null || extraction.markers() == null) {
            throw badRequest("The extracted report was incomplete.");
        }

        return createProfile(
                extraction.markers(),
                true,
                "Food preferences were suggested from the report's extracted "
                        + "flags. Check their accuracy and follow your clinician's "
                        + "individual advice.");
    }

    // Retained so the older confirmation endpoint still works.
    public FocusProfile build(LabConfirmation request) {
        if (request == null
                || !Boolean.TRUE.equals(request.confirmed())
                || request.conditionOrMedication() == null
                || request.enableFoodFocus() == null) {
            throw badRequest("The confirmation request was incomplete.");
        }

        boolean enabled = Boolean.TRUE.equals(request.enableFoodFocus())
                && !Boolean.TRUE.equals(request.conditionOrMedication());

        return createProfile(
                request.markers(),
                enabled,
                enabled
                        ? "Food preferences were created from reviewed report flags."
                        : "Automatic food preferences are off.");
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
            if (marker == null
                    || marker.name() == null
                    || marker.name().isBlank()) {
                throw badRequest("An extracted result has no test name.");
            }

            String canonical = canonicalName(marker.name());

            if (!canonical.equals("unsupported")) {
                counts.merge(canonical, 1, Integer::sum);
            }
        }

        List<LabCard> cards = new ArrayList<>();
        Set<String> focusAreas = new LinkedHashSet<>();

        for (LabMarker marker : markers) {
            String canonical = canonicalName(marker.name());

            String flag = marker.flag() == null
                    ? "UNKNOWN"
                    : marker.flag().trim().toUpperCase(Locale.ROOT);

            if (!Set.of("HIGH", "LOW", "NORMAL", "UNKNOWN")
                    .contains(flag)) {
                flag = "UNKNOWN";
            }

            boolean complete = marker.value() != null
                    && marker.value().signum() >= 0
                    && marker.unit() != null
                    && !marker.unit().isBlank()
                    && marker.referenceRange() != null
                    && !marker.referenceRange().isBlank();

            boolean duplicate = counts.getOrDefault(canonical, 0) > 1;

            if (duplicate) {
                cards.add(new LabCard(
                        marker.name(),
                        flag,
                        "Multiple results for this marker were extracted.",
                        "No food preference was created for this marker. "
                                + "Review the report with your clinician.",
                        sourceFor(canonical)));
                continue;
            }

            if (!complete && !canonical.equals("unsupported")) {
                cards.add(new LabCard(
                        marker.name(),
                        flag,
                        "Some information for this result was unreadable "
                                + "or missing.",
                        "No food preference was created from this result.",
                        sourceFor(canonical)));
                continue;
            }

            String explanation;
            String discussion;

            switch (canonical) {
                case "ldl" -> {
                    explanation = "LDL is a cholesterol-related blood marker.";

                    discussion = flag.equals("HIGH")
                            ? "A saturated-fat food preference is available. "
                                    + "Discuss the result with your clinician."
                            : "No food preference was created for this flag.";

                    if (enabled && flag.equals("HIGH")) {
                        focusAreas.add("WATCH_SATURATED_FAT");
                    }
                }

                case "triglycerides" -> {
                    explanation = "Triglycerides are a type of fat in the blood.";

                    discussion = flag.equals("HIGH")
                            ? "An added-sugar food preference is available. "
                                    + "Meal timing and your health context can affect "
                                    + "interpretation."
                            : "No food preference was created for this flag.";

                    if (enabled && flag.equals("HIGH")) {
                        focusAreas.add("WATCH_ADDED_SUGAR");
                    }
                }

                case "hba1c" -> {
                    explanation = "HbA1c reflects average blood glucose over roughly "
                            + "the past three months.";

                    discussion = "Ask your clinician how to interpret this result. "
                            + "No automatic food preference was created "
                            + "from HbA1c alone.";
                }

                case "ferritin" -> {
                    explanation = "Ferritin is a protein that stores iron.";

                    if (flag.equals("LOW")) {
                        discussion = "Iron-containing food ideas are available. "
                                + "Ask your clinician about the cause and "
                                + "any follow-up needed.";

                        if (enabled) {
                            focusAreas.add("DISCUSS_IRON_FOODS");
                        }
                    } else if (flag.equals("HIGH")) {
                        discussion = "High ferritin has several possible causes. "
                                + "No increase-iron preference was created. "
                                + "Discuss follow-up with your clinician.";
                    } else {
                        discussion = "No iron food preference was created "
                                + "for this flag.";
                    }
                }

                default -> {
                    explanation = "This marker is outside the supported set.";

                    discussion = "No interpretation or food preference was created. "
                            + "Ask your clinician to explain this result.";
                }
            }

            cards.add(new LabCard(
                    marker.name(),
                    flag,
                    explanation,
                    discussion,
                    sourceFor(canonical)));
        }

        return new FocusProfile(
                List.copyOf(focusAreas),
                List.copyOf(cards),
                message);
    }

    private String sourceFor(String canonical) {
        return switch (canonical) {
            case "ldl", "triglycerides" -> LIPID_SOURCE;
            case "hba1c" -> A1C_SOURCE;
            case "ferritin" -> FERRITIN_SOURCE;
            default -> GENERAL_SOURCE;
        };
    }

    private String canonicalName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");

        return switch (normalized) {
            case "hba1c", "a1c", "hemoglobina1c",
                    "haemoglobina1c", "glycatedhemoglobin" ->
                "hba1c";

            case "ldl", "ldlc", "ldlcholesterol",
                    "ldlcholesterolcalculated",
                    "ldlcholesteroldirect" ->
                "ldl";

            case "triglycerides", "triglyceride", "tg" ->
                "triglycerides";

            case "ferritin", "serumferritin" -> "ferritin";

            default -> "unsupported";
        };
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message);
    }
}