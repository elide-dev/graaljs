/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.interop;

import static com.oracle.truffle.js.lang.JavaScriptLanguage.ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.test.JSTest;

/**
 * Tests for prototypes registered for interop meta-objects with
 * {@code Graal.registerForeignPrototype} ({@link JSContextOptions#FOREIGN_PROTOTYPE_REGISTRY}) and
 * {@link JSRealm#registerForeignPrototype}.
 */
public class ForeignPrototypeRegistryTest {

    static final TestMeta TABLE_META = new TestMeta("Table");
    static final TestMeta RESOURCE_META = new TestMeta("Resource");
    static final TestMeta OTHER_META = new TestMeta("Other");

    private static Context.Builder newContextBuilder() {
        return JSTest.newContextBuilder(ID).option(JSContextOptions.FOREIGN_PROTOTYPE_REGISTRY_NAME, "true").option(JSContextOptions.EXPLICIT_RESOURCE_MANAGEMENT_NAME, "true");
    }

    /**
     * Registers a prototype for {@code meta} that inherits from the trait-based prototype of
     * {@code sample} and adds a {@code Symbol.toStringTag} of {@code tag}.
     */
    private static final String REGISTER_TAGGED = """
                    (meta, sample, tag) => {
                        const proto = Object.create(Object.getPrototypeOf(sample), {
                            [Symbol.toStringTag]: {value: tag},
                        });
                        Graal.registerForeignPrototype(meta, proto);
                        return proto;
                    }
                    """;

    @Test
    public void testOptionDisabledByDefault() {
        try (Context context = JSTest.newContextBuilder(ID).build()) {
            assertEquals("undefined", context.eval(ID, "typeof Graal.registerForeignPrototype").asString());
        }
        try (Context context = newContextBuilder().build()) {
            assertEquals("function", context.eval(ID, "typeof Graal.registerForeignPrototype").asString());
        }
    }

    @Test
    public void testSymbolMembers() {
        try (Context context = newContextBuilder().build()) {
            TestResource resource = new TestResource(RESOURCE_META);
            Value result = context.eval(ID, """
                            (meta, resource) => {
                                const log = [];
                                const proto = Object.create(Object.getPrototypeOf(resource), {
                                    [Symbol.toStringTag]: {value: 'Resource'},
                                    [Symbol.dispose]: {value() { log.push('dispose:' + (Object.getPrototypeOf(this) === proto)); }},
                                    close: {value() { return 'closed'; }},
                                });
                                Graal.registerForeignPrototype(meta, proto);
                                {
                                    using r = resource;
                                    log.push('body');
                                }
                                log.push(Object.getPrototypeOf(resource) === proto);
                                log.push(Object.prototype.toString.call(resource));
                                log.push(resource.close());
                                log.push(typeof resource.hasOwnProperty);
                                log.push(resource instanceof Object);
                                return log.join();
                            }
                            """).execute(RESOURCE_META, resource);
            assertEquals("body,dispose:true,true,[object Resource],closed,function,true", result.asString());
        }
    }

    @Test
    public void testAsyncProtocols() {
        try (Context context = newContextBuilder().build()) {
            TestTable table = new TestTable(TABLE_META, 1, 2, 3);
            context.eval(ID, """
                            (meta, table) => {
                                globalThis.log = [];
                                const proto = Object.create(Object.getPrototypeOf(table), {
                                    [Symbol.asyncIterator]: {value: async function*() { for (const x of this) yield x * 10; }},
                                    [Symbol.asyncDispose]: {value() { log.push('asyncDispose'); return Promise.resolve(); }},
                                });
                                Graal.registerForeignPrototype(meta, proto);
                                (async () => {
                                    await using t = table;
                                    for await (const x of t) {
                                        log.push(x);
                                    }
                                })().then(() => log.push('done'), e => log.push('error: ' + e));
                            }
                            """).execute(TABLE_META, table);
            assertEquals("10,20,30,asyncDispose,done", context.eval(ID, "log.join()").asString());
        }
    }

