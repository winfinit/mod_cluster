/*
 *  mod_cluster
 *
 *  Copyright(c) 2010 Red Hat Middleware, LLC,
 *  and individual contributors as indicated by the @authors tag.
 *  See the copyright.txt in the distribution for a
 *  full listing of individual contributors.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library in the file COPYING.LIB;
 *  if not, write to the Free Software Foundation, Inc.,
 *  59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * @author Jean-Frederic Clere
 * @version $Revision$
 */

package org.jboss.mod_cluster;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.catalina.Engine;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.Service;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;

public class TestQuery extends TestCase {

    StandardServer server = null;

    /* Test Context handling:
     * / 
     * /test
     * /testtest
     * Using the servlet MyTest (in ROOT).
     */
    public void testQuery() {

        boolean clienterror = false;
        server = Maintest.getServer();
        JBossWeb service = null;
        Connector connector = null;
        LifecycleListener cluster = null;
        System.out.println("TestQuery Started");
        try {
            String [] Aliases = new String[1];
            Aliases[0] = "cluster.domain.info";
            service = new JBossWeb("node3",  "localhost", false, "ROOT", Aliases);
            connector = service.addConnector(8013);
            service.AddContext("/test", "/test", "MyTest");
            service.AddContext("/myapp", "/myapp", "MyTest");
            server.addService(service);

            cluster = Maintest.createClusterListener("232.0.0.2", 23364, false, "dom1");
            server.addLifecycleListener(cluster);
            // Maintest.listServices();

        } catch(IOException ex) {
            ex.printStackTrace();
            fail("can't start service");
        }

        // start the server thread.
        ServerThread wait = new ServerThread(3000, server);
        wait.start();

        // Wait until the node is created in httpd.
        int countinfo = 0;
        String [] nodes = new String[1];
        nodes[0] = "node3";
        while ((!Maintest.checkProxyInfo(cluster, nodes)) && countinfo < 20) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            countinfo++;
        }
        if (countinfo == 20)
            fail("can't find node in httpd");

        // Start the client and wait for it.
        Client client = new Client();

        // Do a request.
        try {
            if (client.runit("/test/MyTest?name=edwin&state=NY", 1, false, false) != 0)
                clienterror = true;
        } catch (Exception ex) {
            ex.printStackTrace();
            clienterror = true;
        }
        if (clienterror)
            fail("Client error");

        // Check for the result.
        String response = client.getResponse();
        if (response.indexOf("name=edwin&state=NY") == -1) {
            System.out.println("response: " + client.getResponse());
            fail("Can't find the query string in the response");
        }

        // Try with the rewrite rule.
        // RewriteCond %{HTTP_HOST} ^cluster\.domain\.info [NC]
        // ^/?([^/.]+)/(.*)$ balancer://mycluster/$2?partnerpath=/$1 [P,QSA]
        client = new Client();
        client.setVirtualHost("cluster.domain.info");

        // Do a request.
        try {
            if (client.runit("/hisname/MyTest?name=edwin&state=NY", 1, false, false) != 0)
                clienterror = true;
        } catch (Exception ex) {
            ex.printStackTrace();
            clienterror = true;
        }
        if (clienterror)
            fail("Client error");

        // Check for the result.
        response = client.getResponse();
        if (response.indexOf("partnerpath=/hisname&name=edwin&state=NY") == -1) {
            System.out.println("response: " + client.getResponse());
            fail("Can't find the query string in the response");
        }

        // Stop the server or services.
        try {
            wait.stopit();
            wait.join();
            server.removeService(service);
            server.removeLifecycleListener(cluster);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        // Wait until httpd as received the stop messages.
        Maintest.TestForNodes(cluster, null);

        // Test client result.
        if (clienterror)
            fail("Client test failed");

        Maintest.testPort(8013);
        System.out.println("TestQuery Done");
    }
}