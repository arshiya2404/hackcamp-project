let items = [];
let nextId = 1;
let busy = false;
window.pantryIsBusy = () => busy;
let showingPantry = false;

const SAVE_KEY = "pantrypulse.savedPantry";
const SAVE_VERSION = 2;

const $ = (id) => document.getElementById(id);

function escapeHtml(value) {
  return String(value ?? "").replace(
    /[&<>"']/g,
    (character) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      }[character])
  );
}

function setStatus(message) {
  $("status").textContent = message;
}

function needsCheck(item) {
  return item.storage === "review" || item.storage === "unknown";
}

/*
 * Convert a calendar date to a day number.
 * Using UTC for the calculation avoids daylight-saving errors.
 */
function parseDay(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error("A pantry item has an invalid date.");
  }

  const [year, month, day] = value.split("-").map(Number);

  const date = new Date(0);
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCFullYear(year, month - 1, day);

  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    throw new Error("A pantry item has an invalid date.");
  }

  return date.getTime() / 86400000;
}

function daysUntil(value) {
  const today = new Date();

  const localToday = new Date(0);
  localToday.setUTCHours(0, 0, 0, 0);
  localToday.setUTCFullYear(
    today.getFullYear(),
    today.getMonth(),
    today.getDate()
  );

  return parseDay(value) - localToday.getTime() / 86400000;
}

function validateItems(list) {
  if (!Array.isArray(list) || list.length > 300) {
    throw new Error("Invalid pantry data. Maximum 300 items.");
  }

  return list.map((item) => {
    if (
      !item ||
      typeof item.name !== "string" ||
      !item.name.trim() ||
      typeof item.storage !== "string" ||
      typeof item.tip !== "string" ||
      typeof item.needsReview !== "boolean"
    ) {
      throw new Error("A pantry item is incomplete.");
    }

    parseDay(item.purchaseDate);
    parseDay(item.expiryDate);

    return {
      ...item,
      daysLeft: daysUntil(item.expiryDate),
    };
  });
}

function refreshDates(list = items) {
  for (const item of list) {
    item.daysLeft = daysUntil(item.expiryDate);
  }
}

function clearRecipes() {
  $("recipes").replaceChildren();
  $("cook-status").textContent = "";
}

window.addEventListener("pantry-health-changed", () => {
  clearRecipes();
});

window.addEventListener("pantry-health-loading", (event) => {
  if (event.detail) {
    clearRecipes();
  }

  setBusy(Boolean(event.detail));
});

function replaceItems(list) {
  // Validate everything before changing the current inventory.
  const valid = validateItems(list);

  items = valid.map((item) => ({
    ...item,
    id: nextId++,
  }));

  clearRecipes();
  render(items);
}

function addItems(list) {
  const valid = validateItems(list);

  if (items.length + valid.length > 300) {
    throw new Error("This pantry supports up to 300 items.");
  }

  items.push(
    ...valid.map((item) => ({
      ...item,
      id: nextId++,
    }))
  );

  clearRecipes();
  render(items);
}

function setBusy(value) {
  busy = value;

  [
    "file",
    "lab-file",
    "save-pantry",
    "load-pantry",
    "cook",
    "clear-health",
  ].forEach((id) => {
    const element = document.getElementById(id);

    if (element) {
      element.disabled = value;
    }
  });

  document
    .querySelectorAll(".remove-btn, #health-panel input, #health-panel button")
    .forEach((element) => {
      element.disabled = value;
    });

  document
    .querySelectorAll(".scan-button, .lab-upload-button")
    .forEach((element) => {
      element.setAttribute("aria-disabled", String(value));
    });
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
      throw new Error(data?.message || `Server error ${response.status}`);
    }

    if (data === null) {
      throw new Error("The server returned an empty response.");
    }

    return data;
  } finally {
    clearTimeout(timer);
  }
}

function errorText(error) {
  return error.name === "AbortError"
    ? "The request took too long. Please try again."
    : error.message;
}

$("switch-storage").onclick = () => {
  showingPantry = !showingPantry;

  $("fridge-view").hidden = showingPantry;
  $("pantry-view").hidden = !showingPantry;

  $("storage-title").textContent = showingPantry ? "My pantry" : "My fridge";

  $("switch-storage").textContent = showingPantry ? "← Fridge" : "Pantry →";

  $("switch-storage").setAttribute(
    "aria-label",
    showingPantry ? "Show fridge" : "Show pantry"
  );
};