    @Test
    public void testArrayTraitKept() {
        try (Context context = newContextBuilder().build()) {
            TestTable table = new TestTable(TABLE_META, 1, 2, 3);
            TestTable other = new TestTable(OTHER_META, 4, 5);
            Value proto = context.eval(ID, REGISTER_TAGGED).execute(TABLE_META, table, "Table");
            Value result = context.eval(ID, """
                            (table, proto) => [
                                Object.getPrototypeOf(table) === proto,
                                table.map(x => x * 2).join(),
                                table.length,
                                [...table].join(),
                                Array.isArray(table),
                                Object.prototype.toString.call(table),
                            ].join()
                            """).execute(table, proto);
            assertEquals("true,2,4,6,3,1,2,3,true,[object Table]", result.asString());

            // Foreign objects with another meta-object are unaffected.
            result = context.eval(ID, """
                            (other, proto) => [
                                Object.getPrototypeOf(other) === proto,
                                Object.getPrototypeOf(other) === Object.getPrototypeOf(proto),
                                other[Symbol.toStringTag],
                                Object.prototype.toString.call(other),
                                other.map(x => x + 1).join(),
                            ].join()
                            """).execute(other, proto);
            assertEquals("false,true,,[object Array],5,6", result.asString());
        }
    }

    @Test
    public void testRegisterWithInstanceAndUnregister() {
        try (Context context = newContextBuilder().build()) {
            TestResource resource = new TestResource(RESOURCE_META);
            Value result = context.eval(ID, """
                            (resource) => {
                                const base = Object.getPrototypeOf(resource);
                                const proto = Object.create(base, {[Symbol.toStringTag]: {value: 'Resource'}});
                                // An instance stands for its meta-object.
                                Graal.registerForeignPrototype(resource, proto);
                                const registered = Object.getPrototypeOf(resource) === proto;
                                Graal.registerForeignPrototype(resource, null);
                                return [registered, Object.getPrototypeOf(resource) === base, Object.prototype.toString.call(resource)].join();
                            }
                            """).execute(resource);
            assertEquals("true,true,[object Object]", result.asString());

            JSTest.assertThrows(() -> context.eval(ID, "Graal.registerForeignPrototype({}, {})"), e -> assertTrue(e.getMessage(), e.isGuestException()));
            JSTest.assertThrows(() -> context.eval(ID, "(r) => Graal.registerForeignPrototype(r, 42)").execute(resource), e -> assertTrue(e.getMessage(), e.isGuestException()));
            JSTest.assertThrows(() -> context.eval(ID, "(r) => Graal.registerForeignPrototype(r, {})").execute(new TestResource(null)),
                            e -> assertTrue(e.getMessage(), e.isGuestException()));
        }
    }

    /**
     * Host objects are keyed by their host class.
     */
    @Test
    public void testHostClass() {
        try (Context context = newContextBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup(s -> true).build()) {
            Value result = context.eval(ID, """
                            (list) => {
                                const proto = Object.create(Object.getPrototypeOf(list), {[Symbol.toStringTag]: {value: 'JavaList'}});
                                Graal.registerForeignPrototype(Java.type('java.util.ArrayList'), proto);
                                return [Object.getPrototypeOf(list) === proto, list.map(x => x * 2).join(), Object.prototype.toString.call(list)].join();
                            }
                            """).execute(new ArrayList<>(List.of(1, 2)));
            assertEquals("true,2,4,[object JavaList]", result.asString());
        }
    }

    /**
     * Registration through the Java API, as an embedder that has access to the realm would do it.
     */
    @Test
    public void testJavaAPI() {
        try (Context context = JSTest.newContextBuilder(ID).build()) {
            context.enter();
            try {
                JSRealm realm = JavaScriptLanguage.getJSRealm(context);
                JSObject proto = JSOrdinary.create(realm.getContext(), realm, realm.getForeignArrayPrototype());
                JSObjectUtil.putDataProperty(proto, Symbol.SYMBOL_TO_STRING_TAG, Strings.fromJavaString("Table"));
                realm.registerForeignPrototype(TABLE_META, proto);
            } finally {
                context.leave();
            }
            Value result = context.eval(ID, "(t) => [Object.prototype.toString.call(t), t.map(x => -x).join()].join()").execute(new TestTable(TABLE_META, 1, 2));
            assertEquals("[object Table],-1,-2", result.asString());
        }
    }

