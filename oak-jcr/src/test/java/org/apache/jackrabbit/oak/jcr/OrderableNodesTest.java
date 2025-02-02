/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.iterator.NodeIterable;
import org.apache.jackrabbit.oak.fixture.NodeStoreFixture;
import org.apache.jackrabbit.oak.plugins.document.DocumentNodeStore;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.spi.toggle.FeatureToggle;
import org.apache.jackrabbit.oak.spi.whiteboard.Tracker;
import org.junit.Test;

public class OrderableNodesTest extends AbstractRepositoryTest {

    public OrderableNodesTest(NodeStoreFixture fixture) {
        super(fixture);
    }

    @Test
    public void testSimpleOrdering() throws RepositoryException {
        doTest("nt:unstructured");
    }

    @Test
    public void orderableFolder() throws Exception {
        // check ordering with node type without a residual properties definition
        new TestContentLoader().loadTestContent(getAdminSession());
        doTest("test:orderableFolder");
    }

    @Test
    public void orderSameNode() throws Exception {
        Session session = getAdminSession();
        Node n = session.getRootNode().addNode("test", "nt:unstructured");
        Node a = n.addNode("a");

        n.orderBefore("a", "a");
    }

    @Test
    public void setPrimaryType() throws Exception {
        new TestContentLoader().loadTestContent(getAdminSession());
        // start with a node without orderable nodes
        Session session = getAdminSession();
        Node root = session.getRootNode().addNode("test", "nt:folder");

        List<String> names = new ArrayList<String>();
        for (int i = 0; i < 100; i++) {
            String name = "node-" + i;
            root.addNode(name, "nt:folder");
            names.add(name);
        }

        root.setPrimaryType("test:orderableFolder");

        // as of now, the child nodes must be stable and orderable
        List<String> expected = getChildNames(root);
        while (!expected.isEmpty()) {
            String name = expected.remove((int) Math.floor(Math.random() * expected.size()));
            root.getNode(name).remove();

            assertEquals(expected, getChildNames(root));
        }

        for (String name : names) {
            root.addNode(name, "nt:folder");
            expected.add(name);
            assertEquals(expected, getChildNames(root));
        }
    }

    /**
     * OAK-612
     */
    @Test
    public void testAddNode() throws Exception {
        new TestContentLoader().loadTestContent(getAdminSession());

        Session session = getAdminSession();
        Node test = session.getRootNode().addNode("test", "test:orderableFolder");
        assertTrue(test.getPrimaryNodeType().hasOrderableChildNodes());

        test.addNode("a");
        test.addNode("b");
        session.save();

        NodeIterator it = test.getNodes();
        assertEquals("a", it.nextNode().getName());
        assertEquals("b", it.nextNode().getName());
    }

    @Test
    public void orderableAddManyChildrenWithSave() throws Exception {
        int childCount = 2000;
        StringBuilder prefix = new StringBuilder("");
        //keep name length below 512, since that is the maximum supported by RDBDocumentStore
        for (int k = 0; k < 45; k++) {
            prefix.append("0123456789");
        }
        Session session = getAdminSession();
        Node test = session.getRootNode().addNode("test", "nt:unstructured");
        session.save();
        for (int k = 0; k < childCount; k++) {
            test.addNode(prefix.toString() + k, "nt:unstructured");
        }
    }

    @Test
    public void moveOrderableWithManyChildren() throws Exception {
        int childCount = 2000;
        StringBuilder prefix = new StringBuilder("");
        //keep name length below 512, since that is the maximum supported by RDBDocumentStore
        for (int k = 0; k < 45; k++) {
            prefix.append("0123456789");
        }
        Session session = getAdminSession();
        Node test = session.getRootNode().addNode("test-0", "nt:unstructured");
        session.save();
        for (int k = 0; k < childCount; k++) {
            test.addNode(prefix.toString() + k, "nt:unstructured");
            if (k % 100 == 0) {
                session.save();
            }
        }
        session.save();
        session.move("/test-0", "/test-1");
        session.save();
    }

    @Test
    public void copyOrderableWithManyChildren() throws Exception {
        int childCount = 2000;
        StringBuilder prefix = new StringBuilder("");
        //keep name length below 512, since that is the maximum supported by RDBDocumentStore
        for (int k = 0; k < 45; k++) {
            prefix.append("0123456789");
        }
        Session session = getAdminSession();
        Node test = session.getRootNode().addNode("test-0", "nt:unstructured");
        session.save();
        for (int k = 0; k < childCount; k++) {
            test.addNode(prefix.toString() + k, "nt:unstructured");
            if (k % 100 == 0) {
                session.save();
            }
        }
        session.save();
        session.getWorkspace().copy("/test-0", "/test-1");
        session.save();
    }

    @Test
    public void childOrderCleanupFeatureToggleTest() throws RepositoryException {
        //init repository
        getAdminSession();
        NodeStore nodeStore = getNodeStore();
        assertNotNull(nodeStore);
        Tracker<FeatureToggle> track = fixture.getWhiteboard().track(FeatureToggle.class);
        if (nodeStore instanceof DocumentNodeStore) {
            DocumentNodeStore documentNodeStore = (DocumentNodeStore) nodeStore;
            assertTrue(documentNodeStore.isChildOrderCleanupEnabled());
            for (FeatureToggle toggle : track.getServices()) {
                if ("FT_NOCOCLEANUP_OAK-10660".equals(toggle.getName())) {
                    assertFalse(toggle.isEnabled());
                    assertTrue(documentNodeStore.isChildOrderCleanupEnabled());
                    toggle.setEnabled(true);
                    assertTrue(toggle.isEnabled());
                    assertFalse(documentNodeStore.isChildOrderCleanupEnabled());
                    toggle.setEnabled(false);
                    assertFalse(toggle.isEnabled());
                    assertTrue(documentNodeStore.isChildOrderCleanupEnabled());
                }
            }
        }
    }

    private void doTest(String nodeType) throws RepositoryException {
        Session session = getAdminSession();
        Node root = session.getRootNode().addNode("test", nodeType);

        root.addNode("a");
        root.addNode("b");
        root.addNode("c");

        NodeIterator iterator;

        iterator = root.getNodes();
        assertEquals("a", iterator.nextNode().getName());
        assertEquals("b", iterator.nextNode().getName());
        assertEquals("c", iterator.nextNode().getName());
        assertFalse(iterator.hasNext());

        root.orderBefore("c", "a");
        iterator = root.getNodes();
        assertEquals("c", iterator.nextNode().getName());
        assertEquals("a", iterator.nextNode().getName());
        assertEquals("b", iterator.nextNode().getName());
        assertFalse(iterator.hasNext());

        root.orderBefore("b", "c");
        iterator = root.getNodes();
        assertEquals("b", iterator.nextNode().getName());
        assertEquals("c", iterator.nextNode().getName());
        assertEquals("a", iterator.nextNode().getName());
        assertFalse(iterator.hasNext());

        session.save();
    }

    private static List<String> getChildNames(Node node)
            throws RepositoryException {
        List<String> names = new ArrayList<String>();
        for (Node child : new NodeIterable(node.getNodes())) {
            names.add(child.getName());
        }
        return names;
    }
}