function getStorageSection(item) {
  const storage = String(item.storage ?? "").toLowerCase();
  const name = String(item.name ?? "").toLowerCase();

  if (storage === "review" || storage === "unknown") {
    return "fridge-list";
  }

  if (/freez|frozen/.test(storage)) {
    return "freezer-list";
  }

  const isProduce =
    /\b(spinach|lettuce|kale|broccoli|carrots?|peppers?|capsicum|cucumbers?|mushrooms?|berries|strawberries|strawberry|blueberries|blueberry|apples?|avocados?|bananas?|dragonfruit|tomatoes|tomato|oranges?|grapes?|potatoes|potato|onions?|garlic)\b/.test(
      name
    );

  if (/fridge|refrigerat|crisper|fresh drawer/.test(storage)) {
    return isProduce ? "fresh-list" : "fridge-list";
  }

  if (/pantry|cupboard|counter|room temp|shelf/.test(storage)) {
    const isPackaged =
      /\b(canned|tinned|jarred|sauce|jam|preserves|pickles)\b/.test(name);

    if (isProduce && !isPackaged) {
      return "counter-list";
    }

    const isGrain =
      /\b(rice|pasta|spaghetti|penne|fusilli|macaroni|rigatoni|bread|sourdough|oats|oatmeal|porridge|cereal|cornflakes|flour|atta|quinoa|bulgur|bulghur|couscous|wheat)\b/.test(
        name
      );

    if (isGrain && !isPackaged) {
      return "grains-list";
    }

    return "staples-list";
  }

  return null;
}

function foodCard(item) {
  const provisional = needsCheck(item);

  const color = provisional
    ? "expiry-review"
    : item.daysLeft <= 2
    ? "expiry-red"
    : item.daysLeft <= 5
    ? "expiry-amber"
    : "expiry-green";

  const label = provisional
    ? "Check label"
    : item.daysLeft < 0
    ? "Past estimated date"
    : item.daysLeft === 0
    ? "Estimated today"
    : `${item.daysLeft} day${item.daysLeft === 1 ? "" : "s"} left`;

  const storageLabel = provisional ? "Fridge · suggested" : item.storage;

  return `
    <article class="food-card">
      <button
        type="button"
        class="remove-btn"
        data-id="${item.id}"
        aria-label="Remove ${escapeHtml(item.name)}"
        title="Remove"
      >×</button>

      <details>
        <summary>
          <span class="food-name">${escapeHtml(item.name)}</span>
          <span class="expiry-chip ${color}">
            ${escapeHtml(label)}
          </span>
        </summary>

        <div class="food-details">
          <p>Store: ${escapeHtml(storageLabel)}</p>

          ${
            provisional
              ? "<p>Check the printed date before relying on this estimate.</p>"
              : `<p>Estimated date: ${escapeHtml(item.expiryDate)}</p>`
          }

          <p>${escapeHtml(item.tip)}</p>

          ${
            item.needsReview
              ? '<p class="storage-note">Suggested storage · check label</p>'
              : ""
          }
        </div>
      </details>
    </article>
  `;
}

function render(list) {
  refreshDates(list);

  const sections = {
    "freezer-list": [],
    "fridge-list": [],
    "fresh-list": [],
    "grains-list": [],
    "staples-list": [],
    "counter-list": [],
  };

  const unassigned = [];

  // Put items with verified catalogue estimates before unknown dates.
  const rank = (item) => (needsCheck(item) ? Infinity : item.daysLeft);

  const sorted = [...list].sort((a, b) => rank(a) - rank(b));

  for (const item of sorted) {
    const section = getStorageSection(item);

    if (section) {
      sections[section].push(item);
    } else {
      unassigned.push(item);
    }
  }

  for (const [id, foods] of Object.entries(sections)) {
    $(id).innerHTML = foods.length
      ? foods.map(foodCard).join("")
      : '<p class="compartment-empty">Nothing here yet.</p>';
  }

  $("unassigned-section").hidden = unassigned.length === 0;
  $("unassigned-list").innerHTML = unassigned.map(foodCard).join("");

  const soon = list.filter(
    (item) => !needsCheck(item) && item.daysLeft >= 0 && item.daysLeft <= 2
  ).length;

  $("pantry-count").textContent = list.length
    ? `${list.length} items total · ${soon} needing attention soon`
    : "Scan a receipt to fill your fridge.";
}

document.addEventListener("click", (event) => {
  const button = event.target.closest(".remove-btn");

  if (!button || busy) return;

  items = items.filter((item) => item.id !== Number(button.dataset.id));

  clearRecipes();
  render(items);
});

$("save-pantry").onclick = () => {
  if (busy) return;

  try {
    refreshDates();

    const saved = items.map(({ id, ...rest }) => rest);

    // Empty saves are allowed, so removed foods don't return later.
    localStorage.setItem(
      SAVE_KEY,
      JSON.stringify({
        version: SAVE_VERSION,
        savedAt: new Date().toISOString(),
        items: saved,
      })
    );

    setStatus(`Saved ${items.length} items.`);
  } catch (error) {
    setStatus("Could not save: " + errorText(error));
  }
};

