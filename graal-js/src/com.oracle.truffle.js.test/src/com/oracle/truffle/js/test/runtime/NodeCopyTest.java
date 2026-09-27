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

import static org.junit.Assert.assertFalse;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.junit.Test;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode;
import com.oracle.truffle.js.nodes.access.OrdinaryCreateFromConstructorNode;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.JSPromise;
import com.oracle.truffle.js.test.TestHelper;

/**
 * The uninitialized copy of a node ({@link JavaScriptNode#cloneUninitialized}) shares no node with
 * the node it copies.
 */
public class NodeCopyTest {

    /**
     * An object literal used to pass its {@code CreateObjectNode} to the copy as is.
     */
    @Test
    public void uninitializedCopySharesNoNode() {
        try (TestHelper helper = new TestHelper()) {
            assertSharesNoNode(functionBody(helper, "(function f(a) { return {a: a, b: 1}; })"));
            assertSharesNoNode(functionBody(helper, "(function f(a, p) { return {__proto__: p, a: a}; })"));
        }
    }

    /**
     * {@code OrdinaryCreateFromConstructorNode} used to pass its {@code CreateObjectNode} to the
     * copy as is.
     */
    @Test
    public void uninitializedCopyOfOrdinaryCreateFromConstructorSharesNoNode() {
        try (TestHelper helper = new TestHelper()) {
            assertSharesNoNode(OrdinaryCreateFromConstructorNode.create(helper.getJSContext(), JSConstantNode.createUndefined(), JSRealm::getPromisePrototype, JSPromise.INSTANCE));
        }
    }

    private static JavaScriptNode functionBody(TestHelper helper, String source) {
        RootNode root = helper.parseFirstFunction(source).getRootNode();
        return (JavaScriptNode) root.getChildren().iterator().next();
    }

    private static void assertSharesNoNode(JavaScriptNode node) {
        Set<Node> live = nodes(node);
        for (Node copied : nodes(JavaScriptNode.cloneUninitialized(node, null))) {
            assertFalse("shared with the copied node: " + copied.getClass().getName(), live.contains(copied));
        }
    }

    private static Set<Node> nodes(Node root) {
        Set<Node> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        addAll(root, nodes);
        return nodes;
    }

    private static boolean addAll(Node node, Set<Node> nodes) {
        nodes.add(node);
        return NodeUtil.forEachChild(node, n -> addAll(n, nodes));
    }
}
