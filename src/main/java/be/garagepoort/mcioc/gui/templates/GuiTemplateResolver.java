package be.garagepoort.mcioc.gui.templates;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.TubingPlugin;
import be.garagepoort.mcioc.gui.TubingGui;
import be.garagepoort.mcioc.gui.exceptions.TubingGuiException;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@IocBean
public class GuiTemplateResolver {

    private static final String IF_ATTR = "if";
    private static final String ON_LEFT_CLICK_ATTR = "onLeftClick";
    private static final String ON_RIGHT_CLICK_ATTR = "onRightClick";
    private static final String ON_MIDDLE_CLICK_ATTR = "onMiddleClick";
    private static final String SLOT_ATTR = "slot";
    private static final String MATERIAL_ATTR = "material";
    private static final String NAME_ATTR = "name";
    private static final String ENCHANTED_ATTR = "enchanted";
    private static final String TRUE = "true";

    private final Configuration freemarkerConfiguration;
    private final DefaultObjectWrapper defaultObjectWrapper;

    public GuiTemplateResolver() {
        freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_28);
        defaultObjectWrapper = new DefaultObjectWrapper(Configuration.VERSION_2_3_28);
        freemarkerConfiguration.setClassForTemplateLoading(TubingPlugin.getPlugin().getClass(), "/");
    }

    public TubingGui resolve(String templatePath) {
        return resolve(templatePath, new HashMap<>());
    }

    public TubingGui resolve(String templatePath, Map<String, Object> params) {
        try {
            Template template = freemarkerConfiguration.getTemplate(templatePath);
            TemplateModel statics = defaultObjectWrapper.getStaticModels();
            StringWriter stringWriter = new StringWriter();

            Map<String, FileConfiguration> fileConfigurations = TubingPlugin.getPlugin().getFileConfigurations();
            fileConfigurations.forEach((k, v) -> {
                Set<String> keys = v.getKeys(true);
                for (String key : keys) {
                    params.put(k + ":" + key, v.get(key));
                    if (k.equalsIgnoreCase("config")) {
                        params.put(key, v.get(key));
                    }
                }
            });

            params.put("statics", statics);
            template.process(params, stringWriter);
            return parseHtml(stringWriter.toString());
        } catch (IOException | TemplateException e) {
            throw new TubingGuiException("Could not load template: [" + templatePath + "]", e);
        }
    }

    private TubingGui parseHtml(String html) {
        Document document = Jsoup.parse(html);
        Element tubingGuiElement = document.selectFirst("TubingGui");

        if (tubingGuiElement == null) {
            throw new TubingGuiException("Invalid html template. No TubingGui element found");
        }

        int size = StringUtils.isBlank(tubingGuiElement.attr("size")) ? 54 : Integer.parseInt(tubingGuiElement.attr("size"));
        Element titleElement = tubingGuiElement.selectFirst("title");
        String title = titleElement == null ? "" : titleElement.text();

        TubingGui.Builder builder = new TubingGui.Builder(format(title), size);
        Elements guiItems = tubingGuiElement.select("GuiItem");
        for (Element guiItem : guiItems) {
            String ifAttr = guiItem.attr(IF_ATTR);
            if (StringUtils.isBlank(ifAttr) || TRUE.equalsIgnoreCase(ifAttr)) {
                String leftClickAction = guiItem.attr(ON_LEFT_CLICK_ATTR);
                String rightClickAction = guiItem.attr(ON_RIGHT_CLICK_ATTR);
                String middleClickAction = guiItem.attr(ON_MIDDLE_CLICK_ATTR);

                int slot = Integer.parseInt(guiItem.attr(SLOT_ATTR));
                String material = guiItem.attr(MATERIAL_ATTR);
                String name = guiItem.attr(NAME_ATTR);
                boolean enchanted = guiItem.hasAttr(ENCHANTED_ATTR);
                List<String> loreLines = parseLoreLines(guiItem);
                builder.addItem(leftClickAction, rightClickAction, middleClickAction, slot, itemStack(material, name, loreLines, enchanted));
            }
        }

        return builder.build();
    }

    private List<String> parseLoreLines(Element guiItem) {
        Element loreElement = guiItem.selectFirst("Lore");
        List<String> loreLines = new ArrayList<>();
        if (loreElement != null) {
            Elements loreLinesElements = loreElement.select("LoreLine");
            loreLines = loreLinesElements.stream().map(Element::text).collect(Collectors.toList());
        }
        return loreLines;
    }

    private ItemStack itemStack(String material, String name, List<String> lore, boolean enchanted) {
        ItemStack itemStack = new ItemStack(Material.valueOf(material));
        itemStack.setAmount(1);

        addName(itemStack, name);
        addLore(itemStack, lore);

        ItemMeta meta = itemStack.getItemMeta();
        if (enchanted) {
            itemStack.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void addName(ItemStack itemStack, String name) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(name.equals("") ? " " : format(name));
        itemStack.setItemMeta(itemMeta);
    }

    private void addLore(ItemStack itemStack, List<String> lore) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> original = itemMeta.getLore();
        if (original == null) original = new ArrayList<>();
        original.addAll(format(lore));
        itemMeta.setLore(original);
        itemStack.setItemMeta(itemMeta);
    }

    private String format(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }

    private List<String> format(List<String> strings) {
        return strings.stream().map(this::format).collect(Collectors.toList());
    }
}
