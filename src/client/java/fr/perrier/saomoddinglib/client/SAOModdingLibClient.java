package fr.perrier.saomoddinglib.client;

import fr.perrier.saomoddinglib.client.command.UICommands;
import fr.perrier.saomoddinglib.client.ui.debug.DebugOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SAOModdingLibClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register UI commands
        UICommands.register();

        // Register the debug overlay toggle key. Players can rebind it in
        // Minecraft's controls menu. Default F12; the overlay only reacts
        // when DebugOverlay.isEnabled() is true (auto-off in production jars).
        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.saomoddinglib.toggle_debug",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                "key.categories.saomoddinglib"
        ));
        DebugOverlay.setToggleKeyBinding(toggle);
    }
}
