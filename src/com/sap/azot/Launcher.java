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
package com.sap.azot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.sap.azot.analyze.AnalyzeLauncher;

/**
 * @author amuller
 */
public class Launcher {

	private static final Unmarshaller UNMARSHALLER;
	static {
		try {
			final ClassLoader azotCL = ObjectFactory.class.getClassLoader();
			final JAXBContext context = JAXBContext.newInstance("com.sap.azot", azotCL);
			UNMARSHALLER = context.createUnmarshaller();
		} catch (final JAXBException e) {
			throw new AzotException(e);
		}
	}

	private final ThreadGroup workflowGroup = new ThreadGroup("AzotWorkflowGroup");
	private final ThreadGroup jmxGroup = new ThreadGroup("AzotJMXGroup");

	private boolean fork = false;

	/**
	 * Launch Azot.
	 *
	 * @param args
	 * @param variables
	 * @throws InterruptedException
	 */
	public static void main(final String[] args)
	{
		main(args, null);
	}

	/**
	 * Launch Azot with properties.
	 *
	 * @param args
	 * @param variables
	 * @throws InterruptedException
	 */
	public static void main(final String[] args, final Properties azotProperties)
	{
		if(args.length == 0) {
			System.err.println("Please provide the workflow file path as the first argument.");
			System.exit(1);
		}
		
		// Initialize the plugins manager
		PluginsManager.init(UNMARSHALLER);
		
		// Initialize the launcher
		final Launcher mainlauncher = new Launcher();
		final List<Variable> initVariables = mainlauncher.init(azotProperties, args);

		final String workflowFileArgument = args[0];
		if (workflowFileArgument != null) {
			mainlauncher.launch(new File(workflowFileArgument), initVariables);
		}

		while (mainlauncher.getWorkflowGroup().activeCount() > 0) {
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				throw new AzotException(e);
			}
		}

		while (mainlauncher.getJMXGroup().activeCount() > 0) {
			try {
				mainlauncher.getJMXGroup().interrupt();
				Thread.sleep(100);
			} catch (final InterruptedException e) {
				throw new AzotException(e);
			}
		}
		
