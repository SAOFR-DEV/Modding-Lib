package org.triggersstudio.moddinglib.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;
import org.triggersstudio.moddinglib.client.ui.examples.ExampleScreens;

import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client commands for the SAO UI Library.
 */
public class UICommands {

    private static Supplier<Screen> pendingScreen = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingScreen != null) {
                Supplier<Screen> supplier = pendingScreen;
                pendingScreen = null;
                client.setScreen(supplier.get());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerDemoMenuCommand(dispatcher);
        });
    }

    private static void openNextTick(Supplier<Screen> factory) {
        pendingScreen = factory;
    }

    private static void registerDemoMenuCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                literal("demomenu")
                        .executes(context -> {
                            openNextTick(ExampleScreens::createTooltipScreen);
                            return 1;
                        })
                        .then(literal("playerrender").executes(ctx -> {
                            openNextTick(ExampleScreens::createPlayerRenderScreen);
                            return 1;
                        }))
                        .then(literal("chart").executes(ctx -> {
                            openNextTick(ExampleScreens::createChartScreen);
                            return 1;
                        }))
                        .then(literal("gradient").executes(ctx -> {
                            openNextTick(ExampleScreens::createGradientScreen);
                            return 1;
                        }))
        );
    }
}