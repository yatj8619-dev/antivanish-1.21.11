package com.antivanish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AntiVanishClient implements ClientModInitializer {
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("antivanish.txt");
    public static String targetName = "";

    private static final long DURATION_MS = 3000;
    private final Set<UUID> inRange = new HashSet<>();
    private long effectStart = -1;
    private SkinTextures effectSkin;

    @Override
    public void onInitializeClient() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of("antivanish", "jumpscare"), this::renderHud);
    }

    public static void load() {
        try { if (Files.exists(CONFIG)) targetName = Files.readString(CONFIG).trim(); } catch (Exception ignored) {}
    }

    public static void save() {
        try { Files.writeString(CONFIG, targetName == null ? "" : targetName.trim()); } catch (Exception ignored) {}
    }

    private void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { inRange.clear(); effectStart = -1; return; }

        Set<UUID> now = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            now.add(id);
            if (inRange.contains(id)) continue;

            String name = p.getName().getString();
            mc.inGameHud.getChatHud().addMessage(Text.literal("\u00a7c[AntiVanish] \u00a7f" + name + " \u00a7cis in your render distance!"));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f));

            if (!targetName.isBlank() && name.equalsIgnoreCase(targetName.trim())) {
                effectSkin = p.getSkin();
                effectStart = System.currentTimeMillis();
                // Same sound as the Mining Fatigue elder guardian curse
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f));
            }
        }
        inRange.clear();
        inRange.addAll(now);
    }

    private void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (effectStart < 0 || effectSkin == null) return;
        long elapsed = System.currentTimeMillis() - effectStart;
        if (elapsed > DURATION_MS) { effectStart = -1; return; }

        MinecraftClient mc = MinecraftClient.getInstance();
        float t = elapsed / (float) DURATION_MS;                        // 0..1
        float curve = MathHelper.sin(t * (float) Math.PI);              // grows then shrinks, like the vanilla effect
        int screenH = mc.getWindow().getScaledHeight();
        int size = Math.max(1, Math.round(screenH * (0.1f + 1.3f * curve)));
        int alpha = Math.round(255f * Math.min(1f, curve * 1.6f));
        float spinDeg = (1f - t) * 25f * MathHelper.sin(t * 6f);        // little wobble/rotation
        int color = (alpha << 24) | 0xFFFFFF;

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = screenH / 2;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) cx, (float) cy);
        ctx.getMatrices().rotate((float) Math.toRadians(spinDeg));
        // draws the face + hat layer of the player's skin
        PlayerSkinDrawer.draw(ctx, effectSkin, -size / 2, -size / 2, size, color);
        ctx.getMatrices().popMatrix();
    }
}
