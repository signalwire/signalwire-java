/**
 * Stateful agent using global data for session tracking.
 *
 * Demonstrates how to use global data to maintain state across tool calls
 * within a single conversation.
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SessionAndStateDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("stateful-agent")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are a shopping assistant. Help users add items to their cart.");
        agent.promptAddSection("Instructions", "", List.of(
                "Use add_to_cart to add items",
                "Use view_cart to show current cart contents",
                "Use checkout to complete the order"
        ));

        // Initialize global data with empty cart
        agent.updateGlobalData(Map.of(
                "cart", Map.of("items", List.of(), "total", 0)
        ));

        // Add to cart tool
        agent.defineTool("add_to_cart", "Add an item to the shopping cart",
                Map.of("type", "object", "properties", Map.of(
                        "item", Map.of("type", "string", "description", "Item name"),
                        "price", Map.of("type", "number", "description", "Item price")
                ), "required", List.of("item", "price")),
                (toolArgs, raw) -> {
                    String item = (String) toolArgs.get("item");
                    double price = ((Number) toolArgs.get("price")).doubleValue();

                    return new FunctionResult(
                            "Added " + item + " ($" + String.format("%.2f", price) + ") to cart.")
                            .updateGlobalData(Map.of(
                                    "last_item", item,
                                    "last_price", price
                            ));
                });

        // View cart tool
        agent.defineTool("view_cart", "View the current shopping cart",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> globalData = (Map<String, Object>) raw.get("global_data");
                    String lastItem = globalData != null ?
                            (String) globalData.getOrDefault("last_item", "none") : "none";
                    return new FunctionResult("Last item added: " + lastItem);
                });

        // Checkout tool
        agent.defineTool("checkout", "Complete the order",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult(
                        "Order placed successfully! Thank you for shopping with us.")
                        .hangup());

        System.out.println("Starting stateful shopping agent on port 3000...");
        agent.run();
    }
}
