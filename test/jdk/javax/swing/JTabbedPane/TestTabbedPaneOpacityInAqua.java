/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8331619
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary Test JTabbedPane's contentarea and tab color
 *          for Aqua LAFs when opacity is enabled or disabled.
 * @run main/manual TestTabbedPaneOpacityInAqua
 */

public class TestTabbedPaneOpacityInAqua {
    private static JFrame frame;
    private static JTabbedPane tabPane;
    private static final String INSTRUCTIONS = """
            The background color of panel which contains the tabbed pane is green.
            The background color of the tabbed pane is red.
            The TabbedPane is not opaque initially.
            For 'Content Opaque' and 'Tabs Opaque' to have effect, tab pane opacity should
            be set to false i.e. Opaque checkbox should be unchecked.

            Check the default behaviour of the tabbed pane:
              - selected tab is gray and unselected tabs are red(filled with alpha binding).
              - the content area is opaque (it must be gray).

            Test Case 1 - Test Content pane opacity:
            To test Content pane opacity, make sure "Opaque checkbox" is UNCHECKED.

            Verify the following with 'content opaque' option:
            when checked:
              - the content area should be opaque (it must be gray).
            when unchecked:
              - the content area should be transparent (it must be green).

            Test Case 2 - Test Tabs opacity:
            To test Tabs opacity, make sure "Opaque checkbox" is UNCHECKED.

            Verify the following with 'tabs opaque' option:
            when checked:
              - the tabs are opaque (it must be red, except the selected tab which must be gray).
            when unchecked:
              - the tabs are transparent (it must be either gray or green).
              - if Content Opaque checkbox is checked then tabs are gray.
              - if Content Opaque checkbox is unchecked then tabs are green.
              
            Test Case 3 - Test Tab Pane opacity:
            To test Content pane opacity, make sure "Content Opaque checkbox" is UNCHECKED.

            Verify the following with 'opaque' option:
            when checked:
              - the content area should be opaque (it must be gray).
            when unchecked:
              - the content area should be transparent (it must be green).""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JTabbedPane Tab and Content Area Color Test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(10)
                .rows(25)
                .columns(60)
                .testUI(TestTabbedPaneOpacityInAqua::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        int NUM_TABS = 5;
        frame = new JFrame("Test JTabbedPane Opaque Color");
        JTabbedPane tabPane = new JTabbedPane();
        tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabPane.setTabPlacement(JTabbedPane.TOP);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        for (int i = 0; i < NUM_TABS; ++i) {
            tabPane.addTab("Tab " + i , new JLabel("Content Area"));
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabPane, BorderLayout.CENTER);
        panel.setBackground(Color.green);
        tabPane.setBackground(Color.red);

        JCheckBox contentOpaqueChkBox = new JCheckBox(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (((AbstractButton)e.getSource()).isSelected()) {
                    UIManager.put("TabbedPane.contentOpaque", Boolean.TRUE);
                } else {
                    UIManager.put("TabbedPane.contentOpaque", Boolean.FALSE);
                }
                tabPane.repaint();
                SwingUtilities.updateComponentTreeUI(frame);
            }
        });
        contentOpaqueChkBox.setText("Content Opaque");
        contentOpaqueChkBox.setSelected(true);
        contentOpaqueChkBox.setEnabled(true);

        JCheckBox tabOpaqueChkBox = new JCheckBox(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (((AbstractButton)e.getSource()).isSelected()) {
                    UIManager.put("TabbedPane.tabsOpaque", Boolean.TRUE);
                } else {
                    UIManager.put("TabbedPane.tabsOpaque", Boolean.FALSE);
                }
                tabPane.repaint();
                SwingUtilities.updateComponentTreeUI(frame);
            }
        });
        tabOpaqueChkBox.setText("Tabs Opaque");
        tabOpaqueChkBox.setSelected(true);
        tabOpaqueChkBox.setEnabled(true);

        JCheckBox tabPaneOpaqueChkBox = new JCheckBox(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setOpaque(((AbstractButton)e.getSource()).isSelected());
                contentOpaqueChkBox.setEnabled(!((AbstractButton)e.getSource()).isSelected());
                tabOpaqueChkBox.setEnabled(!((AbstractButton)e.getSource()).isSelected());
                tabPane.repaint();
                SwingUtilities.updateComponentTreeUI(frame);
            }
        });
        tabPaneOpaqueChkBox.setText("Opaque");

        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.add(tabPaneOpaqueChkBox);
        checkBoxPanel.add(contentOpaqueChkBox);
        checkBoxPanel.add(tabOpaqueChkBox);

        panel.add(checkBoxPanel, BorderLayout.NORTH);
        frame.add(panel);
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        return frame;
    }
}