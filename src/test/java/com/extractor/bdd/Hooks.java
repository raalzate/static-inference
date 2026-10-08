package com.extractor.bdd;

import com.extractor.model.DependencyGraph;
import io.cucumber.java.After;
import io.cucumber.java.Before;

/** Resets the shared scenario state and restores global switches after each scenario. */
public class Hooks {

    @Before
    public void resetWorld() {
        World.reset();
    }

    @After
    public void restoreGlobals() {
        System.clearProperty(DependencyGraph.Meta.COLLECTED_AT_OPT_IN);
    }
}
