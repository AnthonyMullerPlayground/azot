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

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author amuller
 */
public class CallReport {

	private long startTime = System.currentTimeMillis();
	private long endTime = System.currentTimeMillis();
	
	private String url;
	private String name;
	private Status status = Status.SUCCESS;
	private String message;
	private String type;
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public CallReport(final String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public long getStartTime() {
		return startTime;
	}
	
	public long getEndTime() {
		return endTime;
	}
	
	public float getTime() {
		return ((float)(endTime-startTime))/((float)1000);
	}
	
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	
	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status = status;
	}
	
	public String getMessage() {
		return message;
	}

	public void setErrorMessage(Exception cause) {
		final StringWriter writer = new StringWriter();
		cause.printStackTrace(new PrintWriter(writer));
		
		this.message = writer.toString();
	}
	
	public void setFailureMessage(String cause) {
		this.message = cause;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}

	public static enum Status {
		SUCCESS, FAILURE, ERROR
	}
}
