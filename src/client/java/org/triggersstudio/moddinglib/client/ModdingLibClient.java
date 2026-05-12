package org.triggersstudio.moddinglib.client;

import org.triggersstudio.moddinglib.client.ui.debug.DebugOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModdingLibClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // Register the debug overlay toggle key. Players can rebind it in
        // Minecraft's controls menu. Default F12; the overlay only reacts
        // when DebugOverlay.isEnabled() is true (auto-off in production jars).
        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moddinglib.toggle_debug",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                "key.categories.moddinglib"
        ));
        DebugOverlay.setToggleKeyBinding(toggle);
    }
}
