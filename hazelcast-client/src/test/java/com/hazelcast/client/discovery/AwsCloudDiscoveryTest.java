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

package com.hazelcast.client.discovery;

import com.hazelcast.aws.AwsDiscoveryStrategyFactory;
import com.hazelcast.aws.AwsProperties;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientAwsConfig;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.spi.impl.AwsAddressProvider;
import com.hazelcast.client.spi.properties.ClientProperty;
import com.hazelcast.config.DiscoveryStrategyConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.nio.Address;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.SlowTest;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.aws.AwsProperties.CONNECTION_TIMEOUT_SECONDS;
import static com.hazelcast.aws.AwsProperties.PORT;
import static com.hazelcast.aws.AwsProperties.TAG_KEY;
import static com.hazelcast.aws.AwsProperties.TAG_VALUE;
import static com.hazelcast.test.JenkinsDetector.isOnJenkins;
import static com.hazelcast.util.CollectionUtil.isNotEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

/**
 * NOTE: This tests needs AWS credentials to be set as environment variables!
 * <p>
 * Please set {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY} with the according values.
 */
@RunWith(HazelcastParallelClassRunner.class)
@Category({SlowTest.class, ParallelTest.class})
public class AwsCloudDiscoveryTest {

    private static final String ACCESS_KEY = AwsProperties.ACCESS_KEY.getDefinition().key();
    private static final String SECRET_KEY = AwsProperties.SECRET_KEY.getDefinition().key();
    private static final String AWS_TEST_TAG = "aws-test-tag";
    private static final String AWS_TEST_TAG_VALUE = "aws-tag-value-1";

    @Test
    public void testAwsClient_MemberNonDefaultPortConfig() {
        Map<String, Comparable> props = new HashMap<String, Comparable>();
        props.put(PORT.getDefinition().key(), "60000");
        props.put(ACCESS_KEY, System.getenv("AWS_ACCESS_KEY_ID"));
        props.put(SECRET_KEY, System.getenv("AWS_SECRET_ACCESS_KEY"));
        props.put(TAG_KEY.getDefinition().key(), AWS_TEST_TAG);
        props.put(TAG_VALUE.getDefinition().key(), AWS_TEST_TAG_VALUE);
        props.put(CONNECTION_TIMEOUT_SECONDS.getDefinition().key(), "10");

        if (isOnJenkins()) {
            assertNotNull("AWS_ACCESS_KEY_ID is not set", props.get(ACCESS_KEY));
            assertNotNull("AWS_SECRET_ACCESS_KEY is not set", props.get(SECRET_KEY));
        } else {
            assumeThat("AWS_ACCESS_KEY_ID is not set", props.get(ACCESS_KEY), Matchers.<Comparable>notNullValue());
            assumeThat("AWS_SECRET_ACCESS_KEY is not set", props.get(SECRET_KEY), Matchers.<Comparable>notNullValue());
        }

        ClientConfig config = new ClientConfig();
        config.getNetworkConfig().getDiscoveryConfig()
                .addDiscoveryStrategyConfig(new DiscoveryStrategyConfig(new AwsDiscoveryStrategyFactory(), props));

        config.setProperty(ClientProperty.DISCOVERY_SPI_ENABLED.getName(), "true");
        config.setProperty(ClientProperty.DISCOVERY_SPI_PUBLIC_IP_ENABLED.getName(), "true");

        HazelcastInstance client = HazelcastClient.newHazelcastClient(config);
        IMap<Object, Object> map = client.getMap("MyMap");
        map.put(1, 5);
        assertEquals(5, map.get(1));
    }

    @Test
    @Ignore(value = "https://github.com/hazelcast/hazelcast/issues/11571")
    public void testAwsAddressProvider() {
        String awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        ClientAwsConfig clientAwsConfig = new ClientAwsConfig();

        if (isOnJenkins()) {
            assertNotNull("AWS_ACCESS_KEY_ID is not set", awsAccessKeyId);
            assertNotNull("AWS_SECRET_ACCESS_KEY is not set", awsSecretAccessKey);
            clientAwsConfig.setInsideAws(true);
        } else {
            assumeThat("AWS_ACCESS_KEY_ID is not set", awsAccessKeyId, Matchers.<String>notNullValue());
            assumeThat("AWS_SECRET_ACCESS_KEY is not set", awsSecretAccessKey, Matchers.<String>notNullValue());
            clientAwsConfig.setInsideAws(false);
        }

        clientAwsConfig.setEnabled(true)
                .setAccessKey(awsAccessKeyId)
                .setSecretKey(awsSecretAccessKey)
                .setTagKey(AWS_TEST_TAG)
                .setTagValue(AWS_TEST_TAG_VALUE);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().setAwsConfig(clientAwsConfig);

        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IMap<Object, Object> map = client.getMap("MyMap");
        map.put(1, 5);
        assertEquals(5, map.get(1));

        AwsAddressProvider awsAddressProvider = new AwsAddressProvider(clientAwsConfig, client.getLoggingService());
        Collection<Address> addresses = awsAddressProvider.loadAddresses();
        assertTrue(isNotEmpty(addresses));
    }
}
