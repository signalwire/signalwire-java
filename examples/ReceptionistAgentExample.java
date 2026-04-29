/**
 * Receptionist Agent Example -- using the ReceptionistAgent prefab.
 *
 * Routes callers to the appropriate department via phone transfer.
 */

import com.signalwire.sdk.prefabs.ReceptionistAgent;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReceptionistAgentExample {

    public static void main(String[] args) throws Exception {
        Map<String, Map<String, Object>> departments = new LinkedHashMap<>();
        departments.put("Sales",
                ReceptionistAgent.phoneDepartment(
                        "Product inquiries, pricing, and purchasing", "+15551235555"));
        departments.put("Support",
                ReceptionistAgent.phoneDepartment(
                        "Technical assistance and troubleshooting", "+15551236666"));
        departments.put("Billing",
                ReceptionistAgent.phoneDepartment(
                        "Payment questions, invoices, and subscriptions", "+15551237777"));
        departments.put("General",
                ReceptionistAgent.phoneDepartment(
                        "All other inquiries", "+15551238888"));

        var agent = new ReceptionistAgent(
                "acme-receptionist",
                "Thank you for calling ACME Corporation. How may I direct your call?",
                departments
        );

        System.out.println("Starting ACME receptionist on port 3000...");
        agent.run();
    }
}
