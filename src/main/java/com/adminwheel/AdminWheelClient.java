package com.adminwheel;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class AdminWheelClient implements ClientModInitializer {
    public static KeyBinding wheelKey;
    public static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        AdminConfig.load();

        // Both keys are in the vanilla Controls screen and can be rebound.
        wheelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.adminwheel.wheel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.adminwheel"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.adminwheel.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.adminwheel"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen());
                }
            }
            while (wheelKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    PlayerEntity target = findTarget(client);
                    if (target != null) {
                        client.setScreen(new RadialScreen(target.getGameProfile().getName(), wheelKey));
                    } else {
                        client.player.sendMessage(
                                Text.literal("Наведись на игрока").formatted(Formatting.RED), true);
                    }
                }
            }
        });
    }

    private static PlayerEntity findTarget(MinecraftClient client) {
        Entity cam = client.getCameraEntity();
        if (cam == null) return null;
        double range = AdminConfig.get().range;
        Vec3d start = cam.getCameraPosVec(1.0f);
        Vec3d dir = cam.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(range));
        Box box = cam.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(cam, start, end, box,
                e -> e instanceof PlayerEntity && e != cam && !e.isSpectator(), range * range);
        if (hit != null && hit.getEntity() instanceof PlayerEntity p) {
            return p;
        }
        return null;
    }

    /** True while the (possibly rebound) key or mouse button is physically held. */
    public static boolean isHeld(KeyBinding kb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long window = mc.getWindow().getHandle();
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
        int code = key.getCode();
        if (code == InputUtil.UNKNOWN_KEY.getCode()) return false;
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(window, code);
    }
}
