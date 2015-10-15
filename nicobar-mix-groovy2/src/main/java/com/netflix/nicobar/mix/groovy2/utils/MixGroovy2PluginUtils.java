package com.netflix.nicobar.mix.groovy2.utils;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.PathScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.utils.ClassPathUtils;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2Compiler;
import com.netflix.nicobar.groovy2.plugin.Groovy2CompilerPlugin;
import com.netflix.nicobar.mix.groovy2.internal.compile.MixGroovy2Compiler;
import com.netflix.nicobar.mix.groovy2.plugin.MixBytecodeLoadingPlugin;
import com.netflix.nicobar.mix.groovy2.plugin.MixGroovy2CompilerPlugin;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 *
 */
public class MixGroovy2PluginUtils {

    public static final String GROOVY2_COMPILER_PLUGIN_CLASS = "com.netflix.nicobar.groovy2.plugin.Groovy2CompilerPlugin";

    /**
     * UnJar jar-file into target directory
     *
     * @param source    -
     * @param target    -
     * @param overwrite -
     * @throws BuildException
     */
    public static void unJar(Path source, Path target, boolean overwrite) throws BuildException {
        Expand expand = new Expand();
        expand.setProject(new Project());
        expand.setDest(target.toFile());
        expand.setSrc(source.toFile());
        expand.setOverwrite(overwrite);
        expand.execute();
    }

    public static Path getModulePath(Path basePath, ModuleId moduleId) {
        if (!basePath.isAbsolute()) {
            throw new IllegalArgumentException("Base path should be absolute");
        }
        return basePath.resolve(moduleId.toString());
    }

    /**
     * @param basePath -
     * @param moduleId -
     * @return - ScriptArchive
     * @throws IOException
     */
    public static ScriptArchive getMixScriptArchiveAtPath(Path basePath, ModuleId moduleId) throws IOException {
        ScriptModuleSpec moduleSpec = new ScriptModuleSpec.Builder(moduleId)
                .addCompilerPluginId(MixBytecodeLoadingPlugin.MIX_PLUGIN_ID)
                .addCompilerPluginId(MixGroovy2CompilerPlugin.MIX_PLUGIN_ID)
                .build();

        return new PathScriptArchive.Builder(getModulePath(basePath, moduleId).toAbsolutePath())
                .setRecurseRoot(true)
                .setModuleSpec(moduleSpec)
                .build();
    }

    public
    static ScriptModuleLoader.Builder createMixContainerModuleLoaderBuilder(ClassLoader parentClassLoader, Set<Path> externalLibs) {

        ScriptCompilerPluginSpec.Builder builder = new ScriptCompilerPluginSpec.Builder(MixGroovy2Compiler.GROOVY2_COMPILER_ID)
                .addRuntimeResource(getGroovyRuntime(parentClassLoader))
                .addRuntimeResource(getMixGroovy2PluginLocation(parentClassLoader))
                .addRuntimeResource(getMixByteCodeLoadingPluginPath(parentClassLoader))

                        // hack to make the gradle build work. still doesn't seem to properly instrument the code
                        // should probably add a classloader dependency on the system classloader instead
                .addRuntimeResource(getCoberturaJar(parentClassLoader))
                .withPluginClassName(MixGroovy2CompilerPlugin.class.getName());

        // in version higher 0.2.6 of Nicobar should be added some useful methods, but now needs to iterate
        // add run time .jar libs
        if (!externalLibs.isEmpty()) {
            for (Path path : externalLibs) {
                builder.addRuntimeResource(path.toAbsolutePath());
            }
        }

        ScriptCompilerPluginSpec mixGroovy2CompilerPluginSpec = builder.build();
        ScriptCompilerPluginSpec mixByteCodeCompilerPluginSpec = buildByteCodeCompilerPluginSpec(parentClassLoader, externalLibs);

        ScriptCompilerPluginSpec groovy2CompilerPluginSpec = buildGroovy2CompilerPluginSpec(parentClassLoader, externalLibs);
        ScriptCompilerPluginSpec byteCodeCompilerPluginSpec = buildByteCodeCompilerPluginSpec(parentClassLoader, externalLibs);

        // create and start the builder with the plugin
        return new ScriptModuleLoader.Builder()
                .addPluginSpec(mixGroovy2CompilerPluginSpec)
                .addPluginSpec(mixByteCodeCompilerPluginSpec)
                .addPluginSpec(groovy2CompilerPluginSpec)
                .addPluginSpec(byteCodeCompilerPluginSpec);
    }

