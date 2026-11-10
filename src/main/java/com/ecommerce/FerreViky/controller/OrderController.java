package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.OrderDetail;
import com.ecommerce.FerreViky.dto.dtos.order.OrderDTO.OrderSummary;
import com.ecommerce.FerreViky.models.Customer;
import com.ecommerce.FerreViky.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Read the authenticated customer's orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "List my orders",
            description = "Returns all orders belonging to the authenticated customer, newest first. This endpoint is not paginated."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Orders retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = OrderSummary.class)),
                            examples = @ExampleObject(
                                    value = "[{\"id\":42,\"createdAt\":\"2026-11-08T18:00:00\",\"total\":210.00,\"status\":\"PAID\"}]"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT is missing or invalid",
                    content = @Content
            )
    })
    @GetMapping
    public List<OrderSummary> list(@AuthenticationPrincipal Customer customer) {
        return orderService.myOrders(customer);
    }

    @Operation(
            summary = "Get my order details",
            description = "Returns an order belonging to the authenticated customer. Redemption codes are included only when the order is PAID. The response is marked no-store to prevent caching sensitive codes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order details retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderDetail.class),
                            examples = @ExampleObject(
                                    value = "{\"id\":42,\"createdAt\":\"2026-11-08T18:00:00\",\"total\":210.00,\"status\":\"PAID\",\"items\":[{\"productId\":7,\"name\":\"Steam Gift Card $200 MXN\",\"quantity\":1,\"unitPrice\":210.00,\"redemptionInstructions\":\"Redeem the code in Steam\",\"codes\":[\"ABCD-EFGH-IJKL\"]}]}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The order ID has an invalid format",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"status\":400,\"message\":\"Parameter 'id' has an invalid format\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT is missing or invalid",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The order does not exist or does not belong to the authenticated customer",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Order not found with ID: 42")
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetail> detail(
            @Parameter(description = "Internal order ID", example = "42")
            @PathVariable Long id,
            @AuthenticationPrincipal Customer customer) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // que no se cachee con códigos
                .body(orderService.myOrder(id, customer));
    }
}