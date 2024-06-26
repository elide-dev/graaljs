/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

load('../assert.js');

for (type of [java.lang.Runnable, java.beans.BeanInfo, java.lang.Math]) {
    assertFalse(null instanceof type);
    assertFalse(undefined instanceof type);
    assertFalse(true instanceof type);
    assertFalse(42 instanceof type);
    assertFalse(42n instanceof type);
    assertFalse("foo" instanceof type);
    assertFalse(Symbol.toStringTag instanceof type);
    assertFalse({} instanceof type);
    assertFalse(function() {} instanceof type);
}
var stringBuilder = new java.lang.StringBuilder();
assertTrue(stringBuilder instanceof java.lang.StringBuilder);
assertTrue(stringBuilder instanceof java.lang.CharSequence);
assertTrue(stringBuilder instanceof java.lang.Object);
assertFalse(stringBuilder instanceof java.lang.Runnable);

assertThrows(() => stringBuilder instanceof (new java.lang.Object()), TypeError); // Right-hand-side of instanceof is not a meta object
assertFalse(Function.prototype[Symbol.hasInstance].call(java.lang.Object, stringBuilder)); // Callable (instantiable) foreign object
assertFalse(Function.prototype[Symbol.hasInstance].call(java.lang.Runnable, stringBuilder)); // Not callable foreign object
