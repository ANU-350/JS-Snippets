/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

/*
 * @test
 * @bug 4223328
 * @summary Printer graphics must behave the same as screen graphics
 * @key printer
 * @library /test/lib /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintNullString
 */
public class PrintNullString extends Frame {
    private final TextCanvas c;

    private static final String INSTRUCTIONS =
            "You must have a printer available to perform this test\n" +
            "This test should print a page which contains the same\n" +
            "text messages as in the test window on the screen\n" +
            "The messages should contain only 'OK' and 'expected' messages\n" +
            "There should be no FAILURE messages.\n" +
            "You should also monitor the command line to see if any exceptions\n" +
            "were thrown\n" +
            "If the page fails to print, but there were no exceptions\n" +
            "then the problem is likely elsewhere (ie your printer)";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(PrintNullString::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }

    public PrintNullString() {
        super("JDK 1.2 drawString Printing");

        c = new TextCanvas();
        add("Center", c);

        Button printButton = new Button("Print");
        add("South", printButton);
        printButton.addActionListener(e -> {
            PrinterJob pj = PrinterJob.getPrinterJob();
            if (pj.printDialog()) {
                pj.setPrintable(c);
                try {
                    pj.print();
                } catch (PrinterException pe) {
                    PassFailJFrame.forceFail("Print Failed");
                    pe.printStackTrace();
                }
            }
        });

        pack();
    }

    static class TextCanvas extends Panel implements Printable {
        String nullStr = null;
        String emptyStr = "";
        AttributedString emptyAttStr = new AttributedString(emptyStr);
        AttributedCharacterIterator nullIterator = null;
        AttributedCharacterIterator emptyIterator = emptyAttStr.getIterator();

        public int print(Graphics g, PageFormat pgFmt, int pgIndex) {

            if (pgIndex > 0)
                return Printable.NO_SUCH_PAGE;

            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
            paint(g2d);

            return Printable.PAGE_EXISTS;
        }

        public void paint(Graphics2D g2d) {

            // API 1: null & empty drawString(String, int, int);
            try {
                g2d.drawString(nullStr, 20, 40);
                g2d.drawString("FAILURE: No NPE for null String, int", 20, 40);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null String, int", 20, 40);
            }

            g2d.drawString(emptyStr, 20, 60);
            g2d.drawString("OK for empty String, int", 20, 60);

            // API 2: null & empty drawString(String, float, float);
            try {
                g2d.drawString(nullStr, 20.0f, 80.0f);
                g2d.drawString("FAILURE: No NPE for null String, float", 20, 80);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null String, float", 20, 80);
            }

            g2d.drawString(emptyStr, 20.0f, 100.0f);
            g2d.drawString("OK for empty String, float", 20.0f, 100.f);

            // API 3: null & empty drawString(Iterator, int, int);
            try {
                g2d.drawString(nullIterator, 20, 120);
                g2d.drawString("FAILURE: No NPE for null iterator, float", 20, 120);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null iterator, int", 20, 120);
            }

            try {
                g2d.drawString(emptyIterator, 20, 140);
                g2d.drawString("FAILURE: No IAE for empty iterator, int", 20, 140);
            } catch (IllegalArgumentException e) {
                g2d.drawString("caught expected IAE for empty iterator, int", 20, 140);
            }

            // API 4: null & empty drawString(Iterator, float, int);
            try {
                g2d.drawString(nullIterator, 20.0f, 160.0f);
                g2d.drawString("FAILURE: No NPE for null iterator, float", 20, 160);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null iterator, float", 20, 160);
            }

            try {
                g2d.drawString(emptyIterator, 20, 180);
                g2d.drawString("FAILURE: No IAE for empty iterator, float", 20, 180);
            } catch (IllegalArgumentException e) {
                g2d.drawString("caught expected IAE for empty iterator, float", 20, 180);
            }
        }

        public Dimension getPreferredSize() {
            return new Dimension(450, 250);
        }
    }
}
