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
package com.oracle.truffle.js.test.wasm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PreinitializedSources;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.JSObject;

/**
 * With {@code js.webassembly}, the wasm language is initialized on the first use of the
 * WebAssembly API rather than with the realm, so a pre-initialized JavaScript context holds no wasm
 * context and can still be patched.
 */
public class WebAssemblyLazyInitializationTest {

    private static final String MEMORY_SIZE = "new WebAssembly.Memory({initial: 1}).buffer.byteLength";

    /** The wasm language exports {@code WebAssembly} to the polyglot bindings when it is created. */
    private static boolean wasmInitialized(Context context) {
        return context.getPolyglotBindings().hasMember("WebAssembly");
    }

    private static Context.Builder newContextBuilder() {
        return Context.newBuilder("js", "wasm").allowPolyglotAccess(PolyglotAccess.ALL).option(JSContextOptions.WEBASSEMBLY_NAME, "true");
    }

    @Test
    public void wasmIsInitializedOnFirstUse() {
        try (Context context = newContextBuilder().build()) {
            context.initialize("js");
            assertTrue(context.eval("js", "typeof WebAssembly.Memory === 'function'").asBoolean());
            assertFalse(wasmInitialized(context));
            assertEquals(65536, context.eval("js", MEMORY_SIZE).asInt());
            assertTrue(wasmInitialized(context));
        }
    }

    /**
     * A context pre-initialized with {@code js.webassembly} is used: the call target that
     * pre-initialization creates for a function of a pre-parsed source is there at run time.
     */
    @Test
    public void preInitializedContextIsUsed() throws Exception {
        Class<?> holder = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, WebAssemblyLazyInitializationTest.class.getClassLoader());
        Method preInitializeEngine = holder.getDeclaredMethod("preInitializeEngine");
        Method resetPreInitializedEngine = holder.getDeclaredMethod("resetPreInitializedEngine");
        preInitializeEngine.setAccessible(true);
        resetPreInitializedEngine.setAccessible(true);
        String[][] properties = {
                        {"polyglot.image-build-time.PreinitializeContexts", "js"},
                        {"polyglot." + JSContextOptions.WEBASSEMBLY_NAME, "true"},
        };
        Source source = Source.newBuilder("js", "var f = () => 42;", "preinit-wasm.js").buildLiteral();
        try {
            for (String[] property : properties) {
                System.setProperty(property[0], property[1]);
            }
            PreinitializedSources.register(source);
            preInitializeEngine.invoke(null);
            try (Context context = newContextBuilder().build()) {
                context.eval(source);
                context.enter();
                try {
                    JSRealm realm = JavaScriptLanguage.getJSRealm(context);
                    JSFunctionData f = JSFunction.getFunctionData((JSFunctionObject) JSObject.get(realm.getGlobalObject(), Strings.fromJavaString("f")));
                    Field callTarget = JSFunctionData.class.getDeclaredField("callTarget");
                    callTarget.setAccessible(true);
                    assertNotNull(callTarget.get(f));
                } finally {
                    context.leave();
                }
                assertFalse(wasmInitialized(context));
                assertEquals(65536, context.eval("js", MEMORY_SIZE).asInt());
            }
        } finally {
            for (String[] property : properties) {
                System.clearProperty(property[0]);
            }
            resetPreInitializedEngine.invoke(null);
        }
    }
}
