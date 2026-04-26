package org.triggersstudio.moddinglib.client.ui.components;

/**
 * One section of an {@link AccordionComponent}: a clickable title bar paired
 * with an arbitrary {@code body} component shown when the section is open.
 *
 * <p>{@code defaultOpen} is honored only when the accordion uses internal
 * state. When a developer passes their own {@code State<Set<Integer>>} or
 * {@code State<Integer>}, that state always wins on initialization.
 */
public record AccordionSection(String title, UIComponent body, boolean defaultOpen) {

    public AccordionSection {
        if (title == null) title = "";
        if (body == null) {
            throw new IllegalArgumentException("AccordionSection body must not be null");
        }
    }
}
