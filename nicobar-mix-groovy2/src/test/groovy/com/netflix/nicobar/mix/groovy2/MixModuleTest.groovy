package com.netflix.nicobar.mix.groovy2

import com.netflix.nicobar.core.archive.ModuleId
import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

/**
 *
 */
@Slf4j
class MixModuleTest extends Specification {

    def 'mix compilation of module'(){
        setup:
        log.info "setup:"

        ModuleId moduleId = ModuleId.create("moduleName", "moduleVersion")

        final String BASE_PATH = "src/test/resources/base-path-build-module-src-plus-jar"
        TestHelper.delete(Paths.get(BASE_PATH, "classes", moduleId.toString()))
        TestHelper.prepareJarAndClass(BASE_PATH)

        Path srcPath = Paths.get(BASE_PATH, "src").toAbsolutePath();
        Path classesPath = Paths.get(BASE_PATH, "classes").toAbsolutePath();
        Path libPath = Paths.get(BASE_PATH, "libs").toAbsolutePath();

        Set<Path> runtimeJars = new HashSet<>();
        // because of AntBuilder inside MixBytecodeLoader
        runtimeJars.add(Paths.get("src/test/resources/libs/ant-1.9.6.jar").toAbsolutePath())
        // 'cause it's run under spock tests
        runtimeJars.add(Paths.get("src/test/resources/libs/spock-core-0.7-groovy-2.0.jar").toAbsolutePath())


        when:
        log.info "when"

        TestHelper.resetContainer()

        new Container.Builder(srcPath, classesPath, libPath)
                .setModuleLoader(
                ContainerUtils.createMixContainerModuleLoaderBuilder(runtimeJars)
                        .withCompilationRootDir(classesPath)
                        .build()
        )
                .setRuntimeJarLibs(runtimeJars)
                .build()

        Container container = Container.getInstance();


        1.times(){
            container.addSrcModuleAndCompile(moduleId, false)
            Thread.sleep(2000);
            log.info "\n\nmake it again";
        }


        then:
        log.info "then"

        container.executeScript(moduleId, "com.company.script")
        container.destroy();

    }

}
