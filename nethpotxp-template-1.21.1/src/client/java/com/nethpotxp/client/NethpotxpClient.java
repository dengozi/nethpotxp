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
                        isRunning ? "§a[XP] Aktif!" : "§c[XP] Kapatıldı!"
                ), true);
                actionDelay = 0;
            }

            if (!isRunning) return;

            // KESİN ÇÖZÜM: Gecikme varsa tick'i tamamen burada kesiyoruz!
            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            // 1. Durum: Canları ve takılı olma durumlarını kontrol et
            double helmet = getArmorDurability(client, EquipmentSlot.HEAD);
            double chest  = getArmorDurability(client, EquipmentSlot.CHEST);
            double legs   = getArmorDurability(client, EquipmentSlot.LEGS);
            double boots  = getArmorDurability(client, EquipmentSlot.FEET);

            boolean helmetOn = !client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
            boolean chestOn  = !client.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
            boolean legsOn   = !client.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty();
            boolean bootsOn  = !client.player.getEquippedStack(EquipmentSlot.FEET).isEmpty();

            // Sadece takılı olanların min/max değerlerini bul
            double min = 1.0;
            if (helmetOn) min = Math.min(min, helmet);
            if (chestOn)  min = Math.min(min, chest);
            if (legsOn)   min = Math.min(min, legs);
            if (bootsOn)  min = Math.min(min, boots);

            double max = 0.0;
            if (helmetOn) max = Math.max(max, helmet);
            if (chestOn)  max = Math.max(max, chest);
            if (legsOn)   max = Math.max(max, legs);
            if (bootsOn)  max = Math.max(max, boots);

            // Eğer tüm setler eşitlendiyse veya hepsi %100 olduysa kapat
            if ((helmetOn || chestOn || legsOn || bootsOn) && (max - min) <= 0.03 && min > 0.95) {
                // Eğer envanterde zırh kalmışsa önce hepsini geri giy
                if (equipArmorFromInventory(client)) {
                    actionDelay = 3;
                    return;
                }
                client.player.sendMessage(Text.of("§b[XP] Setler eşitlendi ve giyildi!"), true);
                isRunning = false;
                return;
            }

            // 2. Durum: Canı yüksek olan zırhı ÇIKAR
            if (helmetOn && helmet > min + 0.05) {
                clickSlot(client, HELMET_SLOT);
                actionDelay = 3; // Çıkarma sonrası bekle
                return;
            }
            if (chestOn && chest > min + 0.05) {
                clickSlot(client, CHEST_SLOT);
                actionDelay = 3;
                return;
            }
            if (legsOn && legs > min + 0.05) {
                clickSlot(client, LEGS_SLOT);
                actionDelay = 3;
                return;
            }
            if (bootsOn && boots > min + 0.05) {
                clickSlot(client, BOOTS_SLOT);
                actionDelay = 3;
                return;
            }

            // 3. Durum: Eğer canı az olan zırh kırıldıysa veya yanlışlıkla çıktıysa envanterden GERİ TAK
            if (!helmetOn || !chestOn || !legsOn || !bootsOn) {
                // Sadece canı en az olan zırh kategorisi boşsa envanterden onu giymeye çalış
                if (equipArmorFromInventory(client)) {
                    actionDelay = 3; // Giyme sonrası bekle
                    return;
                }
            }

            // 4. Durum: Her şey hazırsa XP ŞİŞESİ AT
            int xpSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.EXPERIENCE_BOTTLE) {
                    xpSlot = i;
                    break;
                }
            }

            if (xpSlot == -1) {
                client.player.sendMessage(Text.of("§4[XP] Pot kalmadı!"), true);
                isRunning = false;
                return;
            }

            // El değiştirme hızını sunucunun yutmaması için delay koyduk
            client.player.getInventory().selectedSlot = xpSlot;
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            actionDelay = 2; // Pot atma hızı (Çok hızlı atıp sunucudan kick yememek için ideal)
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
        // 9'dan 44'e kadar envanteri tara
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot targetSlot = armor.getSlotType();
                // Eğer o zırh slotu zaten doluysa envanterdeki bu zırhı giyme
                if (!client.player.getEquippedStack(targetSlot).isEmpty()) continue;
                
                clickSlot(client, i);
                return true;
            }
        }
        return false;
    }

    private double getArmorDurability(MinecraftClient client, EquipmentSlot slot) {
        if (client.player == null) return 0.0;
        ItemStack stack = client.player.getEquippedStack(slot);
        if (stack.isEmpty() || !stack.isDamageable()) return 0.0;
        return (double)(stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage();
    }
}
