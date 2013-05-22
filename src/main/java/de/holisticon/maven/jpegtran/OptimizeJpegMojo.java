/**
 * Copyright 2013 Oliver Ochs
 * based on a project copyright 2011 Niklas Schmidtmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.holisticon.maven.jpegtran;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/**
 * Goal which optimizes PNG images.
 *
 * @goal optimize
 * @phase compile
 */
public class OptimizeJpegMojo extends AbstractMojo {
    /**
     * File extension of PNG images.
     */
    private static final String JPG_SUFFIX = ".jpg";

    /**
     * jpegtran executable.
     */
    private static final String JPEGTRAN_EXE = "jpegtran";

    /**
     * jpegtran parameter specifying compression level.
     */
    private static final String JPEGTRAN_COMPRESSION_LEVEL_PARAM = "-o";

    /**
     * Timeout in seconds for processes to terminate.
     */
    private static final int POOL_TIMEOUT = 10;

    /**
     * Lower bound for optimization level passed to jpegtran.
     */
    private static final int LEVEL_LOWER_BOUND = 0;

    /**
     * Upper bound for optimization level passed to jpegtran.
     */
    private static final int LEVEL_UPPER_BOUND = 7;

    /**
     * List of directories to consider.
     *
     * @parameter
     * @required
     */
    private List<String> pngDirectories;

    /**
     * Specifies the intensity of compression.
     *
     * @parameter default-value=2
     */
    private int level;

    /**
     * Thread pool containing processes which optimize a single image.
     */
    private ExecutorService pool;

    /**
     * Creates a new instance of this plugin.
     */
    public OptimizeJpegMojo() {
        pool = Executors.newCachedThreadPool();
    }

    /**
     * A filename filter for PNG files.
     */
    private static class JpegFilenameFilter implements FilenameFilter {
        @Override
        public boolean accept(final File dir, final String name) {
            return name.endsWith(JPG_SUFFIX);
        }
    }

    /**
     * Executes the mojo.
     *
     * @throws MojoExecutionException if execution failed
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (!verifyjpegtranInstallation()) {
            throw new MojoExecutionException("Could not find jpegtran on "
                + "this system");
        }

        if (!verifyLevel()) {
            throw new MojoExecutionException(String.format(
                "Invalid level. Must be >= %d and <= %d", LEVEL_LOWER_BOUND,
                LEVEL_UPPER_BOUND));
        }

        int numberImages = 0;
        for (final String directory : pngDirectories) {
            File d = new File(directory);
            if (!d.exists()) {
                throw new MojoExecutionException(String.format(
                    "Directory %s does not exist.", directory));
            }

            if (!d.isDirectory()) {
                throw new MojoExecutionException(String.format(
                    "The path %s is not a directory.", directory));
            }

            File[] containedImages = d.listFiles(new JpegFilenameFilter());

            numberImages += containedImages.length;
            for (File image : containedImages) {
                getLog().debug("Optimzing " + image);
                pool.submit(new OptimizeTask(image, getLog()));
            }
        }

        pool.shutdown();
        try {
            pool.awaitTermination(calculateTimeout(numberImages), TimeUnit.
                SECONDS);
        } catch (InterruptedException e) {
            throw new MojoExecutionException(
                "Waiting for process termination was interrupted.", e);
        }
    }

    /**
     * A task which optimizes a single image.
     */
    private class OptimizeTask implements Runnable {
        /**
         * Image to optimize.
         */
        private File image;

        /**
         * Reference to maven logger object for printing the result of the
         * optimization.
         */
        private Log log;

        /**
         * Creates a new optimization task.
         *
         * @param image image to optimize
         * @param log logger to print optimization result to
         */
        public OptimizeTask(final File image, final Log log) {
            this.image = image;
            this.log = log;
        }

        /**
         * Runs the actual optimization.
         */
        @Override
        public void run() {
            Process p = null;

            long sizeUnoptimized = image.length();
            try {
                p = startProcess(image);
            } catch (IOException e) {
                log.error("Failed to start a process.", e);
                return;
            }

            try {
                p.waitFor();
            } catch (InterruptedException e) {
                log.error("Failed to wait for the process to finish.", e);
                return;
            }

            float kbOptimized = (sizeUnoptimized - (long) image.length())
                / 1024f;
            float percentageOptimized = kbOptimized / (sizeUnoptimized / 1024f)
                * 100;

            log.info(String.format("Optimized %s by %.2f kb (%.2f%%)",
                image.getPath(), kbOptimized, percentageOptimized));
        }
    }

    /**
     * Builds a jpegtran call and spawns a new process.
     *
     * @param image image to optimize
     * @return spawned process
     * @throws IOException in case building the process failed
     */
    private Process startProcess(final File image) throws IOException {
        final StringBuilder args = new StringBuilder();
        args.append(JPEGTRAN_COMPRESSION_LEVEL_PARAM).append(" ")
            .append(String.valueOf(level)).append(" ")
            .append(image.getPath());
        return new ProcessBuilder(JPEGTRAN_EXE, args.toString()).start();
    }

    /**
     * Calculate a proper timeout threshold given the number of images to
     * compress and the compression level.
     *
     * @param numberImages number of images to compress
     * @return timeout in seconds
     */
    private int calculateTimeout(final int numberImages) {
        return numberImages * POOL_TIMEOUT + numberImages * level * 5;
    }

    /**
     * Verifies whether jpegtran is installed.
     *
     * @return <code>true</code> if installed, <code>false</code> otherwise
     * @throws MojoExecutionException in case building the process failed
     */
    private static boolean verifyjpegtranInstallation() throws
            MojoExecutionException {
        List<String> args = new LinkedList<String>();
        args.add(JPEGTRAN_EXE);

        Process p;
        try {
            p = new ProcessBuilder(args).start();
            p.waitFor();
        } catch (IOException e) {
            throw new MojoExecutionException(
                "Failed to verify jpegtran installation", e);
        } catch (InterruptedException e) {
            throw new MojoExecutionException(
                "Failed to verify jpegtran installation", e);
        }

        return p.exitValue() == 0;
    }

    /**
     * Verifies whether the provided level is within legal bounds.
     *
     * @return <code>true</code> if legal, <code>false</code> otherwise
     */
    private boolean verifyLevel() {
        return level >= LEVEL_LOWER_BOUND && level <= LEVEL_UPPER_BOUND;
    }
}

