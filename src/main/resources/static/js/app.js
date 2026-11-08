let items = [];
let nextId = 1; 
const SAVE_KEY = "pantrypulse.savedPantry";

let showingPantry = false;

document.getElementById("switch-storage").onclick = () => {
  showingPantry = !showingPantry;

  document.getElementById("fridge-view").hidden = showingPantry;

  document.getElementById("pantry-view").hidden = !showingPantry;

  document.getElementById("storage-title").textContent = showingPantry
    ? "My pantry"
    : "My fridge";

  const button = document.getElementById("switch-storage");

  button.textContent = showingPantry ? "← Fridge" : "Pantry →";

  button.setAttribute(
    "aria-label",
    showingPantry ? "Show fridge" : "Show pantry"
  );
};

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
  document.getElementById("status").textContent = message;
}

function daysUntil(dateString) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(dateString + "T00:00:00");
  return Math.round((target - today) / 86400000);
}

function addItems(newItems) {
  for (const item of newItems) {
    items.push({ ...item, id: nextId++ });
  }
}

function getStorageSection(item) {
  const storage = String(item.storage ?? "").toLowerCase();
  const name = String(item.name ?? "").toLowerCase();

  const isProduce =
    /\b(spinach|lettuce|kale|broccoli|carrot|carrots|pepper|peppers|cucumber|berries|strawberries|blueberries|apple|apples|avocado|avocados|banana|bananas|dragonfruit|tomato|tomatoes|orange|oranges|grape|grapes)\b/.test(
      name
    );

  // Storage takes priority: frozen vegetables belong in the freezer.
  if (/freez|frozen/.test(storage)) {
    return "freezer-list";
  }

  if (/fridge|refrigerat|crisper|fresh drawer/.test(storage)) {
    return isProduce ? "fresh-list" : "fridge-list";
  }

  if (/pantry|cupboard|counter|room temp|shelf/.test(storage)) {
    if (isProduce) {
      return "counter-list";
    }

    const isGrain =
      /\b(rice|pasta|bread|oats|oatmeal|cereal|flour|quinoa)\b/.test(name);

    return isGrain ? "grains-list" : "staples-list";
  }

  return null;
}

function foodCard(item) {
  const color =
    item.daysLeft <= 2
      ? "expiry-red"
      : item.daysLeft <= 5
      ? "expiry-amber"
      : "expiry-green";

  const label =
    item.daysLeft < 0
      ? "Past estimated date"
      : item.daysLeft === 0
      ? "Estimated today"
      : `${item.daysLeft} day${item.daysLeft === 1 ? "" : "s"} left`;

  return `
    <article class="food-card">
    <button type="button" class="remove-btn" data-id="${item.id}"
        aria-label="Remove ${escapeHtml(item.name)}" title="Remove">×</button>
      <details>
        <summary>
          <span class="food-name">
            ${escapeHtml(item.name)}
          </span>

          <span class="expiry-chip ${color}">
            ${escapeHtml(label)}
          </span>
        </summary>

        <div class="food-details">
          <p>Store: ${escapeHtml(item.storage)}</p>
          <p>Estimated date: ${escapeHtml(item.expiryDate)}</p>
          <p>${escapeHtml(item.tip)}</p>

          ${
            item.needsReview ? "<p>Please check this general estimate.</p>" : ""
          }
        </div>
      </details>
    </article>
  `;
}

function render(list) {
  const sections = {
    "freezer-list": [],
    "fridge-list": [],
    "fresh-list": [],
    "grains-list": [],
    "staples-list": [],
    "counter-list": [],
  };

  const unassigned = [];

  const sorted = [...list].sort((a, b) => a.daysLeft - b.daysLeft);

  for (const item of sorted) {
    const section = getStorageSection(item);

    if (section) {
      sections[section].push(item);
    } else {
      unassigned.push(item);
    }
  }

  for (const [id, foods] of Object.entries(sections)) {
    document.getElementById(id).innerHTML = foods.length
      ? foods.map(foodCard).join("")
      : '<p class="compartment-empty">Nothing here yet.</p>';
  }

  document.getElementById("unassigned-section").hidden =
    unassigned.length === 0;

  document.getElementById("unassigned-list").innerHTML = unassigned
    .map(foodCard)
    .join("");

  const soon = list.filter(
    (item) => item.daysLeft >= 0 && item.daysLeft <= 2
  ).length;

  document.getElementById("pantry-count").textContent = list.length
    ? `${list.length} items total · ${soon} needing attention soon`
    : "Scan a receipt to fill your fridge.";
}

