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
public class CallKey implements Comparable<CallKey> {

	private int position;
	private String name;
	
	public CallKey(int position, String name) {
		this.position = position;
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public int getPosition() {
		return position;
	}

	@Override
	public int compareTo(final CallKey other) {
		if(position < other.position) {
			return -1;
		}
		else if(position > other.position) {
			return 1;
		}
		return 0;
	}
}
