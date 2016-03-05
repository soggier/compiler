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

package ch.ntb.inf.deep.eclipse.launcher;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.osgi.framework.Bundle;

import ch.ntb.inf.deep.config.Configuration;
import ch.ntb.inf.deep.config.Programmer;
import ch.ntb.inf.deep.eclipse.DeepPlugin;
import ch.ntb.inf.deep.eclipse.ui.view.ConsoleDisplayMgr;
import ch.ntb.inf.deep.host.ErrorReporter;
import ch.ntb.inf.deep.host.StdStreams;
import ch.ntb.inf.deep.launcher.Launcher;
import ch.ntb.inf.deep.strings.HString;
import ch.ntb.inf.deep.target.TargetConnection;


public class DeepLaunchDelegate extends JavaLaunchDelegate{


	@Override
	public final void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)throws CoreException{
		if(!mode.equals(ILaunchManager.RUN_MODE)){
			return;
		}

		//terminate all other DebugTargets
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		ILaunch[] launches =lm.getLaunches();
		for(int i = 0; i < launches.length; i++){
			if(launches[i].getDebugTarget() != null){
				launches[i].getDebugTarget().terminate();
			}
		}

		ConsoleDisplayMgr cdm = ConsoleDisplayMgr.getDefault();

		String targetConfig = configuration.getAttribute(DeepPlugin.ATTR_TARGET_CONFIG, "");
		String location = configuration.getAttribute(DeepPlugin.ATTR_DEEP_LOCATION, "");
		String program = configuration.getAttribute(DeepPlugin.ATTR_DEEP_PROGRAM, "");

		monitor.beginTask("Download Target Image", 100);

		//clear Console
		if (cdm != null) cdm.clear();
	
		monitor.worked(10);
		if(monitor.isCanceled()) {
			monitor.done();
			return;
		}

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IResource projectResource = workspace.getRoot().findMember(location);
		if (projectResource == null)
			throw new RuntimeException("Failed to resolve project '" + location + "' in '" + workspace + "'");

		IProject project = projectResource.getProject();
		if (project == null)
			throw new RuntimeException("not a project:" + projectResource.getFullPath());
		
		File[] extraClasspathEntries = null;

		//get classpath entries from project and its dependencies
		Set<IProject> visitedProjects = new HashSet<IProject>();
		Set<File> classpathEntries = new LinkedHashSet<File>();
		resolveClasspath(visitedProjects, classpathEntries, workspace, project);
		extraClasspathEntries = classpathEntries.toArray(new File[classpathEntries.size()]);

		Launcher.buildAll(project.getFile(program).getRawLocation().toString(), targetConfig, extraClasspathEntries);

		monitor.worked(50);
		if (monitor.isCanceled()) {
			monitor.done();
			return;
		}
		
		if(ErrorReporter.reporter.nofErrors == 0 ) {
			monitor.worked(60);
			Programmer programmer = Configuration.getProgrammer();
			if (programmer != null) {
				if (programmer.getPluginId().equals(HString.getHString("ch.ntb.inf.bdi2000"))) {
					// ignore
				}
				else {
					java.lang.Class<?> cls;
					try {
						Bundle bundle = Platform.getBundle(programmer.getPluginId().toString());
						if (bundle != null) {
	//						System.out.println(bundle.getSymbolicName() + " 1");
							cls = bundle.loadClass(programmer.getClassName().toString());
	//						System.out.println(cls.getName() + " 2");
							//					cls = java.lang.Class.forName(programmer.getClassName().toString());
							java.lang.reflect.Method m;
							m = cls.getDeclaredMethod("getInstance");
							TargetConnection tc = (TargetConnection) m.invoke(cls);
							Launcher.setTargetConnection(tc);
							Launcher.openTargetConnection();
							Launcher.downloadTargetImage();
							Launcher.startTarget();
						} else ErrorReporter.reporter.error(812, programmer.getClassName().toString());
					} catch (ClassNotFoundException e) {
						ErrorReporter.reporter.error(811, programmer.getClassName().toString());
					} catch (SecurityException e) {
						e.printStackTrace();
					} catch (NoSuchMethodException e) {
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {
						e.printStackTrace();
					}
				}
			}
		}
		monitor.worked(100);
		monitor.done();

		IDebugTarget target = new DummyDebugTarget(launch);
		launch.addDebugTarget(target);
		target.terminate();
	}

	/**
	 * Resolves classpath
	 * 
	 * TODO refactoring: move to another class
	 * 
	 * @param classpathEntries
	 * @param workspace
	 * @param javaProject
	 * @throws JavaModelException
	 */
	private static void resolveClasspath(Set<IProject> visitedProjects, Set<File> classpathEntries,
			IWorkspace workspace, IProject project) throws JavaModelException {

		if (!visitedProjects.add(project)) {
			// TODO verify .equals() implementation
			StdStreams.err.println("dependency cycle detected, involved project: " + project.getName());
			return;
		}

		IJavaProject javaProject = JavaCore.create(project);
		if (javaProject == null)
			return;

		IResource outputLocationResource = workspace.getRoot().findMember(javaProject.getOutputLocation());
		classpathEntries.add(outputLocationResource.getRawLocation().toFile());

		for (IClasspathEntry classpathEntry : javaProject.getResolvedClasspath(true)) {
			switch (classpathEntry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
			case IClasspathEntry.CPE_VARIABLE:
				IClasspathEntry resolvedClasspathEntry = JavaCore.getResolvedClasspathEntry(classpathEntry);
				IResource dependencyClasspathResource = workspace.getRoot()
						.findMember(resolvedClasspathEntry.getPath());
				if (dependencyClasspathResource == null)
					break;

				classpathEntries.add(dependencyClasspathResource.getRawLocation().toFile());

				break;
			case IClasspathEntry.CPE_PROJECT:
				IResource dependencyProjectResource = workspace.getRoot().findMember(classpathEntry.getPath());
				if (dependencyProjectResource == null)
					break;
				IProject dependencyProject = dependencyProjectResource.getProject();
				if (dependencyProject == null)
					break;

				resolveClasspath(visitedProjects, classpathEntries, workspace, dependencyProject);
				break;
			default:
			}
		}
	}
}
