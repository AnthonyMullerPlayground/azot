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

import java.awt.image.ImageProducer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sap.azot.Request.Header;
import com.sap.azot.report.CallReport;
import com.sap.azot.report.CallReport.Status;
import com.sap.azot.report.WorkflowReport;

/**
 * @author amuller
 */
public class WorkflowEngine {

	private static final XPathFactory XFACTORY = XPathFactory.newInstance();
	private static final String XPATH = "${xpath";
	private static final String XPATH_EXPR = XPATH + "-expr:";
	private static final String XPATH_EVAL = XPATH + "-eval:";

	private static final String AZOT = "${azot";
	private static final String AZOT_REPLACE = AZOT + "-replace:";
	
	private final Workflow workflow;

	private final WorkflowReport workflowReport;

	private final String BOUNDARY;


	public WorkflowEngine(final Workflow workflow) {
		this.workflow = workflow;
		this.workflowReport = new WorkflowReport(workflow.getName());
		this.BOUNDARY = generateBoundary();
	}

	public void start() {
		if (AzotConfig.GLOBAL.DEBUG) {
			final List<Variable> workflowVariables = workflow.getContext().getVariables();
			for (final Variable variable : workflowVariables) {
				if (variable.getId() != null && !variable.getId().startsWith("azot-")) {
					println("Available variable: ${" + variable.getId() + "} = " + variable.getValue() + " [" +variable+ "]");
				}
			}
		}

		if (AzotConfig.GLOBAL.DUMP || AzotConfig.GLOBAL.REPORT) {
			if (AzotConfig.GLOBAL.DEBUG) {
				println("Output file: " + workflowReport.getOutputDirectory().getAbsolutePath());
			}
		}

		println("==========================================================================================");

		final List<Object> executables = workflow.getCallsAndWorkflowsAndLoops();
		processExecutables(executables, 0);

		if (AzotConfig.GLOBAL.REPORT) {
			final List<Variable> workflowVariables = workflow.getContext().getVariables();
			for (final Variable workflowVariable : workflowVariables) {
				if (workflowVariable.isReport()) {
					workflowReport.getVariables().put(workflowVariable.getId(), workflowVariable.getValue());
				}
			}
			workflowReport.dump();
		}
	}

