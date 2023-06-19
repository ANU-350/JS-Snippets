/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;

/**
 * This class represents a "raw IR node" as read from a check attribute. It has a node part that either represents an
 * {@link IRNode} placeholder string or a user defined regex. In the former case, we could additionally have a user
 * postfix string. This raw IR node is not specific to any compile phase (i.e. the IR node placeholder is not replaced
 * by an actual regex, yet, and thus is named "raw").
 *
 * @see IRNode
 */
public class RawIRNode {
    private final String node;
    private final CheckAttributeString userPostfix;

    public RawIRNode(String node, CheckAttributeString userPostfix) {
        this.node = node;
        this.userPostfix = userPostfix;
    }

    public String irNodePlaceholder() {
        return IRNode.getIRNodeAccessString(node);
    }

    public CompilePhase defaultCompilePhase() {
        return IRNode.getDefaultPhase(node);
    }

    public String regex(CompilePhase compilePhase, VMInfo vmInfo, String vectorSizeDefault) {
        String nodeRegex = node;
        if (IRNode.isIRNode(node)) {
            nodeRegex = IRNode.getRegexForCompilePhase(node, compilePhase);
            if (IRNode.isVectorIRNode(node)) {
                String type;
                String size;
                if (userPostfix.isValid()) {
                    String value = userPostfix.value();
                    TestFormat.checkNoReport(value.startsWith(IRNode.VECTOR_TYPE), "Vector node's vector type must start with IRNode.VECTOR_TYPE, got: \"" + value + "\"");
                    String value2 = value.substring(2);
                    String[] l = value2.split(":");
                    TestFormat.checkNoReport(l.length == 2, "Vector nodei's vector type must have pattern \"_@<type>,<size>\", got: \"" + value + "\"");
                    type = l[0];
                    size = l[1];
                } else {
                    type = IRNode.getVectorNodeType(node);
                    size = vectorSizeDefault;
                }
                // Parse type
                TestFormat.checkNoReport(IRNode.getTypeSizeInBytes(type) > 0, "Vector node's type must have valid type, got \"" + type + "\" for \"" + node + "\"");
                // Parse size
                String sizes_regex = size;
                if (size.equals("any")) {
                    sizes_regex = "\\\\d+";
                } else {
                    size = IRNode.parseSizeTags(size, type, vmInfo);
                    String[] sizes = size.split(",");
                    sizes_regex = "";
                    for (int i = 0; i < sizes.length; i++) {
                        int s = 0;
                        try {
                            s = Integer.parseInt(sizes[i]);
                        } catch (NumberFormatException e) {
                            TestFormat.checkNoReport(false, "Vector node specifier has invalid (not int) size \"" + sizes[i] + "\", in \"" + size + "\" for \"" + node + "\"");
                        }
                        sizes_regex += ((i > 0) ? "|" : "") + s;
                    }
                    if (sizes.length > 1) {
                       sizes_regex += "(" + sizes_regex + ")";
                    }
                }
                nodeRegex = nodeRegex.replaceAll(IRNode.IS_REPLACED, "vector[A-Za-z]\\\\[" + sizes_regex + "\\\\]:\\\\{" + type + "\\\\}");
            } else {
                if (userPostfix.isValid()) {
                    nodeRegex = nodeRegex.replaceAll(IRNode.IS_REPLACED, userPostfix.value());
                }
            }
        }
        return nodeRegex;
    }
}