$("load-pantry").onclick = async () => {
  if (busy) return;

  try {
    const raw = localStorage.getItem(SAVE_KEY);

    if (!raw) {
      setStatus("No saved pantry found yet.");
      return;
    }

    const data = JSON.parse(raw);

    if (data.version !== undefined && data.version !== SAVE_VERSION) {
      throw new Error("This save version is not supported.");
    }

    let loaded = validateItems(data.items);

    if (
      items.length > 0 &&
      !confirm("Replace the current pantry with your saved pantry?")
    ) {
      return;
    }

    if (data.version === undefined && loaded.length > 0) {
      const approved = confirm(
        "This is an older save. Recheck food matches and recalculate " +
          "estimates from the original purchase dates? " +
          "The original save remains until you click Save pantry."
      );

      if (!approved) return;

      setBusy(true);
      setStatus("Rechecking older food matches...");

      loaded = await requestJson(
        "/api/pantry/recheck",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(
            loaded.map((item) => ({
              name: item.name,
              purchaseDate: item.purchaseDate,
            }))
          ),
        },
        15000
      );
    }

    replaceItems(loaded);

    setStatus(
      `Loaded ${items.length} items. ` +
        "Click Save pantry to keep this version."
    );
  } catch (error) {
    setStatus("Could not load: " + errorText(error));
  } finally {
    setBusy(false);
  }
};

function renderRecipes(recipes) {
  const valid =
    Array.isArray(recipes) &&
    recipes.every(
      (recipe) =>
        recipe &&
        typeof recipe.title === "string" &&
        typeof recipe.time === "string" &&
        ["usedExpiringItems", "ingredients", "steps"].every(
          (key) =>
            Array.isArray(recipe[key]) &&
            recipe[key].every((value) => typeof value === "string")
        )
    );

  if (!valid) {
    throw new Error("The recipe response was incomplete.");
  }

  $("recipes").innerHTML = recipes
    .map(
      (recipe) => `
    <details class="recipe-card">
      <summary>
        <div class="recipe-art" aria-hidden="true">
          <div class="plate">
            <span></span>
            <span></span>
          </div>
        </div>

        <div>
          <h3>${escapeHtml(recipe.title)}</h3>
          <p class="recipe-time">${escapeHtml(recipe.time)}</p>
          <p class="recipe-uses">
            Uses: ${recipe.usedExpiringItems.map(escapeHtml).join(", ")}
          </p>
          <span class="recipe-link">View recipe</span>
        </div>
      </summary>

      ${
        Array.isArray(recipe.healthNotes) && recipe.healthNotes.length
          ? `
      <div class="recipe-health-notes">
        ${recipe.healthNotes
          .map((note) => `<p>${escapeHtml(note)}</p>`)
          .join("")}
      </div>
    `
          : ""
      }

      <div class="recipe-details">
        <h4>Ingredients</h4>
        <ul>
          ${recipe.ingredients
            .map((value) => `<li>${escapeHtml(value)}</li>`)
            .join("")}
        </ul>

        <h4>Let’s make it</h4>
        <ol>
          ${recipe.steps
            .map((value) => `<li>${escapeHtml(value)}</li>`)
            .join("")}
        </ol>
      </div>
    </details>
  `
    )
    .join("");
}

$("cook").onclick = async () => {
  if (busy) return;

  if (items.length === 0) {
    $("cook-status").textContent = "Scan a receipt first.";
    return;
  }

  render(items);
  clearRecipes();
  setBusy(true);

  $("cook").textContent = "Finding recipes...";
  $("cook-status").textContent = "Cooking up ideas...";

  const healthVersion = window.pantryHealth?.getVersion() ?? 0;

  try {
    const recipes = await requestJson("/api/recipes", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        items: items.map((item) => ({
          name: item.name,
          daysLeft: needsCheck(item) ? null : item.daysLeft,
        })),
        focusAreas: window.pantryHealth?.getFocusAreas() ?? [],
      }),
    });

    if (healthVersion !== (window.pantryHealth?.getVersion() ?? 0)) {
      $("cook-status").textContent =
        "Your health preferences changed. Generate a new recipe.";
      return;
    }

    renderRecipes(recipes);

    $("cook-status").textContent = recipes.length
      ? ""
      : "No recipes found. Try adding more ingredients.";
  } catch (error) {
    $("cook-status").textContent = "Could not get recipes: " + errorText(error);
  } finally {
    setBusy(false);
    $("cook").textContent = "What should I cook? ↗";
  }
};

$("file").onchange = async (event) => {
  const file = event.target.files[0];

  if (!file || busy) return;

  if (file.size > 5 * 1024 * 1024) {
    setStatus("Use an image smaller than 5 MB.");
    event.target.value = "";
    return;
  }

  const form = new FormData();
  form.append("image", file);

  setBusy(true);
  setStatus("Reading receipt...");

  try {
    const added = await requestJson("/api/receipt", {
      method: "POST",
      body: form,
    });

    addItems(added);

    setStatus(
      added.length
        ? `Added ${added.length} items. Check suggested storage and dates.`
        : "No food items were found. Try a clearer image."
    );
  } catch (error) {
    setStatus("Could not read receipt: " + errorText(error));
  } finally {
    event.target.value = "";
    setBusy(false);
  }
};

function refreshOpenPage() {
  if (busy) return;

  const changed = items.some(
    (item) => item.daysLeft !== daysUntil(item.expiryDate)
  );

  if (changed) {
    clearRecipes();
    render(items);
  }
}

window.addEventListener("focus", refreshOpenPage);

document.addEventListener("visibilitychange", () => {
  if (!document.hidden) refreshOpenPage();
});

setInterval(refreshOpenPage, 60000);

render(items);
