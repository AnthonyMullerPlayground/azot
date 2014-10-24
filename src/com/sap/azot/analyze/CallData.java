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

/**
 * @author amuller
 */
public class CallData {

	private Status status = Status.SUCCESS;
	
	private final int startTime;
	private final int endTime;
	
	private int duration;

	public CallData(final int startTime, final int endTime) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.duration = endTime - startTime;
	}

	
	public int getStartTime() {
		return startTime;
	}
	
	public int getEndTime() {
		return endTime;
	}

	public int getDuration() {
		return duration;
	}
	
	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}



	public static enum Status {
		SUCCESS, FAILURE, ERROR
	}
}