    /**
     * Contexts sharing an engine (and thus code) have independent registrations.
     */
    @Test
    public void testSharedEngine() {
        Source source = Source.create(ID, """
                        (o) => {
                            let r;
                            for (let i = 0; i < 1000; i++) {
                                r = [o[Symbol.toStringTag], Object.prototype.toString.call(o), o.length].join();
                            }
                            return r;
                        }
                        """);
        try (Engine engine = JSTest.newEngineBuilder().build();
                        Context c1 = newContextBuilder().engine(engine).build();
                        Context c2 = newContextBuilder().engine(engine).build();
                        Context c3 = newContextBuilder().engine(engine).build()) {
            TestTable table = new TestTable(TABLE_META, 1, 2, 3);
            for (int i = 0; i < 3; i++) {
                assertEquals(",[object Array],3", c1.eval(source).execute(table).asString());
                assertEquals(",[object Array],3", c2.eval(source).execute(table).asString());
            }
            c1.eval(ID, REGISTER_TAGGED).execute(TABLE_META, table, "A");
            c2.eval(ID, REGISTER_TAGGED).execute(TABLE_META, table, "B");
            for (int i = 0; i < 3; i++) {
                assertEquals("A,[object A],3", c1.eval(source).execute(table).asString());
                assertEquals("B,[object B],3", c2.eval(source).execute(table).asString());
                assertEquals(",[object Array],3", c3.eval(source).execute(table).asString());
            }
        }
    }

    /**
     * The registry can be used in a context created from a pre-initialized context.
     */
    @Test
    public void testPreinitializedContext() throws Exception {
        Class<?> holder = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ForeignPrototypeRegistryTest.class.getClassLoader());
        Method preInitializeEngine = holder.getDeclaredMethod("preInitializeEngine");
        Method resetPreInitializedEngine = holder.getDeclaredMethod("resetPreInitializedEngine");
        preInitializeEngine.setAccessible(true);
        resetPreInitializedEngine.setAccessible(true);
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("polyglot.image-build-time.PreinitializeContexts", "js");
        properties.put("polyglot.image-build-time.PreinitializeAllowExperimentalOptions", "true");
        properties.put("polyglot." + JSContextOptions.FOREIGN_PROTOTYPE_REGISTRY_NAME, "true");
        properties.put("polyglot." + JSContextOptions.EXPLICIT_RESOURCE_MANAGEMENT_NAME, "true");
        try {
            properties.forEach(System::setProperty);
            preInitializeEngine.invoke(null);
        } finally {
            properties.keySet().forEach(System::clearProperty);
        }
        try (Context context = newContextBuilder().build()) {
            context.enter();
            try {
                assertNotNull("the context must use the pre-initialized context", preinitIntlObject(JavaScriptLanguage.getJSRealm(context)));
            } finally {
                context.leave();
            }
            TestTable table = new TestTable(TABLE_META, 1, 2, 3);
            context.eval(ID, REGISTER_TAGGED).execute(TABLE_META, table, "Table");
            assertEquals("[object Table],6", context.eval(ID, "(t) => [Object.prototype.toString.call(t), t.reduce((a, b) => a + b)].join()").execute(table).asString());
        } finally {
            resetPreInitializedEngine.invoke(null);
        }
    }

    private static Object preinitIntlObject(JSRealm realm) throws ReflectiveOperationException {
        Field field = JSRealm.class.getDeclaredField("preinitIntlObject");
        field.setAccessible(true);
        return field.get(realm);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestMeta implements TruffleObject {
        final String name;

        TestMeta(String name) {
            this.name = name;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMetaObject() {
            return true;
        }

        @ExportMessage(name = "getMetaQualifiedName")
        @ExportMessage(name = "getMetaSimpleName")
        Object getName() {
            return name;
        }

        @ExportMessage
        boolean isMetaInstance(Object instance) {
            return (instance instanceof TestTable t && t.meta == this) || (instance instanceof TestResource r && r.meta == this);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestResource implements TruffleObject {
        final TestMeta meta;

        TestResource(TestMeta meta) {
            this.meta = meta;
        }

        @ExportMessage
        boolean hasMetaObject() {
            return meta != null;
        }

        @ExportMessage
        Object getMetaObject() throws UnsupportedMessageException {
            if (meta == null) {
                throw UnsupportedMessageException.create();
            }
            return meta;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestTable implements TruffleObject {
        final TestMeta meta;
        final int[] values;

        TestTable(TestMeta meta, int... values) {
            this.meta = meta;
            this.values = values;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return values.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < values.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return values[(int) index];
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        Object getMetaObject() {
            return meta;
        }
    }
}
