package com.nlocketz.maven.plugin;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;

public class MainClassSearchVisitor implements FileVisitor<Path> {

	private static Log log;

	// System path separator which can be used in a regular expression pattern
	private final String pathSep;
	private Set<String> mainsFound;
	private ClassLoader projectClasses;
	private Path base;
	private String includeFilterString;
	private PathMatcher includeFilter;

	MainClassSearchVisitor(Set<String> mainsFound, ClassLoader projectClasses, FileSystem fs, String includeFilter) {
		this.mainsFound = mainsFound;
		this.projectClasses = projectClasses;
		this.includeFilterString = includeFilter;
		this.includeFilter = fs.getPathMatcher(includeFilter.replace(".", fs.getSeparator()));
		this.pathSep = fs.getSeparator();
	}

	MainClassSearchVisitor(Set<String> mainsFound, ClassLoader projectClasses, String includeFilter) {
		this(mainsFound, projectClasses, FileSystems.getDefault(), includeFilter);
	}

	private MainClassSearchVisitor(MainClassSearchVisitor other, Path newBase, FileSystem fs, String includeFilter) {
		mainsFound = other.mainsFound;
		projectClasses = other.projectClasses;
		base = newBase;
		this.includeFilterString = includeFilter;
		this.includeFilter = fs.getPathMatcher(includeFilter.replace(".", fs.getSeparator()));
		this.pathSep = fs.getSeparator();
	}

	public FileVisitResult postVisitDirectory(Path p, IOException e) throws IOException {
		// Don't care about directories
		return FileVisitResult.CONTINUE;
	}

	public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes attr) throws IOException {
		// We want to save the top level directory
		// so we can synthesize a package path for classes
		if (base == null) {
			base = p;
		}
		return FileVisitResult.CONTINUE;
	}

	public FileVisitResult visitFile(Path p, BasicFileAttributes attr) throws IOException {
		try {
			String curFileName = p.getFileName().toString();
			getLog().debug("Checking: " + p.toString());
			if (curFileName.endsWith(".class")) {
				if (!includeFilter.matches(base.relativize(p))) {
					return FileVisitResult.CONTINUE;
				}
				String className = toClassName(p);
				Class<?> c = projectClasses.loadClass(className);
				c.getMethod("main", String[].class);
				getLog().debug("Found a class with a main method: " + className);
				// If we made it here we found one!
				mainsFound.add(className);
			} else if (curFileName.endsWith(".jar")) {
				getLog().debug("Found jar: " + p.toString());
				// So the default file system provider recognizes "jar" scheme
				// and will allow us to work with a jar file as
				// though it were a normal file system.

				FileSystem jarFS = FileSystems.newFileSystem(URI.create("jar:" + p.toUri().toString()),
						Collections.<String, String>emptyMap());
				for (Path jarsRoot : jarFS.getRootDirectories()) {
					getLog().debug("Searching jar root: " + jarsRoot.toString());
					Files.walkFileTree(jarsRoot, new MainClassSearchVisitor(this, jarsRoot, jarFS, includeFilterString));
				}

			}
		} catch (ClassNotFoundException | SecurityException e) {
			throw new IOException("Couldn't reflect on class " + p.toString(), e);
			// NoClassDefFoundError ignores ClassLoader errors stemming from OSGi dependencies
		} catch (NoSuchMethodException | NoClassDefFoundError e) {
			// Nothing wrong here, just didn't find a main method
		}
		return FileVisitResult.CONTINUE;
	}

	public FileVisitResult visitFileFailed(Path p, IOException e) throws IOException {
		getLog().error("Visit to path failed: " + p.toString(), e);
		throw e;
	}

	private String toClassName(Path classFile) {
		String rel = base.relativize(classFile).toString();
		rel = rel.replaceAll(pathSep, "."); // Path separators to .'s
		rel = rel.substring(0, rel.length() - ".class".length()); // Remove file extension
		getLog().debug(String.format("toClassName: from %s to %s", classFile.toString(), rel));
		return rel;
	}

	static void setLog(Log log) {
		MainClassSearchVisitor.log = log;
	}

	private static Log getLog() {
		if (log == null) {
			throw new IllegalStateException("Log should have been set up...");
		}
		return log;
	}
}
