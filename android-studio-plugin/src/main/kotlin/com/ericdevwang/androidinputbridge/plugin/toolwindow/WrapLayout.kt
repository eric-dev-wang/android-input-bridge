package com.ericdevwang.androidinputbridge.plugin.toolwindow

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout

/**
 * A FlowLayout that reports the height required by all wrapped rows.
 *
 * Based on the public-domain WrapLayout pattern by Rob Camick (Tips4Java).
 */
internal class WrapLayout(
    alignment: Int = LEFT,
    horizontalGap: Int = 5,
    verticalGap: Int = 5,
) : FlowLayout(alignment, horizontalGap, verticalGap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension {
        val minimum = layoutSize(target, preferred = false)
        minimum.width -= getHgap() + 1
        return minimum
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            var targetWidth = target.width
            var container = target
            while (targetWidth == 0 && container.parent != null) {
                container = container.parent
                targetWidth = container.width
            }
            if (targetWidth == 0) targetWidth = Int.MAX_VALUE

            val insets = target.insets
            val horizontalInsets = insets.left + insets.right + getHgap() * 2
            val maximumRowWidth = targetWidth - horizontalInsets
            var rowWidth = 0
            var rowHeight = 0
            val size = Dimension(0, 0)

            target.components.filter { it.isVisible }.forEach { component ->
                val componentSize = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth > 0 && rowWidth + getHgap() + componentSize.width > maximumRowWidth) {
                    addRow(size, rowWidth, rowHeight)
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth > 0) rowWidth += getHgap()
                rowWidth += componentSize.width
                rowHeight = maxOf(rowHeight, componentSize.height)
            }
            addRow(size, rowWidth, rowHeight)

            size.width += horizontalInsets
            size.height += insets.top + insets.bottom + getVgap() * 2
            return size
        }
    }

    private fun addRow(size: Dimension, rowWidth: Int, rowHeight: Int) {
        size.width = maxOf(size.width, rowWidth)
        if (size.height > 0) size.height += getVgap()
        size.height += rowHeight
    }
}
