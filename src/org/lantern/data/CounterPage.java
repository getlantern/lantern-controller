package org.lantern.data;

/* Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Renders an HTML page showing current count of the sharded counters.
 *
 */
public class CounterPage extends HttpServlet {
    private static final long serialVersionUID = 1062519021365467153L;

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        displayPageStart(resp);
        ShardedCounterManager factory = new ShardedCounterManager();
        Map<String, DatastoreCounter> counters = factory.getAllCounters();
        displayCounts(counters, resp);
        displayPageEnd(resp);
    }

    private void displayPageEnd(HttpServletResponse resp) throws IOException {
        resp.getWriter().println("  </body>");
        resp.getWriter().println("</html>");
    }

    private void displayPageStart(HttpServletResponse resp) throws IOException {
        resp.getWriter().println("<html>");
        resp.getWriter().println("  <body>");
    }

    private void displayCounts(Map<String, DatastoreCounter> counters,
            HttpServletResponse resp) throws IOException {
        ArrayList<String> sorted = new ArrayList<String>(counters.keySet());
        Collections.sort(sorted);
        resp.getWriter()
                .println("<table><tr><th>Count</th><th>Shard</th></tr>");
        for (String key : sorted) {
            DatastoreCounter counter = counters.get(key);
            resp.getWriter().println(
                    "  <tr><td>" + counter.getCount() + "</td>");
            resp.getWriter().println(
                    "  <td>" + counter.getShardCount() + "</td></tr>/");
        }
        resp.getWriter().println("</table>");
    }
}