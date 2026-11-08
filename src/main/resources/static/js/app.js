let items = [];

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

  document.getElementById(
    "pantry-count"
  ).textContent = `${list.length} items total · ${soon} needing attention soon`;
}

document.getElementById("cook").onclick = async () => {
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

async function loadSample() {
  try {
    const res = await fetch("/api/pantry/sample");
    if (!res.ok) throw new Error("Server error " + res.status);

    items = await res.json();
    render(items);
  } catch (err) {
    document.getElementById("status").textContent =
      "Could not load pantry: " + err.message;
  }
}

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
    items = items.concat(await res.json());
    render(items);
    document.getElementById("status").textContent = "";
  } catch (err) {
    document.getElementById("status").textContent =
      "Could not read the receipt: " + err.message;
  }
};

loadSample();
