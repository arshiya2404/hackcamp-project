const $ = (id) => document.getElementById(id);

let markers = [];
let busy = false;

const focusLabels = {
  WATCH_SATURATED_FAT: "Discuss saturated-fat choices",
  WATCH_ADDED_SUGAR: "Discuss added-sugar choices",
  DISCUSS_IRON_FOODS: "Discuss iron-containing foods",
};

function setBusy(value) {
  busy = value;

  [
    "extract-button",
    "sample-button",
    "confirm-button",
    "clear-button",
    "report-image",
  ].forEach((id) => {
    $(id).disabled = value;
  });

  // Prevent changes to reviewed values during confirmation.
  $("review-section")
    .querySelectorAll("input, select, button")
    .forEach((element) => {
      element.disabled = value;
    });
}

function clearProfile() {
  $("profile-section").hidden = true;
  $("profile-message").textContent = "";
  $("focus-list").replaceChildren();
  $("profile-cards").replaceChildren();
}

function createField(labelText, field, value, index) {
  const label = document.createElement("label");
  label.textContent = labelText;

  const input = document.createElement("input");
  input.value = value ?? "";

  if (field === "value") {
    input.type = "number";
    input.step = "any";
    input.min = "0";
  } else {
    input.type = "text";
  }

  input.oninput = () => {
    markers[index][field] = input.value;
    $("confirmed-values").checked = false;
    clearProfile();
  };

  label.append(input);
  return label;
}

function renderReview() {
  const container = $("review-results");
  container.replaceChildren();

  $("review-section").hidden = markers.length === 0;

  markers.forEach((marker, index) => {
    const card = document.createElement("article");
    card.className = "marker-editor";

    const heading = document.createElement("h3");
    heading.textContent = `Result ${index + 1}`;

    const grid = document.createElement("div");
    grid.className = "editor-grid";

    grid.append(
      createField("Test name", "name", marker.name, index),
      createField("Value", "value", marker.value, index),
      createField("Unit", "unit", marker.unit, index),
      createField(
        "Report reference range",
        "referenceRange",
        marker.referenceRange,
        index
      )
    );

    const flagLabel = document.createElement("label");
    flagLabel.textContent = "Flag printed on report";

    const select = document.createElement("select");

    ["UNKNOWN", "NORMAL", "HIGH", "LOW"].forEach((value) => {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = value;
      select.append(option);
    });

    select.value = marker.flag;

    select.onchange = () => {
      markers[index].flag = select.value;
      $("confirmed-values").checked = false;
      clearProfile();
    };

    flagLabel.append(select);
    grid.append(flagLabel);

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "secondary";
    remove.textContent = "Remove result";

    remove.onclick = () => {
      markers.splice(index, 1);
      $("confirmed-values").checked = false;
      clearProfile();
      renderReview();
    };

    card.append(heading, grid, remove);
    container.append(card);
  });
}

function loadExtraction(data, sample) {
  if (!data || !Array.isArray(data.markers)) {
    throw new Error("The report response was incomplete.");
  }

  const extracted = data.markers.map((marker) => {
    if (!marker || typeof marker.name !== "string") {
      throw new Error("An extracted result was invalid.");
    }

    const flag = String(marker.flag ?? "UNKNOWN").toUpperCase();

    return {
      name: marker.name,
      value: marker.value ?? "",
      unit: marker.unit ?? "",
      referenceRange: marker.referenceRange ?? "",
      flag: ["HIGH", "LOW", "NORMAL", "UNKNOWN"].includes(flag)
        ? flag
        : "UNKNOWN",
    };
  });

  markers = extracted;

  $("sample-notice").hidden = !sample;
  $("confirm-form").reset();

  clearProfile();
  renderReview();

  $("lab-status").textContent = markers.length
    ? "Check each field against the report before confirming."
    : "No numeric results were found. Try a clearer image.";
}

async function requestJson(url, options = {}, timeout = 75000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      throw new Error(data?.message ?? `Request failed (${response.status}).`);
    }

    if (!data) {
      throw new Error("The server returned an empty response.");
    }

    return data;
  } finally {
    clearTimeout(timer);
  }
}

function showError(error) {
  $("lab-status").textContent =
    error.name === "AbortError"
      ? "The request took too long. Please try again."
      : error.message;
}

