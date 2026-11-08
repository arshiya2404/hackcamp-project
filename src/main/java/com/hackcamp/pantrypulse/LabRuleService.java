package com.hackcamp.pantrypulse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabRuleService {

    private static final String A1C_SOURCE =
        "https://www.niddk.nih.gov/health-information/diagnostic-tests/a1c-test";

    private static final String LIPID_SOURCE =
        "https://www.healthlinkbc.ca/healthlinkbc-files/"
        + "healthy-eating-lower-high-cholesterol-and-triglycerides";

    private static final String FERRITIN_SOURCE =
        "https://medlineplus.gov/lab-tests/ferritin-blood-test/";

    private static final String GENERAL_SOURCE =
        "https://medlineplus.gov/lab-tests/";

    public FocusProfile build(LabConfirmation request) {
        validate(request);

        List<LabCard> cards = new ArrayList<>();
        Set<String> focusAreas = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();

        boolean foodFocusAllowed =
            request.enableFoodFocus()
            && !request.conditionOrMedication();

        for (LabMarker marker : request.markers()) {
            String canonical = canonicalName(marker.name());
            String flag = marker.flag().toUpperCase(Locale.ROOT);

            String duplicateKey = canonical.equals("unsupported")
                ? marker.name().trim().toLowerCase(Locale.ROOT)
                : canonical;

            if (!seen.add(duplicateKey)) {
                throw badRequest(
                    "Keep only one current result per marker before confirming."
                );
            }

            String explanation;
            String discussion;
            String source;

            switch (canonical) {
                case "hba1c" -> {
                    explanation =
                        "HbA1c reflects average blood glucose over roughly "
                        + "the past three months. Some conditions can affect "
                        + "how the result should be interpreted.";

                    discussion = switch (flag) {
                        case "HIGH" ->
                            "Ask your clinician what this flagged result "
                            + "means for you and whether further testing "
                            + "or an individual eating plan is needed.";
                        case "LOW" ->
                            "Ask your clinician whether this low flag "
                            + "needs follow-up. No food change is inferred.";
                        case "NORMAL" ->
                            "The result was confirmed as not flagged. "
                            + "This does not replace your clinician's review.";
                        default ->
                            "No report flag was confirmed. "
                            + "No food focus has been created.";
                    };

                    source = A1C_SOURCE;
                }

                case "ldl" -> {
                    explanation =
                        "LDL carries cholesterol in the blood. "
                        + "Your clinician interprets it alongside your "
                        + "overall cardiovascular risk.";

                    discussion = switch (flag) {
                        case "HIGH" ->
                            "Ask whether choosing foods lower in saturated "
                            + "fat fits your care plan.";
                        case "LOW" ->
                            "Ask whether the low flag needs follow-up. "
                            + "No food change is inferred.";
                        case "NORMAL" ->
                            "No LDL food focus was created from this result.";
                        default ->
                            "Confirm the report flag with your clinician. "
                            + "No food focus was created.";
                    };

                    source = LIPID_SOURCE;

                    if (foodFocusAllowed && flag.equals("HIGH")) {
                        focusAreas.add("WATCH_SATURATED_FAT");
                    }
                }

                case "triglycerides" -> {
                    explanation =
                        "Triglycerides are a type of fat in the blood. "
                        + "Meal timing and other health factors can affect "
                        + "how a result is interpreted.";

                    discussion = switch (flag) {
                        case "HIGH" ->
                            "Ask whether reducing added sugars fits your "
                            + "care plan and whether fasting status matters "
                            + "for this result.";
                        case "LOW" ->
                            "Ask whether the low flag needs follow-up. "
                            + "No food change is inferred.";
                        case "NORMAL" ->
                            "No triglyceride food focus was created.";
                        default ->
                            "No report flag was confirmed. "
                            + "No food focus was created.";
                    };

                    source = LIPID_SOURCE;

                    if (foodFocusAllowed && flag.equals("HIGH")) {
                        focusAreas.add("WATCH_ADDED_SUGAR");
                    }
                }

                case "ferritin" -> {
                    explanation =
                        "Ferritin is a protein that stores iron. "
                        + "A ferritin result alone does not explain "
                        + "the cause of an abnormal result.";

                    discussion = switch (flag) {
                        case "LOW" ->
                            "Ask whether this indicates low iron stores "
                            + "and what follow-up is needed. Discuss "
                            + "iron-containing foods with your clinician.";
                        case "HIGH" ->
                            "High ferritin has several possible causes. "
                            + "Ask your clinician about follow-up. "
                            + "Do not infer a need to increase iron.";
                        case "NORMAL" ->
                            "No iron food focus was created.";
                        default ->
                            "No report flag was confirmed. "
                            + "No iron food focus was created.";
                    };

                    source = FERRITIN_SOURCE;

                    if (foodFocusAllowed && flag.equals("LOW")) {
                        focusAreas.add("DISCUSS_IRON_FOODS");
                    }
                }

                default -> {
                    explanation =
                        "This marker is outside PantryPulse's supported set.";

                    discussion =
                        "Ask your clinician to explain this result. "
                        + "No interpretation or food focus was generated.";

                    source = GENERAL_SOURCE;
                }
            }

            cards.add(new LabCard(
                marker.name(),
                flag,
                explanation,
                discussion,
                source
            ));
        }

        String message;

        if (request.conditionOrMedication()) {
            message =
                "Food focus is paused because you reported a diagnosed "
                + "condition or medication use. Review food changes with "
                + "your clinician or pharmacist.";
        } else if (!request.enableFoodFocus()) {
            message =
                "Food focus is off. Your results have not changed "
                + "recipe preferences.";
        } else {
            message =
                "These are optional discussion prompts, not a diagnosis "
                + "or treatment plan. No numerical targets were created.";
        }

        return new FocusProfile(
            List.copyOf(focusAreas),
            List.copyOf(cards),
            message
        );
    }

    private void validate(LabConfirmation request) {
        if (request == null || !request.confirmed()) {
            throw badRequest("Confirm that you checked the report values.");
        }

        if (request.markers() == null
                || request.markers().isEmpty()
                || request.markers().size() > 30) {
            throw badRequest("Confirm between 1 and 30 results.");
        }

        Set<String> flags = Set.of("HIGH", "LOW", "NORMAL", "UNKNOWN");

        for (LabMarker marker : request.markers()) {
            if (marker == null
                    || marker.name() == null
                    || marker.name().isBlank()
                    || marker.value() == null
                    || marker.value().signum() < 0
                    || marker.unit() == null
                    || marker.unit().isBlank()
                    || marker.referenceRange() == null
                    || marker.referenceRange().isBlank()
                    || marker.flag() == null
                    || !flags.contains(
                        marker.flag().toUpperCase(Locale.ROOT))) {
                throw badRequest(
                    "Each retained result needs a name, nonnegative "
                    + "numeric value, unit, reference range, and valid flag. "
                    + "Remove results that cannot be confirmed."
                );
            }
        }
    }

    private String canonicalName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");

        return switch (normalized) {
            case "hba1c", "a1c", "hemoglobina1c",
                 "haemoglobina1c", "glycatedhemoglobin" -> "hba1c";

            case "ldl", "ldlc", "ldlcholesterol",
                 "ldlcholesterolcalculated",
                 "ldlcholesteroldirect" -> "ldl";

            case "triglycerides", "triglyceride", "tg" ->
                "triglycerides";

            case "ferritin", "serumferritin" -> "ferritin";

            default -> "unsupported";
        };
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST, message
        );
    }
}
