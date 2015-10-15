package com.netflix.nicobar.mix.groovy2.utils;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;

import java.io.File;

/**
 *
 */
public class MixGroovy2PluginUtils {

    /**
     *
     * @param source
     * @param target
     * @param overwrite
     * @throws BuildException
     */
    public static void unJar(File source, File target, boolean overwrite) throws BuildException {
        Expand expand = new Expand();
        expand.setProject(new Project());
        expand.setDest(target);
        expand.setSrc(source);
        expand.setOverwrite(overwrite);
        expand.execute();
    }
}
