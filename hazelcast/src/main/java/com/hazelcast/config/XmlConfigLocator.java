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

import com.hazelcast.core.HazelcastException;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Support class for the {@link com.hazelcast.config.XmlConfigBuilder} that locates the XML configuration:
 * <ol>
 * <li>system property</li>
 * <li>working directory</li>
 * <li>classpath</li>
 * <li>default</li>
 * </ol>
 */
public class XmlConfigLocator {

    private static final ILogger LOGGER = Logger.getLogger(XmlConfigLocator.class);

    private InputStream in;
    private File configurationFile;
    private URL configurationUrl;

    /**
     * Constructs a XmlConfigLocator that tries to find a usable XML configuration file.
     *
     * @throws HazelcastException if there was a problem locating the config-file
     */
    /**
     * 获取一个配置文件的配置
     */
    public XmlConfigLocator() {
        try {
            //从系统文件中获取当前hazelcast.config
            if (loadFromSystemProperty()) {
                return;
            }
            //查询当前目录下文件名为hazelcase.xml
            if (loadFromWorkingDirectory()) {
                return;
            }
            //查询当前源文件目录中文件名为hazelcase.xml
            if (loadHazelcastXmlFromClasspath()) {
                return;
            }
            //查询当前源文件目录下文件名为hazelcast-default.xml
            loadDefaultConfigurationFromClasspath();
        } catch (RuntimeException e) {
            throw new HazelcastException(e);
        }
    }

    public InputStream getIn() {
        return in;
    }

    public File getConfigurationFile() {
        return configurationFile;
    }

    public URL getConfigurationUrl() {
        return configurationUrl;
    }

    private void loadDefaultConfigurationFromClasspath() {
        LOGGER.info("Loading 'hazelcast-default.xml' from classpath.");

        configurationUrl = Config.class.getClassLoader().getResource("hazelcast-default.xml");

        if (configurationUrl == null) {
            throw new HazelcastException("Could not find 'hazelcast-default.xml' in the classpath!"
                    + "This may be due to a wrong-packaged or corrupted jar file.");
        }
        //返回当前文件输入流
        in = Config.class.getClassLoader().getResourceAsStream("hazelcast-default.xml");
        if (in == null) {
            throw new HazelcastException("Could not load 'hazelcast-default.xml' from classpath");
        }
    }

    private boolean loadHazelcastXmlFromClasspath() {
        URL url = Config.class.getClassLoader().getResource("hazelcast.xml");
        if (url == null) {
            LOGGER.finest("Could not find 'hazelcast.xml' in classpath.");
            return false;
        }

        LOGGER.info("Loading 'hazelcast.xml' from classpath.");

        configurationUrl = url;
        in = Config.class.getClassLoader().getResourceAsStream("hazelcast.xml");
        if (in == null) {
            throw new HazelcastException("Could not load 'hazelcast.xml' from classpath");
        }
        return true;
    }

    private boolean loadFromWorkingDirectory() {
        File file = new File("hazelcast.xml");
        if (!file.exists()) {
            LOGGER.finest("Could not find 'hazelcast.xml' in working directory.");
            return false;
        }

        LOGGER.info("Loading 'hazelcast.xml' from working directory.");

        configurationFile = file;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new HazelcastException("Failed to open file: " + file.getAbsolutePath(), e);
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(System.getProperties());
    }

    private boolean loadFromSystemProperty() {
        //查看当前系统的配置文件是否有配置hazelcast
        String configSystemProperty = System.getProperty("hazelcast.config");
        //如果没有配置打出日志返回
        if (configSystemProperty == null) {
            LOGGER.finest("Could not 'hazelcast.config' System property");
            return false;
        }

        LOGGER.info("Loading configuration " + configSystemProperty + " from System property 'hazelcast.config'");
        //如果当前配置文件是在源文件目录路径下
        if (configSystemProperty.startsWith("classpath:")) {
            loadSystemPropertyClassPathResource(configSystemProperty);
        } else {
            loadSystemPropertyFileResource(configSystemProperty);
        }
        return true;
    }

    private void loadSystemPropertyFileResource(String configSystemProperty) {
        // it's a file
        configurationFile = new File(configSystemProperty);
        LOGGER.info("Using configuration file at " + configurationFile.getAbsolutePath());

        if (!configurationFile.exists()) {
            String msg = "Config file at '" + configurationFile.getAbsolutePath() + "' doesn't exist.";
            throw new HazelcastException(msg);
        }

        try {
            in = new FileInputStream(configurationFile);
        } catch (FileNotFoundException e) {
            throw new HazelcastException("Failed to open file: " + configurationFile.getAbsolutePath(), e);
        }

        try {
            configurationUrl = configurationFile.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new HazelcastException("Failed to create URL from the file: " + configurationFile.getAbsolutePath(), e);
        }
    }

    private void loadSystemPropertyClassPathResource(String configSystemProperty) {
        // it's an explicit configured classpath resource
        String resource = configSystemProperty.substring("classpath:".length());

        LOGGER.info("Using classpath resource at " + resource);

        if (resource.isEmpty()) {
            throw new HazelcastException("classpath resource can't be empty");
        }

        in = Config.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new HazelcastException("Could not load classpath resource: " + resource);
        }
        configurationUrl = Config.class.getResource(resource);
    }
}
