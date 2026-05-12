package org.triggersstudio.moddinglib.client.ui;

/**
 * Modding UI Library - A simple and intuitive framework for creating Minecraft mod menus.
 *
 * ## Features
 * - Composition-based component system (similar to Jetpack Compose / React)
 * - Fluent Style API for intuitive styling
 * - Built-in components: Text, Button, Image, Container (Row/Column), ScrollView
 * - Automatic layout system (measure → layout → render phases)
 * - Easy event handling (clicks, scrolling, hover)
 * - Seamless Minecraft integration (GuiScreen adapter)
 *
 * ## Quick Start
 *
 * ### Basic Usage
 *
 * ```java
 * import static org.triggersstudio.moddinglib.client.ui.api.Components.*;
 * import static org.triggersstudio.moddinglib.client.ui.styling.Styles.*;
 *
 * // Create a simple screen
 * UIComponent screen = Column(
 *     padding(20).backgroundColor(0xFF_1A_1A_1A).build(),
 *
 *     Text("Hello World", fontSize(24).color(WHITE).build()),
 *
 *     Button("Click Me",
 *         backgroundColor(0xFF_00_AA_FF)
 *             .height(50)
 *             .width(MATCH_PARENT)
 *             .onClick((x, y, btn) -> { System.out.println("Clicked!"); })
 *             .build()
 *     )
 * );
 *
 * // Display it
 * MinecraftClient.getInstance().setScreen(Screen(screen));
 * ```
 *
 * ## Core Concepts
 *
 * ### 1. Components
 * All UI elements are UIComponent instances:
 * - **Text** - Display text
 * - **Button** - Clickable button with text
 * - **Image** - Display textures
 * - **Container** - Layout parent (use Row/Column)
 * - **Row** - Horizontal layout container
 * - **Column** - Vertical layout container
 * - **ScrollView** - Scrollable content area
 *
 * ### 2. Style System
 * Apply styles using the fluent builder pattern:
 * ```java
 * Style style = Style.builder()
 *     .width(300)
 *     .height(50)
 *     .padding(16)
 *     .backgroundColor(0xFF_1A_1A_1A)
 *     .textColor(0xFF_FFFFFF)
 *     .fontSize(16)
 *     .onClick(this::handleClick)
 *     .build();
 * ```
 *
 * Or use static shortcuts with import static:
 * ```java
 * import static org.triggersstudio.moddinglib.client.ui.styling.Styles.*;
 *
 * Style style = padding(16)
 *     .backgroundColor(0xFF_1A_1A_1A)
 *     .fontSize(24)
 *     .build();
 * ```
 *
 * ### 3. Layout System
 * The library uses a 3-phase layout system:
 * 1. **Measure** - Determine component sizes
 * 2. **Layout** - Position components
 * 3. **Render** - Draw components
 *
 * Size constants:
 * - `WRAP_CONTENT` - Take only needed space
 * - `MATCH_PARENT` - Fill available space
 * - Explicit pixels (e.g., 200)
 *
 * ### 4. Composition
 * Build complex UIs by composing simple components:
 * ```java
 * UIComponent complexScreen = Column(
 *     padding(20).build(),
 *
 *     Text("Main Title"),
 *
 *     Row(
 *         spacing(8).build(),
 *         Button("Button 1"),
 *         Button("Button 2")
 *     ),
 *
 *     ScrollView(
 *         backgroundColor(0xFF_2A_2A_2A).build(),
 *
 *         Column(
 *             // Many items...
 *         )
 *     )
 * );
 * ```
 *
 * ## Style Reference
 *
 * ### Size
 * - `width(int)` - Set component width
 * - `height(int)` - Set component height
 *
 * ### Spacing
 * - `padding(int)` - Internal spacing (all sides)
 * - `padding(int vertical, int horizontal)` - Vertical and horizontal padding
 * - `padding(int top, int right, int bottom, int left)` - Individual sides
 * - `margin(...)` - External spacing (same overloads as padding)
 *
 * ### Colors
 * - `backgroundColor(int color)` - Background color (ARGB format)
 * - `textColor(int color)` - Text color
 * - `border(int color, int width)` - Border styling
 * - `opacity(float)` - Transparency (0.0 to 1.0)
 *
 * ### Text
 * - `fontSize(float)` - Text size
 * - `bold()` - Bold text
 *
 * ### Layout
 * - `align(Alignment horizontal, Alignment vertical)` - Alignment
 *
 * ### Events
 * - `onClick(ClickHandler handler)` - Click event handler
 *
 * ## Common Color Values
 *
 * ```java
 * import static org.triggersstudio.moddinglib.client.ui.styling.Styles.*;
 *
 * BLACK       // 0xFF_00_00_00
 * WHITE       // 0xFF_FF_FF_FF
 * RED         // 0xFF_FF_00_00
 * GREEN       // 0xFF_00_FF_00
 * BLUE        // 0xFF_00_00_FF
 * TRANSPARENT // 0x00_00_00_00
 * ```
 *
 * ## Examples
 *
 * See `ExampleScreens` class for complete working examples:
 * - `createDemoScreen()` - Simple demo with buttons
 * - `createScrollableListScreen()` - List with scroll
 *
 * ## Component API
 *
 * Each component can be created using static import:
 * ```java
 * import static org.triggersstudio.moddinglib.client.ui.api.Components.*;
 * ```
 *
 * Functions:
 * - `Text(String content)` - Simple text
 * - `Text(String content, Style style)` - Text with style
 * - `Button(String label)` - Simple button
 * - `Button(String label, Style style)` - Button with style
 * - `Image(Identifier texture)` - Image/texture
 * - `Image(Identifier texture, Style style)` - Image with style
 * - `Row(UIComponent... children)` - Horizontal layout
 * - `Row(Style style, UIComponent... children)` - Row with style
 * - `Column(UIComponent... children)` - Vertical layout
 * - `Column(Style style, UIComponent... children)` - Column with style
 * - `ScrollView(UIComponent content)` - Scrollable container
 * - `ScrollView(Style style, UIComponent content)` - Scroll with style
 * - `Screen(UIComponent component)` - Create drawable screen
 * - `Screen(UIComponent component, String title)` - Screen with title
 *
 * ## Package Structure
 *
 * ```
 * org.triggersstudio.moddinglib.client.ui
 * ├── api/                 → Component creation functions
 * ├── components/          → Component implementations
 * ├── events/              → Event handling
 * ├── layout/              → Layout system
 * ├── rendering/           → Rendering engine
 * ├── screen/              → Minecraft Screen integration
 * └── styling/             → Style and color system
 * ```
 *
 * ## Future Roadmap
 *
 * Phase 2:
 * - TextField for text input
 * - Checkbox, RadioButton, Toggle
 * - Slider, ProgressBar
 * - FlexLayout (CSS-like flex model)
 * - Lazy containers with view recycling
 *
 * Phase 3:
 * - Animation framework
 * - Reactive state management
 * - Gesture recognition
 * - Accessibility support
 *
 * ## License
 *
 * See LICENSE.txt in the project root.
 */
public class UILibraryGuide {
}

