# Dshop

A configurable Paper shop plugin with category-based economy providers and a quantity confirmation GUI.

## Commands

- `/shop` - Open categories.
- `/shop create <category>` - Create a category with page 1, then click its slot in the categories menu.
- `/shop delete <category>` - Delete a category and its stored prices.
- `/shop add <category> <page>` - Add the item in your main hand, then click an empty slot.
- `/shop remove <category> <page>` - Click the product to remove.
- `/shop price <category> <page>` - Click a product and type its price in chat.
- `/shop reload` - Reload config, prices and category files.
- `/shop edit` - Show the in-game editing guide.
- `/shop seticon <category>` - Choose a category icon from a searchable material picker.
- `/shop setname <category> <display name>` - Change a category's display name.
- `/shop name <category> <page>` - Change a product's display name.
- `/shop command <category> <page>` - Set a console command to run after purchase.

Admin commands require `dshop.admin`. Opening the shop requires no special permission.

## Built-in categories\n\nOn first startup, Dshop installs five SMPShop-inspired starter categories: End, Nether, Gear, Food and Shards. They include the supplied starter item lists, prices, category icons and purchase commands. The Shards category uses CoinsEngine currency ID `shards`; the other four categories use Vault. Existing category files are never overwritten, so your edits are preserved.\n\n## Files

- `plugins/Dshop/config.yml` - Global currency symbols and price formatting only.
- `plugins/Dshop/prices.yml` - Prices indexed by category and item ID.
- `plugins/Dshop/categories/<category>.yml` - Category display settings, economy provider, currency ID, pages and product item stacks.

Each category can use a different provider:
- `VAULT`
- `COINSENGINE` (currency ID is set by `category.currency`)
- `EXCELENTECONOMY` (currency ID is set by `category.currency`)

Vault requires Vault and a registered economy provider. CoinsEngine and ExcellentEconomy are soft dependencies. Dshop uses the UUID-based CoinsEngine withdrawal API when available and supports older Player-based API builds.

## Build

Run `mvn -B package`. The plugin jar is created in `target/`.
