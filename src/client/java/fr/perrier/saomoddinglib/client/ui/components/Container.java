package fr.perrier.saomoddinglib.client.ui.components;

import net.minecraft.client.gui.DrawContext;
import fr.perrier.saomoddinglib.client.ui.styling.Style;
import fr.perrier.saomoddinglib.client.ui.styling.Size;
import fr.perrier.saomoddinglib.client.ui.layout.LayoutType;

import java.util.ArrayList;
import java.util.List;

/**
 * Container component that can hold child components.
 * Handles layout of children using LinearLayout.
 */
public class Container extends UIComponent {
    protected final List<UIComponent> children;
    protected final LayoutType layoutType;
    protected int spacing;
    
    public Container(Style style, LayoutType layoutType, int spacing) {
        super(style);
        this.children = new ArrayList<>();
        this.layoutType = layoutType;
        this.spacing = spacing;
    }
    
    public void addChild(UIComponent child) {
        this.children.add(child);
        if (isAttached()) {
            child.onAttach();
        }
    }

    public void addChildren(UIComponent... childArray) {
        for (UIComponent child : childArray) {
            this.children.add(child);
            if (isAttached()) {
                child.onAttach();
            }
        }
    }

    @Override
    public void onAttach() {
        if (isAttached()) return;
        super.onAttach();
        for (UIComponent child : children) {
            child.onAttach();
        }
    }

    @Override
    public void onDetach() {
        if (!isAttached()) return;
        for (UIComponent child : children) {
            child.onDetach();
        }
        super.onDetach();
    }
    
    @Override
    public MeasureResult measure(int maxWidth, int maxHeight) {
        int availableWidth = maxWidth - style.getPadding().getHorizontal();
        int availableHeight = maxHeight - style.getPadding().getVertical();
        
        int totalWidth = 0;
        int totalHeight = 0;
        
        if (layoutType == LayoutType.HORIZONTAL) {
            // Row: sum widths, max height
            for (int i = 0; i < children.size(); i++) {
                var result = children.get(i).measure(availableWidth - totalWidth, availableHeight);
                totalWidth += result.width;
                totalHeight = Math.max(totalHeight, result.height);
                if (i < children.size() - 1) {
                    totalWidth += spacing;
                }
            }
        } else {
            // Column: max width, sum heights
            for (int i = 0; i < children.size(); i++) {
                var result = children.get(i).measure(availableWidth, availableHeight - totalHeight);
                totalWidth = Math.max(totalWidth, result.width);
                totalHeight += result.height;
                if (i < children.size() - 1) {
                    totalHeight += spacing;
                }
            }
        }
        
        // Apply style constraints
        int resultWidth = applyConstraint(totalWidth, style.getWidth(), maxWidth);
        int resultHeight = applyConstraint(totalHeight, style.getHeight(), maxHeight);
        
        return new MeasureResult(
            resultWidth + style.getPadding().getHorizontal(),
            resultHeight + style.getPadding().getVertical()
        );
    }
    
    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        
        int contentX = x + style.getPadding().left;
        int contentY = y + style.getPadding().top;
        int contentWidth = width - style.getPadding().getHorizontal();
        int contentHeight = height - style.getPadding().getVertical();
        
        if (layoutType == LayoutType.HORIZONTAL) {
            layoutRow(contentX, contentY, contentWidth, contentHeight);
        } else {
            layoutColumn(contentX, contentY, contentWidth, contentHeight);
        }
    }
    
    private void layoutRow(int contentX, int contentY, int contentWidth, int contentHeight) {
        int currentX = contentX;
        
        for (int i = 0; i < children.size(); i++) {
            UIComponent child = children.get(i);
            var measured = child.measure(contentWidth - (currentX - contentX), contentHeight);
            
            child.layout(currentX, contentY, measured.width, measured.height);
            
            currentX += measured.width;
            if (i < children.size() - 1) {
                currentX += spacing;
            }
        }
    }
    
    private void layoutColumn(int contentX, int contentY, int contentWidth, int contentHeight) {
        int currentY = contentY;
        
        for (int i = 0; i < children.size(); i++) {
            UIComponent child = children.get(i);
            var measured = child.measure(contentWidth, contentHeight - (currentY - contentY));
            
            child.layout(contentX, currentY, measured.width, measured.height);
            
            currentY += measured.height;
            if (i < children.size() - 1) {
                currentY += spacing;
            }
        }
    }
    
    @Override
    public void render(DrawContext drawContext) {
        // Draw background
        if (style.getBackgroundColor() != 0x00_00_00_00) {
            drawContext.fill(x, y, x + width, y + height, style.getBackgroundColor());
        }
        
        // Draw border
        if (style.getBorderWidth() > 0) {
            drawBorder(drawContext);
        }
        
        // Render all children
        for (UIComponent child : children) {
            child.render(drawContext);
        }
    }
    
    protected void drawBorder(DrawContext drawContext) {
        int borderWidth = style.getBorderWidth();
        int color = style.getBorderColor();
        
        // Top border
        drawContext.fill(x, y, x + width, y + borderWidth, color);
        // Bottom border
        drawContext.fill(x, y + height - borderWidth, x + width, y + height, color);
        // Left border
        drawContext.fill(x, y, x + borderWidth, y + height, color);
        // Right border
        drawContext.fill(x + width - borderWidth, y, x + width, y + height, color);
    }
    
    @Override
    public boolean onMouseClick(double mx, double my, int button) {
        if (!isPointInside(mx, my)) {
            return false;
        }
        
        // First check if any child consumes the event
        for (UIComponent child : children) {
            if (child.onMouseClick(mx, my, button)) {
                return true;
            }
        }
        
        // Then check this component's click handler
        if (style.getClickHandler() != null) {
            style.getClickHandler().onClick(mx, my, button);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean onMouseScroll(double mx, double my, double scrollDelta) {
        if (!isPointInside(mx, my)) {
            return false;
        }
        
        // Propagate to children
        for (UIComponent child : children) {
            if (child.onMouseScroll(mx, my, scrollDelta)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void onMouseMove(double mx, double my) {
        for (UIComponent child : children) {
            child.onMouseMove(mx, my);
        }
    }
    
    /**
     * Apply style constraint to measured value
     */
    protected int applyConstraint(int measured, int constraint, int max) {
        if (Size.isMatchParent(constraint)) {
            return max;
        } else if (Size.isWrapContent(constraint)) {
            return measured;
        } else {
            // Fixed size
            return Math.min(constraint, max);
        }
    }
    
    public List<UIComponent> getChildren() {
        return children;
    }
    
    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }
}
