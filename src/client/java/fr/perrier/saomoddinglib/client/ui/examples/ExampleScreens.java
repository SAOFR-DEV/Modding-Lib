package fr.perrier.saomoddinglib.client.ui.examples;

import fr.perrier.saomoddinglib.client.ui.api.Components;
import fr.perrier.saomoddinglib.client.ui.components.UIComponent;
import fr.perrier.saomoddinglib.client.ui.screen.UIScreen;
import fr.perrier.saomoddinglib.client.ui.state.State;
import fr.perrier.saomoddinglib.client.ui.styling.Style;

import static fr.perrier.saomoddinglib.client.ui.styling.Styles.*;

/**
 * Example UI screens showing how to use the UI library.
 */
public class ExampleScreens {


    /**
     * Create a simple demo screen with an auto-updating counter.
     * Demonstrates the reactive {@link State} primitive: the Text is bound
     * to {@code counter.map(...)} and refreshes by itself whenever the button
     * increments the state.
     */
    public static UIScreen createDemoScreen() {
        State<Integer> counter = State.of(0);

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                // Title
                Components.Text(
                        "SAO UI Library",
                        fontSize(24).textColor(WHITE).bold().build()
                ),

                // Reactive label: re-reads counter.map(...) every frame
                Components.Text(
                        counter.map(v -> "Counter: " + v),
                        fontSize(12).textColor(0xFF_AA_AA_AA).margin(8, 0).build()
                ),

                // Button mutates the state; no manual re-render needed
                Components.Button(
                        "Click",
                        backgroundColor(0xFF_00_AA_FF)
                                .height(50)
                                .width(MATCH_PARENT)
                                .margin(16, 0)
                                .onClick((x, y, btn) -> counter.set(counter.get() + 1))
                                .build()
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