		if (AzotConfig.GLOBAL.ANALYZE) {
			AnalyzeLauncher.main(new String[] {AzotConfig.GLOBAL.OUTPUT_DIRECTORY.getAbsolutePath()});
		}
	}

	public ThreadGroup getWorkflowGroup() {
		return workflowGroup;
	}

	public ThreadGroup getJMXGroup() {
		return jmxGroup;
	}

	private List<Variable> init(final Properties customProperties, final String... args)
	{
		jmxGroup.setMaxPriority(Thread.MAX_PRIORITY);
		
		final Properties azotProperties = new Properties();

		// Property file variables
		final File azotConfig = new File("./azot.properties");
		final List<Variable> initVariables = new ArrayList<Variable>();
		if (azotConfig.exists() && azotConfig.isFile()) {
			try {
				azotProperties.load(new FileInputStream(azotConfig));
			} catch (final IOException e) {
				throw new AzotException(e);
			}

			if (AzotConfig.GLOBAL.DEBUG) {
				System.out.println("Reading azot.properties...");
			}
		}

		if (customProperties != null) {
			azotProperties.putAll(customProperties);
		}

		// Init Azot configuration
		AzotConfig.GLOBAL.init(azotProperties);

		for (final Object propKey : azotProperties.keySet()) {
			String key = (String) propKey;

			Variable newVariable = VariableHelper.addOrUpdateVariable(key, azotProperties.getProperty(key), true, false, initVariables);

			if (AzotConfig.GLOBAL.DEBUG) {
				System.out.println("Setting variable: ${" + newVariable.getId() + "} = " + newVariable.getValue());
			}
		}

		// Command line variables
		for (int i = 0; i < args.length; i++) {
			//final Variable newVariable = new Variable();
			final String varId;
			final String varValue;
			if(args[i].contains("=")) {
				final String[] argParts = args[i].split("=");
				varId = argParts[0];
				varValue = argParts[1];
			}
			else {
				varId = String.valueOf(i);
				varValue = args[i];
			}

			final Variable newVariable = VariableHelper.addOrUpdateVariable(varId, varValue, true, false, initVariables);

			if (AzotConfig.GLOBAL.DEBUG) {
				System.out.println("Setting variable: ${" + newVariable.getId() + "} = " + newVariable.getValue());
			}
		}

		/*
		if (AzotConfig.GLOBAL.JMX != null) {
			final String[] jmxParams = AzotConfig.GLOBAL.JMX.split(",");
			for (final String jmxParam : jmxParams) {
				try {
					int sampling = 100;
					final String str = jmxParam.split(";")[1];
					if (str != null && !str.isEmpty()) {
						if ( str.split("=")[0].equals("sampling") && str.split("=")[1] != null && !str.split("=")[1].isEmpty()) {
							sampling = Integer.parseInt(str.split("=")[1]);
						}
					}
					final String jmxConnection = jmxParam.split(";")[0];
					final JMXMonitoring jmxMonitoring = new JMXMonitoring(jmxGroup, jmxConnection, sampling);
					jmxMonitoring.setOutputFile(new File(AzotConfig.GLOBAL.OUTPUT_DIRECTORY, "profile-" + jmxConnection.replace(':', '_') + ".xml"));
					jmxMonitoring.begin();
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
		*/

		return initVariables;
	}



	public boolean isFork() {
		return fork;
	}

	public void setFork(boolean fork) {
		this.fork = fork;
	}

	/**
	 * Launch the given workflow.
	 *
	 * @param workflowFile
	 * @throws JAXBException
	 */
	public void launch(final File workflowFile, final List<Variable> inheritedVariables) {

		final Workflow workflow;
		try {
			workflow = (Workflow) UNMARSHALLER.unmarshal(workflowFile);
		} catch (final JAXBException e) {
			throw new AzotException(e);
		}

		if (workflow != null)
		{
			final WorkflowStarter workflowStarter = new WorkflowStarter(workflowFile, inheritedVariables, workflow);

			// Asynchrone
			if (isFork()) {
				final Thread workflowThread = new Thread(getWorkflowGroup(), workflowStarter);
				workflowThread.start();    	
			}
			// Synchrone
			else {
				workflowStarter.run();
			}
		}
	}

	class WorkflowStarter implements Runnable {

		private final Workflow workflow;
		private final File workflowFile;
		private final List<Variable> inheritedVariables;

		public WorkflowStarter(final File workflowFile, final List<Variable> inheritedVariables, final Workflow workflow) 
		{
			this.workflowFile = workflowFile;
			this.inheritedVariables = inheritedVariables;
			this.workflow = workflow;
		}

		@Override
		public void run() {
			if(workflow.getContext() == null) {
				workflow.setContext(new Context());
			}

			if (AzotConfig.GLOBAL.DEBUG) {
				System.out.println("Reading " + workflowFile + "...");
			}

			final List<Variable> contextVariables = workflow.getContext().getVariables();
			for (final Variable contextVariable : contextVariables) {
				String value = VariableHelper.substituteVariables(contextVariable.getValue(), inheritedVariables);
				value = VariableHelper.substituteVariables(value, contextVariables);
				if (AzotConfig.GLOBAL.DEBUG) {
					System.out.println("Reading variable: ${" + contextVariable.getId() + "} = " + value + " (overwrite=" + contextVariable.isOverwrite() + ")");
				}
				contextVariable.setValue(value);
			}


			// Add inherited variables which are not already present into context variables 
			for (final Variable inheritVariable : inheritedVariables) 
			{
				Variable existingContextVariable = VariableHelper.exist(inheritVariable.getId(), contextVariables);
				if (existingContextVariable == null) {
					contextVariables.add(inheritVariable);	    			
				}
				else {
					if(!existingContextVariable.isOverwrite()) {
						existingContextVariable.setValue(inheritVariable.getValue());
						if (AzotConfig.GLOBAL.DEBUG) {
							System.out.println("Setting variable: ${" + existingContextVariable.getId() + "} = " + existingContextVariable.getValue());
						}
					}
				}
			}

			if (AzotConfig.GLOBAL.VERBOSE) {
				System.out.println("Starting workflow '" + workflow.getName() + "'");
			}

			new WorkflowEngine(workflow).start();
		}
	}
}