	private int processExecutables(final List<Object> executables, int count) {
		for (final Object executable : executables) {
			if (executable instanceof Call)
			{
				final Call call = (Call) executable;

				String callName = VariableHelper.substituteVariables(call.getName(), getWorkflow().getContext().getVariables());
				final CallReport callReport = new CallReport(callName);
				workflowReport.getCallReports().add(count, callReport);
				callReport.setStartTime(System.currentTimeMillis());
				try {
					processCall(call, count);
				} catch (final Exception e) {
					callReport.setStatus(CallReport.Status.ERROR);
					callReport.setType(e.getClass().getName());
					callReport.setErrorMessage(e);
				}
				callReport.setEndTime(System.currentTimeMillis());


				println("==========================================================================================");


				// check breakpoints definition (separated by a semicolon)
				boolean breakpointFound = true;
				if (AzotConfig.GLOBAL.BREAKPOINT != null) {
					breakpointFound = false;
					String[] breakpoints = AzotConfig.GLOBAL.BREAKPOINT.split(";");
					for (String breakpoint : breakpoints) {
						breakpointFound |= breakpoint.equals(call.getName());
					}
				}

				if (AzotConfig.GLOBAL.INTERACTIVE && breakpointFound) {
					Scanner sc = new Scanner(System.in);
					echo("Press 'Enter' to continue... (type 'off' to turn off the interactive mode)");
					String what = sc.nextLine();
					if("off".equalsIgnoreCase(what)) {
						AzotConfig.GLOBAL.INTERACTIVE = false;
					}
				}
				count++;
			}
			else if(executable instanceof WorkflowRef)
			{
				final WorkflowRef workflowRef = (WorkflowRef) executable;
				final Launcher nestedWorkflowLauncher = new Launcher();
				nestedWorkflowLauncher.setFork(workflowRef.isFork());
				final String filename = VariableHelper.substituteVariables(workflowRef.getFilename(), getWorkflow().getContext().getVariables());

				final List<Variable> inheritedVariables = new ArrayList<Variable>();
				if (workflowRef.isFork())
				{
					// In multi-threading, we clone all variables to avoid concurrent modifications
					for (final Variable contextVariable : getWorkflow().getContext().getVariables()) {
						inheritedVariables.add(VariableHelper.clone(contextVariable));
					}
				}
				else
				{
					inheritedVariables.addAll(getWorkflow().getContext().getVariables());
				}

				final List<Variable> workflowVariables = workflowRef.getVariables();
				for (final Variable workflowVariable : workflowVariables) {
					Variable existingVariable = VariableHelper.exist(workflowVariable.getId(), inheritedVariables);
					final Variable newVariable;
					if (existingVariable == null) {
						newVariable = VariableHelper.addOrUpdateVariable(workflowVariable.getId(), workflowVariable.getValue(), workflowVariable.isOverwrite(), workflowVariable.isReport(), inheritedVariables);
					} else {
						newVariable = VariableHelper.clone(workflowVariable);
					}
					final String newValue = VariableHelper.substituteVariables(newVariable.getValue(), inheritedVariables);
					newVariable.setValue(newValue);
				}


				nestedWorkflowLauncher.launch(new File(filename), inheritedVariables);
			}
			else if(executable instanceof Loop)
			{
				final Loop loop = (Loop) executable;
				final String inValue = VariableHelper.substituteVariables(loop.getIn(), getWorkflow().getContext().getVariables());
				if(inValue != null && !inValue.isEmpty()) 
				{
					final Variable inVariable = new Variable();
					inVariable.setId(loop.getVariableId());
					getWorkflow().getContext().getVariables().add(inVariable);
					final String[] inIterationValues = inValue.split(" ");
					for (int i = 0; i < inIterationValues.length; i++) {
						inVariable.setValue(inIterationValues[i]);
						count = processExecutables(loop.getCallsAndWorkflowsAndLoops(), count);
					}
					getWorkflow().getContext().getVariables().remove(inVariable);	
				}
			}
			else if(executable instanceof Repeat)
			{
				final Repeat repeat = (Repeat) executable;
				
				
				if(repeat.getTimes() != null) {
					String srtTimes = repeat.getTimes();
					int times = 0;
					try {
						times = Integer.parseInt(srtTimes);
					} catch (NumberFormatException e1) {
						srtTimes = VariableHelper.substituteVariables(srtTimes, getWorkflow().getContext().getVariables());
						try {
							times = Integer.parseInt(srtTimes);
						} catch (NumberFormatException e2) {
							
						}
					}

					final Variable inVariable = new Variable();
					inVariable.setId(repeat.getVariableId());
					getWorkflow().getContext().getVariables().add(inVariable);
					
					for (int i=0; i<times; i++) {
						inVariable.setValue(String.valueOf(i));
						processExecutables(repeat.getCallsAndWorkflowsAndLoops(), count);
					}
				}
			}
			else if(executable instanceof PluginRef) {
				final PluginRef plugin = (PluginRef) executable;
				PluginsManager.invokePlugin(plugin.getName(), plugin, getWorkflow().getContext().getVariables());
			}
		}
		return count;
	}

	public Workflow getWorkflow() {
		return workflow;
	}

