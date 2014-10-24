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

/**
 * @author amuller
 */
import java.util.ArrayList;
import java.util.List;

public class WorkflowDef {

	private String filename;

	/** Variables */
	private final List<VariableDef> variables = new ArrayList<VariableDef>();

	public WorkflowDef() {

	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	/**
	 * @param variable
	 */
	public final void addVariable(final VariableDef arg) {
		variables.add(arg);
	}
}
