/*
 * Copyright 2021 Palantir Technologies, Inc. All rights reserved.
 */

package org.apache.lucene.geo;

import org.apache.lucene.tests.util.LuceneTestCase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public final class TestTessellatorTemp extends LuceneTestCase {

    // Use the tessellator from main branch
    public void testTessellator() throws Exception {
        Tessellator.tessellate(loadPolygon(), false);
    }

    // Use the tessellator from Lucene 8.2.0
    public void testTessellator820() throws Exception {
        Tessellator820.tessellate(loadPolygon());
    }

    // Use the tessellator from Lucene 8.3.0
    public void testTessellator830() throws Exception {
        Tessellator830.tessellate(loadPolygon());
    }

    private Polygon loadPolygon() throws Exception {
        String geoJson = new BufferedReader(
                new InputStreamReader(getDataInputStream("polygon-3.json"))).lines()
                .collect(Collectors.joining());
        return Polygon.fromGeoJSON(geoJson)[0];
    }
}
