/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.config;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import static com.hazelcast.config.XmlElements.IMPORT;
import static java.lang.String.format;

/**
 * Contains logic for replacing system variables in the XML file and importing XML files from different locations.
 */
public abstract class AbstractConfigBuilder extends AbstractXmlConfigHelper {

    protected enum ConfigType {
        SERVER("hazelcast"),
        CLIENT("hazelcast-client"),
        JET("hazelcast-jet");

        final String name;

        ConfigType(String name) {
            this.name = name;
        }
    }

    private static final ILogger LOGGER = Logger.getLogger(AbstractConfigBuilder.class);

    private final Set<String> currentlyImportedFiles = new HashSet<String>();
    private final XPath xpath;

    public AbstractConfigBuilder() {
        XPathFactory fac = XPathFactory.newInstance();
        this.xpath = fac.newXPath();

        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "hz".equals(prefix) ? xmlns : null;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator getPrefixes(String namespaceURI) {
                return null;
            }
        });
    }

    protected void process(Node root) throws Exception {
        traverseChildrenAndReplaceVariables(root);
        replaceImportElementsWithActualFileContents(root);
    }

    private void replaceImportElementsWithActualFileContents(Node root) throws Exception {
        //获取文件对象
        Document document = root.getOwnerDocument();
        NodeList misplacedImports = (NodeList) xpath.evaluate(
                format("//hz:%s/parent::*[not(self::hz:%s)]", IMPORT.name, getXmlType().name), document, XPathConstants.NODESET);
        if (misplacedImports.getLength() > 0) {
            throw new InvalidConfigurationException("<import> element can appear only in the top level of the XML");
        }
        NodeList importTags = (NodeList) xpath.evaluate(
                format("/hz:%s/hz:%s", getXmlType().name, IMPORT.name), document, XPathConstants.NODESET);
        for (Node node : asElementIterable(importTags)) {
            loadAndReplaceImportElement(root, node);
        }
    }

    private void loadAndReplaceImportElement(Node root, Node node) throws Exception {
        NamedNodeMap attributes = node.getAttributes();
        Node resourceAttribute = attributes.getNamedItem("resource");
        String resource = resourceAttribute.getTextContent();
        URL url = ConfigLoader.locateConfig(resource);
        if (url == null) {
            throw new InvalidConfigurationException("Failed to load resource: " + resource);
        }
        if (!currentlyImportedFiles.add(url.getPath())) {
            throw new InvalidConfigurationException("Cyclic loading of resource " + url.getPath() + " is detected!");
        }
        Document doc = parse(url.openStream());
        Element importedRoot = doc.getDocumentElement();
        traverseChildrenAndReplaceVariables(importedRoot);
        replaceImportElementsWithActualFileContents(importedRoot);
        for (Node fromImportedDoc : childElements(importedRoot)) {
            Node importedNode = root.getOwnerDocument().importNode(fromImportedDoc, true);
            root.insertBefore(importedNode, node);
        }
        root.removeChild(node);
    }

    /**
     * Reads XML from InputStream and parses.
     *
     * @param inputStream {@link InputStream} to read from
     * @return Document after parsing XML
     * @throws Exception if the XML configuration cannot be parsed or is invalid
     */
    protected abstract Document parse(InputStream inputStream) throws Exception;

    /**
     * @return system properties
     */
    protected abstract Properties getProperties();

    /**
     * @return ConfigType of current config class as enum value
     */
    @Override
    protected abstract ConfigType getXmlType();


    /**
     * 替换和检验当前XML当中的部分需要替换的变量单位
     * @param root 文件根节点
     */
    private void traverseChildrenAndReplaceVariables(Node root) {
        //获取当前根节点的属性对象
        NamedNodeMap attributes = root.getAttributes();
        if (attributes != null) {
            for (int k = 0; k < attributes.getLength(); k++) {
                //获取当前的属性节点
                Node attribute = attributes.item(k);
                replaceVariables(attribute);
            }
        }
        //如果当前根节点只有当前节点
        if (root.getNodeValue() != null) {
            replaceVariables(root);
        }
        //获取当前所有的子节点列表
        final NodeList childNodes = root.getChildNodes();
        for (int k = 0; k < childNodes.getLength(); k++) {
            Node child = childNodes.item(k);
            //深沉遍历
            traverseChildrenAndReplaceVariables(child);
        }
    }

    private void replaceVariables(Node node) {
        //获取当前节点值
        String value = node.getNodeValue();
        StringBuilder sb = new StringBuilder(value);
        int endIndex = -1;
        int startIndex = sb.indexOf("${");
        while (startIndex > -1) {
            //如果当前属性值只有}没有${当前值的属性不读
            endIndex = sb.indexOf("}", startIndex);
            if (endIndex == -1) {
                LOGGER.warning("Bad variable syntax. Could not find a closing curly bracket '}' on node: " + node.getLocalName());
                break;
            }
            //获取当前需要替换的变量名称
            String variable = sb.substring(startIndex + 2, endIndex);
            //根据变量的配置文件获取当前变量名对应的值
            String variableReplacement = getProperties().getProperty(variable);
            if (variableReplacement != null) {
                //替换成对应的数值
                sb.replace(startIndex, endIndex + 1, variableReplacement);
                endIndex = startIndex + variableReplacement.length();
            } else {
                LOGGER.warning("Could not find a value for property  '" + variable + "' on node: " + node.getLocalName());
            }
            //获取下一个需要替换的变量
            startIndex = sb.indexOf("${", endIndex);
        }
        //将节点的数值改编成当前的已经替换变量的值
        node.setNodeValue(sb.toString());
    }
}
