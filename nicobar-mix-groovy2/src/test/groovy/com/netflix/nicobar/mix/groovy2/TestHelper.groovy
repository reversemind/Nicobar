package com.netflix.nicobar.mix.groovy2

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 *
 */
class TestHelper {

    def static delete(Path rootPath) {

        if (rootPath == null) {
            return
        }

        if (!rootPath.toFile().exists()) {
            return
        }

        Files.walkFileTree(rootPath.toAbsolutePath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.delete(directory)
                return FileVisitResult.CONTINUE
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
