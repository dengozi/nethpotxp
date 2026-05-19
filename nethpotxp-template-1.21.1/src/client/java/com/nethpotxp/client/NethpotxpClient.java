package com.example.client;

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

public class ExampleModClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private boolean isRunning = false;
    private int actionDelay = 0;

    // Armor slot ID'leri playerScreenHandler'da
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

            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            // Zırh durumlarını al (sadece takılı olanlar)
            double helmet = getArmorDurability(client, EquipmentSlot.HEAD);
            double chest  = getArmorDurability(client, EquipmentSlot.CHEST);
            double legs   = getArmorDurability(client, EquipmentSlot.LEGS);
            double boots  = getArmorDurability(client, EquipmentSlot.FEET);

            boolean helmetOn = !client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
            boolean chestOn  = !client.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
            boolean legsOn   = !client.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty();
            boolean bootsOn  = !client.player.getEquippedStack(EquipmentSlot.FEET).isEmpty();

            // Hiç zırh takılı değilse envanterden takt
            if (!helmetOn && !chestOn && !legsOn && !bootsOn) {
                if (equipArmorFromInventory(client)) {
                    actionDelay = 4;
                    return;
                }
                client.player.sendMessage(Text.of("§c[XP] Envanterde zırh yok!"), true);
                isRunning = false;
                return;
            }

            // Sadece takılı zırhların min durability'sini hesapla
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

            // Eşitlendi mi?
            if ((max - min) <= 0.02) {
                client.player.sendMessage(Text.of("§b[XP] Setler eşitlendi!"), true);
                isRunning = false;
                return;
            }

            // En yüksek durability'li zırhı çıkar (envantere at)
            if (helmetOn && helmet > min + 0.02) {
                clickSlot(client, HELMET_SLOT);
                actionDelay = 3;
                return;
            }
            if (chestOn && chest > min + 0.02) {
                clickSlot(client, CHEST_SLOT);
                actionDelay = 3;
                return;
            }
            if (legsOn && legs > min + 0.02) {
                clickSlot(client, LEGS_SLOT);
                actionDelay = 3;
                return;
            }
            if (bootsOn && boots > min + 0.02) {
                clickSlot(client, BOOTS_SLOT);
                actionDelay = 3;
                return;
            }

            // Çıkarılan zırhı geri tak (envanterden)
            if (!helmetOn || !chestOn || !legsOn || !bootsOn) {
                if (equipArmorFromInventory(client)) {
                    actionDelay = 4;
                    return;
                }
            }

            // XP şişesi bul ve kullan
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

            int prev = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = xpSlot;
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.getInventory().selectedSlot = prev;
            actionDelay = 2;
        });
    }

    private void clickSlot(MinecraftClient client, int slotId) {
        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                slotId, 0, SlotActionType.QUICK_MOVE, client.player
        );
    }

    // Envanterden zırh tak (slot 9-35 = ana envanter)
    private boolean equipArmorFromInventory(MinecraftClient client) {
        // playerScreenHandler'da slot 9-44 arası envanter
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot targetSlot = armor.getSlotType();
                // O slot zaten doluysa atla
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