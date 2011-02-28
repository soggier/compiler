package ch.ntb.inf.deep.eclipse.ui.view;

import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.IOConsoleOutputStream;
 
/**
 * Create an instance of this class in any of your plugin classes.
 * 
 * Use it as follows ...
 * 
 * ConsoleDisplayMgr.getDefault().println("Some error msg", ConsoleDisplayMgr.MSG_ERROR);
 * ...
 * ...
 * ConsoleDisplayMgr.getDefault().clear();
 * ...  
 */
public class ConsoleDisplayMgr
{
	private static ConsoleDisplayMgr fDefault = null;
	private String fTitle = null;
	private IOConsole fIOConsole = null;
	
	public static final int MSG_INFORMATION = 1;
	public static final int MSG_ERROR = 2;
	public static final int MSG_WARNING = 3;
		
	private ConsoleDisplayMgr(String messageTitle)
	{		
		fDefault = this;
		fTitle = messageTitle;
	}
	
	public static ConsoleDisplayMgr getDefault() {
		if(fDefault == null) new ConsoleDisplayMgr("Deep-build");
		return fDefault;
	}	
		
	public void println(String msg, int msgKind)
	{		
		if( msg == null ) return;
		
		/* if console-view in Java-perspective is not active, then show it and
		 * then display the message in the console attached to it */		
		if( !displayConsoleView() )
		{
			/*If an exception occurs while displaying in the console, then just diplay atleast the same in a message-box */
			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error", msg);
			return;
		}
		
		/* display message on console */	
		try {
			getNewIOConsoleOutputStream(msgKind).write(msg.getBytes());
		} catch (IOException e) {
		}				
	}
	
//	public void println()
//	{		
//		/* if console-view in Java-perspective is not active, then show it and
//		 * then display the message in the console attached to it */		
//		if( !displayConsoleView() )
//		{
//			/*If an exception occurs while displaying in the console, then just diplay atleast the same in a message-box */
//			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error", "");
//			return;
//		}
//		
//		/* display message on console */	
//		getNewMessageConsoleStream(MSG_INFORMATION).println();				
//	}
//	
//	public void print(String msg, int msgKind)
//	{		
//		if( msg == null ) return;
//		
//		/* if console-view in Java-perspective is not active, then show it and
//		 * then display the message in the console attached to it */		
//		if( !displayConsoleView() )
//		{
//			/*If an exception occurs while displaying in the console, then just diplay atleast the same in a message-box */
//			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error", msg);
//			return;
//		}
//		
//		/* display message on console */	
//		getNewMessageConsoleStream(msgKind).print(msg);				
//	}
//	public void print(int msg, int msgKind)
//	{		
//			/* if console-view in Java-perspective is not active, then show it and
//		 * then display the message in the console attached to it */		
//		if( !displayConsoleView() )
//		{
//			/*If an exception occurs while displaying in the console, then just diplay atleast the same in a message-box */
//			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Error",Integer.toString(msg));
//			return;
//		}
//		
//		/* display message on console */	
//		getNewMessageConsoleStream(msgKind).print(Integer.toString(msg));				
//	}
//	
	public void clear()
	{		
		if(fIOConsole != null){
			fIOConsole.clearConsole();
		}
	}	
		
	private boolean displayConsoleView(){
		try
		{
			IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if( activeWorkbenchWindow != null )
			{
				IWorkbenchPage activePage = activeWorkbenchWindow.getActivePage();
				if( activePage != null ){
					IConsoleView view =(IConsoleView) activePage.showView(IConsoleConstants.ID_CONSOLE_VIEW, null, IWorkbenchPage.VIEW_VISIBLE);
					view.display(fIOConsole);
				}
			}
			
		} catch (PartInitException partEx) {			
			return false;
		}
		
		return true;
	}
	
	public IOConsoleOutputStream getNewIOConsoleOutputStream(int msgKind)
	{		
		int swtColorId = SWT.COLOR_DARK_GREEN;
		
		switch (msgKind)
		{
			case MSG_INFORMATION:
				swtColorId = SWT.COLOR_BLACK;				
				break;
			case MSG_ERROR:
				swtColorId = SWT.COLOR_RED;
				break;
			case MSG_WARNING:
				swtColorId = SWT.COLOR_DARK_YELLOW;
				break;
			default:				
		}	
		
		IOConsoleOutputStream ioConsoleStream = getIOConsole().newOutputStream();		
		//ioConsoleStream.setColor(PlatformUI.getWorkbench().getDisplay().getSystemColor(swtColorId)); //TODO solve problem with Nullpointer when started from non-UI-Thread
		return ioConsoleStream;
	}
	
	private IOConsole getIOConsole()
	{
		if( fIOConsole == null )
			createIOConsoleStream(fTitle);	
		
		return fIOConsole;
	}
		
	private void createIOConsoleStream(String title)
	{
		fIOConsole = new IOConsole(title, null); 
		ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{ fIOConsole });
		displayConsoleView();
	}	
}
 