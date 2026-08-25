package com.athensmc.shopeditor;

import com.athensmc.shopeditor.net.EditorNetwork;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;

/**
 * An administrator's interface for the shops Shopkeepers already owns.
 *
 * <p>A Forge mod on both sides: the screens run on the client, the editing runs on the server, and the two talk
 * over the mod's own channel. It is <strong>not</strong> a port of Shopkeepers, and that is a deliberate refusal
 * rather than a shortcut - the plugin is 1116 classes, 509 of them written against the Bukkit API, and its
 * shops live in its own save format. Reimplementing it would mean every shop already built on the server
 * failing to carry over, which is the one outcome that was ruled out.</p>
 *
 * <p>So the plugin keeps owning the shops and this mod drives it. What an admin gets is the part that was
 * missing: a full-screen editor that says what everything does, an item chooser that can list the whole game or
 * just what is in your inventory, and prices typed in Fantastic Cash instead of counted out in items.</p>
 *
 * <p>Ordinary players need none of this. They click the villager and get the vanilla trade window, with the
 * price written on the payment item so it reads in Cash without any mod installed.</p>
 */
@Mod(ShopEditor.MOD_ID)
public final class ShopEditor {

    public static final String MOD_ID = "fsshopeditor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShopEditor() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        EditorNetwork.register();
    }

    private void setup(FMLCommonSetupEvent event) {
        LOGGER.info("Editor de tiendas listo. Los administradores lo abren con /tienda.");
    }
}
