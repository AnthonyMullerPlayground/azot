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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.sap.azot.analyze.CallData.Status;

/**
 * @author amuller
 */
public class AnalyzeLauncher {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(final String[] args) {
		if (args.length == 0) {
			System.err.println("Please provide directory path as the first argument.");
			System.exit(1);
		}

		final File workflowsDirectory = new File(args[0]);
		if (!workflowsDirectory.exists() || !workflowsDirectory.isDirectory()) {
			System.err.println("Please provide a valid directory path as the first argument.");
			System.exit(1);
		}

		final Map<File, AnalysisData> processedWorkflows = new TreeMap<File, AnalysisData>();
		processWorkflowDirectory(workflowsDirectory, processedWorkflows);
		
		// Generate consolidated analysis
		generatedConsolidatedAnalysis(processedWorkflows);
		generatedConsolidatedHTML(processedWorkflows);
	}


	private static void generatedConsolidatedHTML(final Map<File, AnalysisData> processedWorkflows) {
		try {
			final File globalAnalyze = new File("global-analyze.html");
			final FileWriter gaWriter = new FileWriter(globalAnalyze);
			gaWriter.write("<html>");
			gaWriter.write("<body>");
			
			// Consolidated analyze
			final File consolidatedAnalysisHTML = new File("analyze.html");
			if(consolidatedAnalysisHTML.exists() && consolidatedAnalysisHTML.isFile()) {
				final String consolidatedAnalyse = stringify(new FileInputStream(consolidatedAnalysisHTML));
				gaWriter.write(consolidatedAnalyse);	
			}
			
			// Workflow analyzes
			for (final File processedWorkflowDirectory : processedWorkflows.keySet()) {
				final File analyseFile =  new File(processedWorkflowDirectory, "analyze.html");
				if(analyseFile.exists() && analyseFile.isFile()) {
					final String fragmentAnalyse = stringify(new FileInputStream(analyseFile));
					gaWriter.write(fragmentAnalyse);	
				}
			}
			
			gaWriter.write("</body>");
			gaWriter.write("</html>");
			gaWriter.flush();
			gaWriter.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}


	private static void generatedConsolidatedAnalysis(final Map<File, AnalysisData> processedWorkflows) throws FactoryConfigurationError, TransformerFactoryConfigurationError {
		// Build consolidated workflow analysis data 
		final AnalysisData consolidatedData = new AnalysisData();
		consolidatedData.name = "Consolidated Analysis";

		final List<AnalysisData.Row> consolidatedRows = new ArrayList<AnalysisData.Row>();
		for (final File processedWorkflowDirectory : processedWorkflows.keySet()) {
			final AnalysisData analysisData = processedWorkflows.get(processedWorkflowDirectory);

			if (consolidatedData.duration == -1) {
				consolidatedData.duration += analysisData.duration;
			} else {
				consolidatedData.duration = (consolidatedData.duration + analysisData.duration) / 2;
			}
			
			if (analysisData.rows != null) {
				for (final AnalysisData.Row analysisRow : analysisData.rows) {
					// Search for already existing consolidated row
					AnalysisData.Row existingConsolidatedRow = null;
					for (final AnalysisData.Row consolidatedRow : consolidatedRows) {
						if (consolidatedRow.name.equals(analysisRow.name)) {
							existingConsolidatedRow = consolidatedRow;
							break;
						}
					}
					if (existingConsolidatedRow == null) {
						existingConsolidatedRow = new AnalysisData.Row();
						existingConsolidatedRow.name = analysisRow.name;
						consolidatedRows.add(existingConsolidatedRow);
					}
					
					existingConsolidatedRow.count += analysisRow.count;
					existingConsolidatedRow.countErrors += analysisRow.countErrors;
					existingConsolidatedRow.countFailures += analysisRow.countFailures;
					
					if (existingConsolidatedRow.min > analysisRow.min) {
						existingConsolidatedRow.min = analysisRow.min;
					}
					if (existingConsolidatedRow.max < analysisRow.max) {
						existingConsolidatedRow.max = analysisRow.max;
					}
					if (existingConsolidatedRow.avg == -1) {
						existingConsolidatedRow.avg = analysisRow.avg;
					} else {
						existingConsolidatedRow.avg =  (existingConsolidatedRow.avg + analysisRow.avg) / 2;
					}
				}	
			}
		}
		consolidatedData.rows = consolidatedRows.toArray(new AnalysisData.Row[consolidatedRows.size()]);
		
		generateXML(consolidatedData, new File("."));
		generateHTML(consolidatedData, new File("."));
	}
	
	
	private static void processWorkflowDirectory(final File workflowsDirectory, final Map<File, AnalysisData> processedWorkflows) {
		final File workflowReportFile = new File(workflowsDirectory, "report.xml");
		if (workflowReportFile.exists() && workflowReportFile.isFile()) {
			//System.out .println("Azotyzing: " + workflowReportFile.getAbsolutePath());
			final AnalysisData analysisData = new AnalyzeLauncher().processWorkflowReport(workflowReportFile);
			processedWorkflows.put(workflowsDirectory, analysisData);
		}
		else {
			final File[] files = workflowsDirectory.listFiles();
			for (final File file : files) {
				if (file.isDirectory()) {
					processWorkflowDirectory(file, processedWorkflows);
				}
			}	
		}
	}

	private AnalysisData processWorkflowReport(final File workflowReportFile) {
		final WorkflowData workflowData = new WorkflowData();
		try {
			final InputStream in = new FileInputStream(workflowReportFile);
			final XMLInputFactory factory = XMLInputFactory.newInstance();
			final XMLStreamReader parser = factory.createXMLStreamReader(in);

			CallData currentCallData = null;
			while (parser.hasNext()) {

				int eventType = parser.next();
				switch (eventType) {

				case XMLStreamConstants.START_ELEMENT:
					if ("testsuite".equals(parser.getName().toString())) {
						workflowData.setName(parser.getAttributeValue(null, "name"));
						float duration = Float.parseFloat(parser.getAttributeValue(null, "time"));
						workflowData.setDuration((long)(duration * 1000)); // convert to milliseconds
					}
					else if ("testcase".equals(parser.getName().toString())) {
						currentCallData = workflowData.addCallData(
							parser.getAttributeValue(null, "callName"),
							Integer.parseInt(parser.getAttributeValue(null, "callStartTime")),
							Integer.parseInt(parser.getAttributeValue(null, "callEndTime"))
						);
					}
					else if ("property".equals(parser.getName().toString())) {
						workflowData.getProperties().put(
							parser.getAttributeValue(null, "name"),
							parser.getAttributeValue(null, "value")
						);
					}
					else if ("failure".equals(parser.getName().toString())) {
						if(currentCallData != null) {
							currentCallData.setStatus(Status.FAILURE);
						}
					}
					else if ("error".equals(parser.getName().toString())) {
						if(currentCallData != null) {
							currentCallData.setStatus(Status.ERROR);
						}
					}
					break;
				}
			}
			
			in.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		// Output generation (XML and HTML)
		final File analyzeOuputDirectory = workflowReportFile.getParentFile();
		final AnalysisData analysisData = workflowData.createAnalysisData();
		generateXML(analysisData, analyzeOuputDirectory);
		generateHTML(analysisData, analyzeOuputDirectory);
		
		return analysisData;
	}


	private static void generateHTML(final AnalysisData analysisData, final File analyzeOuputDirectory) throws TransformerFactoryConfigurationError {
		// Write HTML report
		if (analysisData.rows != null && analysisData.rows.length > 0) {
	        try {
				TransformerFactory factory = TransformerFactory.newInstance();
				InputStream xslIS = ClassLoader.getSystemResourceAsStream("analyze.xsl");
				if(xslIS == null) {
					xslIS = ClassLoader.getSystemResourceAsStream("/analyze.xsl");
				}
				if(xslIS == null) {
					xslIS = AnalyzeLauncher.class.getClassLoader().getResourceAsStream("analyze.xsl");
				}
				if(xslIS == null) {
					xslIS = AnalyzeLauncher.class.getClassLoader().getResourceAsStream("/analyze.xsl");
				}
				if(xslIS == null) {
					xslIS = Thread.currentThread().getContextClassLoader().getResourceAsStream("analyze.xsl");
				}
				if(xslIS == null) {
					xslIS = Thread.currentThread().getContextClassLoader().getResourceAsStream("/analyze.xsl");
				}
				if(xslIS == null) {
					xslIS = new FileInputStream(new File("analyze.xsl"));
				}
				
				Source xslt = new StreamSource(xslIS);
				Transformer transformer = factory.newTransformer(xslt);
				
				final File analysisFile =  new File(analyzeOuputDirectory, "analyze.xml");
				if(analysisFile.exists() && analysisFile.length() > 0) {
					Source text = new StreamSource(analysisFile);
					transformer.transform(text, new StreamResult(new File(analyzeOuputDirectory, "analyze.html")));	
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}	
		}
	}


	private static void generateXML(final AnalysisData analysisData, final File analyzeOuputDirectory) throws FactoryConfigurationError {
		// Write XML report
		try {
			// Create an output factory
			final XMLOutputFactory xmlof = XMLOutputFactory.newInstance();

			// Create an XML stream writer
			final File reportFile = new File(analyzeOuputDirectory, "analyze.xml");
			final XMLStreamWriter analyseWriter = xmlof.createXMLStreamWriter(new FileWriter(reportFile));
			analyseWriter.writeStartDocument();
			analyseWriter.writeStartElement("analyze");
			analyseWriter.writeAttribute("duration", String.valueOf(analysisData.duration));
			analyseWriter.writeAttribute("name", analysisData.name);
		
			if(analysisData.properties != null) {
				for (String propertyName : analysisData.properties.keySet()) {
					analyseWriter.writeStartElement("property");
					analyseWriter.writeAttribute("name", propertyName);
					analyseWriter.writeAttribute("value", analysisData.properties.get(propertyName));
					analyseWriter.writeEndElement();
				}	
			}
			
			if(analysisData.rows != null) {
				for (final AnalysisData.Row row : analysisData.rows) {
					analyseWriter.writeStartElement("call");
					analyseWriter.writeAttribute("name", row.name);
					analyseWriter.writeAttribute("count", String.valueOf(row.count));
					analyseWriter.writeAttribute("errors", String.valueOf(row.countErrors));
					analyseWriter.writeAttribute("failures", String.valueOf(row.countFailures));
					analyseWriter.writeAttribute("min", String.valueOf(row.min));
					analyseWriter.writeAttribute("max", String.valueOf(row.max));
					analyseWriter.writeAttribute("avg", String.valueOf(row.avg));
					analyseWriter.writeEndElement();
				}	
			}

			analyseWriter.writeEndElement();
			analyseWriter.writeEndDocument();
			analyseWriter.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	
	private static String stringify(final InputStream inputStream) {
		if (inputStream == null) {
			return "";
		}
		try {
			int ichar;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for (;;) {
				ichar = inputStream.read();
				if (ichar < 0) {
					break;
				}
				baos.write(ichar);
			}
			return baos.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
