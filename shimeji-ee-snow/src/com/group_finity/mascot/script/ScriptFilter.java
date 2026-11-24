package com.group_finity.mascot.script;

import org.openjdk.nashorn.api.scripting.ClassFilter;

public class ScriptFilter implements ClassFilter
{
    @Override
    public boolean exposeToScripts( String string )
    {
        // Expressions already receive mascot/environment objects as bindings.
        // Importing application classes also exposes filesystem-writing helpers.
        return false;
    }
}
