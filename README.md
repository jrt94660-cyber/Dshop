# Dshop

A configurable Paper shop plugin with category-based economy providers and a quantity confirmation GUI.

## Commands

- `/shop` - Open categories.
- `/shop create <category> <page>` - Create a category, then click its slot in the categories menu.
- `/shop delete <category>` - Delete a category and its stored prices.
- `/shop add <category> <page>` - Add the item in your main hand, then click an empty slot.
- `/shop remove <category> <page>` - Click the product to remove.
- `/shop price <category> <page>` - Click a product and type its price in chat.
- `/shop reload` - Reload config, prices and category files.

Admin commands require `dshop.admin`. Opening the shop requires no special permission.

## Files

- `plugins/Dshop/config.yml` - Global currency symbols and number/price formatting, plus GUI titles.
- `plugins/Dshop/prices.yml` - Prices indexed by category and item ID.
- `plugins/Dshop/categories/<category>.yml` - Category display settings, economy provider, currency ID, pages and product item stacks.

Each category can use a different provider:
- `VAULT`
- `COINSENGINE` (currency ID is set by `category.currency`)
- `EXCELENTECONOMY` (currency ID is set by `category.currency`)

Vault requires Vault and a registered economy provider. CoinsEngine and ExcellentEconomy are soft dependencies.

## Build

Run `mvn -B package`. The plugin jar is created in `target/`.
