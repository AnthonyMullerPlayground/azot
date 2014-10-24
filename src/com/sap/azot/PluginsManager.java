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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.bind.Unmarshaller;

/**
 * @author amuller
 */
public class PluginsManager {

	private static Map<String, Plugin> plugins = null;
	
	private static Map<String, ClassLoader> pluginClassloaders = null;
	
	private static Map<String, Method> pluginMethods = null;
	
	public static void init(final Unmarshaller unmarshaller) {
		if(plugins == null) {
			plugins = new HashMap<String, Plugin>();
			pluginClassloaders = new HashMap<String, ClassLoader>();
			pluginMethods = new HashMap<String, Method>();
					
			File pluginsDir = new File("plugins");
			if(pluginsDir != null && pluginsDir.exists() && pluginsDir.isDirectory()) {
				System.out.println("Plugins directory found.");
				File[] pluginFolders = pluginsDir.listFiles();
				if (pluginFolders != null) {
					for (final File pluginFolder : pluginFolders) {
						if (pluginFolder != null && pluginFolder.exists() && pluginFolder.isDirectory()) {
							File crtPlugin = new File(pluginFolder, "plugin.xml");
							if (crtPlugin != null && crtPlugin.exists() && crtPlugin.isFile()) {
								System.out.println("Reading: " + crtPlugin.getAbsolutePath());
								try {
									Object pluginObject = unmarshaller.unmarshal(crtPlugin);
									if (pluginObject instanceof Plugin) {
										Plugin plugin = (Plugin) pluginObject;
										String pluginName = pluginFolder.getName();
										if (plugin.getName() != null) {
											pluginName = plugin.getName();
										}
										
										// Detect duplicate plugin
										if(plugins.get(pluginName) != null) {
											throw new AzotException("A plugin named '" + pluginName + "' has already be loaded.");
										}

										List<URL> urls = new ArrayList<URL>();
										File[] pluginContentFiles = pluginFolder.listFiles();
										for (File pluginContentFile : pluginContentFiles) {
											urls.add(pluginContentFile.toURI().toURL());
										}
										
										URLClassLoader pluginClassloader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
										
										Class<?> pluginClass = pluginClassloader.loadClass(plugin.getClazz());
										
										final Method pluginMethod = pluginClass.getDeclaredMethod("execute", Properties.class);
										if (!Properties.class.equals(pluginMethod.getReturnType())) {
											throw new AzotException("The return type of the execute() method must be 'java.util.Properties' (not '" + pluginMethod.getReturnType().getName() + "')");
										}
										
										if (!Modifier.isStatic(pluginMethod.getModifiers()) || !Modifier.isPublic(pluginMethod.getModifiers())) {
											throw new AzotException("The execute() method must be declared 'public' and 'static'");
										}
										
										
										plugins.put(pluginName, plugin);
										pluginClassloaders.put(pluginName, pluginClassloader);
										pluginMethods.put(pluginName, pluginMethod);
										System.out.println("Success: plugin '" + pluginName + "' added.");
									}
								} catch (Exception e) {
									println("Error: unable to load plugin");
									if (AzotConfig.GLOBAL.VERBOSE) {
										System.out.print("Cause: ");
										e.printStackTrace();
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	public static void invokePlugin(final String pluginName, final PluginRef plugin, final List<Variable> variables) {
		try {
			if (pluginMethods.get(pluginName) == null) {
				throw new AzotException("Plugin '" + pluginName + "' not found.");
			}
			
			final List<Variable> inheritedVariables = new ArrayList<Variable>();
			inheritedVariables.addAll(variables);

			final List<Variable> pluginVariables = plugin.getVariables();
			for (final Variable pluginVariable : pluginVariables) {
				final String newValue = VariableHelper.substituteVariables(pluginVariable.getValue(), inheritedVariables);
				VariableHelper.addOrUpdateVariable(pluginVariable.getId(), newValue, true, false, inheritedVariables);
			}
			
			
			final Properties inProperties = new Properties();
			if (inheritedVariables != null) {
				for (final Variable variable : inheritedVariables) {
					inProperties.setProperty(variable.getId(), variable.getValue());
				}
			}
			
			final Object returnedValue = pluginMethods.get(pluginName).invoke(null, new Object[] {inProperties});
			
			if (returnedValue instanceof Properties) {
				final Properties outProperties = (Properties) returnedValue;
				
				//System.out.println("Variables received by Azot:");
				Set<Object> keyObjects = outProperties.keySet();
				for (Object keyObject : keyObjects) {
					final String key = (String)keyObject;
					final String value = outProperties.getProperty(key);
					//System.out.println("key: '" + key + "', value: '" + value + "'");
					
					println("Plugin returns the property '" + key + "' with value '" + value + "'");
					VariableHelper.addOrUpdateVariable(key, value, true, false, variables);
				}
			}
		} catch (Exception e) {
			println("Error: plugin invocation failed");
			if (AzotConfig.GLOBAL.VERBOSE) {
				System.out.print("Cause: ");
				e.printStackTrace();
			}
		}
	}

	private static void println(String msg) {
		if (AzotConfig.GLOBAL.VERBOSE) {
			System.out.println(msg);
		}
	}
}
