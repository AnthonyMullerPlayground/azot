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
package com.sap.azot.report;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sap.azot.AzotConfig;
import com.sap.azot.report.CallReport.Status;

/**
 * @author amuller
 */
public class WorkflowReport {

	private List<CallReport> callReports = new LinkedList<CallReport>();
	private Map<String, String> variables = new HashMap<String, String>();
	
	private long startTime = System.currentTimeMillis();
	private long endTime = System.currentTimeMillis();
	
	private String hostname;
	private String name;

	private XMLStreamWriter reportWriter = null;
	
	private final File outputDirectory;
	
	public WorkflowReport(final String name) {
		this.name = name;
		
		File tmpFile = null;
		long uid = startTime;
		do {
			tmpFile = new File(AzotConfig.GLOBAL.OUTPUT_DIRECTORY, "workflow_" + uid);
			uid++;
		} while(tmpFile.exists());

		
		this.outputDirectory = tmpFile;
		if (AzotConfig.GLOBAL.DUMP || AzotConfig.GLOBAL.REPORT) {
			this.outputDirectory.mkdir();
		}
		
		try {
			final InetAddress addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (final UnknownHostException e) {
			hostname = "unknown";
		}
		  
	}
	
	public File getOutputDirectory() {
		return outputDirectory;
	}
	
	public List<CallReport> getCallReports() {
		return callReports;
	}
	
	public Map<String, String> getVariables() {
		return variables;
	}
	
	private float getTime() {
		return ((float)(endTime-startTime))/((float)1000);
	}

	private String getTimestamp() {
		try {
			final GregorianCalendar gCalendar = new GregorianCalendar();
			gCalendar.setTime(new Date(startTime));

			final XMLGregorianCalendar xmlCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(gCalendar);
			return xmlCalendar.toString();
		} catch (DatatypeConfigurationException e) {
			return "";
		}

	}
	
	private int countCall(final Status status) {
		int count = 0;
		for (final CallReport callReport : callReports) {
			if(status.equals(callReport.getStatus())) {
				count++;
			}
		}
		return count;
	}
	
	public void dump() {
		endTime = System.currentTimeMillis();
		
		final NumberFormat formatter = new DecimalFormat("000");
		
		initReportWriter();
		try {
			reportWriter.writeStartDocument();
			reportWriter.writeStartElement("testsuite");
			reportWriter.writeAttribute("errors", String.valueOf(countCall(Status.ERROR)));
			reportWriter.writeAttribute("failures", String.valueOf(countCall(Status.FAILURE)));
			reportWriter.writeAttribute("hostname", this.hostname);
			reportWriter.writeAttribute("name", this.name);
			reportWriter.writeAttribute("tests", String.valueOf(callReports.size()));
			reportWriter.writeAttribute("time", String.valueOf(getTime()) );
			reportWriter.writeAttribute("timestamp", getTimestamp());
		
			if(!getVariables().isEmpty()) {
				reportWriter.writeStartElement("properties");
				for (final String variableId : getVariables().keySet()) {
					reportWriter.writeStartElement("property");
					reportWriter.writeAttribute("name", variableId);
					reportWriter.writeAttribute("value", getVariables().get(variableId));
					reportWriter.writeEndElement();
				}
				reportWriter.writeEndElement();
			}
			
			for (int i=0; i<callReports.size(); i++) {
				final CallReport callReport = callReports.get(i);
				final String callName = (callReport.getName()==null)? "call_" + String.valueOf(i):callReport.getName();
				
				reportWriter.writeStartElement("testcase");
				reportWriter.writeAttribute("time", String.valueOf(callReport.getTime()));
				reportWriter.writeAttribute("classname", this.name + "." +  formatter.format(i) + "_" + callName);
				reportWriter.writeAttribute("name", callReport.getUrl());
				long callStartTime = callReport.getStartTime() - getStartTime();
				long callEndTime = callStartTime + (callReport.getEndTime() - callReport.getStartTime());
				
				// Azot custom attributes
				reportWriter.writeAttribute("callStartTime", String.valueOf(callStartTime));
				reportWriter.writeAttribute("callEndTime", String.valueOf(callEndTime));
				reportWriter.writeAttribute("callName", callName);
				
				switch (callReport.getStatus()) {
					case ERROR:
						reportWriter.writeStartElement("error");
						reportWriter.writeAttribute("type", callReport.getType());
						reportWriter.writeCharacters(callReport.getMessage());
						reportWriter.writeEndElement();
						break;
					case FAILURE:
						reportWriter.writeStartElement("failure");
						reportWriter.writeAttribute("type", callReport.getType());
						reportWriter.writeAttribute("message", callReport.getMessage());
						reportWriter.writeCharacters(callReport.getMessage());
						reportWriter.writeEndElement();					
						break;
					default:
						break;
				}
				reportWriter.writeEndElement();
			}

			reportWriter.writeEndElement();
			reportWriter.writeEndDocument();
			reportWriter.close();
		} catch (final XMLStreamException e) {
			e.printStackTrace();
		}
	}
	
	public long getStartTime() {
		return startTime;
	}
	
	private void initReportWriter() {
		// Create an output factory
		final XMLOutputFactory xmlof = XMLOutputFactory.newInstance();

		// Create an XML stream writer
		final File reportFile = new File(getOutputDirectory(), "report.xml");
		try {
			reportWriter = xmlof.createXMLStreamWriter(new FileWriter(reportFile));

		} catch (final XMLStreamException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}
//	reportWriter.writeStartElement("testsuite");
//	reportWriter.writeAttribute("errors", "");
//	reportWriter.writeAttribute("failures", "");
//	reportWriter.writeAttribute("hostname", "");
//	reportWriter.writeAttribute("name", "");
//	reportWriter.writeAttribute("tests", "");
//	reportWriter.writeAttribute("time", "");
//	reportWriter.writeAttribute("timestamp", "");
	
}
