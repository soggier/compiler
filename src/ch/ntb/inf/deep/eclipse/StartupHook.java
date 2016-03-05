package ch.ntb.inf.deep.eclipse;

import org.eclipse.ui.IStartup;

/**
 * Stub hook to get the plugin started early for installation of resource listeners
 * 
 * @author soggier
 */
public class StartupHook implements IStartup {
	
	/*
	 * Runs after plugin has been started.
	 * If possible, this method shall not do anything.
	 * 
	 * (non-Javadoc)
	 * @see org.eclipse.ui.IStartup#earlyStartup()
	 */
	@Override
	public void earlyStartup() {
	}
}
