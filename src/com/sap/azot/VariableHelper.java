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

import java.util.List;

/**
 * @author amuller
 */
public class VariableHelper {

	/**
	 * Make a substitution.
	 *
	 * @param originalValue
	 * @param variables
	 * @return
	 */
	public static String substituteVariables(final String originalValue, final List<Variable> variables) {
		if(originalValue == null) {
			return null;
		}
		String newValue = originalValue;
		for (final Variable variable : variables) {
			newValue = newValue.replace("${" + variable.getId() + "}", variable.getValue());
		}
		return newValue;
	}

	/**
	 * Add a new variable or update the value of existing variable.
	 * @param b
	 *
	 * @param originalValue
	 * @param variables
	 * @return
	 */
	public static Variable addOrUpdateVariable(final String varId, final String varValue, final boolean overwrite, final boolean report, final List<Variable> variables) {
		if (varId == null) {
			return null;
		}

		final Variable var = new Variable();
		var.setId(varId);
		var.setValue(varValue);
		var.setOverwrite(overwrite);
		var.setReport(report);

		return addOrUpdateVariable(var, variables);
	}

	/**
	 * Add a new variable or update the value of existing variable.
	 *
	 * @param originalValue
	 * @param targetVariables
	 * @return
	 */
	public static Variable addOrUpdateVariable(final Variable newVariable, final List<Variable> targetVariables) {
		if (newVariable == null) {
			return null;
		}

		Variable theVariable = null;
		for (final Variable variable : targetVariables) {
			if (variable.getId().equals(newVariable.getId())) {
				if(newVariable.isOverwrite()) {
					if (AzotConfig.GLOBAL.DEBUG) {
						if(newVariable.getValue() != null && !newVariable.getValue().equals(variable.getValue())) {
							System.out.println("Updating variable: ${" + variable.getId() + "} = " + newVariable.getValue() + " (was '" + variable.getValue() + "')");	
						}
					}
					variable.setValue(newVariable.getValue());
				}
				theVariable = variable;
				break;
			}
		}

		if (theVariable == null) {			
			targetVariables.add(newVariable);
			theVariable = newVariable;
		}

		return theVariable;
	}
	
	/**
	 * Return not <code>null</code> if a variable with the same id already exists.
	 * @return
	 */
	public static Variable exist(final String idToSearch, final List<Variable> targetVariables) {
		Variable existingContextVariable = null;
		for (final Variable targetVariable : targetVariables) {
			if (targetVariable.getId() != null && targetVariable.getId().equals(idToSearch)) {
				existingContextVariable = targetVariable;
				break;
			}
		}
		return existingContextVariable;
	}

	/**
	 * Duplicate a variable.
	 * @param aVariable
	 * @return
	 */
	public static Variable clone(final Variable aVariable) {
		final Variable clone = new Variable();
		clone.setId(aVariable.getId());
		clone.setValue(aVariable.getValue());
		clone.setOverwrite(aVariable.isOverwrite());
		clone.setReport(aVariable.isReport());
		return clone;
	}
}
