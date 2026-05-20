package com.nethpotxp.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class NethpotxpClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private boolean isRunning = false;
    private int actionDelay = 0;

    private static final int HELMET_SLOT = 5;
    private static final int CHEST_SLOT = 6;
    private static final int LEGS_SLOT = 7;
    private static final int BOOTS_SLOT = 8;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.nethpotxp.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "category.nethpotxp"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.interactionManager == null) return;

            while (toggleKey.wasPressed()) {
                isRunning = !isRunning;
                client.player.sendMessage(Text.of(
                        isRunning ? "§a[XP] Aktif! (Sayısal Eşitleme)" : "§c[XP] Kapatıldı!"
                ), true);
                actionDelay = 0;
            }

            if (!isRunning) return;

            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            // 1. ADIM: Takılı zırhların net kalan can SAYILARINI al (Örn: 407, 592)
            int helmetDura = getExactDurability(client, EquipmentSlot.HEAD);
            int chestDura  = getExactDurability(client, EquipmentSlot.CHEST);
            int legsDura   = getExactDurability(client, EquipmentSlot.LEGS);
            int bootsDura  = getExactDurability(client, EquipmentSlot.FEET);

            boolean helmetOn = !client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
            boolean chestOn  = !client.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
            boolean legsOn   = !client.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty();
            boolean bootsOn  = !client.player.getEquippedStack(EquipmentSlot.FEET).isEmpty();

            // Sadece üzerimizde olan zırhlar içindeki EN DÜŞÜK can sayısını bul
            int minDura = Integer.MAX_VALUE;
            if (helmetOn) minDura = Math.min(minDura, helmetDura);
            if (chestOn)  minDura = Math.min(minDura, chestDura);
            if (legsOn)   minDura = Math.min(minDura, legsDura);
            if (bootsOn)  minDura = Math.min(minDura, bootsDura);

            // Eğer üzerimizde hiç zırh kalmadıysa (hepsi çıktıysa) envanterden geri giy
            if (!helmetOn && !chestOn && !legsOn && !bootsOn) {
                if (equipArmorFromInventory(client)) {
                    actionDelay = 3;
                    return;
                }
                client.player.sendMessage(Text.of("§c[XP] Envanterde zırh kalmadı!"), true);
                isRunning = false;
                return;
            }

            // 2. ADIM: Akıllı Çıkarma Mekanizması (Can sayısı, en düşükten 15 sayı fazlaysa ÇIKAR)
            // Tolerans olarak 15 verdik ki durmadan saniyelik tak-çıkar yapıp takılmasın
            int tolerance = 15;

            if (helmetOn && helmetDura > minDura + tolerance) {
                clickSlot(client, HELMET_SLOT);
                actionDelay = 3;
                return;
            }
            if (chestOn && chestDura > minDura + tolerance) {
                clickSlot(client, CHEST_SLOT);
                actionDelay = 3;
                return;
            }
            if (legsOn && legsDura > minDura + tolerance) {
                clickSlot(client, LEGS_SLOT);
                actionDelay = 3;
                return;
            }
            if (bootsOn && bootsDura > minDura + tolerance) {
                clickSlot(client, BOOTS_SLOT);
                actionDelay = 3;
                return;
            }

            // 3. ADIM: Çıkan zırhların canı azaldıysa (veya envanterde tamir sırası bekliyorsa) GERI TAKMA KONTROLÜ
            // Eğer bir parça eksikse ve envanterdeki parçanın canı şu an yerdeki minDura seviyesine yakınsa içeri al
            if (!helmetOn || !chestOn || !legsOn || !bootsOn) {
                if (equipArmorFromInventory(client)) {
                    actionDelay = 3;
                    return;
                }
            }

            // 4. ADIM: OTO POT BASMA (Artık tamamen otomatik)
            int xpSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.EXPERIENCE_BOTTLE) {
                    xpSlot = i;
                    break;
                }
            }

            if (xpSlot == -1) {
                client.player.sendMessage(Text.of("§4[XP] Hotbar'da XP Şişesi Kalmadı!"), true);
                isRunning = false;
                return;
            }

            // El değiştir, sağ tıkla fırlat ve eski eline hemen geri dön
            int originalSlot = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = xpSlot;
            
            // Sistemi yormadan sağ tık paketini gönderir
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            
            client.player.getInventory().selectedSlot = originalSlot;
            
            // Pot atma hızı gecikmesi (Sunucudan yutulmaması için ideal süre)
            actionDelay = 2; 
        });
    }

    private void clickSlot(MinecraftClient client, int slotId) {
        if (client.interactionManager == null || client.player == null) return;
        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                slotId, 0, SlotActionType.QUICK_MOVE, client.player
        );
    }

    private boolean equipArmorFromInventory(MinecraftClient client) {
        if (client.player == null) return false;
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot targetSlot = armor.getSlotType();
                
                // Eğer o slot zaten doluysa giyme
                if (!client.player.getEquippedStack(targetSlot).isEmpty()) continue;
                
                clickSlot(client, i);
                return true;
            }
        }
        return false;
    }

    // YENİ METOD: Yüzde yerine direkt kalan net CAN SAYISINI döndürür (Örn: Kask canı 407 ise 407 verir)
    private int getExactDurability(MinecraftClient client, EquipmentSlot slot) {
        if (client.player == null) return 0;
        ItemStack stack = client.player.getEquippedStack(slot);
        if (stack.isEmpty() || !stack.isDamageable()) return Integer.MAX_VALUE;
        return stack.getMaxDamage() - stack.getDamage();
    }
}
