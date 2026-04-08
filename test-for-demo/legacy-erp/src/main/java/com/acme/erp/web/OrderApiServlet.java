package com.acme.erp.web;

import com.acme.erp.model.Order;
import com.acme.erp.model.OrderStatus;
import com.acme.erp.service.OrderServiceLocal;

import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * REST-like servlet for JSON API access to orders.
 * Mapped in web.xml to /api/orders/*
 * Should migrate to Spring @RestController.
 */
@WebServlet(urlPatterns = {"/api/orders", "/api/orders/*"})
public class OrderApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            OrderServiceLocal orderService = lookupOrderService();
            String pathInfo = req.getPathInfo();

            if (pathInfo == null || pathInfo.equals("/")) {
                // GET /api/orders — list orders
                String status = req.getParameter("status");
                List<Order> orders;
                if (status != null) {
                    orders = orderService.findByStatus(OrderStatus.valueOf(status));
                } else {
                    orders = orderService.findRecentOrders(30);
                }
                out.write(toJsonArray(orders));
            } else {
                // GET /api/orders/{id} — get single order
                Long orderId = Long.parseLong(pathInfo.substring(1));
                Order order = orderService.findById(orderId);
                if (order == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.write("{\"error\":\"Order not found\"}");
                } else {
                    out.write(toJson(order));
                }
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        try {
            OrderServiceLocal orderService = lookupOrderService();
            String pathInfo = req.getPathInfo();
            Long orderId = Long.parseLong(pathInfo.substring(1));
            String newStatus = req.getParameter("status");

            Order order = orderService.updateStatus(orderId, OrderStatus.valueOf(newStatus));
            out.write(toJson(order));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private OrderServiceLocal lookupOrderService() throws Exception {
        InitialContext ctx = new InitialContext();
        return (OrderServiceLocal) ctx.lookup("java:comp/env/ejb/OrderService");
    }

    private String toJson(Order order) {
        return "{\"id\":" + order.getId()
            + ",\"orderNumber\":\"" + order.getOrderNumber() + "\""
            + ",\"status\":\"" + order.getStatus() + "\""
            + ",\"totalAmount\":" + order.getTotalAmount()
            + ",\"createdAt\":\"" + order.getCreatedAt() + "\""
            + "}";
    }

    private String toJsonArray(List<Order> orders) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(orders.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}
