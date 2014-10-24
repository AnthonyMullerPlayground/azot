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
package com.sap.azot.analyze;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sap.azot.analyze.CallData.Status;

/**
 * @author amuller
 */
public class WorkflowData {

	private Map<String, List<CallData>> callData = new HashMap<String, List<CallData>>();
	private List<String> callNames = new LinkedList<String>();
	
	private String name;
	private long duration; // in milliseconds
	
	private Map<String, String> properties = new HashMap<String, String>();
	
	public WorkflowData() {

	}
	
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public List<CallData> getCallData(final String callName) {
		List<CallData> callDataList = callData.get(callName); 
		if(callDataList == null) {
			callDataList = new LinkedList<CallData>();
			callData.put(callName, callDataList);
			callNames.add(callName);
		}
		return callDataList;
	}
	
	public List<String> getCallNames() {
		return callNames;
	}
	
	public Map<String, String> getProperties() {
		return properties;
	}

	public int getCount(final String callName) {
		return getCallData(callName).size();
	}
	
	public int getCountErrors(final String callName) {
		int nbErrors = 0;
		final List<CallData> data = getCallData(callName);
		for (final CallData callData : data) {
			if(Status.ERROR.equals(callData.getStatus())) {
				nbErrors++;
			}
		}
		return nbErrors;
	}
	
	public int getCountFailures(final String callName) {
		int nbErrors = 0;
		final List<CallData> data = getCallData(callName);
		for (final CallData callData : data) {
			if(Status.FAILURE.equals(callData.getStatus())) {
				nbErrors++;
			}
		}
		return nbErrors;
	}
	
	public int getMinimum(final String callName) {
		int min = Integer.MAX_VALUE;
		final List<CallData> callDataList = getCallData(callName);
		for (final CallData callDataEntry : callDataList) {
			if (callDataEntry.getDuration() < min) {
				min = callDataEntry.getDuration();
			}
		}
		return min;
	}
	
	public int getMaximum(final String callName) {
		int max = Integer.MIN_VALUE;
		final List<CallData> callDataList = getCallData(callName);
		for (final CallData callDataEntry : callDataList) {
			if (callDataEntry.getDuration() > max) {
				max = callDataEntry.getDuration();
			}
		}
		return max;
	}
	
	public int getAverage(final String callName) {
		float avg = -1;
		final List<CallData> callDataList = getCallData(callName);
		for (final CallData callDataEntry : callDataList) {
			if (avg == -1) {
				avg = callDataEntry.getDuration();
			} else {
				avg = (avg + (float)callDataEntry.getDuration()) / (float)2;
			}
		}
		return (int) avg;
	}
	
	public CallData addCallData(final String callName, final int startTime, final int endTime) {
		final CallData callData = new CallData(startTime, endTime);
		getCallData(callName).add(callData);
		return callData;
	}
	
	public AnalysisData createAnalysisData() {
		final AnalysisData analysisData = new AnalysisData();
		analysisData.name = getName();
		analysisData.duration = getDuration();
		analysisData.properties = getProperties();
		
		final List<String> callNames = getCallNames();
		analysisData.rows = new AnalysisData.Row[callNames.size()];

		int i = 0;
		for (final String callName : callNames) {
			analysisData.rows[i] = new AnalysisData.Row();
			analysisData.rows[i].name = callName;
			analysisData.rows[i].count = getCount(callName);
			analysisData.rows[i].countErrors = getCountErrors(callName);
			analysisData.rows[i].countFailures = getCountFailures(callName);
			analysisData.rows[i].min = getMinimum(callName);
			analysisData.rows[i].max = getMaximum(callName);
			analysisData.rows[i].avg = getAverage(callName);
			
			i++;
		}
		
		return analysisData;
	}
}