    /**
     * Groovy2 CompilerPluginSpec
     *
     * @param runTimeResourcesJar - set of paths to runtime .jar lib
     * @return
     */
    public static ScriptCompilerPluginSpec buildGroovy2CompilerPluginSpec(ClassLoader parentClassLoader, Set<Path> runTimeResourcesJar) {
        // Groovy2CompilerPlugin

        // create the groovy plugin spec. this plugin specified a new module and classloader called "Groovy2Runtime"
        // which contains the groovy-all-n.n.n.jar
        ScriptCompilerPluginSpec.Builder builder = new ScriptCompilerPluginSpec.Builder(Groovy2Compiler.GROOVY2_COMPILER_ID)
                .addRuntimeResource(getGroovyRuntime(parentClassLoader))
                .addRuntimeResource(getGroovyPluginLocation(parentClassLoader))
                .addRuntimeResource(getByteCodeLoadingPluginPath())

                // hack to make the gradle build work. still doesn't seem to properly instrument the code
                // should probably add a classloader dependency on the system classloader instead
                .addRuntimeResource(getCoberturaJar(parentClassLoader))
                .withPluginClassName(Groovy2CompilerPlugin.class.getName());

        // in version higher 0.2.6 of Nicobar should be added some useful methods, but now needs to iterate
        // add run time .jar libs
        if (!runTimeResourcesJar.isEmpty()) {
            for (Path path : runTimeResourcesJar) {
                builder.addRuntimeResource(path.toAbsolutePath());
            }
        }

        return builder.build();
    }

    public static ScriptCompilerPluginSpec buildByteCodeCompilerPluginSpec(ClassLoader parentClassLoader, Set<Path> runTimeResourcesJar) {

        ScriptCompilerPluginSpec.Builder builder = new ScriptCompilerPluginSpec.Builder(MixBytecodeLoadingPlugin.MIX_PLUGIN_ID)

                .addRuntimeResource(getGroovyRuntime(parentClassLoader))
                .addRuntimeResource(getMixGroovy2PluginLocation(parentClassLoader))
                .addRuntimeResource(getCoberturaJar(parentClassLoader))

                .addRuntimeResource(getMixByteCodeLoadingPluginPath(parentClassLoader))
                .withPluginClassName(MixBytecodeLoadingPlugin.class.getName());

        // in version higher 0.2.6 of Nicobar should be added some useful methods, but now needs to iterate
        // add run time .jar libs
        if (!runTimeResourcesJar.isEmpty()) {
            for (Path path : runTimeResourcesJar) {
                builder.addRuntimeResource(path.toAbsolutePath());
            }
        }

        return builder.build();
    }

    private static Path getByteCodeLoadingPluginPath(ClassLoader classLoader) {
        String resourceName = ClassPathUtils.classNameToResourceName("com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin");
        Path path = ClassPathUtils.findRootPathForResource(resourceName, classLoader);
        if (path == null) {
            throw new IllegalStateException("coudln't find BytecodeLoadingPlugin plugin jar in the classpath.");
        }
        return path;
    }

    public static Path getMixGroovy2PluginLocation(ClassLoader classLoader) {
        String resourceName = ClassPathUtils.classNameToResourceName("com.netflix.nicobar.mix.groovy2.internal.compile.MixGroovy2Compiler");
        Path path = ClassPathUtils.findRootPathForResource(resourceName, classLoader);
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy2 plugin jar in the classpath.");
        }
        return path;
    }

    private static Path getMixByteCodeLoadingPluginPath(ClassLoader classLoader) {
        String resourceName = ClassPathUtils.classNameToResourceName("com.netflix.nicobar.mix.groovy2.plugin.MixBytecodeLoadingPlugin");
        Path path = ClassPathUtils.findRootPathForResource(resourceName, classLoader);
        if (path == null) {
            throw new IllegalStateException("coudln't find MixBytecodeLoadingPlugin plugin jar in the classpath.");
        }
        return path;
    }

    private static Path getCoberturaJar(ClassLoader classLoader) {
        return ClassPathUtils.findRootPathForResource("net/sourceforge/cobertura/coveragedata/HasBeenInstrumented.class", classLoader);
    }

    public static Path getGroovyRuntime(ClassLoader classLoader) {
        Path path = ClassPathUtils.findRootPathForResource("META-INF/groovy-release-info.properties", classLoader);
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy-all.n.n.n.jar in the classpath.");
        }
        return path;
    }

    public static Path getGroovyPluginLocation(ClassLoader classLoader) {
        String resourceName = ClassPathUtils.classNameToResourceName(GROOVY2_COMPILER_PLUGIN_CLASS);
        Path path = ClassPathUtils.findRootPathForResource(resourceName, classLoader);
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy2 plugin jar in the classpath.");
        }
        return path;
    }
}
