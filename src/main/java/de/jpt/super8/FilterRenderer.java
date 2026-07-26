package de.jpt.super8;

import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

class FilterRenderer extends JCheckBox
        implements ListCellRenderer<Filter> {

    private static final long serialVersionUID = 1L;

	@Override
    public Component getListCellRendererComponent(
            JList<? extends Filter> list,
            Filter value,
            int index,
            boolean selected,
            boolean focus) {

        setText(value.getName());
        setSelected(true);

        if (selected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        setOpaque(true);
        return this;
    }
}