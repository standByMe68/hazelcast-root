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

package com.hazelcast.core.server;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static com.hazelcast.nio.IOUtil.closeResource;

/**
 * Starts a Hazelcast server.
 */
public final class StartServer {

    private StartServer() {
    }

    /**
     * Creates a server instance of Hazelcast.
     * <p>
     * If user sets the system property "print.port", the server writes the port number of the Hazelcast instance to a file.
     * The file name is the same as the "print.port" property.
     *
     * @param args none
     */
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        //使用Hazelcast的静态方法
        HazelcastInstance hz = Hazelcast.newHazelcastInstance();
        printMemberPort(hz);
    }



    private static void printMemberPort(HazelcastInstance hz) throws FileNotFoundException, UnsupportedEncodingException {
        String printPort = System.getProperty("print.port");
        if (printPort != null) {
            PrintWriter printWriter = null;
            try {
                printWriter = new PrintWriter("ports" + File.separator + printPort, "UTF-8");
                printWriter.println(hz.getCluster().getLocalMember().getAddress().getPort());
            } finally {
                closeResource(printWriter);
            }
        }
    }
}