$("sample-button").onclick = async () => {
  if (busy) return;

  setBusy(true);
  $("lab-status").textContent = "Loading fictional sample...";
  clearProfile();
  $("review-section").hidden = true;

  try {
    const data = await requestJson("/api/labs/sample", {}, 15000);

    loadExtraction(data, true);
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
};

$("extract-button").onclick = async () => {
  if (busy) return;

  const file = $("report-image").files[0];

  if (!file) {
    $("lab-status").textContent = "Choose a report image first.";
    return;
  }

  if (!$("consent-upload").checked) {
    $("lab-status").textContent =
      "Confirm that you understand the image is sent to Gemini.";
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    $("lab-status").textContent = "Use an image smaller than 5 MB.";
    return;
  }

  setBusy(true);
  clearProfile();
  $("review-section").hidden = true;
  $("lab-status").textContent = "Reading report...";

  const form = new FormData();
  form.append("image", file);

  try {
    const data = await requestJson("/api/labs/extract", {
      method: "POST",
      body: form,
    });

    loadExtraction(data, false);
  } catch (error) {
    showError(error);
  } finally {
    $("report-image").value = "";
    setBusy(false);
  }
};

function renderProfile(profile) {
  if (
    !profile ||
    !Array.isArray(profile.focusAreas) ||
    !Array.isArray(profile.cards)
  ) {
    throw new Error("The focus profile response was incomplete.");
  }

  clearProfile();

  $("profile-message").textContent = profile.message ?? "";

  if (profile.focusAreas.length === 0) {
    const message = document.createElement("p");
    message.textContent = "No food focus topics were created.";
    $("focus-list").append(message);
  }

  profile.focusAreas.forEach((area) => {
    const chip = document.createElement("span");
    chip.className = "focus-chip";
    chip.textContent = focusLabels[area] ?? area;
    $("focus-list").append(chip);
  });

  profile.cards.forEach((card) => {
    const article = document.createElement("article");
    article.className = "result-card";

    const heading = document.createElement("h3");
    heading.textContent = `${card.marker} · ${card.flag}`;

    const explanation = document.createElement("p");
    explanation.textContent = card.explanation;

    const discussion = document.createElement("p");
    discussion.textContent = card.discussion;

    article.append(heading, explanation, discussion);

    // Only create a clickable source when the URL is valid HTTPS.
    try {
      const url = new URL(card.sourceUrl);

      if (url.protocol === "https:") {
        const source = document.createElement("a");
        source.textContent = "Read source";
        source.href = url.href;
        source.target = "_blank";
        source.rel = "noopener noreferrer";
        article.append(source);
      }
    } catch {
      // Leave invalid source URLs unlinked.
    }

    $("profile-cards").append(article);
  });

  $("profile-section").hidden = false;
}

$("confirm-form").onsubmit = async (event) => {
  event.preventDefault();

  if (busy) return;

  const context = document.querySelector(
    'input[name="medical-context"]:checked'
  );

  if (!context || !$("confirmed-values").checked) {
    $("lab-status").textContent =
      "Answer the medical-context question and confirm your review.";
    return;
  }

  const reviewed = [];

  for (const marker of markers) {
    const value = Number(marker.value);

    if (
      !marker.name.trim() ||
      String(marker.value).trim() === "" ||
      !Number.isFinite(value) ||
      value < 0 ||
      !marker.unit.trim() ||
      !marker.referenceRange.trim()
    ) {
      $("lab-status").textContent =
        "Complete every retained result, or remove results " +
        "you cannot confirm.";
      return;
    }

    reviewed.push({
      name: marker.name.trim(),
      value,
      unit: marker.unit.trim(),
      referenceRange: marker.referenceRange.trim(),
      flag: marker.flag,
    });
  }

  if (reviewed.length === 0) {
    $("lab-status").textContent = "Keep at least one result.";
    return;
  }

  const body = {
    markers: reviewed,
    confirmed: true,
    conditionOrMedication: context.value !== "no",
    enableFoodFocus: $("enable-focus").checked,
  };

  setBusy(true);
  clearProfile();

  $("lab-status").textContent = "Building your discussion profile...";

  try {
    const profile = await requestJson(
      "/api/labs/confirm",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      },
      15000
    );

    renderProfile(profile);

    $("lab-status").textContent =
      "Profile created for this session. Recipes are not connected yet.";
  } catch (error) {
    showError(error);
  } finally {
    setBusy(false);
  }
};

$("confirm-form").onchange = () => {
  clearProfile();
};

$("report-image").onchange = () => {
  markers = [];
  $("review-results").replaceChildren();
  $("review-section").hidden = true;
  $("confirmed-values").checked = false;
  clearProfile();
  $("lab-status").textContent = "";
};

$("clear-button").onclick = () => {
  if (busy) return;

  markers = [];

  $("report-image").value = "";
  $("consent-upload").checked = false;
  $("confirm-form").reset();

  $("review-results").replaceChildren();
  $("review-section").hidden = true;
  $("sample-notice").hidden = true;
  $("lab-status").textContent = "";

  clearProfile();
};