document.addEventListener("click", (e) => {
  const button = e.target.closest(".remove-btn");
  if (!button) return;

  const id = Number(button.dataset.id);
  items = items.filter((item) => item.id !== id);
  render(items);
});

document.getElementById("save-pantry").onclick = () => {
  if (items.length === 0) {
    setStatus("Nothing to save yet. Scan a receipt first.");
    return;
  }

  try {
    const saved = items.map(({ id, ...rest }) => rest);
    localStorage.setItem(
      SAVE_KEY,
      JSON.stringify({ savedAt: new Date().toISOString(), items: saved })
    );
    setStatus(`Saved ${items.length} items.`);
  } catch (err) {
    setStatus("Could not save: " + err.message);
  }
};

document.getElementById("load-pantry").onclick = () => {
  try {
    const raw = localStorage.getItem(SAVE_KEY);
    if (!raw) {
      setStatus("No saved pantry found yet.");
      return;
    }

    const data = JSON.parse(raw);
    if (!Array.isArray(data.items)) {
      throw new Error("saved data is not valid");
    }

    if (
      items.length > 0 &&
      !confirm("Replace your current pantry with the saved one?")
    ) {
      return;
    }

    items = [];
    nextId = 1;
    // days left must be recalculated, because time has passed since saving
    addItems(
      data.items.map((item) => ({
        ...item,
        daysLeft: daysUntil(item.expiryDate),
      }))
    );
    render(items);
    setStatus(
      `Loaded ${items.length} items (saved ${new Date(
        data.savedAt
      ).toLocaleString()}).`
    );
  } catch (err) {
    setStatus("Could not load: " + err.message);
  }
};

document.getElementById("cook").onclick = async () => {
  // NEW: friendly message instead of a server error when the pantry is empty
  if (items.length === 0) {
    document.getElementById("cook-status").textContent =
      "Scan a receipt first.";
    return;
  }

  document.getElementById("cook-status").textContent = "Cooking up ideas...";
  try {
    const res = await fetch("/api/recipes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(
        items.map((i) => ({ name: i.name, daysLeft: i.daysLeft }))
      ),
    });
    if (!res.ok) throw new Error("Server error " + res.status);
    const recipes = await res.json();
    document.getElementById("recipes").innerHTML = recipes
      .map(
        (r) => `
            <details class="recipe-card">
              <summary>
                <div class="recipe-art" aria-hidden="true">
                  <div class="plate">
                    <span></span>
                    <span></span>
                  </div>
                </div>

                <div>
                  <h3>${escapeHtml(r.title)}</h3>
                  <p class="recipe-time">${escapeHtml(r.time)}</p>
                  <p class="recipe-uses">
                    Uses:
                    ${r.usedExpiringItems.map(escapeHtml).join(", ")}
                  </p>
                  <span class="recipe-link">View recipe</span>
                </div>
              </summary>

              <div class="recipe-details">
                <h4>Ingredients</h4>
                <ul>
                  ${r.ingredients
                    .map(
                      (ingredient) => `
                    <li>${escapeHtml(ingredient)}</li>
                  `
                    )
                    .join("")}
                </ul>

                <h4>Let’s make it</h4>
                <ol>
                  ${r.steps
                    .map(
                      (step) => `
                    <li>${escapeHtml(step)}</li>
                  `
                    )
                    .join("")}
                </ol>
              </div>
            </details>
          `
      )
      .join("");

    document.getElementById("cook-status").textContent = "";
  } catch (err) {
    document.getElementById("cook-status").textContent =
      "Could not get recipes: " + err.message;
  }
};

document.getElementById("file").onchange = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  document.getElementById("status").textContent = "Reading receipt...";
  const form = new FormData();
  form.append("image", file);
  try {
    const res = await fetch("/api/receipt", {
      method: "POST",
      body: form,
    });
    if (!res.ok) throw new Error("Server error " + res.status);
    addItems(await res.json());
    render(items);
    document.getElementById("status").textContent = "";
  } catch (err) {
    document.getElementById("status").textContent =
      "Could not read the receipt: " + err.message;
  }
  e.target.value = "";
};

render(items);
