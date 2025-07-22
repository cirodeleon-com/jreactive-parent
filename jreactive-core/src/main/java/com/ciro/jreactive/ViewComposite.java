package com.ciro.jreactive;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ViewComposite implements ViewNode {

    private final List<ViewNode> children = new ArrayList<>();

    public ViewComposite add(ViewNode child) {
        children.add(child);
        return this;
    }
    public List<ViewNode> children() { return children; }

    @Override
    public String render() {
        return children.stream()
                       .map(ViewNode::render)
                       .collect(Collectors.joining("\n"));
    }
}
