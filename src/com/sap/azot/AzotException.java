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
package com.sap.azot;

/**
 * Exception thrown into Azot.
 *
 * @author amuller
 */
public class AzotException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AzotException() {
		super();
	}

	public AzotException(String message, Throwable cause) {
		super(message, cause);
	}

	public AzotException(String message) {
		super(message);
	}

	public AzotException(Throwable cause) {
		super(cause);
	}
}
