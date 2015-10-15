package com.netflix.nicobar.mix.groovy2.plugin;

import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.plugin.ScriptCompilerPlugin;
import com.reversemind.nicobar.container.mix.compiler.MixGroovy2Compiler;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class MixGroovy2CompilerPlugin implements ScriptCompilerPlugin {

    public static final String PLUGIN_ID = "mix.groovy2";

    public MixGroovy2CompilerPlugin() {
    }

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers(Map<String, Object> compilerParams) {
        return Collections.singleton(new MixGroovy2Compiler(compilerParams));
    }
}
