package org.triggersstudio.moddinglib.client.ui.examples;

import org.triggersstudio.moddinglib.client.ui.api.Components;
import org.triggersstudio.moddinglib.client.ui.components.UIComponent;
import org.triggersstudio.moddinglib.client.ui.screen.UIScreen;
import org.triggersstudio.moddinglib.client.ui.state.State;
import org.triggersstudio.moddinglib.client.ui.styling.Style;

import static org.triggersstudio.moddinglib.client.ui.styling.Styles.*;

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
        State<Integer> counter = State.of(0, "demo.counter");

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
     * Create a screen demonstrating a TextField bound bidirectionally to a
     * {@code State<String>}. Typing in the field updates the state; derived
     * Text nodes above show how {@link State#map} chains live content.
     */
    public static UIScreen createTextFieldScreen() {
        State<String> query = State.of("", "demo.textfield.query");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "TextField Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        query.map(q -> q.isEmpty() ? "Start typing below..." : "You typed: " + q),
                        fontSize(12).textColor(0xFF_AA_AA_AA).margin(12, 0, 4, 0).build()
                ),

                Components.Text(
                        query.map(q -> q.length() + " character" + (q.length() == 1 ? "" : "s")),
                        fontSize(10).textColor(0xFF_77_77_77).margin(0, 0, 8, 0).build()
                ),

                Components.TextField(
                        query,
                        "Search...",
                        backgroundColor(0xFF_2A_2A_2A)
                                .textColor(WHITE)
                                .width(240).height(22)
                                .padding(4, 6)
                                .build()
                )
        );

        return Components.Screen(content, "TextField Demo");
    }

    /**
     * Create a screen demonstrating slider components bound to reactive state.
     * Dragging the slider updates the {@link State}; the Text above it reads
     * from a {@code map(...)} of the same state and refreshes each frame.
     */
    public static UIScreen createSliderScreen() {
        State<Integer> volume = State.of(50, "demo.slider.volume");
        State<Double> pitch = State.of(1.0, "demo.slider.pitch");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Slider Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                // Int slider with default styles (track derived from fill)
                Components.Text(
                        volume.map(v -> "Volume: " + v),
                        fontSize(12).textColor(0xFF_AA_AA_AA).margin(12, 0, 4, 0).build()
                ),
                Components.SliderInt(volume, 0, 100, 1),

                // Double slider, step 0.1, custom 3-style (explicit track color)
                Components.Text(
                        pitch.map(v -> String.format("Pitch: %.1f", v)),
                        fontSize(12).textColor(0xFF_AA_AA_AA).margin(16, 0, 4, 0).build()
                ),
                Components.SliderDouble(pitch, 0.5, 2.0, 0.1,
                        backgroundColor(0xFF_FF_88_55).width(240).height(6).build(),
                        backgroundColor(WHITE).width(10).height(20).build(),
                        backgroundColor(0xFF_33_33_33).build()
                )
        );

        return Components.Screen(content, "Slider Demo");
    }

    /**
     * Create a screen demonstrating a 3×3 paginated gallery. Items are
     * colored buttons; the {@link Components#Dynamic} container rebuilds the
     * grid whenever the page state changes, while the {@link Components#Pagination}
     * navigator writes to that same state. Single source of truth.
     */
    public static UIScreen createGalleryScreen() {
        final int cols = 3, rows = 3;
        final int pageSize = cols * rows;
        final int totalItems = 36;
        final int totalPages = (totalItems + pageSize - 1) / pageSize;
        final int[] palette = {
                0xFF_55_88_FF, 0xFF_88_DD_55, 0xFF_FF_88_55,
                0xFF_DD_55_88, 0xFF_55_DD_DD, 0xFF_DD_DD_55
        };

        State<Integer> page = State.of(1, "demo.gallery.page");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Gallery 3×3",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        page.map(p -> "Page " + p + " of " + totalPages),
                        fontSize(11).textColor(0xFF_AA_AA_AA).margin(4, 0, 12, 0).build()
                ),

                // Reactive grid: rebuilds whenever `page` changes
                Components.Dynamic(page, p -> {
                    int start = (p - 1) * pageSize;
                    int end = Math.min(totalItems, start + pageSize);

                    return Components.Column(col -> {
                        for (int r = 0; r < rows; r++) {
                            final int rowStart = start + r * cols;
                            if (rowStart >= end) break;
                            col.Row(row -> {
                                for (int c = 0; c < cols; c++) {
                                    int idx = rowStart + c;
                                    if (idx >= end) break;
                                    int color = palette[idx % palette.length];
                                    row.Button(
                                            "#" + (idx + 1),
                                            backgroundColor(color)
                                                    .textColor(WHITE)
                                                    .width(60).height(60)
                                                    .margin(3)
                                                    .build()
                                    );
                                }
                            });
                        }
                    });
                }),

                Components.Pagination(page, totalPages)
        );

        return Components.Screen(content, "Gallery Demo");
    }

    /**
     * Create a screen demonstrating progress bars driven by an external slider.
     * Both bars read the same {@link State}; the second one shows a custom
     * label format and color scheme.
     */
    public static UIScreen createProgressBarScreen() {
        State<Double> progress = State.of(0.4, "demo.progress.value");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Progress Bar Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Drag the slider to fill the bars",
                        fontSize(10).textColor(0xFF_77_77_77).margin(4, 0, 8, 0).build()
                ),

                Components.SliderDouble(progress, 0.0, 1.0, 0.0),

                // Default label "Progression XX%"
                Components.Text(
                        "Default label:",
                        fontSize(10).textColor(0xFF_AA_AA_AA).margin(16, 0, 4, 0).build()
                ),
                Components.ProgressBar(progress, 0.0, 1.0),

                // Custom label + custom styles
                Components.Text(
                        "Custom label & colors:",
                        fontSize(10).textColor(0xFF_AA_AA_AA).margin(12, 0, 4, 0).build()
                ),
                Components.ProgressBar(
                        progress, 0.0, 1.0,
                        v -> String.format("%.0f / 100 XP", v * 100),
                        backgroundColor(0xFF_22_22_22).textColor(WHITE).width(240).height(18).build(),
                        backgroundColor(0xFF_88_DD_55).build()
                )
        );

        return Components.Screen(content, "Progress Bar Demo");
    }

    /**
     * Create a screen demonstrating SelectList — a vertically stacked list
     * of selectable rows bound to a State. Two examples: one with the default
     * String renderer, one with a custom renderer drawing a colored swatch.
     */
    public static UIScreen createSelectListScreen() {
        // Simple list of strings
        java.util.List<String> difficulties = java.util.List.of("Easy", "Normal", "Hard", "Extreme");
        State<String> chosenDifficulty = State.of("Normal", "demo.selectlist.difficulty");

        // Record with an accent color, showcased via a custom renderer
        record Theme(String name, int accent) {}
        java.util.List<Theme> themes = java.util.List.of(
                new Theme("Cobalt", 0xFF_55_88_FF),
                new Theme("Forest", 0xFF_88_DD_55),
                new Theme("Sunset", 0xFF_FF_88_55),
                new Theme("Berry", 0xFF_DD_55_88)
        );
        State<Theme> chosenTheme = State.of(themes.get(0), "demo.selectlist.theme");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Select List Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        chosenDifficulty.map(d -> "Difficulty: " + d),
                        fontSize(11).textColor(0xFF_AA_AA_AA).margin(8, 0, 4, 0).build()
                ),

                // Default renderer: just the toString of each item
                Components.SelectList(chosenDifficulty, difficulties),

                Components.Text(
                        chosenTheme.map(t -> "Theme: " + t.name()),
                        fontSize(11).textColor(0xFF_AA_AA_AA).margin(16, 0, 4, 0).build()
                ),

                // Custom renderer: a small accent square next to the name
                Components.SelectList(
                        chosenTheme,
                        themes,
                        theme -> Components.Row(
                                Components.Column(
                                        backgroundColor(theme.accent())
                                                .width(8).height(8)
                                                .margin(0, 6, 0, 0)
                                                .build()
                                ),
                                Components.Text(theme.name(),
                                        fontSize(11).textColor(WHITE).build())
                        )
                )
        );

        return Components.Screen(content, "Select List Demo");
    }

    /**
     * Create a screen demonstrating both accordion modes side by side.
     * Multi-open lets several sections coexist; single-open behaves like
     * vertical tabs. Both share section bodies that read/write reactive State.
     */
    public static UIScreen createAccordionScreen() {
        State<Integer> volume = State.of(50, "demo.accordion.volume");
        State<String> name = State.of("", "demo.accordion.name");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Accordion Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Multi-open (default — first section starts open):",
                        fontSize(10).textColor(0xFF_AA_AA_AA).margin(12, 0, 6, 0).build()
                ),

                Components.Accordion(
                        Components.AccordionSection("Settings",
                                Components.Column(
                                        padding(12).backgroundColor(0xFF_22_22_22).build(),
                                        Components.Text(
                                                volume.map(v -> "Volume: " + v),
                                                fontSize(11).textColor(0xFF_DD_DD_DD).margin(0, 0, 6, 0).build()
                                        ),
                                        Components.SliderInt(volume, 0, 100, 1)
                                ),
                                true /* defaultOpen */
                        ),
                        Components.AccordionSection("Profile",
                                Components.Column(
                                        padding(12).backgroundColor(0xFF_22_22_22).build(),
                                        Components.TextField(
                                                name, "Your name…",
                                                backgroundColor(0xFF_2A_2A_2A)
                                                        .textColor(WHITE)
                                                        .width(220).height(22)
                                                        .padding(4, 6)
                                                        .build()
                                        ),
                                        Components.Text(
                                                name.map(n -> n.isEmpty() ? "(no name)" : "Hello, " + n + "!"),
                                                fontSize(11).textColor(0xFF_AA_AA_AA).margin(6, 0, 0, 0).build()
                                        )
                                )
                        ),
                        Components.AccordionSection("About",
                                Components.Column(
                                        padding(12).backgroundColor(0xFF_22_22_22).build(),
                                        Components.Text("ModdingLib — UI demo",
                                                fontSize(11).textColor(0xFF_DD_DD_DD).build()),
                                        Components.Text("MIT licensed",
                                                fontSize(10).textColor(0xFF_77_77_77).margin(4, 0, 0, 0).build())
                                )
                        )
                ),

                Components.Text(
                        "Single-open (only one section at a time):",
                        fontSize(10).textColor(0xFF_AA_AA_AA).margin(16, 0, 6, 0).build()
                ),

                Components.AccordionSingle(
                        Components.AccordionSection("Tab A",
                                Components.Column(
                                        padding(12).backgroundColor(0xFF_22_22_22).build(),
                                        Components.Text("Content of A",
                                                fontSize(11).textColor(0xFF_DD_DD_DD).build())
                                )
                        ),
                        Components.AccordionSection("Tab B",
                                Components.Column(
                                        padding(12).backgroundColor(0xFF_22_22_22).build(),
                                        Components.Text("Content of B",
                                                fontSize(11).textColor(0xFF_DD_DD_DD).build())
                                )
                        ),
                        Components.AccordionSection("Tab C",
                                Components.Column(
                                        padding(12).backgroundColor(0xFF_22_22_22).build(),
                                        Components.Text("Content of C",
                                                fontSize(11).textColor(0xFF_DD_DD_DD).build())
                                )
                        )
                )
        );

        return Components.Screen(content, "Accordion Demo");
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