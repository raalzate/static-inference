package com.acme.erp.web;

import com.acme.erp.model.Order;
import com.acme.erp.model.OrderStatus;
import com.acme.erp.service.OrderServiceLocal;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Struts 1.x Action for order management pages.
 * Handles list, detail, and status update actions.
 * Uses JNDI lookup to access EJB — typical legacy pattern.
 */
public class OrderAction extends Action {

    private OrderServiceLocal getOrderService() throws NamingException {
        InitialContext ctx = new InitialContext();
        return (OrderServiceLocal) ctx.lookup("java:comp/env/ejb/OrderService");
    }

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                  HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action");
        if (action == null) action = "list";

        try {
            OrderServiceLocal orderService = getOrderService();

            switch (action) {
                case "list":
                    return handleList(mapping, request, orderService);
                case "detail":
                    return handleDetail(mapping, request, orderService);
                case "updateStatus":
                    return handleUpdateStatus(mapping, request, orderService);
                case "search":
                    return handleSearch(mapping, request, orderService);
                default:
                    return handleList(mapping, request, orderService);
            }
        } catch (Exception e) {
            request.setAttribute("error", "Error processing request: " + e.getMessage());
            return mapping.findForward("error");
        }
    }

    private ActionForward handleList(ActionMapping mapping, HttpServletRequest request,
                                      OrderServiceLocal orderService) {
        String statusParam = request.getParameter("status");
        List<Order> orders;
        if (statusParam != null && !statusParam.isEmpty()) {
            orders = orderService.findByStatus(OrderStatus.valueOf(statusParam));
        } else {
            orders = orderService.findRecentOrders(30);
        }
        request.setAttribute("orders", orders);
        return mapping.findForward("orderList");
    }

    private ActionForward handleDetail(ActionMapping mapping, HttpServletRequest request,
                                        OrderServiceLocal orderService) {
        Long orderId = Long.parseLong(request.getParameter("orderId"));
        Order order = orderService.findById(orderId);
        if (order == null) {
            request.setAttribute("error", "Order not found");
            return mapping.findForward("error");
        }
        request.setAttribute("order", order);
        return mapping.findForward("orderDetail");
    }

    private ActionForward handleUpdateStatus(ActionMapping mapping, HttpServletRequest request,
                                              OrderServiceLocal orderService) {
        Long orderId = Long.parseLong(request.getParameter("orderId"));
        OrderStatus newStatus = OrderStatus.valueOf(request.getParameter("newStatus"));
        orderService.updateStatus(orderId, newStatus);
        request.setAttribute("message", "Order status updated successfully");
        return handleDetail(mapping, request, orderService);
    }

    private ActionForward handleSearch(ActionMapping mapping, HttpServletRequest request,
                                        OrderServiceLocal orderService) {
        String customerId = request.getParameter("customerId");
        if (customerId != null && !customerId.isEmpty()) {
            List<Order> orders = orderService.findByCustomer(Long.parseLong(customerId));
            request.setAttribute("orders", orders);
        }
        return mapping.findForward("orderList");
    }
}
