package fr.perrier.saomoddinglib.client.ui.examples;

import fr.perrier.saomoddinglib.client.ui.api.Components;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.screen.UIScreen;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

import static fr.perrier.saomoddinglib.client.ui.styling.Styles.*;

/**
 * Example UI screens showing how to use the UI library.
 */
public class ExampleScreens {

    /**
     * Create a simple demo screen.
     */
    public static UIScreen createDemoScreen() {
        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                // Title
                Components.Text(
                        "SAO UI Library",
                        fontSize(24).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "A simple and intuitive UI framework for Minecraft mods",
                        fontSize(12).textColor(0xFF_AA_AA_AA).margin(8, 0).build()
                ),

                // Buttons
                Components.Button(
                        "Start Game",
                        backgroundColor(0xFF_00_AA_FF)
                                .height(50)
                                .width(MATCH_PARENT)
                                .margin(16, 0)
                                .onClick((x, y, btn) -> System.out.println("Start clicked"))
                                .build()
                ),

                Components.Button(
                        "Settings",
                        backgroundColor(0xFF_00_88_CC)
                                .height(50)
                                .width(MATCH_PARENT)
                                .margin(8, 0)
                                .onClick((x, y, btn) -> System.out.println("Settings clicked"))
                                .build()
                ),

                // Row with two buttons
                Components.Row(
                        margin(16, 0).build(),

                        Components.Button(
                                "OK",
                                backgroundColor(0xFF_00_CC_00)
                                        .height(40)
                                        .build()
                        ),

                        Components.Button(
                                "Cancel",
                                backgroundColor(0xFF_CC_00_00)
                                        .height(40)
                                        .build()
                        )
                )
        );

        return Components.Screen(content, "Demo Screen");
    }

    /**
     * Create a scrollable list example.
     */
    public static UIScreen createScrollableListScreen() {
        UIComponent content = Components.Column(
                Style.padding(16).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Scrollable List",
                        Style.fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.VScroll(
                        Style.backgroundColor(0xFF_25_25_25)
                                .height(MATCH_PARENT)
                                .width(MATCH_PARENT)
                                .margin(16, 0)
                                .build(),

                        Components.Column(Style.padding(8).build(), col -> {
                            for (int i = 0; i < 50; i++) {
                                final int index = i;
                                col.Button(
                                        "Item " + (i + 1),
                                        Style.backgroundColor(i % 2 == 0 ? 0xFF_2A_2A_2A : 0xFF_35_35_35)
                                                .textColor(WHITE)
                                                .height(30)
                                                .width(MATCH_PARENT)
                                                .onClick((x, y, btn) -> System.out.println("Clicked item " + (index + 1)))
                                                .build()
                                );
                            }
                        })
                )
        );

        return Components.Screen(content, "Scrollable List");
    }
}




