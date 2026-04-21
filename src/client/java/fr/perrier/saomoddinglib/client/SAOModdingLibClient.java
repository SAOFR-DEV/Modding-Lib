package fr.perrier.saomoddinglib.client;

import net.fabricmc.api.ClientModInitializer;
import fr.perrier.saomoddinglib.client.command.UICommands;

public class SAOModdingLibClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register UI commands
        UICommands.register();
    }
}
