package org.example;

import java.awt.*;
import java.util.List;

public class CustomFocusTraversalPolicy extends FocusTraversalPolicy {
    private final List<Component> components;

    public CustomFocusTraversalPolicy(List<Component> components) {
        this.components = components;
    }

    @Override
    public Component getComponentAfter(Container container, Component component) {
        int index = (components.indexOf(component) + 1) % components.size();
        return components.get(index);
    }

    @Override
    public Component getComponentBefore(Container container, Component component) {
        int index = (components.indexOf(component) - 1 + components.size()) % components.size();
        return components.get(index);
    }

    @Override
    public Component getFirstComponent(Container container) {
        return components.get(0);
    }

    @Override
    public Component getLastComponent(Container container) {
        return components.get(components.size() - 1);
    }

    @Override
    public Component getDefaultComponent(Container container) {
        return components.get(0);
    }
}
