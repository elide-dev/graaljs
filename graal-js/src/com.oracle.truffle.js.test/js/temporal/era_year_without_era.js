/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * @option temporal=true
 */

load('../assert.js');

for (const calendar of ['islamic-civil', 'islamic-tbla', 'islamic-umalqura', 'gregory', 'japanese']) {
    for (const from of [Temporal.PlainDate.from, Temporal.PlainDateTime.from, Temporal.PlainYearMonth.from]) {
        assertThrows(() => from({calendar, eraYear: 1447, month: 1, day: 1}), TypeError);
        assertThrows(() => from({calendar, era: 'ah', month: 1, day: 1}), TypeError);
    }
}

for (const calendar of ['islamic-civil', 'islamic-tbla', 'islamic-umalqura']) {
    const after = Temporal.PlainDate.from({calendar, era: 'ah', eraYear: 1447, month: 1, day: 1});
    assertSame(1447, after.eraYear);
    const before = Temporal.PlainDate.from({calendar, era: 'bh', eraYear: 2, month: 1, day: 1});
    assertSame(-1, before.year);
}
