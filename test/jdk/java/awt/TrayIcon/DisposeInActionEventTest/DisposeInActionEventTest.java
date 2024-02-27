/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import jdk.test.lib.Platform;

/*
 * @test
 * @bug 6299866 8316931
 * @summary Tests that no NPE is thrown when the tray icon is disposed from the
 * handler of action event caused by clicking on this icon.
 * @library ../../regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual DisposeInActionEventTest
 */

public class DisposeInActionEventTest {
    private static JTextArea textArea;

    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            throw new jtreg.SkippedException("The test cannot be run because " +
                    "SystemTray is not supported.");
        }
        String clickInstruction = (Platform.isOSX()) ? "Right-click" : "Double click";

        String instructions = "When the test starts, it adds the icon to the tray area. If you\n" +
                       "  don't see a tray icon, please, make sure that the tray area\n" +
                       "  (also called Taskbar Status Area on MS Windows, Notification\n" +
                       "  Area on Gnome or System Tray on KDE) is visible.\n\n" +
                        clickInstruction + " the button on the tray icon to trigger the\n" +
                       "  action event. Brief information about action events is printed\n" +
                       "  in the frame. After each action event the tray icon is removed from\n" +
                       "  the tray and then added back in a second.\n\n" +
                       "The test performs some automatic checks when removing the icon. If\n" +
                       "  something is wrong the corresponding message is displayed.\n" +
                       "  Repeat clicks several times. If no 'Test FAILED' messages\n" +
                       "  are printed, press PASS button else FAIL button.";

        PassFailJFrame.builder()
                .title("Event Message Display")
                .instructions(instructions)
                .testTimeOut(10)
                .rows(15)
                .columns(45)
                .testUI(DisposeInActionEventTest::showFrameAndIcon)
                .build()
                .awaitAndCheck();
    }

    private static JFrame showFrameAndIcon() {
        JFrame frame = new JFrame("DisposeInActionEventTest");
        frame.setLayout(new BorderLayout());

        JScrollPane spane = new JScrollPane();
        textArea = new JTextArea();
        spane.add(textArea);
        frame.getContentPane().add(spane);
        frame.setSize(400, 200);

        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 32, 32);
        g.setColor(Color.RED);
        g.fillRect(6, 6, 20, 20);
        g.dispose();

        SystemTray systemTray = SystemTray.getSystemTray();
        TrayIcon trayIcon = new TrayIcon(img);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(ev -> {
            textArea.append(ev + "\n");
            systemTray.remove(trayIcon);
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    systemTray.add(trayIcon);
                } catch (AWTException | InterruptedException e) {
                    textArea.append(e + "\n");
                    e.printStackTrace();
                    throw new RuntimeException();
                }
            }).start();
        });

        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            textArea.append(e + "\n");
            e.printStackTrace();
            throw new RuntimeException("Test failed.");
        }

        return frame;
    }
}
