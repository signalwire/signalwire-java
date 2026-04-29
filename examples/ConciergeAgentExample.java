/**
 * Concierge Agent Example -- using the ConciergeAgent prefab.
 *
 * Creates a luxury resort virtual concierge with amenity info
 * and availability checking.
 */

import com.signalwire.sdk.prefabs.ConciergeAgent;

import java.util.List;

public class ConciergeAgentExample {

    public static void main(String[] args) throws Exception {
        var agent = new ConciergeAgent(
                "resort-concierge",
                "Oceanview Resort",
                List.of(
                        ConciergeAgent.amenity("Infinity Pool",
                                "Heated infinity pool overlooking the ocean with poolside service.",
                                "7:00 AM - 10:00 PM", "Main Level, Ocean View", null),
                        ConciergeAgent.amenity("Spa",
                                "Full-service luxury spa with massages, facials, and body treatments.",
                                "9:00 AM - 8:00 PM", "Lower Level, East Wing", "$150+"),
                        ConciergeAgent.amenity("Fitness Center",
                                "State-of-the-art gym with cardio, weights, and yoga studio.",
                                "24 hours", "2nd Floor, North Wing", "Complimentary"),
                        ConciergeAgent.amenity("Beach Access",
                                "Private beach with chairs, umbrellas, and towels.",
                                "Dawn to Dusk", "Southern Pathway", "Complimentary")
                )
        );

        System.out.println("Starting Oceanview Resort concierge on port 3000...");
        agent.run();
    }
}
