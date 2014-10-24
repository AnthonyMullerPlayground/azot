/**
 * Copyright (C) 2013 Anthony M�LLER.
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
package com.sap.azot.analyze;

import java.util.Map;

/**
 * @author amuller
 */
public class AnalysisData {

	Map<String, String> properties;
	long duration = -1;
	String name = "";
	Row[] rows;
	

	public static class Row {
		String name = "";
		int count = 0;
		int countErrors = 0;
		int countFailures = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int avg = -1;
	}
}
