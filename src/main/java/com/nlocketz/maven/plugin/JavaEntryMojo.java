package com.nlocketz.maven.plugin;

import edu.emory.mathcs.backport.java.util.Arrays;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

@Mojo(name = "entries", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.RUNTIME)
/**
 * @author pitb0ss
 */
public class JavaEntryMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.build.directory}/entry-points.txt", property = "outputFile", required = false)
	private String outputFilePath;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@SuppressWarnings("unchecked")
	public void execute() throws MojoExecutionException {
		MainClassSearchVisitor.setLog(getLog());
		Path toOutput = Paths.get(outputFilePath);
		prepOutput(toOutput);

		Set<String> classesWithMainMethods = new HashSet<String>();
		try {
			List<String> testClasspath = (List<String>) project.getCompileClasspathElements();

			// Use the system class loader so we don't accidently pollute the
			// results.
			ClassLoader projectClasses = new URLClassLoader(asURLS(testClasspath), ClassLoader.getSystemClassLoader());

			for (String cpEntry : testClasspath) {

				getLog().debug("Walking classpath entry " + cpEntry);
				Files.walkFileTree(Paths.get(cpEntry),
						new MainClassSearchVisitor(classesWithMainMethods, projectClasses));

			}
			getLog().debug("Completed walk of classpath.");

			BufferedWriter bw = Files.newBufferedWriter(toOutput, StandardOpenOption.TRUNCATE_EXISTING);

			getLog().info("Located classes with main methods:");
			List<String> classesAsList = Arrays.asList(classesWithMainMethods.toArray());
			Collections.sort(classesAsList);
			for (String c : classesAsList) {
				getLog().info("  - " + c);
				bw.write(c);
				bw.write("\n");
			}
			bw.flush();
			bw.close();
		} catch (DependencyResolutionRequiredException | IOException e) {
			getLog().error("Failed to walk classpath or write entry point file.", e);
			throw new MojoExecutionException("Couldn't walk classpath, see log.");
		}

	}

	private URL[] asURLS(List<String> testClasspath) throws MojoExecutionException {
		try {
			URL[] urls = new URL[testClasspath.size()];
			for (int i = 0; i < urls.length; i++) {
				urls[i] = new File(testClasspath.get(i)).toURI().toURL();
			}
			return urls;
		} catch (MalformedURLException e) {
			getLog().error("Failed to convert file to url", e);
			throw new MojoExecutionException("Couldn't convert a file to a url, see log.");
		}
	}

	private void prepOutput(Path output) throws MojoExecutionException {
		try {
			Files.createDirectories(output.getParent());
		} catch (FileAlreadyExistsException e) {
			// Nothing to do here..
		} catch (IOException e) {
			getLog().error("Failed to reset output file", e);
			throw new MojoExecutionException("Failed to clear output file, see log.");
		}
		try {
			Files.createFile(output);
		} catch (FileAlreadyExistsException e) {
			// Nothing to do here..
		} catch (IOException e) {
			getLog().error("Failed to reset output file", e);
			throw new MojoExecutionException("Failed to clear output file, see log.");
		}
	}
}
