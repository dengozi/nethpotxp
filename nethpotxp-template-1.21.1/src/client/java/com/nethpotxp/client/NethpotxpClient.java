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

    private static final int TARGET_DURABILITY = 400;

    // BYPASS AYARLARI: Tamamen insansı (legit) hareket süreleri
    private int potThrowStep = 0; // 0: Hazır, 1: Eline aldı bekliyor, 2: Attı dönecek
    private int savedOriginalSlot = -1;

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
                        isRunning ? "§a[XP] Aktif! (Bypass Modu)" : "§c[XP] Kapatıldı!"
                ), true);
                actionDelay = 0;
                potThrowStep = 0;
            }

            if (!isRunning) return;

            // Eğer bir işlem sonrası bekleme süresindeysek tick'i tamamen kes
            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            // Üstümüzdeki zırhların net kalan can sayılarını alıyoruz
            int helmetDura = getExactDurability(client, EquipmentSlot.HEAD);
            int chestDura  = getExactDurability(client, EquipmentSlot.CHEST);
            int legsDura   = getExactDurability(client, EquipmentSlot.LEGS);
            int bootsDura  = getExactDurability(client, EquipmentSlot.FEET);

            boolean helmetOn = !client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
            boolean chestOn  = !client.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
            boolean legsOn   = !client.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty();
            boolean bootsOn  = !client.player.getEquippedStack(EquipmentSlot.FEET).isEmpty();

            // 1. ADIM: AYIKLAMA (Canı 400 ve üzeri olan zırhı ANINDA ÇIKAR)
            boolean armorRemoved = false;

            if (helmetOn && helmetDura >= TARGET_DURABILITY) {
                clickSlot(client, HELMET_SLOT);
                armorRemoved = true;
            } else if (chestOn && chestDura >= TARGET_DURABILITY) {
                clickSlot(client, CHEST_SLOT);
                armorRemoved = true;
            } else if (legsOn && legsDura >= TARGET_DURABILITY) {
                clickSlot(client, LEGS_SLOT);
                armorRemoved = true;
            } else if (bootsOn && bootsDura >= TARGET_DURABILITY) {
                clickSlot(client, BOOTS_SLOT);
                armorRemoved = true;
            }

            if (armorRemoved) {
                actionDelay = 5; // Sunucuya zırh düşürme süresi (Biraz artırdık ki paketler çakışmasın)
                return;
            }

            // 2. ADIM: GERİ GİYME (Envanterde canı hala 400'den küçük bir zırh kalmışsa onu geri tak)
            if (hasArmorInInventoryToEquip(client)) {
                if (equipArmorFromInventory(client)) {
                    actionDelay = 5;
                    return;
                }
            }

            // KURAL: Eğer iş bittiyse kapat
            if (!helmetOn && !chestOn && !legsOn && !bootsOn && !hasArmorInInventoryToEquip(client)) {
                equipAllArmorFinal(client);
                client.player.sendMessage(Text.of("§b[XP] Setler güvenle 400 cana eşitlendi!"), true);
                isRunning = false;
                return;
            }

            // 3. ADIM: BYPASS'LI OTO POT ATMA (Sıralı ve Gecikmeli El Değiştirme)
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

            // İnsansı Atış Döngüsü (Aynı tick içinde her şeyi yapıp ban yemeyi engeller)
            if (potThrowStep == 0) {
                // Aşama 1: Sadece eline potu al ve sunucuya bildir. Bu tick'te BAŞKA HİÇBİR ŞEY YAPMA.
                savedOriginalSlot = client.player.getInventory().selectedSlot;
                client.player.getInventory().selectedSlot = xpSlot;
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(xpSlot));
                
                potThrowStep = 1;
                actionDelay = 1; // 1 tick (50ms) elinde potu tutarak bekle (Legit görünüm)
                return;
            } 
            
            if (potThrowStep == 1) {
                // Aşama 2: Elinde halihazırda pot var, şimdi fırlat.
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                
                potThrowStep = 2;
                actionDelay = 2; // Fırlattıktan sonra sunucunun paketi yutması için 2 tick bekle
                return;
            } 
            
            if (potThrowStep == 2) {
                // Aşama 3: Artık eski elindeki silaha/eşyaya güvenle geri dönebilirsin.
                if (savedOriginalSlot != -1) {
                    client.player.getInventory().selectedSlot = savedOriginalSlot;
                    client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(savedOriginalSlot));
                }
                
                potThrowStep = 0; // Döngüyü sıfırla
                actionDelay = 3;  // İki pot atışı arasına insansı bir boşluk koy (Saniyede max 3-4 pot)
            }
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
                int currentDura = stack.getMaxDamage() - stack.getDamage();
                if (client.player.getEquippedStack(targetSlot).isEmpty() && currentDura < TARGET_DURABILITY) {
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
                int currentDura = stack.getMaxDamage() - stack.getDamage();
                if (currentDura >= TARGET_DURABILITY) continue;
                clickSlot(client, i);
                return true;
            }
        }
        return false;
    }

    private void equipAllArmorFinal(MinecraftClient client) {
        if (client.player == null) return;
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot targetSlot = armor.getSlotType();
                if (client.player.getEquippedStack(targetSlot).isEmpty()) {
                    clickSlot(client, i);
                }
            }
        }
    }

    private int getExactDurability(MinecraftClient client, EquipmentSlot slot) {
        if (client.player == null) return Integer.MAX_VALUE;
        ItemStack stack = client.player.getEquippedStack(slot);
        if (stack.isEmpty() || !stack.isDamageable()) return Integer.MAX_VALUE;
        return stack.getMaxDamage() - stack.getDamage();
    }
}
