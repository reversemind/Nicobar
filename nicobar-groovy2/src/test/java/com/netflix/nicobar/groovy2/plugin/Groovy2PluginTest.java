/*
 *
 *  Copyright 2013 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.nicobar.groovy2.plugin;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.PathScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.execution.HystrixScriptModuleExecutor;
import com.netflix.nicobar.core.execution.ScriptModuleExecutable;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.module.ScriptModuleUtils;
import com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2Compiler;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil.TestScript;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

import static org.testng.Assert.*;

/**
 * Integration tests for the Groovy2 language plugin
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class Groovy2PluginTest {

    private static final String GROOVY2_COMPILER_PLUGIN = Groovy2CompilerPlugin.class.getName();
    private static final Random RANDOM = new Random(System.currentTimeMillis());
    private Path uncompilableArchiveDir;
    private Path uncompilableScriptRelativePath;

    @BeforeClass
    public void setup() throws Exception {
        //Module.setModuleLogger(new StreamModuleLogger(System.err));
        uncompilableArchiveDir = Files.createTempDirectory(Groovy2PluginTest.class.getSimpleName()+"_");
        FileUtils.forceDeleteOnExit(uncompilableArchiveDir.toFile());
        uncompilableScriptRelativePath = Paths.get("Uncompilable.groovy");
        byte[] randomBytes = new byte[1024];
        RANDOM.nextBytes(randomBytes);
        Files.write(uncompilableArchiveDir.resolve(uncompilableScriptRelativePath), randomBytes);
    }

    @Test
    public void testLoadSimpleScript() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();
        // create a new script archive consisting of HellowWorld.groovy and add it the loader.
        // Declares a dependency on the Groovy2RuntimeModule.
        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.HELLO_WORLD);
        ScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.HELLO_WORLD.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.HELLO_WORLD.getModuleId()).build())
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton(scriptArchive));

        // locate the class file in the module and execute it
        ScriptModule scriptModule = moduleLoader.getScriptModule(TestScript.HELLO_WORLD.getModuleId());
        Class<?> clazz = findClassByName(scriptModule, TestScript.HELLO_WORLD);
        assertGetMessage(clazz, "Hello, World!");
    }

    @Test
    public void testLoadScriptWithInterface() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();
        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.IMPLEMENTS_INTERFACE);
        ScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.IMPLEMENTS_INTERFACE.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.IMPLEMENTS_INTERFACE.getModuleId()).build())
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton(scriptArchive));

        // locate the class file in the module and execute it via the executor
        ScriptModuleExecutable<String> executable = new ScriptModuleExecutable<String>() {
            @SuppressWarnings({"rawtypes","unchecked"})
            @Override
            public String execute(ScriptModule scriptModule) throws Exception {
                Class<Callable> callable = (Class<Callable>) ScriptModuleUtils.findAssignableClass(scriptModule, Callable.class);
                assertNotNull(callable, "couldn't find Callable for module " + scriptModule.getModuleId());
                Callable<String> instance = callable.newInstance();
                String result = instance.call();
                return result;
            }
        };
        HystrixScriptModuleExecutor<String> executor = new HystrixScriptModuleExecutor<String>("TestModuleExecutor");
        List<String> results = executor.executeModules(Collections.singletonList(TestScript.IMPLEMENTS_INTERFACE.getModuleId()), executable, moduleLoader);
        assertEquals(results, Collections.singletonList("I'm a Callable<String>"));
    }

    /**
     * Test loading/executing a script which has a dependency on a library
     */
    @Test
    public void testLoadScriptWithLibrary() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();
        Path dependsOnARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.DEPENDS_ON_A);

        ScriptArchive dependsOnAArchive = new PathScriptArchive.Builder(dependsOnARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.DEPENDS_ON_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.DEPENDS_ON_A.getModuleId())
                .addModuleDependency(TestScript.LIBRARY_A.getModuleId())
                .build())
            .build();
        Path libARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.LIBRARY_A);
        ScriptArchive libAArchive = new PathScriptArchive.Builder(libARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.LIBRARY_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.LIBRARY_A.getModuleId()).build())
            .build();
        // load them in dependency order to make sure that transitive dependency resolution is working
        moduleLoader.updateScriptArchives(new LinkedHashSet<ScriptArchive>(Arrays.asList(dependsOnAArchive, libAArchive)));

        // locate the class file in the module and execute it
        ScriptModule scriptModule = moduleLoader.getScriptModule(TestScript.DEPENDS_ON_A.getModuleId());
        Class<?> clazz = findClassByName(scriptModule, TestScript.DEPENDS_ON_A);
        assertGetMessage(clazz, "DepondOnA: Called LibraryA and got message:'I'm LibraryA!'");
    }

    /**
     * Test loading/executing a script which has a dependency on a library
     */
    @Test
    public void testReloadLibrary() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();
        Path dependsOnARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.DEPENDS_ON_A);
        ScriptArchive dependsOnAArchive = new PathScriptArchive.Builder(dependsOnARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.DEPENDS_ON_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.DEPENDS_ON_A.getModuleId())
                .addModuleDependency(TestScript.LIBRARY_A.getModuleId())
                .build())
            .build();

        Path libARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.LIBRARY_A);
        ScriptArchive libAArchive = new PathScriptArchive.Builder(libARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.LIBRARY_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.LIBRARY_A.getModuleId()).build())
            .build();

        moduleLoader.updateScriptArchives(new LinkedHashSet<ScriptArchive>(Arrays.asList(dependsOnAArchive, libAArchive)));

        // reload the library with version 2
        Path libAV2RootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.LIBRARY_AV2);
        ScriptArchive libAV2Archive = new PathScriptArchive.Builder(libAV2RootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.LIBRARY_AV2.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.LIBRARY_A.getModuleId()).build())
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton(libAV2Archive));

        // find the dependent and execute it
        ScriptModule scriptModuleDependOnA = moduleLoader.getScriptModule(TestScript.DEPENDS_ON_A.getModuleId());
        Class<?> clazz = findClassByName(scriptModuleDependOnA, TestScript.DEPENDS_ON_A);
        assertGetMessage(clazz, "DepondOnA: Called LibraryA and got message:'I'm LibraryA V2!'");
    }

    /**
     * Tests that if we deploy an uncompilable dependency, the dependents continue to function
     */
    @Test
    public void testDeployBadDependency() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();
        Path dependsOnARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.DEPENDS_ON_A);

        ScriptArchive dependsOnAArchive = new PathScriptArchive.Builder(dependsOnARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.DEPENDS_ON_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.DEPENDS_ON_A.getModuleId())
                .addModuleDependency(TestScript.LIBRARY_A.getModuleId())
                .build())
            .build();
        Path libARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.LIBRARY_A);
        ScriptArchive libAArchive = new PathScriptArchive.Builder(libARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.LIBRARY_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.LIBRARY_A.getModuleId()).build())
            .build();

        moduleLoader.updateScriptArchives(new LinkedHashSet<ScriptArchive>(Arrays.asList(dependsOnAArchive, libAArchive)));
        assertEquals(moduleLoader.getAllScriptModules().size(), 2);

        // attempt reload library-A with invalid groovy
        libAArchive = new PathScriptArchive.Builder(uncompilableArchiveDir)
            .setRecurseRoot(false)
            .addFile(uncompilableScriptRelativePath)
            .setModuleSpec(createGroovyModuleSpec(TestScript.LIBRARY_A.getModuleId()).build())
            .build();
        moduleLoader.updateScriptArchives(new LinkedHashSet<ScriptArchive>(Arrays.asList(libAArchive)));
        assertEquals(moduleLoader.getAllScriptModules().size(), 2);

        // find the dependent and execute it
        ScriptModule scriptModuleDependOnA = moduleLoader.getScriptModule(TestScript.DEPENDS_ON_A.getModuleId());
        Class<?> clazz = findClassByName(scriptModuleDependOnA, TestScript.DEPENDS_ON_A);
        assertGetMessage(clazz, "DepondOnA: Called LibraryA and got message:'I'm LibraryA!'");
    }

    /**
     * Test loading a module which is composed of several interdependent scripts.
     * InternalDepdencyA->InternalDepdencyB-InternalDepdencyC->InternalDepdencyD
     */
    @Test
    public void testLoadScriptWithInternalDependencies() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();

        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.INTERNAL_DEPENDENCY_A);
        ScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(Paths.get("InternalDependencyB.groovy"))
            .addFile(Paths.get("InternalDependencyA.groovy"))
            .addFile(Paths.get("InternalDependencyD.groovy"))
            .addFile(Paths.get("subpackage/InternalDependencyC.groovy"))
            .setModuleSpec(createGroovyModuleSpec(TestScript.INTERNAL_DEPENDENCY_A.getModuleId()).build())
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton(scriptArchive));

        // locate the class file in the module and execute it
        ScriptModule scriptModule = moduleLoader.getScriptModule(TestScript.INTERNAL_DEPENDENCY_A.getModuleId());
        Class<?> clazz = findClassByName(scriptModule, TestScript.INTERNAL_DEPENDENCY_A);
        assertGetMessage(clazz, "I'm A.  Called B and got: I'm B. Called C and got: I'm C. Called D and got: I'm D.");
    }

    @Test
    public void testMixedModule() throws Exception {
        ScriptModuleLoader.Builder moduleLoaderBuilder = createGroovyModuleLoader();

        ScriptModuleLoader loader = moduleLoaderBuilder.addPluginSpec(
                new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
                    .withPluginClassName(BytecodeLoadingPlugin.class.getName()).build())
                .withCompilationRootDir(Paths.get("/opt/_del/_other"))
               .build();

        Path jarPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.MIXED_MODULE).resolve(TestScript.MIXED_MODULE.getScriptPath());

        ScriptArchive archive = new JarScriptArchive.Builder(jarPath).build();
        loader.updateScriptArchives(Collections.singleton(archive));

        ScriptModule scriptModule = loader.getScriptModule(TestScript.MIXED_MODULE.getModuleId());
        Class<?> clazz = findClassByName(scriptModule, TestScript.MIXED_MODULE);
        Object instance = clazz.newInstance();
        Method method = clazz.getMethod("execute");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello Mixed Module!");

        // Verify groovy class
        clazz = findClassByName(scriptModule, "com.netflix.nicobar.test.HelloBytecode");
        method = clazz.getMethod("execute");
        message = (String)method.invoke(clazz.newInstance());
        assertEquals(message, "Hello Bytecode!");
    }

    /**
     * Test loading/executing a script with app package import filters,
     * and which is dependent a library.
     *
     */
    @Test
    public void testLoadScriptWithAppImports() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader().build();
        Path dependsOnARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.DEPENDS_ON_A);

        ScriptArchive dependsOnAArchive = new PathScriptArchive.Builder(dependsOnARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.DEPENDS_ON_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.DEPENDS_ON_A.getModuleId())
                .addModuleDependency(TestScript.LIBRARY_A.getModuleId())
                .addAppImportFilter("test")
                .build())
            .build();
        Path libARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.LIBRARY_A);
        ScriptArchive libAArchive = new PathScriptArchive.Builder(libARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.LIBRARY_A.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.LIBRARY_A.getModuleId())
                    .addAppImportFilter("test")
                    .build())
            .build();
        // load them in dependency order to make sure that transitive dependency resolution is working
        moduleLoader.updateScriptArchives(new LinkedHashSet<ScriptArchive>(Arrays.asList(dependsOnAArchive, libAArchive)));

        // locate the class file in the module and execute it
        ScriptModule scriptModule = moduleLoader.getScriptModule(TestScript.DEPENDS_ON_A.getModuleId());
        Class<?> clazz = findClassByName(scriptModule, TestScript.DEPENDS_ON_A);
        assertGetMessage(clazz, "DepondOnA: Called LibraryA and got message:'I'm LibraryA!'");
    }
    /**
     * Create a module loader this is wired up with the groovy compiler plugin
     */
    private ScriptModuleLoader.Builder createGroovyModuleLoader() throws Exception {
        // create the groovy plugin spec. this plugin specified a new module and classloader called "Groovy2Runtime"
        // which contains the groovy-all-2.1.6.jar and the nicobar-groovy2 project.
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder(Groovy2Compiler.GROOVY2_COMPILER_ID)
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyRuntime())
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyPluginLocation())
            // hack to make the gradle build work. still doesn't seem to properly instrument the code
            // should probably add a classloader dependency on the system classloader instead
            .addRuntimeResource(GroovyTestResourceUtil.getCoberturaJar(getClass().getClassLoader()))
            .withPluginClassName(GROOVY2_COMPILER_PLUGIN)
            .build();

        // create and start the builder with the plugin
        return new ScriptModuleLoader.Builder().addPluginSpec(pluginSpec);
    }

    /**
     * Create a module spec builder with pre-populated groovy dependency
     */
    private ScriptModuleSpec.Builder createGroovyModuleSpec(String moduleId) {
        return new ScriptModuleSpec.Builder(moduleId)
            .addCompilerPluginId(Groovy2CompilerPlugin.PLUGIN_ID);
    }

    private Class<?> findClassByName(ScriptModule scriptModule, TestScript testScript) {
        assertNotNull(scriptModule, "Missing scriptModule for  " + testScript);
        return findClassByName(scriptModule, testScript.getClassName());
    }

    private Class<?> findClassByName(ScriptModule scriptModule, String className) {
        Set<Class<?>> classes = scriptModule.getLoadedClasses();
        for (Class<?> clazz : classes) {
            if (clazz.getName().equals(className)) {
                return clazz;
            }
        }
        fail("couldn't find class " + className);
        return null;
    }

    private void assertGetMessage(Class<?> targetClass, String expectedMessage) throws Exception {
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, expectedMessage);
    }
}
