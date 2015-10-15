package com.netflix.nicobar.mix.groovy2.utils;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;

import java.nio.file.Path;

/**
 *
 */
public class MixGroovy2PluginUtils {

    /**
     * UnJar
     * @param source
     * @param target
     * @param overwrite
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
}
