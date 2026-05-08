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
        State<String> lastSubmit = State.of("(none yet)", "demo.textfield.lastSubmit");

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
                        "Search... (Enter to submit)",
                        backgroundColor(0xFF_2A_2A_2A)
                                .textColor(WHITE)
                                .width(240).height(22)
                                .padding(4, 6)
                                .build(),
                        submitted -> lastSubmit.set(submitted.isEmpty() ? "(empty)" : submitted)
                ),

                Components.Text(
                        lastSubmit.map(s -> "Last submitted: " + s),
                        fontSize(10).textColor(0xFF_88_DD_88).margin(8, 0, 0, 0).build()
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
     * Create a screen demonstrating the animation system:
     * <ul>
     *   <li>Auto fade-in of the title on mount.</li>
     *   <li>Replay-on-click via {@link org.triggersstudio.moddinglib.client.ui.components.DynamicComponent},
     *       which rebuilds the FadeIn wrapper and restarts its tween.</li>
     *   <li>Slide-in from the left.</li>
     *   <li>Combined opacity + translate via the {@code Animated} builder
     *       with a custom easing.</li>
     * </ul>
     */
    public static UIScreen createAnimationScreen() {
        State<Integer> replayTrigger = State.of(0, "demo.animation.replay");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Animation Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Click 'Replay' to restart every animation below",
                        fontSize(10).textColor(0xFF_77_77_77).margin(8, 0, 12, 0).build()
                ),

                Components.Button(
                        "Replay",
                        backgroundColor(0xFF_2A_2A_2A)
                                .textColor(WHITE)
                                .height(22).width(80)
                                .margin(0, 0, 12, 0)
                                .onClick((mx, my, btn) -> replayTrigger.set(replayTrigger.get() + 1))
                                .build()
                ),

                // Everything animated lives inside a Dynamic bound to replayTrigger.
                // Each click rebuilds this whole subtree, recreating fresh tweens.
                Components.Dynamic(replayTrigger, t -> Components.Column(
                        // Title fade
                        Components.FadeIn(
                                Components.Text(
                                        "Fade-in (replay #" + t + ", 500ms)",
                                        fontSize(14).textColor(WHITE).bold().build()
                                ),
                                500
                        ),

                        // Slide-in from the left
                        Components.SlideIn(
                                Components.Text(
                                        "← Slide-in from the left (600ms)",
                                        fontSize(11).textColor(0xFF_DD_DD_DD).margin(12, 0, 4, 0).build()
                                ),
                                org.triggersstudio.moddinglib.client.ui.animation.Direction.LEFT,
                                600
                        ),

                        // Combined fade + bouncing translate
                        Components.Animated(
                                        Components.Text(
                                                "Bouncing entrance (back-out + fade, 700ms)",
                                                fontSize(11).textColor(0xFF_DD_DD_DD).margin(8, 0, 4, 0).build()
                                        ))
                                .opacity(org.triggersstudio.moddinglib.client.ui.animation.Tween
                                        .over(0.0, 1.0, 700,
                                                org.triggersstudio.moddinglib.client.ui.animation.Easing.OUT_CUBIC)
                                        .play())
                                .translateY(org.triggersstudio.moddinglib.client.ui.animation.Tween
                                        .over(20.0, 0.0, 700,
                                                org.triggersstudio.moddinglib.client.ui.animation.Easing.OUT_BACK)
                                        .play())
                                .build()
                ))
        );

        return Components.Screen(content, "Animation Demo");
    }

    /**
     * Create a screen demonstrating the Calendar component bound to a
     * {@code State<LocalDate>}. Click a day to select it; use the arrows to
     * pan months. A reactive Text shows the chosen date.
     */
    public static UIScreen createCalendarScreen() {
        State<java.time.LocalDate> chosenDate = State.of(java.time.LocalDate.now(), "demo.calendar.date");

        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy", java.util.Locale.ENGLISH);

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Calendar Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        chosenDate.map(d -> "Selected: " + fmt.format(d)),
                        fontSize(11).textColor(0xFF_AA_AA_AA).margin(8, 0, 12, 0).build()
                ),

                Components.Calendar(chosenDate)
        );

        return Components.Screen(content, "Calendar Demo");
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

    /**
     * ColorPicker demo: a picker bound to a {@code State<Integer>} ARGB. A
     * preview rectangle below the picker reads the same state and updates
     * live.
     */
    public static UIScreen createColorPickerScreen() {
        State<Integer> color = State.of(0xFF_55_88_FF, "demo.colorpicker.value");

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "ColorPicker Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Drag the SV pad, hue strip, and alpha strip.",
                        fontSize(10).textColor(0xFF_77_77_77).margin(8, 0, 12, 0).build()
                ),

                Components.ColorPicker(color),

                Components.Text(
                        color.map(c -> String.format("Selected: #%08X  (alpha=%d, rgb=#%06X)",
                                c, (c >>> 24) & 0xFF, c & 0x00_FF_FF_FF)),
                        fontSize(10).textColor(0xFF_AA_AA_AA).margin(12, 0, 4, 0).build()
                )
        );

        return Components.Screen(content, "ColorPicker Demo");
    }

    /**
     * Skeleton demo: loading placeholders simulating a card layout. Each
     * skeleton has a shimmer sweep with the same period, so all bars travel
     * in lock-step — a cheap way to make a loading state feel coherent.
     */
    public static UIScreen createSkeletonScreen() {
        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Skeleton Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Card placeholder — title, paragraph, action row",
                        fontSize(10).textColor(0xFF_77_77_77).margin(8, 0, 12, 0).build()
                ),

                Components.Column(
                        padding(12).backgroundColor(0xFF_22_22_22).width(300).build(),

                        // Avatar + name row
                        Components.Row(row -> {
                            row.Skeleton(40, 40);
                            row.add(Components.Column(col -> {
                                col.add(Components.Text(" ",
                                        fontSize(8).textColor(0xFF_22_22_22).build()));
                                col.Skeleton(140, 12);
                                col.add(Components.Text(" ",
                                        fontSize(8).textColor(0xFF_22_22_22).build()));
                                col.Skeleton(80, 10);
                            }));
                        }),

                        Components.Text(" ", fontSize(8).build()),

                        // Paragraph lines
                        Components.Skeleton(MATCH_PARENT, 10),
                        Components.Text(" ", fontSize(6).build()),
                        Components.Skeleton(MATCH_PARENT, 10),
                        Components.Text(" ", fontSize(6).build()),
                        Components.Skeleton(MATCH_PARENT, 10),
                        Components.Text(" ", fontSize(6).build()),
                        Components.Skeleton(180, 10),

                        Components.Text(" ", fontSize(10).build()),

                        // Action buttons
                        Components.Row(row -> {
                            row.Skeleton(70, 22);
                            row.add(Components.Text(" ", fontSize(8).build()));
                            row.Skeleton(70, 22);
                        })
                )
        );

        return Components.Screen(content, "Skeleton Demo");
    }

    /**
     * ComboBox demo: two drop-downs bound to {@link State} — one over a list
     * of strings, one over an enum with a custom labeler. A reactive Text
     * shows the live selection. Clicking outside the popover dismisses it.
     */
    public static UIScreen createComboBoxScreen() {
        java.util.List<String> fruits = java.util.List.of(
                "Apple", "Banana", "Cherry", "Dragonfruit", "Elderberry", "Fig", "Grape");
        State<String> fruit = State.of("Banana", "demo.combo.fruit");

        State<java.time.DayOfWeek> day = State.of(java.time.DayOfWeek.MONDAY, "demo.combo.day");
        java.util.List<java.time.DayOfWeek> days = java.util.List.of(java.time.DayOfWeek.values());

        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "ComboBox Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Click a combo box, pick an item. Click outside to dismiss.",
                        fontSize(10).textColor(0xFF_77_77_77).margin(8, 0, 12, 0).build()
                ),

                Components.Text(
                        "Fruit:",
                        fontSize(11).textColor(0xFF_AA_AA_AA).margin(6, 0, 4, 0).build()
                ),
                Components.ComboBox(fruit, fruits),

                Components.Text(
                        fruit.map(f -> "You picked: " + f),
                        fontSize(10).textColor(0xFF_88_DD_88).margin(6, 0, 12, 0).build()
                ),

                Components.Text(
                        "Day of week (custom labeler — Title Case):",
                        fontSize(11).textColor(0xFF_AA_AA_AA).margin(6, 0, 4, 0).build()
                ),
                Components.ComboBox(day, days,
                        d -> d.name().charAt(0) + d.name().substring(1).toLowerCase()),

                Components.Text(
                        day.map(d -> "Selected: " + d.name()),
                        fontSize(10).textColor(0xFF_88_DD_88).margin(6, 0, 0, 0).build()
                )
        );

        return Components.Screen(content, "ComboBox Demo");
    }

    /**
     * Spinner demo: shows a few spinners with different sizes, colors, and
     * rotation speeds. Demonstrates the indeterminate loading indicator built
     * on local nanoTime tracking — no global ticker, no input bindings.
     */
    public static UIScreen createSpinnerScreen() {
        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Spinner Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Default 24px / 800ms",
                        fontSize(10).textColor(0xFF_77_77_77).margin(12, 0, 4, 0).build()
                ),
                Components.Spinner(),

                Components.Text(
                        "48px, blue, slow (1500ms)",
                        fontSize(10).textColor(0xFF_77_77_77).margin(12, 0, 4, 0).build()
                ),
                Components.Spinner(48, 0xFF_55_AA_FF, 1500L, Style.DEFAULT),

                Components.Text(
                        "16px, green, fast (400ms)",
                        fontSize(10).textColor(0xFF_77_77_77).margin(12, 0, 4, 0).build()
                ),
                Components.Spinner(16, 0xFF_55_DD_88, 400L, Style.DEFAULT)
        );

        return Components.Screen(content, "Spinner Demo");
    }

    /**
     * Tooltip demo: hover any of the labeled buttons to see a popup appear
     * after a short delay. Demonstrates default styling, custom styling, and
     * multi-line content.
     */
    public static UIScreen createTooltipScreen() {
        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Tooltip Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Hover any button below for ~500ms",
                        fontSize(10).textColor(0xFF_77_77_77).margin(8, 0, 16, 0).build()
                ),

                Components.Tooltip(
                        "Default tooltip styling",
                        Components.Button(
                                "Hover me",
                                backgroundColor(0xFF_2A_2A_2A).textColor(WHITE)
                                        .height(24).width(140).margin(0, 0, 8, 0).build()
                        )
                ),

                Components.Tooltip(
                        "Custom popup style with\nbrighter background",
                        Components.Button(
                                "Custom style",
                                backgroundColor(0xFF_2A_2A_2A).textColor(WHITE)
                                        .height(24).width(140).margin(0, 0, 8, 0).build()
                        ),
                        Style.backgroundColor(0xF5_30_20_50)
                                .textColor(0xFF_FF_E0_FF)
                                .padding(6, 8)
                                .border(0xFF_AA_55_FF, 1)
                                .build()
                ),

                Components.Tooltip(
                        "Fast tooltip — appears after 100ms only",
                        Components.Button(
                                "Fast (100ms)",
                                backgroundColor(0xFF_2A_2A_2A).textColor(WHITE)
                                        .height(24).width(140).margin(0, 0, 8, 0).build()
                        ),
                        null,
                        100L
                ),

                Components.Tooltip(
                        "Line one\nLine two\nLine three",
                        Components.Button(
                                "Multi-line",
                                backgroundColor(0xFF_2A_2A_2A).textColor(WHITE)
                                        .height(24).width(140).build()
                        )
                )
        );

        return Components.Screen(content, "Tooltip Demo");
    }

    /**
     * Toast demo: clicking a button enqueues a toast of the corresponding type.
     * Toasts appear top-right, slide in, persist for ~3.5s, then slide out and fade.
     */
    public static UIScreen createToastScreen() {
        UIComponent content = Components.Column(
                padding(20).backgroundColor(0xFF_1A_1A_1A).build(),

                Components.Text(
                        "Toast Demo",
                        fontSize(20).textColor(WHITE).bold().build()
                ),

                Components.Text(
                        "Click a button to spawn a toast (top-right). Active count is shown live.",
                        fontSize(10).textColor(0xFF_77_77_77).margin(8, 0, 12, 0).build()
                ),

                Components.Button(
                        "Info",
                        backgroundColor(0xFF_4A_8C_C8).textColor(WHITE)
                                .height(28).width(160).margin(0, 0, 6, 0)
                                .onClick((mx, my, btn) ->
                                        Components.Toast.info("Server connected"))
                                .build()
                ),

                Components.Button(
                        "Success",
                        backgroundColor(0xFF_4A_C8_7A).textColor(WHITE)
                                .height(28).width(160).margin(0, 0, 6, 0)
                                .onClick((mx, my, btn) ->
                                        Components.Toast.success("Profile saved"))
                                .build()
                ),

                Components.Button(
                        "Warning",
                        backgroundColor(0xFF_E0_A0_3A).textColor(WHITE)
                                .height(28).width(160).margin(0, 0, 6, 0)
                                .onClick((mx, my, btn) ->
                                        Components.Toast.warning("Disk space low"))
                                .build()
                ),

                Components.Button(
                        "Error",
                        backgroundColor(0xFF_C8_4A_4A).textColor(WHITE)
                                .height(28).width(160).margin(0, 0, 12, 0)
                                .onClick((mx, my, btn) ->
                                        Components.Toast.error("Connection lost"))
                                .build()
                ),

                Components.Button(
                        "Custom (long, 8s)",
                        backgroundColor(0xFF_2A_2A_2A).textColor(WHITE)
                                .height(28).width(160).margin(0, 0, 6, 0)
                                .onClick((mx, my, btn) -> Components.Toast.show(
                                        "This one stays visible 8 seconds",
                                        org.triggersstudio.moddinglib.client.ui.toast.ToastType.INFO,
                                        8000L))
                                .build()
                ),

                Components.Text(
                        () -> "Active toasts: " +
                                org.triggersstudio.moddinglib.client.ui.toast.ToastManager.activeCount(),
                        fontSize(10).textColor(0xFF_77_77_77).margin(12, 0, 0, 0).build()
                )
        );

        return Components.Screen(content, "Toast Demo");
    }
}