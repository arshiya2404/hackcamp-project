(() => {
  const get = (id) => document.getElementById(id);

  const focusLabels = {
    WATCH_SATURATED_FAT: "Saturated-fat choices",
    WATCH_ADDED_SUGAR: "Added-sugar choices",
    DISCUSS_IRON_FOODS: "Iron-containing foods",
  };

  let profile = null;
  let selected = new Set();
  let version = 0;
  let scanning = false;

  // Only food preference codes are exposed to the recipe page.
  // Raw report images and numeric results are not stored here.
  window.pantryHealth = {
    getFocusAreas() {
      if (!profile || !get("health-enabled").checked) {
        return [];
      }

      return [...selected];
    },

    getVersion() {
      return version;
    },
  };

  function notifyChange() {
    version++;

    window.dispatchEvent(new CustomEvent("pantry-health-changed"));
  }

  function notifyLoading(value) {
    window.dispatchEvent(
      new CustomEvent("pantry-health-loading", {
        detail: value,
      })
    );
  }

  function validProfile(data) {
    return (
      data &&
      Array.isArray(data.focusAreas) &&
      data.focusAreas.every((area) =>
        Object.prototype.hasOwnProperty.call(focusLabels, area)
      ) &&
      Array.isArray(data.cards) &&
      data.cards.every(
        (card) =>
          card &&
          ["marker", "flag", "explanation", "discussion", "sourceUrl"].every(
            (key) => typeof card[key] === "string"
          )
      )
    );
  }

  function renderProfile() {
    get("health-content").hidden = false;
    get("clear-health").hidden = false;

    const findingsSection = document.querySelector(
      "#health-panel details.health-findings"
    );

    if (findingsSection) {
      findingsSection.open = true;
    }

    get("health-origin").textContent =
      "Automatically extracted from your report · check accuracy";

    get("health-enabled").checked = true;

    const options = get("health-focus-options");
    options.replaceChildren();

    if (profile.focusAreas.length === 0) {
      const message = document.createElement("p");

      message.className = "health-small";
      message.textContent =
        profile.cards.length > 0
          ? "Your report findings are shown below. " +
            "No automatic recipe preferences were added."
          : "No selected report findings were extracted.";

      options.append(message);
    }

    for (const area of profile.focusAreas) {
      const label = document.createElement("label");
      label.className = "health-focus-option";

      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.checked = true;

      checkbox.onchange = () => {
        if (checkbox.checked) {
          selected.add(area);
        } else {
          selected.delete(area);
        }

        notifyChange();
      };

      const text = document.createElement("span");
      text.textContent = focusLabels[area];

      label.append(checkbox, text);
      options.append(label);
    }

    const findings = get("health-findings-list");
    findings.replaceChildren();

    for (const card of profile.cards) {
      const article = document.createElement("article");
      article.className = "health-finding";

      const heading = document.createElement("h3");
      heading.textContent = `${card.marker} · ${card.flag}`;

      const explanation = document.createElement("p");
      explanation.textContent = card.explanation;

      const discussion = document.createElement("p");
      discussion.textContent = card.discussion;

      article.append(heading, explanation, discussion);

      try {
        const url = new URL(card.sourceUrl);

        if (url.protocol === "https:") {
          const source = document.createElement("a");

          source.href = url.href;
          source.textContent = "Read source";
          source.target = "_blank";
          source.rel = "noopener noreferrer";

          article.append(source);
        }
      } catch {
        // Do not create links from invalid URLs.
      }

      findings.append(article);
    }
  }

  async function scanReport(file) {
    if (scanning || (window.pantryIsBusy && window.pantryIsBusy())) {
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      get("health-status").textContent =
        "Use a JPEG, PNG or WebP image smaller than 5 MB.";

      get("lab-file").value = "";
      return;
    }

    scanning = true;
    notifyLoading(true);

    get("health-status").textContent = "Scanning your report...";

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 75000);

    try {
      const form = new FormData();
      form.append("image", file);

      const response = await fetch("/api/labs/scan", {
        method: "POST",
        body: form,
        signal: controller.signal,
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(
          data?.message || `Report scan failed (${response.status}).`
        );
      }

      if (!validProfile(data)) {
        throw new Error("The report response was incomplete.");
      }

      profile = data;
      selected = new Set(data.focusAreas);

      renderProfile();
      notifyChange();

      get("health-status").textContent =
        data.focusAreas.length > 0
          ? "Report scanned. Your findings are shown below, " +
            "and recipe preferences are ready."
          : data.cards.length > 0
          ? "Report scanned. Your findings are shown below."
          : "No selected report findings were extracted.";
    } catch (error) {
      get("health-status").textContent =
        error.name === "AbortError"
          ? "The scan took too long. Please try again."
          : error.message;

      // A failed replacement scan keeps the previous profile.
    } finally {
      clearTimeout(timer);
      get("lab-file").value = "";
      scanning = false;
      notifyLoading(false);
    }
  }

  get("lab-file").onchange = (event) => {
    const file = event.target.files[0];

    if (file) {
      scanReport(file);
    }
  };

  get("health-enabled").onchange = () => {
    notifyChange();

    get("health-status").textContent = get("health-enabled").checked
      ? "Food preferences are on for your next recipe."
      : "Food preferences are off. Recipes will use the pantry alone.";
  };

  get("clear-health").onclick = () => {
    profile = null;
    selected.clear();

    get("lab-file").value = "";
    get("health-content").hidden = true;
    get("clear-health").hidden = true;

    get("health-focus-options").replaceChildren();
    get("health-findings-list").replaceChildren();

    get("health-status").textContent =
      "Health focus cleared. Recipes will use the pantry alone.";

    notifyChange();
  };
})();
