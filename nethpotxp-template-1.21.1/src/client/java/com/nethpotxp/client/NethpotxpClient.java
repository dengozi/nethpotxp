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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
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
                        isRunning ? "§a[XP] Aktif! (Hedef: En Yüksek Can)" : "§c[XP] Kapatıldı!"
                ), true);
                actionDelay = 0;
            }

            if (!isRunning) return;

            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            // 1. ADIM: Eksik zırh varsa önce envanterden geri giy (Set bütünlüğü için)
            if (hasArmorInInventoryToEquip(client)) {
                if (equipArmorFromInventory(client)) {
                    actionDelay = 4;
                    return;
                }
            }

            // Tüm parçaların anlık can sayılarını alıyoruz
            int helmetDura = getExactDurability(client, EquipmentSlot.HEAD);
            int chestDura  = getExactDurability(client, EquipmentSlot.CHEST);
            int legsDura   = getExactDurability(client, EquipmentSlot.LEGS);
            int bootsDura  = getExactDurability(client, EquipmentSlot.FEET);

            boolean helmetOn = !client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
            boolean chestOn  = !client.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
            boolean legsOn   = !client.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty();
            boolean bootsOn  = !client.player.getEquippedStack(EquipmentSlot.FEET).isEmpty();

            // KURAL: Eğer tüm parçalar zaten fulllendiyse makroyu kapat
            if (helmetDura >= 405 && chestDura >= 590 && legsDura >= 552 && bootsDura >= 478) {
                client.player.sendMessage(Text.of("§b[XP] Tüm set tamamen tamir edildi!"), true);
                isRunning = false;
                return;
            }

            // 2. ADIM: HEDEF BELİRLEME (Senin istediğin mantık)
            // Üzerimizdeki veya envanterdeki zırhlar içindeki EN YÜKSEK canı buluyoruz (Örn: 100)
            int targetMaxDura = 0;
            if (helmetOn && helmetDura < 405) targetMaxDura = Math.max(targetMaxDura, helmetDura);
            if (chestOn && chestDura < 590)   targetMaxDura = Math.max(targetMaxDura, chestDura);
            if (legsOn && legsDura < 552)     targetMaxDura = Math.max(targetMaxDura, legsDura);
            if (bootsOn && bootsDura < 478)   targetMaxDura = Math.max(targetMaxDura, bootsDura);

            // 3. ADIM: AYIKLAMA MEKANİZMASI
            // Eğer bir zırhın canı, hedeflediğimiz en yüksek candan fazlaysa (Tolerans: 15), onu ÇIKAR!
            // Böylece o zırh boşa XP emmeyecek, sadece canı az olanlar üstümüzde kalacak.
            int tolerance = 15;
            boolean armorRemoved = false;

            if (helmetOn && helmetDura > targetMaxDura + tolerance) {
                clickSlot(client, HELMET_SLOT);
                armorRemoved = true;
            } else if (chestOn && chestDura > targetMaxDura + tolerance) {
                clickSlot(client, CHEST_SLOT);
                armorRemoved = true;
            } else if (legsOn && legsDura > targetMaxDura + tolerance) {
                clickSlot(client, LEGS_SLOT);
                armorRemoved = true;
            } else if (bootsOn && bootsDura > targetMaxDura + tolerance) {
                clickSlot(client, BOOTS_SLOT);
                armorRemoved = true;
            }

            if (armorRemoved) {
                actionDelay = 4; // Sunucuya zırhı düşürmesi için süre ver
                return;
            }

            // 4. ADIM: OTO POT ATMA (Sadece canı az olan zırhlar üstümüzde kaldığında tetiklenir)
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

            // Sunucu paketli güvenli el değiştirme
            int originalSlot = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = xpSlot;
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(xpSlot));
            
            // Fırlat
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            
            // Eski eşyaya geri dön
            client.player.getInventory().selectedSlot = originalSlot;
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            
            actionDelay = 2; // Seri atış hızı
        });
    }

    private void clickSlot(MinecraftClient client, int slotId) {
        if (client.interactionManager == null || client.player == null) return;
        client.interactionManager.clickSlot(
                client.player.playerScreenHandler.syncId,
                slotId, 0, SlotActionType.QUICK_MOVE, client.player
        );
    }

    private boolean hasArmorInInventoryToEquip(MinecraftClient client) {
        if (client.player == null) return false;
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot targetSlot = armor.getSlotType();
                if (client.player.getEquippedStack(targetSlot).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean equipArmorFromInventory(MinecraftClient client) {
        if (client.player == null) return false;
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot targetSlot = armor.getSlotType();
                if (!client.player.getEquippedStack(targetSlot).isEmpty()) continue;
                
                clickSlot(client, i);
                return true;
            }
        }
        return false;
    }

    private int getExactDurability(MinecraftClient client, EquipmentSlot slot) {
        if (client.player == null) return Integer.MAX_VALUE;
        ItemStack stack = client.player.getEquippedStack(slot);
        if (stack.isEmpty() || !stack.isDamageable()) return Integer.MAX_VALUE;
        return stack.getMaxDamage() - stack.getDamage();
    }
}
