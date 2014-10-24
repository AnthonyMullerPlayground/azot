/**
 * Copyright (C) 2013 Anthony MÜLLER.
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
 */ 
package com.sap.azot.ant;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.tools.ant.Task;

import com.sap.azot.AzotException;
import com.sap.azot.Launcher;


/**
 * Azot Ant task
 *
 * @author amuller
 */
public class AzotTask extends Task {

	private final ThreadGroup antWorkflowStarterGroup = new ThreadGroup("AntWorkflowStarterGroup");
	
	private boolean verbose = false;
	private boolean debug = false;
	private boolean dump = false;
	private boolean concurrent = false;
	private boolean report = false;
	private boolean analyze = false;

	/** Variables */
	private final List<VariableDef> variables = new ArrayList<VariableDef>();

	/** Workflow to process */
	private WorkflowDef workflow = null;

	@Override
	public final void execute() 
	{
		if (verbose) {
			System.out.println("verbose: " + verbose);
			System.out.println("debug: " + debug);
			System.out.println("dump: " + dump);
			System.out.println("concurrent:" + concurrent);
			System.out.println("report:" + report);
			System.out.println("analyze:" + analyze);
		}

		if (workflow != null && workflow.getFilename() != null) {
			final File workflowFile = new File(workflow.getFilename());
			
			if (workflowFile.isFile()) 
			{
				final AzotTaskWorkflowStarter starter = new AzotTaskWorkflowStarter(workflowFile.getAbsolutePath());
				starter.run();
			} 
			else if (workflowFile.isDirectory()) 
			{
				final File[] workflowFiles = workflowFile.listFiles();
				for (final File file : workflowFiles) {
					if (file.getName().endsWith(".xml")) {
						final AzotTaskWorkflowStarter starter = new AzotTaskWorkflowStarter(file.getAbsolutePath());
						if (concurrent) {
							final Thread workflowThread = new Thread(antWorkflowStarterGroup, starter);
							workflowThread.start();	
						} else {
							starter.run();
						}
					}
				}
			}
		}

		while (antWorkflowStarterGroup.activeCount() > 0) {
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				throw new AzotException(e);
			}
		}
	}

	public class AzotTaskWorkflowStarter implements Runnable {
		
		private final String workflowFile;

		public AzotTaskWorkflowStarter(final String workflowFile) {
			this.workflowFile = workflowFile;
		}
		
		public void run() 
		{
			final String[] azotArgs = new String[1];
			azotArgs[0] = workflowFile;
			
			final Properties customProperties = new Properties();
			customProperties.setProperty("azot-verbose", String.valueOf(verbose));
			customProperties.setProperty("azot-debug", String.valueOf(debug));
			customProperties.setProperty("azot-dump", String.valueOf(dump));
			customProperties.setProperty("azot-report", String.valueOf(report));
			customProperties.setProperty("azot-analyze", String.valueOf(analyze));
			
			for (int i = 0; i < variables.size(); i++) {
				customProperties.setProperty(variables.get(i).getId(), variables.get(i).getValue());
				if(verbose) {
					System.out.println(variables.get(i).getId() + ": " + variables.get(i).getValue());
				}
			}

			if (verbose) {
				for (int i = 0; i < azotArgs.length; i++) {
					System.out.println("arg " + i + ": '" + azotArgs[i] + "'");
				}
			}

			try {

				Launcher.main(azotArgs, customProperties);
			} catch (final Throwable e) {
				e.printStackTrace();
			}
		}
	}
	

	/**
	 * @param variable
	 */
	public final void addVariable(final VariableDef arg) {
		variables.add(arg);
	}

	public final WorkflowDef getWorkflow() {
		return this.workflow;
	}
	
	public void addWorkflow(final WorkflowDef workflow) {
		this.workflow = workflow;
	}

	/**
	 * @return the verbose
	 */
	public final boolean isVerbose() {
		return verbose;
	}

	/**
	 * @param verboseMode
	 *            the verbose to set
	 */
	public final void setVerbose(final boolean verboseMode) {
		this.verbose = verboseMode;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public boolean isDump() {
		return dump;
	}

	public void setDump(boolean dump) {
		this.dump = dump;
	}

	public boolean isConcurrent() {
		return concurrent;
	}

	public void setConcurrent(boolean concurrent) {
		this.concurrent = concurrent;
	}

	public boolean isReport() {
		return report;
	}

	public void setReport(boolean report) {
		this.report = report;
	}
	
	public boolean isAnalyze() {
		return analyze;
	}
	
	public void setAnalyze(boolean analyze) {
		this.analyze = analyze;
	}
}
