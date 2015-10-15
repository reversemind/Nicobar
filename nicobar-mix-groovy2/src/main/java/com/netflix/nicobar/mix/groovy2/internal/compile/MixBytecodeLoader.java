package com.netflix.nicobar.mix.groovy2.internal.compile;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.internal.compile.BytecodeLoader;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;
import com.netflix.nicobar.mix.groovy2.utils.MixGroovy2PluginUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Mix - 'cause it is possible to put dependency of .jar & .class at level of .groovy scripts
 * based on {@link BytecodeLoader} - added extraction to targetDir all dependent .class
 */
public class MixBytecodeLoader extends BytecodeLoader implements ScriptArchiveCompiler {

    /**
     * Compile (load from) an archive, if it contains any .class and .jar files
     */
    @Override
    public boolean shouldCompile(ScriptArchive archive) {
        Set<String> entries = archive.getArchiveEntryNames();
        boolean shouldCompile = false;
        for (String entry: entries) {
            if (entry.endsWith(".class") || entry.endsWith(".jar")) {
                shouldCompile = true;
            }
        }

        return shouldCompile;
    }

    /**
     * Supports features
     *
     * @param archive archive to generate class files for
     * @param moduleClassLoader class loader which can be used to find all classes and resources for modules.
     *  The resultant classes of the compile operation should be injected into the classloader
     *  for which the given archive has a declared dependency on
     * @param targetDir a directory in which to store compiled classes, if any.
     * @return
     * @throws ScriptCompilationException
     * @throws IOException
     */
    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path targetDir)
            throws ScriptCompilationException, IOException {
        HashSet<Class<?>> addedClasses = new HashSet<Class<?>>(archive.getArchiveEntryNames().size());
        for (String entry : archive.getArchiveEntryNames()) {
            if (!(entry.endsWith(".class") || entry.endsWith(".jar"))) {
                continue;
            }

            if(entry.endsWith(".class")){
                // Load from the underlying archive class resource
                String entryName = entry.replace(".class", "").replace("/", ".");
                try {
                    Path pathToClass = Paths.get(archive.getRootUrl().toURI()).toAbsolutePath().resolve(entry);
                    Class<?> addedClass = moduleClassLoader.loadClassLocal(entryName, true);
                    addedClasses.add(addedClass);
                    copyClassRelativelyAt(targetDir, pathToClass, addedClass.getCanonicalName(), addedClass.getSimpleName());
                } catch (Exception e) {
                    throw new ScriptCompilationException("Unable to load and copy class: " + entryName, e);
                }
            } else {
                // Extract dependency from unJar .class into targetDir
                try {
                    Path pathForUnJar = Paths.get(
                            Paths.get(archive.getRootUrl().toURI()).toAbsolutePath().toString(),
                            entry);
                    MixGroovy2PluginUtils.unJar(pathForUnJar, targetDir, true);
                } catch (Exception e) {
                    throw new ScriptCompilationException("Unable to extract and copy classes from jar: " + entry, e);
                }
            }
            moduleClassLoader.addClasses(addedClasses);
        }

        return Collections.unmodifiableSet(addedClasses);
    }

    /**
     *
     * @param targetPath
     * @param sourceClassPath
     * @param canonicalClassName
     * @return - if null - file was not copy
     */
    private static Path copyClassRelativelyAt(
            final Path targetPath,
            final Path sourceClassPath,
            final String canonicalClassName,
            final String className) throws IOException {
        Path _targetPackagesPath = getTargetPackagesPath(targetPath, canonicalClassName);

        if (_targetPackagesPath == null) {
            return null;
        }

        Files.createDirectories(_targetPackagesPath);
        return Files.copy(sourceClassPath, _targetPackagesPath.resolve(className + ".class"), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     *
     * @param targetPath
     * @param canonicalClassName - com.other.package10.OtherScript
     * @return - if null - no need to create sub paths
     */
    private static Path getTargetPackagesPath(final Path targetPath, final String canonicalClassName){
        if(targetPath == null){
            return null;
        }

        if(canonicalClassName == null){
            return null;
        }

        if(canonicalClassName.trim().length() == 0){
            return null;
        }

        String _classSubPath = canonicalClassName.replaceAll("\\.", File.separator) + ".class";
        int nameCount = Paths.get(_classSubPath).getNameCount();

        if(nameCount == 0){
            return null;
        }

        Paths.get(_classSubPath).subpath(0,Paths.get(_classSubPath).getNameCount() - 1);

        Path packagesPath = Paths.get(_classSubPath).subpath(0,Paths.get(_classSubPath).getNameCount()-1);
        return Paths.get(targetPath.toString(), packagesPath.toString());
    }

}
