package com.netflix.nicobar.mix.groovy2.internal.compile;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2CompilerHelper;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * Mix - 'cause it is possible to put dependency of .jar & .class at level of .groovy scripts
 * <p/>
 * Helper class for compiling Groovy files into classes. This class takes as it's input a collection
 * of {@link ScriptArchive}s and outputs a {@link GroovyClassLoader} with the classes pre-loaded into it.
 * <p/>
 * Dependency of .class & .jar - put directly to the groovyClassLoader
 * <p/>
 * based on {@link Groovy2CompilerHelper}
 *
 * @author Eugene Kalinin
 */
public class MixGroovy2CompilerHelper extends Groovy2CompilerHelper{

    public MixGroovy2CompilerHelper(Path targetDir) {
        super(targetDir);
    }

    /**
     * Compile the given source and load the resultant classes into a new {@link ClassNotFoundException}
     *
     * @return initialized and laoded classes
     * @throws ScriptCompilationException
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<GroovyClass> compile() throws ScriptCompilationException {
        final CompilerConfiguration conf = this.getCompileConfig() != null ? this.getCompileConfig() : CompilerConfiguration.DEFAULT;
        conf.setTolerance(0);
        conf.setVerbose(true);
        conf.setTargetDirectory(this.getTargetDir().toFile());
        final ClassLoader buildParentClassloader = this.getParentClassLoader() != null ?
                this.getParentClassLoader() : Thread.currentThread().getContextClassLoader();
        GroovyClassLoader groovyClassLoader = AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
            public GroovyClassLoader run() {
                return new GroovyClassLoader(buildParentClassloader, conf, false);
            }
        });

        CompilationUnit unit = new CompilationUnit(conf, null, groovyClassLoader);
        try {
            for (ScriptArchive scriptArchive : this.getScriptArchives()) {
                Set<String> entryNames = scriptArchive.getArchiveEntryNames();
                for (String entryName : entryNames) {
                    if (entryName.endsWith("groovy")) {
                        unit.addSource(scriptArchive.getEntry(entryName));
                    }

                    if (entryName.endsWith("class") || entryName.endsWith("jar")) {
                        groovyClassLoader.addURL(scriptArchive.getEntry(entryName));
                    }
                }
            }
        } catch (IOException e) {
            throw new ScriptCompilationException("Exception loading source files", e);
        }
        for (Path sourceFile : this.getSourceFiles()) {
            unit.addSource(sourceFile.toFile());
        }
        try {
            unit.compile(Phases.OUTPUT);
        } catch (CompilationFailedException e) {
            throw new ScriptCompilationException("Exception during script compilation", e);
        }
        return new HashSet<GroovyClass>(unit.getClasses());
    }
}

