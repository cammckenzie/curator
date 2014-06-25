/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */

package org.apache.curator.framework.recipes.cache;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.DescendantHandlingMode;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.*;

public class TestPathChildrenCacheRecursive extends BaseClassForTests
{
    /**
     * Test the case where there is a whole tree but we're only interested in updates of direct
     * descendants
     *
     * @throws Exception
     */
    @Test
    public void testCacheDirectDesendantsOnly() throws Exception
    {
        Timing timing = new Timing();
        PathChildrenCache cache = null;

        CuratorFramework client = CuratorFrameworkFactory.newClient(
            server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(
            1));

        try
        {
            client.start();

            final CountDownLatch initLatch = new CountDownLatch(1);

            final CountDownLatch createLatch = new CountDownLatch(1);
            final CountDownLatch updateLatch = new CountDownLatch(1);
            final CountDownLatch deleteLatch = new CountDownLatch(1);

            final String root = "/cachetest";

            cache = new PathChildrenCache(
                client, root, true, DescendantHandlingMode.DIRECT_DESCENDANTS_ONLY);
            cache.getListenable().addListener(new PathChildrenCacheListener()
            {

                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event)
                    throws Exception
                {
                    switch ( event.getType() )
                    {
                    case INITIALIZED:
                        Assert.assertTrue(initLatch.getCount() >= 1);
                        initLatch.countDown();
                        break;
                    case CHILD_ADDED:
                        Assert.assertTrue(createLatch.getCount() >= 1);
                        createLatch.countDown();
                        break;
                    case CHILD_REMOVED:
                        Assert.assertTrue(deleteLatch.getCount() >= 1);
                        deleteLatch.countDown();
                        break;
                    case CHILD_UPDATED:
                        Assert.assertTrue(updateLatch.getCount() >= 1);
                        updateLatch.countDown();
                        break;
                    default:
                        //Not expecting any other events
                        Assert.fail("Unexpected child event");
                    }
                }
            });

            cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
            Assert.assertTrue(timing.awaitLatch(initLatch));

            client.create().creatingParentsIfNeeded().forPath(
                root + "/oneLevel/secondLevel/thirdLevel");
            Assert.assertTrue(timing.awaitLatch(createLatch));
            client.setData().forPath(root + "/oneLevel", "NewData1".getBytes());
            client.setData().forPath(root + "/oneLevel/secondLevel", "NewData2".getBytes());
            client.setData().forPath(
                root + "/oneLevel/secondLevel/thirdLevel", "NewData3".getBytes());
            Assert.assertTrue(timing.awaitLatch(updateLatch));
            client.delete().deletingChildrenIfNeeded().forPath(root + "/oneLevel");
            Assert.assertTrue(timing.awaitLatch(deleteLatch));
        }
        finally
        {
            CloseableUtils.closeQuietly(cache);
            CloseableUtils.closeQuietly(client);
        }
    }

    /**
     * Test the case where there is a whole tree and we're interested in all updates. See CURATOR-33
     *
     * @throws Exception
     */
    @Test
    public void testCacheWholeTree() throws Exception
    {
        Timing timing = new Timing();
        PathChildrenCache cache = null;

        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
        try
        {
            client.start();

            final CountDownLatch initLatch = new CountDownLatch(1);

            final CountDownLatch createLatch = new CountDownLatch(3);
            final CountDownLatch updateLatch = new CountDownLatch(3);
            final CountDownLatch deleteLatch = new CountDownLatch(3);

            final String root = "/cachetest";

            cache = new PathChildrenCache(client, root, true, DescendantHandlingMode.ALL_DESCENDANTS);
            cache.getListenable().addListener(new PathChildrenCacheListener()
            {

                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event)
                    throws Exception
                {
                    switch ( event.getType() )
                    {
                    case INITIALIZED:
                        initLatch.countDown();
                        break;
                    case CHILD_ADDED:
                        System.err.println("Added : " + event);
                        createLatch.countDown();
                        break;
                    case CHILD_REMOVED:
                        System.err.println("Removed : " + event);
                        deleteLatch.countDown();
                        break;
                    case CHILD_UPDATED:
                        System.err.println("Updated : " + event);
                        updateLatch.countDown();
                        break;
                    default:
                        //Not expecting any other events
                        Assert.fail("Unexpected child event");                        
                    }
                }
            });

            cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
            Assert.assertTrue(timing.awaitLatch(initLatch));

            client.create().creatingParentsIfNeeded().forPath(root + "/oneLevel/secondLevel/thirdLevel");
            Assert.assertTrue(timing.awaitLatch(createLatch));
            
            client.setData().forPath(root + "/oneLevel", "NewData1".getBytes());
            client.setData().forPath(root + "/oneLevel/secondLevel", "NewData2".getBytes());
            client.setData().forPath(root + "/oneLevel/secondLevel/thirdLevel", "NewData3".getBytes());
            Assert.assertTrue(timing.awaitLatch(updateLatch));
            client.delete().deletingChildrenIfNeeded().forPath(root + "/oneLevel");
            Assert.assertTrue(timing.awaitLatch(deleteLatch));
        }
        finally
        {
            CloseableUtils.closeQuietly(cache);
            CloseableUtils.closeQuietly(client);
        }
    }
}
