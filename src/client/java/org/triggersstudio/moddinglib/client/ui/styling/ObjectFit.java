package org.triggersstudio.moddinglib.client.ui.styling;

/**
 * How an aspect-ratio'd source (video, image) fits its component bounds.
 * Mirrors the CSS {@code object-fit} property.
 *
 * <ul>
 *   <li>{@link #STRETCH} — fill the bounds completely, distorting the aspect
 *   ratio if necessary. The previous (and only) behavior of
 *   {@code VideoComponent}.</li>
 *   <li>{@link #CONTAIN} — scale uniformly so the whole source fits inside
 *   the bounds; leaves transparent margins (letterbox / pillarbox) on the
 *   short side.</li>
 *   <li>{@link #COVER} — scale uniformly so the source fully covers the
 *   bounds; crops the overflowing side. No empty bars, but you may lose
 *   pixels.</li>
 *   <li>{@link #NONE} — draw at native pixel size, centered in the bounds.
 *   Crops on overflow.</li>
 * </ul>
 */
public enum ObjectFit {
    STRETCH,
    CONTAIN,
    COVER,
    NONE
}
