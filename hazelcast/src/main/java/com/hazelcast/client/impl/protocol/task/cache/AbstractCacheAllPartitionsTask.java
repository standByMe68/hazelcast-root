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

package com.hazelcast.client.impl.protocol.task.cache;

import com.hazelcast.cache.CacheNotExistsException;
import com.hazelcast.cache.impl.CacheOperationProvider;
import com.hazelcast.cache.impl.CacheService;
import com.hazelcast.cache.impl.ICacheService;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.task.AbstractAllPartitionsMessageTask;
import com.hazelcast.config.CacheConfig;
import com.hazelcast.instance.Node;
import com.hazelcast.nio.Connection;

import java.security.Permission;

/**
 * Abstract Cache All Partitions request to handle InMemoryFormat which needed for operation provider
 */
abstract class AbstractCacheAllPartitionsTask<P>
        extends AbstractAllPartitionsMessageTask<P> {

    public AbstractCacheAllPartitionsTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    protected CacheOperationProvider getOperationProvider(String name) {
        ICacheService service = getService(CacheService.SERVICE_NAME);
        CacheConfig cacheConfig = service.getCacheConfig(name);
        if (cacheConfig == null) {
            throw new CacheNotExistsException("Cache config for cache " + name + " has not been created yet!");
        }
        return service.getCacheOperationProvider(name, cacheConfig.getInMemoryFormat());
    }

    @Override
    public String getServiceName() {
        return CacheService.SERVICE_NAME;
    }

    @Override
    public String getMethodName() {
        return null;
    }

    @Override
    public Object[] getParameters() {
        return null;
    }

    @Override
    public Permission getRequiredPermission() {
        return null;
    }

}
