# hackcamp-project - PantryPulse

PantryPulse turns a grocery receipt into a smart fridge. It gives every item an expiry date and a green, amber or red chip, then suggests a recipe that uses what's about to expire and based on your lab results. If the user would like to track their health and are prone to feeling lightheaded or dizzy, the application also allows the user to upload a scan of their recent lab results where the application would suggest what available foods to eat to help stabilize their health. 

Built for [HackCamp 2027].

## The problem

Students, especially those who live on their own tend to forget the food they buy, and it's easy to not do anything about their lab results due to a busy schedule. 

## How it works

- **Receipt scan:** upload a photo to `/api/receipt` and get a list of food items with expiry dates
- **Shelf life:** a lookup table (`shelf-life.json`) gives each food a storage method, days and a tip. Unknown items get a general estimate
- **Colors:** red is 0-2 days left (or expired), amber is 3-5, green is more than 5
- **Recipes:** send the pantry to `/api/recipes` and get a recipe that prefers items expiring first
- **Sample data:** `/api/pantry/sample` returns a demo pantry, for when the receipt scan isn't available

Lab results are scanned from the most recent scan where the previous scans are deleted. 

## How to run it

1. **Requirements:** Java [17+], Maven (or the included wrapper)
2. **Get a Gemini API key** from Google AI Studio