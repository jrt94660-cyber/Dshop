package com.flovex.dshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Dshop extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<String,YamlConfiguration> categories = new LinkedHashMap<>();
    private final Map<UUID,EditSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID,Purchase> purchases = new ConcurrentHashMap<>();
    private NamespacedKey actionKey, categoryKey, pageKey, itemKey;
    private Economy vault;
    private File pricesFile;
    private YamlConfiguration prices;

    @Override public void onEnable() {
        saveDefaultConfig(); actionKey=new NamespacedKey(this,"action"); categoryKey=new NamespacedKey(this,"category");
        pageKey=new NamespacedKey(this,"page"); itemKey=new NamespacedKey(this,"item");
        pricesFile=new File(getDataFolder(),"prices.yml");
        if(!pricesFile.exists()) try { getDataFolder().mkdirs(); pricesFile.createNewFile(); } catch(Exception e){getLogger().severe(e.getMessage());}
        prices=YamlConfiguration.loadConfiguration(pricesFile);
        loadCategories();
        if(getCommand("shop")!=null){getCommand("shop").setExecutor(this);getCommand("shop").setTabCompleter(this);}
        Bukkit.getPluginManager().registerEvents(this,this); setupVault();
        getLogger().info("Dshop enabled.");
    }
    private void setupVault(){ if(Bukkit.getPluginManager().getPlugin("Vault")!=null){RegisteredServiceProvider<Economy> rsp=Bukkit.getServicesManager().getRegistration(Economy.class);if(rsp!=null)vault=rsp.getProvider();}}
    private void loadCategories(){
        categories.clear(); File dir=new File(getDataFolder(),"categories"); if(!dir.exists())dir.mkdirs();
        File[] files=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".yml")); if(files==null)return;
        Arrays.sort(files); for(File f:files)categories.put(f.getName().substring(0,f.getName().length()-4).toLowerCase(Locale.ROOT),YamlConfiguration.loadConfiguration(f));
    }
    private String color(String s){
        if(s==null)return "";
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(s);
        StringBuffer out=new StringBuffer();
        while(matcher.find()){String hex=matcher.group(1);StringBuilder legacy=new StringBuilder("§x");for(char ch:hex.toCharArray())legacy.append('§').append(ch);matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(legacy.toString()));}
        matcher.appendTail(out);return ChatColor.translateAlternateColorCodes('&',out.toString());
    }
    private void msg(Player p,String s){p.sendMessage(color(getConfig().getString("messages.prefix","&8[&bDshop&8] ")+s));}
    private ItemStack tagged(Material m,String name,List<String> lore,String action,String cat,int page,String item){
        ItemStack it=new ItemStack(m); ItemMeta meta=it.getItemMeta();meta.setDisplayName(color(name));
        if(lore!=null)meta.setLore(lore.stream().map(this::color).toList());
        if(action!=null)meta.getPersistentDataContainer().set(actionKey,PersistentDataType.STRING,action);
        if(cat!=null)meta.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,cat);
        meta.getPersistentDataContainer().set(pageKey,PersistentDataType.INTEGER,page);
        if(item!=null)meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,item);
        it.setItemMeta(meta);return it;
    }
    private void openCategories(Player p){
        int rows=3;
        Inventory inv=Bukkit.createInventory(p,rows*9,color(getConfig().getString("gui.categories-title","&8Shop | Categories")));
        for(var en:categories.entrySet()){
            YamlConfiguration c=en.getValue(); int slot=c.getInt("category.slot",-1); if(slot<0||slot>=inv.getSize())continue;
            String display=c.getString("category.display-name","&b"+en.getKey().toUpperCase(Locale.ROOT));
            String desc=c.getString("category.description","Browse this category.");
            Material mat=Material.matchMaterial(c.getString("category.material","CHEST"));if(mat==null)mat=Material.CHEST;
            inv.setItem(slot,tagged(mat,display,List.of("&7Description","", "&bInformation:","&bClick here to view","&f"+desc,"","&e▸ &lCLICK &eto Open"),"category",en.getKey(),c.getInt("category.default-page",1),null));
        }
        p.openInventory(inv);
    }
    private void openPage(Player p,String cat,int page){
        YamlConfiguration c=categories.get(cat);if(c==null){msg(p,"&cCategory not found.");return;}
        int rows=3;
        Inventory inv=Bukkit.createInventory(p,rows*9,color(getConfig().getString("gui.page-title","&8{category} | Page {page}").replace("{category}",c.getString("category.name",cat)).replace("{page}",String.valueOf(page))));
        ConfigurationSection section=c.getConfigurationSection("pages."+page+".items");
        if(section!=null)for(String key:section.getKeys(false)){
            int slot=section.getInt(key+".slot",-1);if(slot<0||slot>=inv.getSize())continue;
            ItemStack stack=section.getItemStack(key+".item");if(stack==null)continue;
            ItemStack shown=stack.clone();ItemMeta meta=shown.getItemMeta();
            List<String> lore=new ArrayList<>();
            lore.add(color("&7Description"));
            if(meta.hasLore())lore.addAll(meta.getLore());
            lore.add("");
            lore.add(color("&bInformation:"));
            lore.add(color("&bPrice: &f"+formatPrice(price(cat,key),cat)));
            lore.add("");
            lore.add(color("&e▸ &lCLICK &eto Purchase"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey,PersistentDataType.STRING,"product");
            meta.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,cat);
            meta.getPersistentDataContainer().set(pageKey,PersistentDataType.INTEGER,page);
            meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,key);
            shown.setItemMeta(meta);
            inv.setItem(slot,shown);
        }
        inv.setItem(inv.getSize()-9,tagged(Material.ARROW,"&ePrevious Page",List.of("&7Go to page "+Math.max(1,page-1)),"previous",cat,page,null));
        inv.setItem(inv.getSize()-1,tagged(Material.ARROW,"&eNext Page",List.of("&7Go to page "+(page+1)),"next",cat,page,null));
        p.openInventory(inv);
    }
    private double price(String cat,String id){return prices.getDouble(cat+"."+id,0);}
    private String formatPrice(double amount,String category){
        YamlConfiguration data=categories.get(category);
        String provider=data==null?"VAULT":data.getString("category.economy","VAULT").toUpperCase(Locale.ROOT);
        String currency=data==null?"":data.getString("category.currency","");
        String symbol=getConfig().getString("currency.symbols."+provider+"."+currency,
                getConfig().getString("currency.symbols."+provider,getConfig().getString("currency.symbol","$")));
        if(getConfig().getBoolean("price-format.enabled",true)){
            List<String> suffixes=getConfig().getStringList("price-format.suffixes");
            if(suffixes.isEmpty())suffixes=List.of("k","m","b","t","q","qq");
            int i=0;double n=amount;while(n>=1000&&i<suffixes.size()){n/=1000;i++;}
            String suffix=i==0?"":suffixes.get(i-1);
            return symbol+new DecimalFormat(getConfig().getString("price-format.decimal-pattern","0.##")).format(n)+suffix;
        }
        return symbol+new DecimalFormat(getConfig().getString("currency.format","#,##0.##")).format(amount);
    }
    private void openPurchase(Player p,String cat,int page,String id){
        YamlConfiguration c=categories.get(cat);ItemStack stack=c.getItemStack("pages."+page+".items."+id+".item");if(stack==null)return;
        double unit=price(cat,id);if(unit<=0){msg(p,"&cThis item has no valid price.");return;}
        Purchase purchase=new Purchase(cat,page,id,stack.clone(),unit,1);purchases.put(p.getUniqueId(),purchase);renderPurchase(p);
    }
    private void renderPurchase(Player p){
        Purchase b=purchases.get(p.getUniqueId());if(b==null)return;
        Inventory inv=Bukkit.createInventory(p,27,color(getConfig().getString("gui.purchase-title","&8Confirm Purchase")));
        inv.setItem(4,b.item.clone());
        inv.setItem(9,tagged(Material.RED_CANDLE,"&cRemove 1",List.of("&7Remove one item"),"minus1",b.cat,b.page,b.id));
        inv.setItem(10,tagged(Material.RED_CANDLE,"&cRemove 10",List.of("&7Remove ten items"),"minus10",b.cat,b.page,b.id));
        inv.setItem(11,tagged(Material.RED_CANDLE,"&cRemove 64",List.of("&7Remove sixty-four items"),"minus64",b.cat,b.page,b.id));
        inv.setItem(12,tagged(Material.RED_STAINED_GLASS_PANE,"&cCancel",List.of("&7Cancel this purchase"),"cancel",b.cat,b.page,b.id));
        inv.setItem(18,tagged(Material.ARROW,"&cPrevious Page",List.of("&7Return to page "+Math.max(1,b.page-1)),"previous",b.cat,b.page,null));
        inv.setItem(14,tagged(Material.LIME_CANDLE,"&aAdd 1",List.of("&7Add one item"),"plus1",b.cat,b.page,b.id));
        inv.setItem(15,tagged(Material.LIME_CANDLE,"&aAdd 10",List.of("&7Add ten items"),"plus10",b.cat,b.page,b.id));
        inv.setItem(16,tagged(Material.LIME_CANDLE,"&aAdd 64",List.of("&7Add sixty-four items"),"plus64",b.cat,b.page,b.id));
        inv.setItem(26,tagged(Material.ARROW,"&aNext Page",List.of("&7Go to page "+(b.page+1)),"next",b.cat,b.page,null));
        inv.setItem(22,tagged(Material.LIME_STAINED_GLASS_PANE,"&aConfirm Purchase",List.of("&7Quantity: &f"+b.amount,"&7Total: &a"+formatPrice(b.unit*b.amount,b.cat)),"confirm",b.cat,b.page,b.id));
        p.openInventory(inv);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(a.length==0){openCategories(p);return true;}
        if(!p.hasPermission("dshop.admin")){msg(p,"&cYou don't have permission.");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "reload" -> {reloadConfig();prices=YamlConfiguration.loadConfiguration(pricesFile);loadCategories();setupVault();msg(p,"&aDshop reloaded.");}
            case "create" -> {if(a.length<3){msg(p,"&c/shop create <category> <page>");return true;}String name=a[1].toLowerCase(Locale.ROOT);File f=new File(getDataFolder(),"categories/"+name+".yml");if(f.exists()){msg(p,"&cCategory already exists.");return true;}YamlConfiguration y=new YamlConfiguration();y.set("category.name",a[1]);y.set("category.display-name","&b&l"+a[1].toUpperCase(Locale.ROOT));y.set("category.description","Browse "+a[1]);y.set("category.material","CHEST");y.set("category.slot",-1);y.set("category.default-page",Integer.parseInt(a[2]));y.set("category.economy","VAULT");y.set("category.currency","shards");y.set("pages."+a[2]+".items",new LinkedHashMap<>());try{y.save(f);}catch(Exception ex){msg(p,"&cCould not save category.");return true;}loadCategories();sessions.put(p.getUniqueId(),new EditSession("category",name,Integer.parseInt(a[2])));p.closeInventory();openCategories(p);msg(p,"&eClick an empty slot in the category menu to place it.");return true;}
            case "delete" -> {if(a.length<2)return true;File f=new File(getDataFolder(),"categories/"+a[1].toLowerCase(Locale.ROOT)+".yml");if(f.delete()){categories.remove(a[1].toLowerCase(Locale.ROOT));prices.set(a[1].toLowerCase(Locale.ROOT),null);savePrices();msg(p,"&aCategory and its prices deleted.");}else msg(p,"&cCategory not found.");return true;}
            case "add","remove","price" -> {if(a.length<3){msg(p,"&c/shop "+a[0]+" <category> <page>");return true;}String cat=a[1].toLowerCase(Locale.ROOT);if(!categories.containsKey(cat)){msg(p,"&cCategory not found.");return true;}int page;try{page=Integer.parseInt(a[2]);}catch(Exception e){msg(p,"&cInvalid page.");return true;}if(a[0].equalsIgnoreCase("add")){ItemStack hand=p.getInventory().getItemInMainHand();if(hand.getType()==Material.AIR){msg(p,"&cHold an item first.");return true;}sessions.put(p.getUniqueId(),new EditSession("add",cat,page,singleItem(hand)));msg(p,"&eClick an empty slot in the category page to place the item.");}else if(a[0].equalsIgnoreCase("remove")){sessions.put(p.getUniqueId(),new EditSession("remove",cat,page));msg(p,"&eClick the item you want to remove.");}else{sessions.put(p.getUniqueId(),new EditSession("price",cat,page));msg(p,"&eClick the item, then type its price in chat. Type cancel to stop.");}p.closeInventory();openPage(p,cat,page);return true;}
            default -> msg(p,"&cUnknown command.");
        }return true;
    }
    private ItemStack singleItem(ItemStack source){ItemStack copy=source.clone();copy.setAmount(1);return copy;}
    private void saveCategory(String cat){try{categories.get(cat).save(new File(getDataFolder(),"categories/"+cat+".yml"));}catch(Exception e){getLogger().warning(e.getMessage());}}
    private void savePrices(){try{prices.save(pricesFile);}catch(Exception e){getLogger().warning(e.getMessage());}}
    @EventHandler public void onClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        ItemStack clicked=e.getCurrentItem();
        String action=clicked!=null&&clicked.hasItemMeta()?clicked.getItemMeta().getPersistentDataContainer().get(actionKey,PersistentDataType.STRING):null;
        EditSession edit=sessions.get(p.getUniqueId());
        if(edit!=null && (edit.type.equals("category")||edit.type.equals("add")||edit.type.equals("remove")||edit.type.equals("price"))){
            e.setCancelled(true);
            if(e.getClickedInventory()==null||e.getClickedInventory()!=e.getView().getTopInventory())return;
            int slot=e.getSlot();
            if(edit.type.equals("category")){YamlConfiguration c=categories.get(edit.cat);c.set("category.slot",slot);saveCategory(edit.cat);sessions.remove(p.getUniqueId());p.closeInventory();msg(p,"&aCategory slot saved.");return;}
            YamlConfiguration c=categories.get(edit.cat);String base="pages."+edit.page+".items.";
            if(edit.type.equals("add")){if(clicked!=null&&clicked.getType()!=Material.AIR){msg(p,"&cChoose an empty slot.");return;}String id=UUID.randomUUID().toString().substring(0,8);c.set(base+id+".slot",slot);c.set(base+id+".item",edit.item);saveCategory(edit.cat);sessions.remove(p.getUniqueId());msg(p,"&aItem added.");openPage(p,edit.cat,edit.page);return;}
            String id=null;ConfigurationSection sec=c.getConfigurationSection(base);if(sec!=null)for(String k:sec.getKeys(false))if(sec.getInt(k+".slot",-1)==slot){id=k;break;}
            if(id==null){msg(p,"&cNo shop item in that slot.");return;}
            if(edit.type.equals("remove")){c.set(base+id,null);prices.set(edit.cat+"."+id,null);saveCategory(edit.cat);savePrices();sessions.remove(p.getUniqueId());msg(p,"&aItem removed.");openPage(p,edit.cat,edit.page);}
            else{edit.itemId=id;sessions.put(p.getUniqueId(),edit);p.closeInventory();msg(p,"&eType the new price in chat (examples: 2500000, 2.5m). Type cancel to stop.");}
            return;
        }
        if(action==null)return;e.setCancelled(true);
        String cat=clicked.getItemMeta().getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING);
        int page=clicked.getItemMeta().getPersistentDataContainer().getOrDefault(pageKey,PersistentDataType.INTEGER,1);
        switch(action){
            case "category" -> openPage(p,cat,page);
            case "previous" -> openPage(p,cat,Math.max(1,page-1));
            case "next" -> openPage(p,cat,page+1);
            case "product" -> openPurchase(p,cat,page,clicked.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING));
            case "minus1","minus10","minus64","plus1","plus10","plus64" -> {Purchase b=purchases.get(p.getUniqueId());if(b==null)return;int n=Integer.parseInt(action.replaceAll("[^0-9]",""));b.amount=Math.max(1,Math.min(2304,b.amount+(action.startsWith("plus")?n:-n)));renderPurchase(p);}
            case "cancel" -> {purchases.remove(p.getUniqueId());openPage(p,cat,page);}
            case "confirm" -> {Purchase b=purchases.get(p.getUniqueId());if(b==null)return;double total=b.unit*b.amount;if(b.amount>b.item.getMaxStackSize()*36){msg(p,"&cThat quantity is too large.");return;}if(!withdraw(p,cat,total)){msg(p,"&cYou don't have enough currency or the economy provider is unavailable.");return;}int remaining=b.amount;while(remaining>0){int give=Math.min(remaining,b.item.getMaxStackSize());ItemStack part=b.item.clone();part.setAmount(give);Map<Integer,ItemStack> overflow=p.getInventory().addItem(part);overflow.values().forEach(it->p.getWorld().dropItemNaturally(p.getLocation(),it));remaining-=give;}purchases.remove(p.getUniqueId());msg(p,"&aPurchased &f"+b.amount+"x &a"+b.item.getType().name()+" for &f"+formatPrice(total,b.cat));openPage(p,b.cat,b.page);}
        }
    }
    private boolean withdraw(Player p,String cat,double amount){
        YamlConfiguration category=categories.get(cat);
        String provider=category.getString("category.economy","VAULT").toUpperCase(Locale.ROOT);
        String currency=category.getString("category.currency","shards");
        try {
            if(provider.equals("VAULT"))return vault!=null&&vault.has(p,amount)&&vault.withdrawPlayer(p,amount).transactionSuccess();
            if(provider.equals("COINSENGINE")){
                if(Bukkit.getPluginManager().getPlugin("CoinsEngine")==null)return false;
                Class<?> api=Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
                Object currencyObject=api.getMethod("getCurrency",String.class).invoke(null,currency);
                if(currencyObject==null)return false;
                for(var method:api.getMethods())if(method.getName().equals("removeBalance")&&method.getParameterCount()==3){
                    Class<?>[] types=method.getParameterTypes();
                    if(types[0].isAssignableFrom(p.getClass())&&types[1].isInstance(currencyObject)&&(types[2]==double.class||types[2]==Double.class)){
                        Object result=method.invoke(null,p,currencyObject,amount);
                        return result instanceof Boolean b&&b;
                    }
                }
                return false;
            }
            if(provider.equals("EXCELENTECONOMY")||provider.equals("EXCELLENTEconomy".toUpperCase(Locale.ROOT))){
                Class<?> api=Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
                Object registration=Bukkit.getServicesManager().getRegistration(api);
                if(registration==null)return false;
                Object instance=registration.getClass().getMethod("getProvider").invoke(registration);
                for(var method:api.getMethods())if(method.getName().equals("withdraw")&&method.getParameterCount()==3){
                    Object result=method.invoke(instance,p,currency,amount);
                    return result instanceof Boolean b&&b;
                }
            }
        } catch(Exception ex) {
            getLogger().warning("Economy withdrawal failed for "+provider+": "+ex.getClass().getSimpleName()+" - "+ex.getMessage());
        }
        return false;
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent e){
        EditSession s=sessions.get(e.getPlayer().getUniqueId());if(s==null||!s.type.equals("price"))return;e.setCancelled(true);String input=e.getMessage().trim();
        Bukkit.getScheduler().runTask(this,()->{Player p=e.getPlayer();if(input.equalsIgnoreCase("cancel")){sessions.remove(p.getUniqueId());msg(p,"&cPrice editing cancelled.");return;}double amount=parseAmount(input);if(!Double.isFinite(amount)||amount<=0){msg(p,"&cInvalid price. Try 2500000 or 2.5m.");return;}prices.set(s.cat+"."+s.itemId,amount);savePrices();sessions.remove(p.getUniqueId());msg(p,"&aPrice saved: &f"+formatPrice(amount,s.cat));openPage(p,s.cat,s.page);});
    }
    private double parseAmount(String raw){try{String s=raw.toLowerCase(Locale.ROOT).replace(",","").trim();double mult=1;for(String suffix:List.of("qq","k","m","b","t","q"))if(s.endsWith(suffix)){mult=switch(suffix){case "k"->1e3;case "m"->1e6;case "b"->1e9;case "t"->1e12;case "q"->1e15;default->1e18;};s=s.substring(0,s.length()-suffix.length());break;}return Double.parseDouble(s)*mult;}catch(Exception e){return -1;}}
    @EventHandler public void onInventoryDrag(InventoryDragEvent e){if(sessions.containsKey(e.getWhoClicked().getUniqueId()))e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return List.of("create","delete","add","remove","price","reload");if(args.length==2)return new ArrayList<>(categories.keySet());return List.of();}
    private static final class Purchase{final String cat,id;final int page;final ItemStack item;final double unit;int amount;Purchase(String c,int p,String i,ItemStack it,double u,int a){cat=c;page=p;id=i;item=it;unit=u;amount=a;}}
    private static final class EditSession{final String type,cat;final int page;final ItemStack item;String itemId;EditSession(String t,String c,int p){this(t,c,p,null);}EditSession(String t,String c,int p,ItemStack i){type=t;cat=c;page=p;item=i;}}
}
