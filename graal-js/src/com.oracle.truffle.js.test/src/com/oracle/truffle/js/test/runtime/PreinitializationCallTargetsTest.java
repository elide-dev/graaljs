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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PreinitializedSources;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.function.JSFunctionExpressionNode;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.test.JSTest;

/**
 * Context pre-initialization creates the call targets of the functions that a source parsed during
 * pre-initialization defines, nested ones included, without running them. With
 * {@code js.lazy-translation}, it translates their bodies to do so. A context that uses the
 * pre-initialized context then finds them created.
 */
public class PreinitializationCallTargetsTest {

    private static final String SOURCE = "var outer = (x) => { const add = (a, b) => a + b; const make = () => { const nested = (c, d) => c + d; return nested; }; " +
                    "return [add, make]; };";

    private static final String OUTER = "outer";

    @Test
    public void preInitializationCreatesNestedFunctionCallTargets() throws Exception {
        assertCallTargetsCreated(preInitializeAndGetFunctions(false));
    }

    @Test
    public void preInitializationCreatesNestedFunctionCallTargetsWithLazyTranslation() throws Exception {
        assertCallTargetsCreated(preInitializeAndGetFunctions(true));
    }

    /**
     * Without pre-initialization, the call target of a function that has not been called yet is
     * not created, and with {@code js.lazy-translation} its body is not translated.
     */
    @Test
    public void callTargetsAreCreatedOnFirstCallWithoutPreInitialization() throws Exception {
        try (Context context = JSTest.newContextBuilder().build()) {
            context.eval(Source.create("js", SOURCE));
            context.enter();
            try {
                JSFunctionData outer = getOuterFunctionData(context);
                assertNull(callTarget(outer));
                assertNotNull(outer.getRootNode());
            } finally {
                context.leave();
            }
        }
        try (Context context = JSTest.newContextBuilder().option(JSContextOptions.LAZY_TRANSLATION_NAME, "true").build()) {
            context.eval(Source.create("js", SOURCE));
            context.enter();
            try {
                JSFunctionData outer = getOuterFunctionData(context);
                assertNull(callTarget(outer));
                assertTrue(outer.hasLazyInit());
                assertNull(outer.getRootNode());
            } finally {
                context.leave();
            }
        }
    }

    private static void assertCallTargetsCreated(Map<String, JSFunctionData> functions) throws Exception {
        assertEquals(functions.toString(), 4, functions.size());
        for (Map.Entry<String, JSFunctionData> function : functions.entrySet()) {
            assertNotNull(function.getKey(), function.getValue().getRootNode());
            assertNotNull(function.getKey(), callTarget(function.getValue()));
        }
    }

    /**
     * Pre-initializes a context that parses {@link #SOURCE} without running it, then runs it in a
     * context that uses the pre-initialized context, and returns the data of the functions it
     * defines, by name, as they are before any of them is called.
     */
    private static Map<String, JSFunctionData> preInitializeAndGetFunctions(boolean lazyTranslation) throws Exception {
        Class<?> holder = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, PreinitializationCallTargetsTest.class.getClassLoader());
        Method preInitializeEngine = holder.getDeclaredMethod("preInitializeEngine");
        Method resetPreInitializedEngine = holder.getDeclaredMethod("resetPreInitializedEngine");
        preInitializeEngine.setAccessible(true);
        resetPreInitializedEngine.setAccessible(true);
        String[][] properties = {
                        {"polyglot.image-build-time.PreinitializeContexts", "js"},
                        {"polyglot." + JSContextOptions.LAZY_TRANSLATION_NAME, String.valueOf(lazyTranslation)},
        };
        Source source = Source.newBuilder("js", SOURCE, "preinit.js").buildLiteral();
        try {
            for (String[] property : properties) {
                System.setProperty(property[0], property[1]);
            }
            PreinitializedSources.register(source);
            preInitializeEngine.invoke(null);
            try (Context context = Context.create("js")) {
                context.eval(source);
                context.enter();
                try {
                    Map<String, JSFunctionData> functions = new HashMap<>();
                    JSFunctionData outer = getOuterFunctionData(context);
                    functions.put(OUTER, outer);
                    collectFunctions(outer.getRootNode(), functions);
                    return functions;
                } finally {
                    context.leave();
                }
            }
        } finally {
            for (String[] property : properties) {
                System.clearProperty(property[0]);
            }
            resetPreInitializedEngine.invoke(null);
        }
    }

    private static JSFunctionData getOuterFunctionData(Context context) {
        JSRealm realm = JavaScriptLanguage.getJSRealm(context);
        return JSFunction.getFunctionData((JSFunctionObject) JSObject.get(realm.getGlobalObject(), Strings.fromJavaString(OUTER)));
    }

    private static void collectFunctions(RootNode root, Map<String, JSFunctionData> functions) {
        if (root == null) {
            return;
        }
        root.accept(node -> {
            if (node instanceof JSFunctionExpressionNode function) {
                JSFunctionData functionData = function.getFunctionData();
                if (functions.putIfAbsent(Strings.toJavaString(functionData.getName()), functionData) == null) {
                    collectFunctions(functionData.getRootNode(), functions);
                }
            }
            return true;
        });
    }

    /**
     * The call target of {@code functionData}, or {@code null} if it has not been created yet:
     * {@link JSFunctionData#getCallTarget()} would create it.
     */
    private static Object callTarget(JSFunctionData functionData) throws ReflectiveOperationException {
        Field field = JSFunctionData.class.getDeclaredField("callTarget");
        field.setAccessible(true);
        return field.get(functionData);
    }
}
