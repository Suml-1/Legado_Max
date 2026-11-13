package org.htmlunit.corejs.javascript;

import org.htmlunit.corejs.javascript.xml.XMLLib;

/**
 * Keeps a host-initiated nested evaluation in its own realm. Rhino otherwise keeps
 * the outermost top call, including the realm used by dynamic default this/eval.
 * This package-local engine access avoids reflection. RhinoClassShutter explicitly
 * denies script access to this class; it is only called by the host's eval entry points.
 */
public final class NestedScriptScope implements AutoCloseable {
    private final Context context;
    private final TopLevel previousScope;
    private final XMLLib previousXmlLib;

    private NestedScriptScope(Context context, TopLevel scope) {
        this.context = context;
        previousScope = context.topCallScope;
        previousXmlLib = context.cachedXMLLib;
        context.topCallScope = scope;
        context.cachedXMLLib = null;
    }

    public static NestedScriptScope enter(Context context, VarScope scope) {
        TopLevel topLevel = ScriptableObject.getTopLevelScope(scope);
        // A root evaluation must still enter through Rhino's normal doTopCall gate.
        if (context.topCallScope == null || context.topCallScope == topLevel) {
            return null;
        }
        return new NestedScriptScope(context, topLevel);
    }

    @Override
    public void close() {
        context.topCallScope = previousScope;
        context.cachedXMLLib = previousXmlLib;
    }
}
