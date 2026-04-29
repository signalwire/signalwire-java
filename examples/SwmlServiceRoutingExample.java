/**
 * SWML Service Routing Example.
 *
 * Demonstrates building multiple SWML document sections and serving
 * different content based on the path -- all from a single Service instance.
 *
 * Paths served:
 *   /main     -> Default greeting
 *   /customer -> Customer service greeting
 *   /product  -> Product information greeting
 */

import com.signalwire.sdk.swml.Service;

import java.util.Map;

public class SwmlServiceRoutingExample {

    public static void main(String[] args) throws Exception {
        // Main service at /main
        var mainSvc = new Service("main-service", "/main");
        mainSvc.answer(null);
        mainSvc.play(Map.of("url", "say:Hello from the main service!"));
        mainSvc.hangup();

        // Customer service at /customer
        var customerSvc = new Service("customer-service", "/customer");
        customerSvc.answer(null);
        customerSvc.play(Map.of("url", "say:Hello from customer service!"));
        customerSvc.prompt(Map.of(
                "play", "say:Press 1 for account management, 2 for technical support.",
                "max_digits", 1,
                "terminators", "#"
        ));
        customerSvc.hangup();

        // Product service at /product
        var productSvc = new Service("product-service", "/product");
        productSvc.answer(null);
        productSvc.play(Map.of("url", "say:Hello from the product service!"));
        productSvc.play(Map.of("url",
                "say:We offer voice, video, and messaging APIs for modern applications."));
        productSvc.hangup();

        // Start all three services on different ports
        // In production you'd use a single server with routing; here we start main.
        System.out.println("Starting SWML routing example...");
        System.out.println("  Main:     http://localhost:3000/main");
        System.out.println("  Customer: (use AgentServer for multi-route hosting)");
        System.out.println("  Product:  (use AgentServer for multi-route hosting)");
        mainSvc.serve();
    }
}
