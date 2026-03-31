/**
 * Auto-Vivified SWML Service Example.
 *
 * Demonstrates calling verb methods directly on a SWMLService instead
 * of using addVerb(). Builds voicemail, IVR, and call transfer services.
 */

import com.signalwire.sdk.swml.SWMLService;

import java.util.List;
import java.util.Map;

public class AutoVivifiedExample {

    public static void main(String[] args) throws Exception {
        // --- Voicemail Service ---
        var voicemail = SWMLService.builder()
                .name("voicemail")
                .route("/voicemail")
                .port(3000)
                .build();

        voicemail.addAnswerVerb();
        voicemail.play(Map.of("url",
                "say:Hello, you have reached the voicemail service. Please leave a message after the beep."));
        voicemail.sleep(1000);
        voicemail.play(Map.of("url", "https://example.com/beep.wav"));
        voicemail.record(Map.of(
                "format", "mp3",
                "stereo", false,
                "beep", false,
                "max_length", 120,
                "terminators", "#",
                "status_url", "https://example.com/voicemail-status"));
        voicemail.play(Map.of("url", "say:Thank you for your message. Goodbye!"));
        voicemail.addHangupVerb();

        // --- IVR Menu Service ---
        var ivr = SWMLService.builder()
                .name("ivr")
                .route("/ivr")
                .port(3000)
                .build();

        ivr.addAnswerVerb();
        ivr.addSection("main_menu");
        ivr.addVerbToSection("main_menu", "prompt", Map.of(
                "play", "say:Press 1 for sales, 2 for support, or 3 to leave a message.",
                "max_digits", 1,
                "terminators", "#"));
        ivr.addVerbToSection("main_menu", "switch", Map.of(
                "variable", "prompt_digits",
                "case", Map.of(
                        "1", List.of(Map.of("transfer", Map.of("dest", "sales"))),
                        "2", List.of(Map.of("transfer", Map.of("dest", "support"))))));
        ivr.addVerb("transfer", Map.of("dest", "main_menu"));

        // --- Call Transfer Service ---
        var transfer = SWMLService.builder()
                .name("transfer")
                .route("/transfer")
                .port(3000)
                .build();

        transfer.addAnswerVerb();
        transfer.addVerb("play", Map.of("url", "say:Connecting you with the next available agent."));
        transfer.addVerb("connect", Map.of(
                "from", "+15551234567",
                "timeout", 30,
                "parallel", List.of(
                        Map.of("to", "+15552223333"),
                        Map.of("to", "+15554445555"))));
        transfer.addVerb("record", Map.of("format", "mp3", "beep", true, "max_length", 120));
        transfer.addHangupVerb();

        // Run the voicemail service by default
        System.out.println("Starting voicemail service on port 3000...");
        voicemail.run();
    }
}
