/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.test.runtime;

import static org.junit.Assert.assertEquals;

import java.util.function.BiFunction;

import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode;
import com.oracle.truffle.js.nodes.binary.JSBitwiseAndConstantNode;
import com.oracle.truffle.js.nodes.binary.JSBitwiseOrConstantNode;
import com.oracle.truffle.js.nodes.binary.JSBitwiseXorConstantNode;
import com.oracle.truffle.js.runtime.BigInt;

/**
 * Bitwise operators with a BigInt constant right operand ({@link JSBitwiseOrConstantNode} and its
 * {@code &} and {@code ^} counterparts) combine the left operand with the constant.
 */
public class BitwiseConstantBigIntTest {

    @Test
    public void bitwiseOr() {
        assertEquals(BigInt.valueOf(7), execute(JSBitwiseOrConstantNode::create, 3, 5));
        assertEquals(BigInt.valueOf(-1), execute(JSBitwiseOrConstantNode::create, -2, 5));
    }

    @Test
    public void bitwiseAnd() {
        assertEquals(BigInt.valueOf(1), execute(JSBitwiseAndConstantNode::create, 3, 5));
        assertEquals(BigInt.valueOf(4), execute(JSBitwiseAndConstantNode::create, -2, 5));
    }

    @Test
    public void bitwiseXor() {
        assertEquals(BigInt.valueOf(6), execute(JSBitwiseXorConstantNode::create, 3, 5));
        assertEquals(BigInt.valueOf(-5), execute(JSBitwiseXorConstantNode::create, -2, 5));
    }

    /**
     * Executes {@code left <op> right}, both BigInts, with the node that {@code factory} creates for
     * a constant right operand.
     */
    private static Object execute(BiFunction<JavaScriptNode, Object, JavaScriptNode> factory, long left, long right) {
        JavaScriptNode node = factory.apply(JSConstantNode.create(BigInt.valueOf(left)), BigInt.valueOf(right));
        return new RootNode(null) {
            @Child private JavaScriptNode child = node;

            @Override
            public Object execute(VirtualFrame frame) {
                return child.execute(frame);
            }
        }.getCallTarget().call();
    }
}
