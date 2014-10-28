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
import java.util.Properties;

/**
 * @author amuller
 */
public final class AzotConfig {

	public static final AzotConfig GLOBAL = new AzotConfig();

	public boolean DEBUG = false;

	public boolean VERBOSE = false;

	public boolean INTERACTIVE = false;

	public boolean DUMP = false;

	public boolean REPORT = false;
	
	public boolean ANALYZE = false;
	
	public String BREAKPOINT = null;
	
	public File OUTPUT_DIRECTORY = null;
	
	public String JMX = null;

	private AzotConfig() {}

	public void init(final Properties azotProperties) {
		if(azotProperties != null) {
			VERBOSE = toBoolean(azotProperties, "azot-verbose", false);
			DEBUG = toBoolean(azotProperties, "azot-debug", false);
			INTERACTIVE = toBoolean(azotProperties, "azot-interactive", false);
			DUMP = toBoolean(azotProperties, "azot-dump", false);
			REPORT = toBoolean(azotProperties, "azot-report", false);
			ANALYZE = toBoolean(azotProperties, "azot-analyze", false);
			BREAKPOINT = toString(azotProperties, "azot-breakpoint", null);
			
			OUTPUT_DIRECTORY = toFile(azotProperties, "azot-output-directory", ".");
			JMX = toString(azotProperties, "azot-jmx", null);
		}
	}

	private boolean toBoolean(final Properties azotProperties, final String key, final boolean defaultValue) {
		boolean flag = defaultValue;
		if (azotProperties.getProperty(key) != null) {
			try {
				flag = Boolean.parseBoolean(azotProperties.getProperty(key));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	
	private String toString(final Properties azotProperties, final String key, final String defaultValue) {
		String value = defaultValue;
		if (azotProperties.getProperty(key) != null) {
			value = azotProperties.getProperty(key);
		}
		return value;
	}
	
	private File toFile(final Properties azotProperties, final String key, final String defaultValue) {
		File file = null;
		if (azotProperties.getProperty(key) != null) {
			try {
				final String fileName = azotProperties.getProperty(key);
				if(fileName != null) {
					file = new File(fileName);
					if(!file.exists()) {
						file.mkdirs();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				file = null;
			}
		}
		if (file == null) {
			file = new File(defaultValue);
		}
		return file;
	}
}
