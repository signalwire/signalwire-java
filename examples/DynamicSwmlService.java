/*
 * Dynamic SWML Service Example.
 *
 * <p>Demonstrates creating SWML services that generate different responses based on request data --
 * a dynamic greeting service and a call router.
 *
 * <p>These use the SWML Service class directly (no AI component).
 *
 * <p>Usage: java DynamicSwmlService [greeting|router]
 */
import com.signalwire.sdk.swml.Service;
import java.util.Map;

public class DynamicSwmlService {

  public static void main(String[] args) throws Exception {
    String mode = args.length > 0 ? args[0] : "greeting";

    switch (mode) {
      case "greeting" -> startGreeting();
      case "router" -> startRouter();
      default -> {
        System.out.println("Usage: DynamicSwmlService [greeting|router]");
        System.exit(1);
      }
    }
  }

  /** A greeting service that serves different SWML based on caller type. */
  static void startGreeting() throws Exception {
    var svc = new Service("dynamic-greeting", "/greeting");

    // Build a default greeting document
    svc.answer(null);
    svc.play(Map.of("url", "say:Hello, thank you for calling our service."));
    svc.prompt(
        Map.of(
            "play", "say:Press 1 for sales, 2 for support, or 3 to leave a message.",
            "max_digits", 1,
            "terminators", "#"));
    svc.hangup();

    System.out.println("Starting dynamic greeting service...");
    System.out.println(
        "POST JSON to customize: {\"caller_name\":\"John\",\"caller_type\":\"vip\"}");
    svc.serve();
  }

  /** A call router that routes calls by region. */
  static void startRouter() throws Exception {
    var svc = new Service("call-router", "/router");

    svc.answer(null);
    svc.play(
        Map.of("url", "say:Thank you for calling. We'll connect you with an available agent."));
    svc.connect(Map.of("to", "+15551234567", "timeout", 30));
    svc.play(Map.of("url", "say:All agents are busy. Please try again later."));
    svc.hangup();

    System.out.println("Starting call router service...");
    System.out.println("POST JSON to customize: {\"region\":\"west\",\"high_volume\":true}");
    svc.serve();
  }
}
