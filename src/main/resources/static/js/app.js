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

function render(list) {
  const sorted = [...list].sort((a, b) => a.daysLeft - b.daysLeft);

  const soon = list.filter((i) => i.daysLeft >= 0 && i.daysLeft <= 2).length;

  document.getElementById(
    "pantry-count"
  ).textContent = `${list.length} items · ${soon} needing attention soon`;

  document.getElementById("list").innerHTML = sorted
    .map((i) => {
      const color =
        i.daysLeft <= 2
          ? "expiry-red"
          : i.daysLeft <= 5
          ? "expiry-amber"
          : "expiry-green";

      const label =
        i.daysLeft < 0
          ? "Past estimated date"
          : i.daysLeft === 0
          ? "Estimated today"
          : `${i.daysLeft} day${i.daysLeft === 1 ? "" : "s"} left`;

      return `
                <article class="food-card">
                  <details>
                    <summary>
                      <span class="food-name">
                        ${escapeHtml(i.name)}
                      </span>
                      <span class="expiry-chip ${color}">
                        ${escapeHtml(label)}
                      </span>
                    </summary>

                    <div class="food-details">
                      <p>Store: ${escapeHtml(i.storage)}</p>
                      <p>
                        Estimated date: ${escapeHtml(i.expiryDate)}
                      </p>
                      <p>${escapeHtml(i.tip)}</p>
                      ${
                        i.needsReview
                          ? "<p>Please check this general estimate.</p>"
                          : ""
                      }
                    </div>
                  </details>
                </article>
              `;
    })
    .join("");
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
