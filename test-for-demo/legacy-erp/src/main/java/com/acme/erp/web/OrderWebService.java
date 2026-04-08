package com.acme.erp.web;

import com.acme.erp.model.Order;
import com.acme.erp.model.OrderStatus;
import com.acme.erp.service.OrderServiceLocal;

import javax.ejb.EJB;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import java.util.List;

/**
 * SOAP Web Service for external B2B integrations.
 * Partners call this service to place and query orders.
 * Should migrate to REST API with OpenAPI spec.
 */
@WebService(
    serviceName = "OrderService",
    portName = "OrderServicePort",
    targetNamespace = "http://acme.com/erp/orders"
)
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT)
public class OrderWebService {

    @EJB
    private OrderServiceLocal orderService;

    @WebMethod(operationName = "getOrder")
    @WebResult(name = "order")
    public OrderSoapResponse getOrder(
            @WebParam(name = "orderId") Long orderId) {
        Order order = orderService.findById(orderId);
        if (order == null) {
            throw new RuntimeException("Order not found: " + orderId);
        }
        return mapToResponse(order);
    }

    @WebMethod(operationName = "getOrdersByCustomer")
    @WebResult(name = "orders")
    public List<OrderSoapResponse> getOrdersByCustomer(
            @WebParam(name = "customerId") Long customerId) {
        List<Order> orders = orderService.findByCustomer(customerId);
        return orders.stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
    }

    @WebMethod(operationName = "getOrdersByStatus")
    @WebResult(name = "orders")
    public List<OrderSoapResponse> getOrdersByStatus(
            @WebParam(name = "status") String status) {
        List<Order> orders = orderService.findByStatus(OrderStatus.valueOf(status));
        return orders.stream().map(this::mapToResponse).collect(java.util.stream.Collectors.toList());
    }

    @WebMethod(operationName = "updateOrderStatus")
    @WebResult(name = "order")
    public OrderSoapResponse updateOrderStatus(
            @WebParam(name = "orderId") Long orderId,
            @WebParam(name = "newStatus") String newStatus) {
        Order order = orderService.updateStatus(orderId, OrderStatus.valueOf(newStatus));
        return mapToResponse(order);
    }

    private OrderSoapResponse mapToResponse(Order order) {
        OrderSoapResponse resp = new OrderSoapResponse();
        resp.setId(order.getId());
        resp.setOrderNumber(order.getOrderNumber());
        resp.setStatus(order.getStatus().name());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCustomerName(order.getCustomer().getCompanyName());
        resp.setCreatedAt(order.getCreatedAt().toString());
        return resp;
    }
}
