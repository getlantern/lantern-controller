package org.lantern;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.charts.DataTable;
import org.lantern.charts.DataTable.ColumnType;
import org.lantern.data.Dao;
import org.lantern.data.LanternInstance;
import org.lantern.data.Metric;
import org.lantern.data.Metric.Period;

/**
 * This controller fetches the data needed by the proxyStatus.html page.
 */
@SuppressWarnings("serial")
public class ProxyStatusController extends HttpServlet {

    @Override
    public void doGet(final HttpServletRequest request,
            final HttpServletResponse response) throws IOException {

        List<Map<String, Object>> proxies = new ArrayList<Map<String, Object>>();
        final Dao dao = new Dao();
        for (LanternInstance instance : dao.findFallbackProxies()) {
            DataTable loadAverages = new DataTable();
            loadAverages.addColumn("period", "Period", ColumnType.string);
            loadAverages.addColumn("loadAverage", "Load Average",
                    ColumnType.number);
            Metric loadAverage = instance.getSystemLoadAverage();
            double latestLoadAverage = 0;
            for (Period period : loadAverage.getPeriods()) {
                if (period.hasStartTime()) {
                    loadAverages.addRow().addCell(period.getStartTime().toString())
                            .addCell(period.getMovingAverage());
                    latestLoadAverage = period.getMovingAverage();
                }
            }
            Map<String, Object> proxy = new HashMap<String, Object>();
            proxy.put("userId", instance.getParent().getName());
            proxy.put("instanceId", instance.getId());
            proxy.put("latestLoadAverage", latestLoadAverage);
            proxy.put("loadAverages", loadAverages);
            proxies.add(proxy);
        }

        final String responseString = JsonUtils.jsonify(proxies);
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_OK);

        final byte[] content = responseString.getBytes("UTF-8");
        response.setContentLength(content.length);

        final OutputStream os = response.getOutputStream();

        System.out.println("Writing javascript callback.");
        os.write(content);
        os.flush();
    }
}
