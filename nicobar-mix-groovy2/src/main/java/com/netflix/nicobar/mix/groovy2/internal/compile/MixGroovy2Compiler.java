package com.netflix.nicobar.mix.groovy2.internal.compile;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2Compiler;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Mix - 'cause it is possible to put dependency of .jar & .class at level of .groovy scripts
 */
public class MixGroovy2Compiler extends Groovy2Compiler implements ScriptArchiveCompiler {

    public final static String MIX_GROOVY2_COMPILER_ID = "mix.groovy2";

    private List<String> customizerClassNames = new LinkedList<String>();

    public MixGroovy2Compiler(Map<String, Object> compilerParams) {
        super(compilerParams);
    }

    @Override
    public String getCompilerId(){
        return MIX_GROOVY2_COMPILER_ID;
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path compilationRootDir)
            throws ScriptCompilationException, IOException {

        List<CompilationCustomizer> customizers = new LinkedList<CompilationCustomizer>();

        for (String klassName: this.customizerClassNames) {
            CompilationCustomizer instance = this.getCustomizerInstanceFromString(klassName, moduleClassLoader);
            if (instance != null ) {
                customizers.add(instance);
            }
        }

        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.getScriptExtensions().add("class");
        config.getScriptExtensions().add("jar");

        config.addCompilationCustomizers(customizers.toArray(new CompilationCustomizer[0]));

        new MixGroovy2CompilerHelper(compilationRootDir)
                .addScriptArchive(archive)
                .withParentClassloader(moduleClassLoader) // TODO: replace JBossModuleClassLoader with generic class loader
                .withConfiguration(config)
                .compile();
        return Collections.emptySet();
    }
}
