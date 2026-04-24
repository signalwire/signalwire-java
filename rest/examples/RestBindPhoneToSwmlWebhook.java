/**
 * Example: bind an inbound phone number to an SWML webhook (the happy path).
 *
 * This is the simplest way to route a SignalWire phone number to a backend
 * that returns an SWML document per inbound call. You set call_handler on
 * the phone number; the server auto-materializes a swml_webhook Fabric
 * resource pointing at your URL. You do NOT need to create the Fabric
 * webhook resource manually; you do NOT call assign_phone_route.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space (e.g. example.signalwire.com)
 *   PHONE_NUMBER_SID        - SID of a phone number you own (pn-...)
 *   SWML_WEBHOOK_URL        - your backend's SWML endpoint
 */

import com.signalwire.sdk.rest.PhoneCallHandler;
import com.signalwire.sdk.rest.RestClient;

import java.util.Map;

public class RestBindPhoneToSwmlWebhook {

    public static void main(String[] args) {
        String project     = System.getenv("SIGNALWIRE_PROJECT_ID");
        String token       = System.getenv("SIGNALWIRE_API_TOKEN");
        String space       = System.getenv("SIGNALWIRE_SPACE");
        String pnSid       = System.getenv("PHONE_NUMBER_SID");
        String webhookUrl  = System.getenv("SWML_WEBHOOK_URL");

        var client = RestClient.builder()
                .project(project)
                .token(token)
                .space(space)
                .build();

        // The typed helper — one line:
        System.out.printf("Binding %s to %s ...%n", pnSid, webhookUrl);
        client.phoneNumbers().setSwmlWebhook(pnSid, webhookUrl);

        // The equivalent wire-level form (use this if you need unusual fields):
        //
        // client.phoneNumbers().update(pnSid, Map.of(
        //         "call_handler",          PhoneCallHandler.RELAY_SCRIPT.wireValue(),
        //         "call_relay_script_url", webhookUrl
        // ));

        // Verify: the server auto-created a swml_webhook Fabric resource.
        Map<String, Object> pn = client.phoneNumbers().get(pnSid);
        System.out.printf("  call_handler = %s%n", pn.get("call_handler"));
        System.out.printf("  call_relay_script_url = %s%n", pn.get("call_relay_script_url"));
        System.out.printf("  calling_handler_resource_id (server-derived) = %s%n",
                pn.get("calling_handler_resource_id"));

        // To route to something other than an SWML webhook, use:
        //   client.phoneNumbers().setCxmlWebhook(sid, url)         // LAML / Twilio-compat
        //   client.phoneNumbers().setAiAgent(sid, agentId)         // AI Agent
        //   client.phoneNumbers().setCallFlow(sid, flowId)         // Call Flow
        //   client.phoneNumbers().setRelayApplication(sid, name)   // Named RELAY app
        //   client.phoneNumbers().setRelayTopic(sid, topic)        // RELAY topic
    }
}
