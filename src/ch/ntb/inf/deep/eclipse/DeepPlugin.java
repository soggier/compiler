/*
 * Copyright 2011 - 2013 NTB University of Applied Sciences in Technology
 * Buchs, Switzerland, http://www.ntb.ch/inf
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package ch.ntb.inf.deep.eclipse;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import ch.ntb.inf.deep.eclipse.nature.DeepNature;
import ch.ntb.inf.deep.eclipse.ui.preferences.PreferenceConstants;
import ch.ntb.inf.deep.eclipse.ui.properties.DeepFileChanger;
import ch.ntb.inf.deep.eclipse.ui.view.ConsoleDisplayMgr;
import ch.ntb.inf.deep.host.StdStreams;

public class DeepPlugin extends AbstractUIPlugin {
	private static final boolean DEBUG  = false;

	//The shared instance.
	private static DeepPlugin plugin;
	//Resource bundle.
	private ResourceBundle resourceBundle;
	/**
	 *  default entries for deep projects settings
	 */
	public static final String LIB_PATH = "I:\\deep\\lib";
	public static final String BOARD = "ntbMpc555HB";
	public static final String OS = "ntbSTS";
	public static final String PROGRAMMER = "ntbMpc555UsbBdi";
	
	/**
	 * Unique identifier for the deep model (value 
	 * <code>deep.Model</code>).
	 */
	public static final String ID_DEEP_MODEL = "deep.Model";
	
	/**
	 * Launch configuration attribute key. Value is a path to a java
	 * program. The path is a string representing a full path
	 * to the top class in the workspace. 
	 */
	public static final String ATTR_DEEP_PROGRAM = ID_DEEP_MODEL + ".ATTR_DEEP_PROGRAM";
	/**
	 * Launch configuration attribute key. Value is a path to a java
	 * project. The path is a string representing workspace relative path. 
	 */
	public static final String ATTR_DEEP_LOCATION = ID_DEEP_MODEL + ".ATTR_DEEP_LOCATION";
	/**
	 * Launch configuration attribute key. Value is a path to a target configuration.
	 * The path is a string representing a full path to a target image in the workspace. 
	 */
	public static final String ATTR_TARGET_CONFIG = ID_DEEP_MODEL + ".ATTR_TARGET_CONFIG";
	
	/**
	 * Identifier for the deep launch configuration type
	 * (value <code>deep.launchType</code>)
	 */
	public static final String ID_DEEP_LAUNCH_CONFIGURATION_TYPE = "deep.launchType";	
	
	/** 
	 * The relative path to the images directory. 
	 */
	private static final String IMAGES_PATH = "icons/";
	
	/**
	 * Plug-in identifier.
	 */
	public static final String PLUGIN_ID = "ch.ntb.inf.deep";
	
	/**
	 * The constructor.
	 */
	public DeepPlugin() {
		super();
		plugin = this;
		ConsoleDisplayMgr cdm = ConsoleDisplayMgr.getDefault();
		//Init Console
		if(cdm != null){
			cdm.clear();
			PrintStream log = new PrintStream(cdm.getNewIOConsoleOutputStream(ConsoleDisplayMgr.MSG_INFORMATION));
			PrintStream err = new PrintStream(cdm.getNewIOConsoleOutputStream(ConsoleDisplayMgr.MSG_ERROR));
			PrintStream vrb = new PrintStream(cdm.getNewIOConsoleOutputStream(ConsoleDisplayMgr.MSG_VERBOSE));
			StdStreams.vrb = vrb;
			StdStreams.log = log;
			StdStreams.err = err;
		}
	}

	/**
	 * This method is called upon plug-in activation
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);

		// install a resource listener in order to pick up changes in deep files
		// automatically
		ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener);
	}

	/**
	 * This method is called when the plug-in is stopped
	 */
	public void stop(BundleContext context) throws Exception {
		super.stop(context);

		plugin = null;
		resourceBundle = null;

		ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
	}

	/**
	 * Returns the shared instance.
	 */
	public static DeepPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns the string from the plugin's resource bundle,
	 * or 'key' if not found.
	 */
	public static String getResourceString(String key) {
		ResourceBundle bundle = DeepPlugin.getDefault().getResourceBundle();
		try {
			return (bundle != null) ? bundle.getString(key) : key;
		} catch (MissingResourceException e) {
			return key;
		}
	}

	/**
	 * Returns the plugin's resource bundle,
	 */
	public ResourceBundle getResourceBundle() {
		try {
			if (resourceBundle == null)
				resourceBundle = ResourceBundle.getBundle("ch.ntb.inf.debug.core.mpc555.DebugCorePluginResources");
		} catch (MissingResourceException x) {
			resourceBundle = null;
		}
		return resourceBundle;
	}

	/**
	 * Return a <code>java.io.File</code> object that corresponds to the specified
	 * <code>IPath</code> in the plugin directory, or <code>null</code> if none.
	 * Return a <code>java.io.File</code> object that corresponds to the
	 */
	public static File getFileInPlugin(String relPath) {
		try {
			final Bundle pluginBundle =  Platform.getBundle(DeepPlugin.PLUGIN_ID);
			
			final Path filePath = new Path(relPath);
			
			final URL fileUrl = FileLocator.find(pluginBundle, filePath, null);

			URL localURL = FileLocator.toFileURL(fileUrl);
			
			return new File(localURL.getFile());
		} catch (IOException ioe) {
			return null;
		}
	}
	
	public static Image createImage(String imagePath)
    {
         final Bundle pluginBundle =
              Platform.getBundle(DeepPlugin.PLUGIN_ID);

         final Path imageFilePath =
              new Path(DeepPlugin.IMAGES_PATH + imagePath);

         final URL imageFileUrl =
              FileLocator.find(pluginBundle, imageFilePath, null);

         return
              ImageDescriptor.createFromURL(imageFileUrl).createImage();
    }

	private static final String DEEP_CLASSPATH_KEY_ATTR = "deepPlatformLibrary";

	public static void updateClasspath(IProject project, String libpathStr, IProgressMonitor monitor)
			throws JavaModelException {
		if (libpathStr == null)
			libpathStr = getDefault().getPreferenceStore().getString(PreferenceConstants.DEFAULT_LIBRARY_PATH);

		IPath libPath = Path.fromOSString(libpathStr);
		IPath binPath = libPath.append("bin");
		IPath srcPath = libPath.append("src");

		IJavaProject javaProject = JavaCore.create(project);
		if (javaProject != null) {
			List<IClasspathEntry> projectClassPath = new ArrayList<IClasspathEntry>(
					Arrays.asList(javaProject.getRawClasspath()));
			for (Iterator<IClasspathEntry> classpathIt = projectClassPath.iterator(); classpathIt.hasNext();) {
				IClasspathEntry existingClasspathEntry = classpathIt.next();

				boolean matches = false;

				matches = binPath.equals(existingClasspathEntry.getPath());
				if (!matches)
					for (IClasspathAttribute attr : existingClasspathEntry.getExtraAttributes()) {
						if (DEEP_CLASSPATH_KEY_ATTR.equals(attr.getName())) {
							matches = true;
							break;
						}
					}

				if (matches)
					classpathIt.remove();// remove duplicate entries
			}
			IClasspathEntry classpathEntry = JavaCore.newLibraryEntry(binPath, // binaries
					srcPath, // sources
					null, // source root
					new IAccessRule[] {},
					new IClasspathAttribute[] { JavaCore.newClasspathAttribute(DEEP_CLASSPATH_KEY_ATTR, null) }, false);
			projectClassPath.add(classpathEntry);
			javaProject.setRawClasspath(projectClassPath.toArray(new IClasspathEntry[projectClassPath.size()]), null);
		} else {
			StdStreams.err.println("Failed to modify classpath of project: " + project.getName());
		}
	}

	private IResourceChangeListener resourceChangeListener = new IResourceChangeListener() {
		public void resourceChanged(final IResourceChangeEvent event) {
			final IResourceDelta changes = event.getDelta();
			if (changes == null)
				return;

			if(DEBUG)
				try {
					final String type;
					final String buildKind;
					
					switch (event.getType()) {
					case IResourceChangeEvent.POST_CHANGE:
						type = "POST_CHANGE";
						break;
					case IResourceChangeEvent.POST_BUILD:
						type = "POST_BUILD";
						break;
					case IResourceChangeEvent.PRE_BUILD:
						type = "PRE_BUILD";
						break;
					case IResourceChangeEvent.PRE_CLOSE:
						type = "PRE_CLOSE";
						break;
					case IResourceChangeEvent.PRE_DELETE:
						type = "PRE_DELETE";
						break;
					case IResourceChangeEvent.PRE_REFRESH:
						type = "PRE_REFRESH";
						break;
					default:
						type = "unknown(" + event.getType() + ")";
						break;
					}

					switch (event.getBuildKind()) {
					case IncrementalProjectBuilder.AUTO_BUILD:
						buildKind = "AUTO_BUILD";
						break;
					case IncrementalProjectBuilder.FULL_BUILD:
						buildKind = "FULL_BUILD";
						break;
					case IncrementalProjectBuilder.INCREMENTAL_BUILD:
						buildKind = "INCREMENTAL_BUILD";
						break;
					case IncrementalProjectBuilder.CLEAN_BUILD:
						buildKind = "CLEAN_BUILD";
						break;
					default:
						buildKind = "unknown(" + event.getBuildKind() + ")";
						break;
					}
	
					changes.accept(new IResourceDeltaVisitor() {
						@Override
						public boolean visit(IResourceDelta delta) throws CoreException {
							final int flags = delta.getFlags();

							List<String> flagNames = new ArrayList<String>();
							String kind;

							switch (delta.getKind()) {
							case IResourceDelta.ADDED:
								kind = "ADDED";
								break;
							case IResourceDelta.REMOVED:
								kind = "REMOVED";
								break;
							case IResourceDelta.CHANGED:
								kind = "CHANGED";
								break;
							case IResourceDelta.ADDED_PHANTOM:
								kind = "ADDED_PHANTOM";
								break;
							case IResourceDelta.REMOVED_PHANTOM:
								kind = "REMOVED_PHANTOM";
								break;
							default:
								kind = "unknown(" + changes.getKind() + ")";
								break;
							}
	
							if ((flags & IResourceDelta.CONTENT) != 0)
								flagNames.add("CONTENT");
							if ((flags & IResourceDelta.DERIVED_CHANGED) != 0)
								flagNames.add("DERIVED_CHANGED");
							if ((flags & IResourceDelta.DESCRIPTION) != 0)
								flagNames.add("DESCRIPTION");
							if ((flags & IResourceDelta.ENCODING) != 0)
								flagNames.add("ENCODING");
							if ((flags & IResourceDelta.LOCAL_CHANGED) != 0)
								flagNames.add("LOCAL_CHANGED");
							if ((flags & IResourceDelta.OPEN) != 0)
								flagNames.add("OPEN");
							if ((flags & IResourceDelta.MOVED_TO) != 0)
								flagNames.add("MOVED_TO");
							if ((flags & IResourceDelta.MOVED_FROM) != 0)
								flagNames.add("MOVED_FROM");
							if ((flags & IResourceDelta.COPIED_FROM) != 0)
								flagNames.add("COPIED_FROM");
							if ((flags & IResourceDelta.TYPE) != 0)
								flagNames.add("TYPE");
							if ((flags & IResourceDelta.SYNC) != 0)
								flagNames.add("SYNC");
							if ((flags & IResourceDelta.MARKERS) != 0)
								flagNames.add("MARKERS");
							if ((flags & IResourceDelta.REPLACED) != 0)
								flagNames.add("REPLACED");
	
							StdStreams.err.println("entry " + buildKind + " / " + type + " / " + flagNames + " / " + kind
									+ ": " + delta.getFullPath());
							return true;
						}
					});
				} catch (CoreException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

			try {
				final Set<IResource> deepFiles = new LinkedHashSet<IResource>();

				changes.accept(new IResourceDeltaVisitor() {
					@Override
					public boolean visit(IResourceDelta delta) throws CoreException {
						try {
							IResource resource = delta.getResource();
							if (resource != null) {
								switch (resource.getType()) {
								case IResource.PROJECT:
									if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
										IProject project = resource.getProject();
										if(project == null)
											return false;
										IProjectNature projectNature = project.getNature(DeepNature.NATURE_ID);
										if(projectNature == null)
											return false;

										IFile deepFile = resource.getProject().getFile(project.getName() + ".deep");
										if (deepFile.exists()) {
											deepFiles.add(deepFile);
											StdStreams.vrb.println(
													"updating from default deep file: " + resource.getRawLocation());
										}
									}
									return true;
								case IResource.FILE:
									if ((delta.getFlags() & IResourceDelta.CONTENT) != 0) {
										if ("deep".equalsIgnoreCase(resource.getFileExtension())) {

											IProject project = resource.getProject();
											if (project == null)
												return false;
											IProjectNature projectNature = project.getNature(DeepNature.NATURE_ID);
											if (projectNature == null)
												return false;

											deepFiles.add(resource);
											StdStreams.vrb
													.println("updating configuration using deep file: " + resource.getRawLocation());
										}
									}
									return false;
								}
							}
						} catch (Exception e) {
							// TODO proper exception handling in
							// DeepFileChanger, etc.
							e.printStackTrace();
							return false;
						}
						return true;
					}
				});

				for (IResource deepFile : deepFiles) {

					DeepConfigurationJob job = new DeepConfigurationJob(deepFile);
					job.setPriority(Job.BUILD);
					job.schedule();
				}

			} catch (Exception e) {
				// TODO proper exception handling
				e.printStackTrace();
			}
		}
	};

	private static class DeepConfigurationJob extends Job {
		private IResource resource;

		public DeepConfigurationJob(IResource resource) {
			super("Deep project configuration");
			this.resource = resource;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			IPath rawLocation = resource.getRawLocation();
			if (rawLocation != null) {
				try {
					DeepFileChanger dfc = new DeepFileChanger(rawLocation.toString());
					String libpathStr = dfc.getContent("libpath");
					String libpath;

					if (libpathStr != null && libpathStr.length() > 2)
						libpath = libpathStr.substring(1, libpathStr.length() - 1);
					else
						libpath = null;

					IProject project = resource.getProject();
					if (project != null) {
						updateClasspath(project, libpath, monitor);

						try {
							project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
						} catch (CoreException e) {
							e.printStackTrace();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return Status.OK_STATUS;
		}
	}
}
