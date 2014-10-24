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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Inspired from: http://www.ibm.com/developerworks/library/x-nmspccontext/
 * 
 * @author amuller
 */
public class UniversalNamespaceCache implements NamespaceContext {
    private static final String DEFAULT_NS = "azotns";
    private Map<String, String> prefix2Uri = new HashMap<String, String>();
    private Map<String, String> uri2Prefix = new HashMap<String, String>();

    private boolean debug = false;
    
    /**
     * This constructor parses the document and stores all namespaces it can
     * find. If toplevelOnly is true, only namespaces in the root are used.
     * 
     * @param document
     *            source document
     * @param toplevelOnly
     *            restriction of the search to enhance performance
     */
    public UniversalNamespaceCache(Document document, boolean toplevelOnly) {
        examineNode(document.getFirstChild(), toplevelOnly);
        if (AzotConfig.GLOBAL.DEBUG && isDebug()) {
            System.out.println("The list of the cached namespaces:");
            for (String key : prefix2Uri.keySet()) {
                System.out.println("prefix " + key + ": uri " + prefix2Uri.get(key));
            }	
		}
    }

    /**
     * A single node is read, the namespace attributes are extracted and stored.
     * 
     * @param node
     *            to examine
     * @param attributesOnly,
     *            if true no recursion happens
     */
    private void examineNode(Node node, boolean attributesOnly) {
        NamedNodeMap attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            storeAttribute((Attr) attribute);
        }

        if (!attributesOnly) {
            NodeList chields = node.getChildNodes();
            for (int i = 0; i < chields.getLength(); i++) {
                Node chield = chields.item(i);
                if (chield.getNodeType() == Node.ELEMENT_NODE)
                    examineNode(chield, false);
            }
        }
    }

    /**
     * This method looks at an attribute and stores it, if it is a namespace
     * attribute.
     * 
     * @param attribute
     *            to examine
     */
    private void storeAttribute(Attr attribute) {
        // examine the attributes in namespace xmlns
        if (attribute.getNamespaceURI() != null
                && attribute.getNamespaceURI().equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            // Default namespace xmlns="uri goes here"
            if (attribute.getNodeName().equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                putInCache(DEFAULT_NS, attribute.getNodeValue());
            } else {
                // The defined prefixes are stored here
                putInCache(attribute.getLocalName(), attribute.getNodeValue());
            }
        }

    }

    private void putInCache(String prefix, String uri) {
    	// Not already in cache
    	if (!uri2Prefix.containsKey(uri)) {
    		// Find an available default namespace
        	if (DEFAULT_NS.equals(prefix)) {
        		int index = 0;
        		while(prefix2Uri.containsKey(DEFAULT_NS + index)){
        			index++;
        		}
        		prefix = DEFAULT_NS + index;
        	}
        	
            prefix2Uri.put(prefix, uri);
            uri2Prefix.put(uri, prefix);	
    	}
    }

    /**
     * This method is called by XPath. It returns the default namespace, if the
     * prefix is null or "".
     * 
     * @param prefix
     *            to search for
     * @return uri
     */
    public String getNamespaceURI(String prefix) {
        if (prefix == null || prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
            return prefix2Uri.get(DEFAULT_NS);
        } else {
            return prefix2Uri.get(prefix);
        }
    }

    /**
     * This method is not needed in this context, but can be implemented in a
     * similar way.
     */
    public String getPrefix(String namespaceURI) {
        return uri2Prefix.get(namespaceURI);
    }

    /**
     * List of prefixes, by URI -> PREFIX
     * @return
     */
    public Map<String, String> getUris() {
        return uri2Prefix;
    }

	@Override
	public Iterator<?> getPrefixes(String namespaceURI) {
		return null;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}
}