	private void processCall(final Call call, final int count) {

		println();println();println();
		String callName = "| Starting call '" + call.getName() + "' |";
		println(pad('-', callName.length()));
		println(callName);
		println(pad('-', callName.length()));

		HttpURLConnection connection = null;
		final CallReport callReport = workflowReport.getCallReports().get(count);

		try {
			connection = processRequest(callReport, call.getRequest(), count);

			println("------------------------------------------------------------------------------------------");

			processResponse(callReport, call.getResponse(), connection, count);

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (ProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			if (connection != null) {
				connection.disconnect();
				connection = null;
			}
		}
	}


	private HttpURLConnection processRequest(CallReport callReport, Request request, int count) throws MalformedURLException, IOException {
		println("REQUEST:");

		if (AzotConfig.GLOBAL.DEBUG) {
			final List<Variable> contextVariables = workflow.getContext().getVariables();
			for (final Variable variable : contextVariables) {
				println(" variable:"  + variable.getId() + " = " + variable.getValue());
			}
			println();
		}

		final String method = request.getMethod();
		final String url = getExpandedUrl(request);


		println("[" + method +"] " + url);
		println();

		callReport.setUrl(url);

		/* Contents */
		final List<Content> contents = getExpandedContents(request);

		/* External Contents */
		final List<ExternalContent> externalContents = getExpandedExternalContents(request);

		/* Headers */
		final List<Header> headers = getExpandedHeaders(request);

		// Request handling
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod(method);

		for (final Header header : headers) {
			connection.setRequestProperty(header.getName(), header.getValue());
		}

		dumpFile(workflowReport, count, contents, externalContents);

		connection.setDoOutput(true);

		// Multi part
		if(isMultipart(headers)) {

			final DataOutputStream out = new DataOutputStream(connection.getOutputStream());

			//Write the http request content
			for (final Content content : contents) {

				out.writeBytes("\n--" + BOUNDARY + "\r\n");
				out.writeBytes("Content-Disposition: form-data; name=\"" + content.getName() + "\"\r\n");
				out.writeBytes("Content-Type: " + content.getType() + " \n\n");
				out.write(content.getValue().getBytes(Charset.forName("UTF8")));

			}

			for (final ExternalContent eContent : externalContents) {

				File file = new File(eContent.getFilename());

				out.writeBytes("\n--" + BOUNDARY + "\r\n");
				out.writeBytes("Content-Disposition: form-data; name=\"" + eContent.getName() + "\"; filename=\"" + file.getName() + "\"\r\n");
				out.writeBytes("Content-Type: " + eContent.getType() + " \n\n");
				out.write(getFileContent(file));

			}

			out.writeBytes("\n--" + BOUNDARY + "\r\n");
			out.flush();
			out.close();
		} else {
			// Single Part
			String singleContent = null;

			if (request.getContents().size() > 0) {
				singleContent = request.getContents().get(0).getValue();
			}

			if (singleContent != null) {

				final String contentLenght = String.valueOf(singleContent.getBytes().length);
				connection.setRequestProperty("Content-Length", contentLenght);

				println(" header: "  + "Content-Length" + " = " + contentLenght);

				final DataOutputStream out = new DataOutputStream(connection.getOutputStream());
				out.write(singleContent.getBytes(Charset.forName("UTF8")));
				out.flush();
				out.close();
			}
		}
		
		return connection;
	}

	private void processResponse(CallReport callReport, Response response, HttpURLConnection connection, int count) throws IOException {
		println("RESPONSE:");

		final List<Variable> contextVariables = workflow.getContext().getVariables();

		final List<Variable> headerVariables = new ArrayList<Variable>();
		final Map<String,List<String>> responseHeaders = connection.getHeaderFields();
		for (final String headerName : responseHeaders.keySet()) {
			if (headerName != null) {
				final Variable newVariable = new Variable();
				newVariable.setId("header:" + headerName);
				newVariable.setValue(connection.getHeaderField(headerName));
				headerVariables.add(newVariable);
			}
		}
				
		final Variable statusVariable = new Variable();
		statusVariable.setId("meta:Status");
		statusVariable.setValue(connection.getHeaderField(null));
		headerVariables.add(statusVariable);

		final Variable codeVariable = new Variable();
		codeVariable.setId("meta:HttpCode");
		codeVariable.setValue(String.valueOf(connection.getResponseCode()));
		headerVariables.add(codeVariable);
		
		for (final Variable headerVariable : headerVariables) {
			println(" " + headerVariable.getId() + " = " + headerVariable.getValue());
		}

		// Add context variables for substitution purpose
		headerVariables.addAll(contextVariables);

		String kind = "out";
		boolean binary = false;
		String contentType = connection.getHeaderField("Content-Type");
		String contentEncoding = connection.getHeaderField("Content-Encoding");

		// Handle : text/xml;charset=utf-8
		if (contentType != null && contentType.contains(";")) {
			contentType = contentType.split(";")[0];
			contentType = contentType.trim();
		}
		
		if (contentType != null) {
			if (contentType.endsWith("xml")) {
				kind = "xml";
			}
			else if (contentType.endsWith("html")) {
				kind = "html";
			}
			else if (contentType.endsWith("plain")) {
				kind = "txt";
			}
			else if (contentType.endsWith("pdf")) {
				kind = "pdf";
				binary = true;
			}
			else if (contentType.equals("application/vnd.ms-excel")) {
				kind = "xls";
				binary = true;
			}
			else if (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
				kind = "xlsx";
				binary = true;
			}
			else if (contentType.equals("application/zip")) {
				kind = "zip";
				binary = true;
			}
			else if (contentType.startsWith("multipart/form-data")) {
				kind = "multipart";
				//binary = true;
			}
			else if (contentType.endsWith("jpg") || contentType.endsWith("jpeg")) {
				kind = "jpg";
				binary = true;
			}
			else if (contentType.endsWith("png")) {
				kind = "png";
				binary = true;
			}
			else if (contentType.endsWith("gif")) {
				kind = "gif";
				binary = true;
			}
			else if (contentType.endsWith("bmp")) {
				kind = "bmp";
				binary = true;
			}
		}

		if (contentEncoding != null && contentEncoding.equals("gzip")) {
			kind = "zip";
			binary = true;
		}

		
		// Raw data of response
		if (response != null) {
			if (response.getRaw() == null) {
				response.setRaw(new RawResponse());
			}
			final RawResponse rawResponse = response.getRaw();
			rawResponse.setCode(connection.getResponseCode());
			rawResponse.setStatus(connection.getHeaderField(null));
			
			for (final String headerName : responseHeaders.keySet()) {
				if (headerName != null) {
					final com.sap.azot.RawResponse.Header newResponseHeader = new com.sap.azot.RawResponse.Header();
					newResponseHeader.setName(headerName);
					newResponseHeader.setValue(connection.getHeaderField(headerName));
					rawResponse.getHeaders().add(newResponseHeader);
				}
			}
		}
		
		
		InputStream responseStream = null;
		try {
			// Normal content
			Object content = connection.getContent();
			if (content instanceof InputStream) {
				responseStream = (InputStream) content;				
			}
			else if(content instanceof ImageProducer) {
				responseStream = connection.getInputStream();
			}
		} catch (IOException e) {
			// Error content
			responseStream = connection.getErrorStream();
		}

		String responseContent = "";
		// Binary
		if (binary) {
			println("<binary>");
			println();

			if (AzotConfig.GLOBAL.DUMP && responseStream != null) {
				final File responseFile = new File(workflowReport.getOutputDirectory(), "call_" + String.valueOf(count) + "_response." + kind);
				final FileOutputStream out = new FileOutputStream(responseFile);
				copy(responseStream, out);
				out.flush();
				out.close();
				
				// Raw data of response
				if (response != null && response.getRaw() != null) {
					final RawResponse rawResponse = response.getRaw();
					
					final ExternalContent reponseContent = new ExternalContent();
					reponseContent.setType(contentType);
					reponseContent.setFilename(responseFile.getAbsolutePath());
					rawResponse.setExternalContent(reponseContent);
				}

				// Add a Variable with the dump path
				final Variable filepathVariable = new Variable();
				filepathVariable.setId("azot-dump-filepath");
				filepathVariable.setValue(responseFile.getCanonicalPath());
				headerVariables.add(filepathVariable);
			}
		} else {
			if (responseStream != null) {
				responseContent = stringify(responseStream);	
			}

			if (responseContent != null && !responseContent.isEmpty()) {

				// Add content as an Azot variable
				final Variable contentVariable = new Variable();
				contentVariable.setId("meta:Content");
				contentVariable.setValue(responseContent);
				headerVariables.add(contentVariable);

				println(" meta:Content = " + responseContent);

				if (AzotConfig.GLOBAL.DUMP) {
					final File responseFile = new File(workflowReport.getOutputDirectory(), "call_" + String.valueOf(count) + "_response." + kind);
					final FileOutputStream out = new FileOutputStream(responseFile);
					out.write(responseContent.getBytes());
					out.flush();
					out.close();
					
					// Add a Variable with the dump path
					final Variable filepathVariable = new Variable();
					filepathVariable.setId("azot-dump-filepath");
					filepathVariable.setValue(responseFile.getCanonicalPath());
					headerVariables.add(filepathVariable);
				}
			}
			
			// Raw data of response
			if (response != null && response.getRaw() != null) {
				final RawResponse rawResponse = response.getRaw();
				
				final Content reponseContent = new Content();
				reponseContent.setType(contentType);
				reponseContent.setValue(responseContent);
				rawResponse.setContent(reponseContent);
			}
		}
	
		
		if (response != null) {
			Document document = null;
			
			// Handle metadata on namespaces
			if (response.isNamespaceAware()) {
				try {
					final XPath xpath = XFACTORY.newXPath();
					document = initDocument(responseContent, document, response.isNamespaceAware());
					final UniversalNamespaceCache nsCache = new UniversalNamespaceCache(document, false);
					nsCache.setDebug(true);
					xpath.setNamespaceContext(nsCache);
					xpath.compile("/*");
					
					final Map<String, String> uriForPrefixes = nsCache.getUris();
					final Set<String> uris = uriForPrefixes.keySet();
					
					for (final String uri : uris) {
						// Add content as an Azot variable
						final Variable nsVariable = new Variable();
						final String nsVarName = "meta:NameSpace:" + uri;
						nsVariable.setId(nsVarName);
						nsVariable.setValue(uriForPrefixes.get(uri));
						headerVariables.add(nsVariable);
						
						println(" " + nsVariable.getId() + " = " + nsVariable.getValue());
					}
				} catch (XPathExpressionException e) {
					e.printStackTrace();
				}
			}

			final List<Variable> responseVariables = response.getVariables();
			for (final Variable responseVariable : responseVariables) {
				// Don't modify the variable in response call to keep initial expression
				final Variable variable = VariableHelper.addOrUpdateVariable(responseVariable.getId(), responseVariable.getValue(), responseVariable.isOverwrite(), responseVariable.isReport(), contextVariables);

				String value = VariableHelper.substituteVariables(variable.getValue(), headerVariables);

				if (responseContent != null) {
					if(value.startsWith(XPATH)) {
						// Init XML document (if needed)
						document = initDocument(responseContent, document, response.isNamespaceAware());
						value = processXPathValue(document, value, response.isNamespaceAware(), contextVariables);	
					}
					else if(value.startsWith(AZOT)){
						value = processAzotValue(responseContent, value, contextVariables);
					}
				}

				variable.setValue(value);

				println();
				println("Setting variable: ${" + variable.getId() + "} = " + value);
			}

			// Plugins handling
			final List<PluginRef> responsePlugins = response.getPlugins();
			for (final PluginRef responsePlugin : responsePlugins) {
				PluginsManager.invokePlugin(responsePlugin.getName(), responsePlugin, headerVariables);
			}

			
			// Assertions handling
			final List<Assert> responseAsserts = response.getAsserts();
			for (final Assert responseAssert : responseAsserts) {
				String actual = responseAssert.getActual();
				actual = VariableHelper.substituteVariables(actual, headerVariables);
				actual = VariableHelper.substituteVariables(actual, contextVariables);

				if (responseContent != null && actual.startsWith(XPATH)) {
					// Init XML document (if needed)
					document = initDocument(responseContent, document, response.isNamespaceAware());
					actual = processXPathValue(document, actual, response.isNamespaceAware(), contextVariables);
				}

				String expected = responseAssert.getExpected();
				expected = VariableHelper.substituteVariables(expected, headerVariables);
				expected = VariableHelper.substituteVariables(expected, contextVariables);
				if (responseContent != null && expected.startsWith(XPATH)) {
					// Init XML document (if needed)
					document = initDocument(responseContent, document, response.isNamespaceAware());
					expected = processXPathValue(document, expected, response.isNamespaceAware(), contextVariables);
				}

				if (actual != null && !actual.equals(expected)) {
					callReport.setStatus(Status.FAILURE);
					callReport.setType("com.sap.azot.AssertionFailedError");
					callReport.setFailureMessage("Assert failure: expected='" + expected + "' actual='" + actual + "'\n" +
							"Original values: expected='" + responseAssert.getExpected() + "' actual='" + responseAssert.getActual() + "'\n" +
									"Response content: " + response.getRaw().getContent().getValue());
					break;
				}
			}
		}

	}

	private String getExpandedUrl(final Request request) {
		return VariableHelper.substituteVariables(request.getUrl(), workflow.getContext().getVariables());
	}

	private boolean isMultipart(final List<Header> headers) {

		for (final Header header : headers) {
			if (header.getName().equals("Content-Type") && header.getValue().startsWith("multipart/form-data")) {
				return true;
			}
		}

		return false;		
	}

	private void dumpFile(WorkflowReport workflowReport, int count, List<Content> contents, List<ExternalContent> externalContents) throws IOException {
		if (AzotConfig.GLOBAL.DUMP) {
			final File requestFile = new File(workflowReport.getOutputDirectory(), "call_" + String.valueOf(count) + "_request.xml");
			FileWriter writer = null;

			if (contents != null && contents.size() > 0) {
				if (writer == null) {
					writer = new FileWriter(requestFile);
				}
				for (final Content content : contents) {
					writer.append(content.getValue());
				}
			}

			if (externalContents != null && externalContents.size() > 0) {
				if (writer == null) {
					writer = new FileWriter(requestFile);
				}
				for (final ExternalContent eContent : externalContents) {
					writer.append(eContent.getFilename());
				}
			}

			if (writer != null) {
				writer.flush();
				writer.close();
			}
		}

	}

	private List<Header> getExpandedHeaders(final Request request) {

		final List<Header> headers = request.getHeaders();
		final List<Variable> contextVariables = workflow.getContext().getVariables();

		for (final Header header : headers) {
			final String newValue = VariableHelper.substituteVariables(header.getValue(), contextVariables);
			header.setValue(newValue);

			if (header.getName().equals("Content-Type") && header.getValue().startsWith("multipart/form-data")) {
				header.setValue("multipart/form-data;boundary=" + BOUNDARY);
			}

			println(" header:"  + header.getName() + " = " + header.getValue());
		}

		return headers;
	}

	private List<ExternalContent> getExpandedExternalContents(final Request request) {

		final List<ExternalContent> externalContents = request.getExternalContents();
		final List<Variable> contextVariables = workflow.getContext().getVariables();

		for (final ExternalContent eContent : externalContents) {

			print("External content :");

			if (eContent.getName() != null) {
				final String newName = VariableHelper.substituteVariables(eContent.getName(), contextVariables);
				eContent.setName(newName);

				print(" name="  + eContent.getName());
			}

			if (eContent.getType() != null) {
				final String newType = VariableHelper.substituteVariables(eContent.getType(), contextVariables);			
				eContent.setType(newType);

				print(" type="  + eContent.getType());
			}

			if (eContent.getFilename() != null) {
				final String newFilename = VariableHelper.substituteVariables(eContent.getFilename(), contextVariables);			
				eContent.setFilename(newFilename);

				print(" filename="  + eContent.getFilename());
			}

			println();
		}

		return externalContents;
	}

	private List<Content> getExpandedContents(final Request request) {

		List<Content> contents = request.getContents();
		final List<Variable> contextVariables = workflow.getContext().getVariables();

		for (Content content : contents) {

			print("Content :");

			if (content.getName() != null) {
				final String newName = VariableHelper.substituteVariables(content.getName(), contextVariables);
				content.setName(newName);

				print(" name="  + content.getName());
			}

			if (content.getType() != null) {
				final String newType = VariableHelper.substituteVariables(content.getType(), contextVariables);			
				content.setType(newType);

				print(" type="  + content.getType());
			}

			println();

			if (content.getValue() != null) {
				final String newValue = VariableHelper.substituteVariables(content.getValue(), contextVariables);			
				content.setValue(newValue.trim());

				println(content.getValue());
			}
		}

		return contents;
	}

	private byte[] getFileContent(File file) {

		//TODO test file size

		byte[] buffer = new byte[(int) file.length()];
		InputStream ios = null;
		try {
			ios = new FileInputStream(file);
			if ( ios.read(buffer) == -1 ) {
				throw new IOException("EOF reached while trying to read the whole file");
			}        
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally { 
			try {
				if ( ios != null ) 
					ios.close();
			} catch ( IOException e) {
			}
		}

		return buffer;
	}

	private String generateBoundary() {

		final char[] BOUNDARY_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
		final String BOUNDARY_BASE = "---BOUNDARY-";

		StringBuilder buffer = new StringBuilder();
		buffer.append(BOUNDARY_BASE);
		Random rand = new Random();
		int count = rand.nextInt(11) + 30; // count between 30 and 40
		for (int i = 0; i < count; i++) {
			buffer.append(BOUNDARY_CHARS[rand.nextInt(BOUNDARY_CHARS.length)]);
		}
		return buffer.toString();
	}

	
	
	private String processXPathValue(Document document, String v, boolean namespaceAware, final List<Variable> contextVariables) {
		if(v.contains(XPATH_EXPR)) {
			String xpathExpr = v.substring(v.indexOf(XPATH_EXPR) + XPATH_EXPR.length(), v.lastIndexOf("}"));
			xpathExpr = VariableHelper.substituteVariables(xpathExpr, contextVariables);
			try {
				final XPath xpath = XFACTORY.newXPath();
				if (namespaceAware) {
					xpath.setNamespaceContext(new UniversalNamespaceCache(document, false));	
				}
				
				final XPathExpression expr = xpath.compile(xpathExpr);

				final Object result = expr.evaluate(document, XPathConstants.NODESET);
				final NodeList nodes = (NodeList) result;
				v = "";
				for (int i = 0; i < nodes.getLength(); i++) {
					if(i != 0) {
						v += " ";
					}
					v += nodes.item(i).getNodeValue();
				}

			} catch (XPathExpressionException e) {
				e.printStackTrace();
			}

		}
		else if(v.contains(XPATH_EVAL)) {
			String xpathEval = v.substring(v.indexOf(XPATH_EVAL) + XPATH_EVAL.length(), v.lastIndexOf("}"));
			xpathEval = VariableHelper.substituteVariables(xpathEval, contextVariables);
			
			try {
				final XPath xpath = XFACTORY.newXPath();
				if (namespaceAware) {
					xpath.setNamespaceContext(new UniversalNamespaceCache(document, false));	
				}
				
				final XPathExpression expr = xpath.compile(xpathEval);

				final Object result = expr.evaluate(document, XPathConstants.STRING);
				v = result.toString();

			} catch (XPathExpressionException e) {
				e.printStackTrace();
			}
		}
		return v;
	}

	private String processAzotValue(String responseContent, String v, final List<Variable> contextVariables) {
		if(v.contains(AZOT_REPLACE)) {
			String azotExpr = v.substring(v.indexOf(AZOT_REPLACE) + AZOT_REPLACE.length(), v.lastIndexOf("}"));
			azotExpr = VariableHelper.substituteVariables(azotExpr, contextVariables);
			
			Pattern p = Pattern.compile("'(.+)','(.+)','(.+)'");
			Matcher m = p.matcher(azotExpr);
			if(m.groupCount() == 3) {
				while(m.find())
				{
					String value = m.group(1);
					String oldValue = m.group(2);
					String newValue = m.group(3);
					
					return value.replace(oldValue, newValue);
				}				
			}
			
			return v;
		}
		return v;
	}

	private Document initDocument(String responseContent, Document document, boolean namespaceAware) {
		if(document == null) {
			try {
				final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(namespaceAware);
				final DocumentBuilder builder = factory.newDocumentBuilder();
				document = builder.parse(new ByteArrayInputStream(responseContent.getBytes("UTF8")));
			} catch (UnsupportedEncodingException e) {
				throw new AzotException(e);
			} catch (ParserConfigurationException e) {
				throw new AzotException(e);
			} catch (SAXException e) {
				throw new AzotException(e);
			} catch (IOException e) {
				throw new AzotException(e);
			}
		}
		return document;
	}


	private String stringify(final InputStream inputStream) {
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
				throw new RuntimeException(e);
			}
		}
	}

	private final int copy(InputStream paramInputStream, OutputStream paramOutputStream) throws IOException {

		byte[] arrayOfByte = new byte[4096];
		int i = 0;
		int j = 0;
		while (-1 != (j = paramInputStream.read(arrayOfByte))) {
			paramOutputStream.write(arrayOfByte, 0, j);
			i += j;
		}
		return i;
	}

	private void println() {
		if (AzotConfig.GLOBAL.VERBOSE) {
			System.out.println();
		}
	}

	private void println(String msg) {
		if (AzotConfig.GLOBAL.VERBOSE) {
			System.out.println(msg);
		}
	}

	private void print(String msg) {
		if (AzotConfig.GLOBAL.VERBOSE) {
			System.out.print(msg);
		}
	}

	private void echo(String msg) {
		System.out.print(msg);
	}

	private String pad (char ch, int n) {
		return String.format("%" + n + "s", "").replace(' ', ch);
	}
}
