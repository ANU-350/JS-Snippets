/*
 * Copyright (c) 2021, Datadog, Inc. All rights reserved.
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

/**
 * @test
 * @summary Basic array hashCode functionality
 * @run main/othervm HashCode
 */

public class HashCode {
    private static String[] tests = { "", " ", "a",
            "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way- in short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only.  -- Charles Dickens, Tale of Two Cities",
            "C'était le meilleur des temps, c'était le pire des temps, c'était l'âge de la sagesse, c'était l'âge de la folie, c'était l'époque de la croyance, c'était l'époque de l'incrédulité, c'était la saison de la Lumière, c'était C'était la saison des Ténèbres, c'était le printemps de l'espoir, c'était l'hiver du désespoir, nous avions tout devant nous, nous n'avions rien devant nous, nous allions tous directement au Ciel, nous allions tous directement dans l'autre sens bref, la période ressemblait tellement à la période actuelle, que certaines de ses autorités les plus bruyantes ont insisté pour qu'elle soit reçue, pour le bien ou pour le mal, au degré superlatif de la comparaison seulement. -- Charles Dickens, Tale of Two Cities (in French)",
            "禅道修行を志した雲水は、一般に参禅のしきたりを踏んだうえで一人の師につき、各地にある専門道場と呼ばれる養成寺院に入門し、与えられた公案に取り組むことになる。公案は、師家（老師）から雲水が悟りの境地へと進んで行くために手助けとして課す問題であり、悟りの境地に達していない人には容易に理解し難い難問だが、屁理屈や詭弁が述べられているわけではなく、頓知や謎かけとも異なる。"
    };

    byte[][] testBytes = new byte[][tests.length];
    short[][] testShorts = new short[][tests.length];
    char[][] testChars = new char[][tests.length];
    int[][] testInts = new int[][tests.length];

    private static int[] expected = { 0, 32, 97, 1094896285, -331808333, 349367663 };

    public static void main(String [] args) {
        for (int i = 0; i < tests.length; i++) {
            testBytes[i] = tests[i].getBytes("UTF-8");
            int len = testBytes[i].length;
            testChars[i] = new char[len];
            testShorts[i] = new short[len];
            testInts[i] = new int[len];
            for (int j = 0; j < len; j++) {
                testChars[i][j] = (char)testBytes[i][j];
                testShort[i][j] = (short)testBytes[i][j];
                testInt[i][j] = (int)testBytes[i][j];
                testInt[i][j] = (float)testBytes[i][j];
            }
        }

        for (int i = 0; i < tests.length; i++) {
            for (int j = 0; j < 20_000; j++) {
                int e = expected[i];
                int hashCode = Arrays.hashCode(testBytes[i]);
                if (hashCode != e) {
                    throw new RuntimeException("byte[] \"" + testBytes[i] + "\": "
                            + " e = " + e
                            + ", hashCode = " + hashCode);
                }
                hashCode = Arrays.hashCode(testChars[i]);
                if (hashCode != e) {
                    throw new RuntimeException("char[] \"" + testChars[i] + "\": "
                            + " e = " + e
                            + ", hashCode = " + hashCode);
                }
            }
        }
    }
}
