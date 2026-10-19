package com.dfwl.fleet.security;

import java.util.HashSet;
import java.util.Set;

/** Constructs valid page/button test fixtures without bypassing the production parent-page rule. */
public final class TestFunctionGrants {
    private static final FunctionRegistry FUNCTIONS = new FunctionRegistry();

    private TestFunctionGrants() { }

    public static Set<String> grants(String... codes) {
        HashSet<String> result = new HashSet<>();
        for (String code : codes) {
            FunctionRegistry.Function function = FUNCTIONS.find(code);
            if (function == null) throw new IllegalArgumentException("Unknown test function: " + code);
            result.add(code);
            if (function.parentCode() != null) result.add(function.parentCode());
        }
        return Set.copyOf(result);
    }
}
