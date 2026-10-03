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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.runtime.JSRealm;

/**
 * With {@link JSContextOptions#PREINIT_OPTIONAL_GLOBALS}, context pre-initialization defines the
 * optional globals, and patching only changes the ones whose options differ. The globals must be
 * the same as without pre-initialization.
 */
public class PreinitializationOptionalGlobalsTest {

    private static final String GLOBALS = "[typeof global === 'object' && global === globalThis, typeof print, typeof printErr, typeof console, typeof performance, " +
                    "typeof require, typeof __dirname, typeof module].join()";

    @Test
    public void sameOptionsKeepPreinitializedGlobals() throws Exception {
        Map<String, String> options = options("true", "true", "true", "true");
        assertEquals("true,function,function,object,object,function,string,object", preinitializeAndEval(options, options, GLOBALS));
    }

    @Test
    public void optionsDisabledAtRuntimeRemoveGlobals() throws Exception {
        assertEquals("false,undefined,undefined,undefined,undefined,function,string,object",
                        preinitializeAndEval(options("true", "true", "true", "true"), options("false", "false", "false", "false"), GLOBALS));
    }

    @Test
    public void optionsEnabledAtRuntimeAddGlobals() throws Exception {
        assertEquals("true,function,function,object,object,function,string,object",
                        preinitializeAndEval(options("false", "false", "false", "false"), options("true", "true", "true", "true"), GLOBALS));
    }

    /**
     * The global object has the same properties as when the optional globals are added while
     * patching, for matching and for differing options.
     */
    @Test
    public void globalsMatchPreinitializationWithoutOption() throws Exception {
        String code = GLOBALS + " + ';' + Object.getOwnPropertyNames(globalThis).sort().join()";
        Map<String, String> on = options("true", "true", "true", "true");
        Map<String, String> off = options("false", "false", "false", "false");
        assertEquals(preinitializeAndEval(on, on, code, false), preinitializeAndEval(on, on, code, true));
        assertEquals(preinitializeAndEval(on, off, code, false), preinitializeAndEval(on, off, code, true));
        assertEquals(preinitializeAndEval(off, on, code, false), preinitializeAndEval(off, on, code, true));
    }

    /** The CommonJS root folder is checked against the run-time file system. */
    @Test
    public void commonJSRequireCwdIsValidatedAtRuntime() throws Exception {
        Map<String, String> runtime = options("true", "true", "true", "true");
        runtime.put(JSContextOptions.COMMONJS_REQUIRE_CWD_NAME, "/nonexistent/preinit-optional-globals-test");
        try {
            preinitializeAndEval(options("true", "true", "true", "true"), runtime, GLOBALS);
            fail("expected an invalid CommonJS root folder to fail");
        } catch (PolyglotException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid CommonJS root folder"));
        }
    }

    private static Map<String, String> options(String print, String globalProperty, String console, String performance) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(JSContextOptions.COMMONJS_REQUIRE_NAME, "true");
        options.put(JSContextOptions.PRINT_NAME, print);
        options.put(JSContextOptions.GLOBAL_PROPERTY_NAME, globalProperty);
        options.put(JSContextOptions.CONSOLE_NAME, console);
        options.put(JSContextOptions.PERFORMANCE_NAME, performance);
        return options;
    }

    private static Context.Builder newContextBuilder(Map<String, String> options) {
        return Context.newBuilder("js").allowExperimentalOptions(true).allowIO(org.graalvm.polyglot.io.IOAccess.ALL).options(options);
    }

    /**
     * Pre-initializes a context with {@code preinitOptions} and, if requested,
     * {@link JSContextOptions#PREINIT_OPTIONAL_GLOBALS}, then evaluates {@code code} in a context
     * with {@code runtimeOptions}, which must use the pre-initialized context.
     */
    private static String preinitializeAndEval(Map<String, String> preinitOptions, Map<String, String> runtimeOptions, String code) throws Exception {
        return preinitializeAndEval(preinitOptions, runtimeOptions, code, true);
    }

    private static String preinitializeAndEval(Map<String, String> preinitOptions, Map<String, String> runtimeOptions, String code, boolean preinitOptionalGlobals) throws Exception {
        Class<?> holder = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, PreinitializationOptionalGlobalsTest.class.getClassLoader());
        Method preInitializeEngine = holder.getDeclaredMethod("preInitializeEngine");
        Method resetPreInitializedEngine = holder.getDeclaredMethod("resetPreInitializedEngine");
        preInitializeEngine.setAccessible(true);
        resetPreInitializedEngine.setAccessible(true);
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("polyglot.image-build-time.PreinitializeContexts", "js");
        properties.put("polyglot.image-build-time.PreinitializeAllowExperimentalOptions", "true");
        properties.put("polyglot." + JSContextOptions.PREINIT_OPTIONAL_GLOBALS_NAME, String.valueOf(preinitOptionalGlobals));
        for (Map.Entry<String, String> option : preinitOptions.entrySet()) {
            properties.put("polyglot." + option.getKey(), option.getValue());
        }
        try {
            properties.forEach(System::setProperty);
            preInitializeEngine.invoke(null);
        } finally {
            properties.keySet().forEach(System::clearProperty);
        }
        try (Context context = newContextBuilder(runtimeOptions).build()) {
            String result = context.eval("js", code).asString();
            context.enter();
            try {
                assertNotNull("the context must use the pre-initialized context", preinitIntlObject(JavaScriptLanguage.getJSRealm(context)));
            } finally {
                context.leave();
            }
            return result;
        } finally {
            resetPreInitializedEngine.invoke(null);
        }
    }

    private static Object preinitIntlObject(JSRealm realm) throws ReflectiveOperationException {
        Field field = JSRealm.class.getDeclaredField("preinitIntlObject");
        field.setAccessible(true);
        return field.get(realm);
    }
}
