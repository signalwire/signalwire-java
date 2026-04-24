/**
 * Auto-Vivified SWML Service Example.
 *
 * Demonstrates calling verb methods directly on a Service (answer(), play(),
 * record(), hangup(), etc.) instead of going through Document.addVerb().
 * Builds voicemail, IVR, and call transfer services that share a single
 * Service instance per route.
 */

import com.signalwire.sdk.swml.Service;

import java.util.List;
import java.util.Map;

public class AutoVivifiedExample {

    public static void main(String[] args) throws Exception {
        // --- Voicemail Service ---
        // Auto-vivification: each Service method (answer, play, record,
        // hangup) appends the matching verb to the underlying SWML
        // Document; no manual Document.addVerb(...) required.
        var voicemail = new Service("voicemail", "/voicemail");

        voicemail.answer(null);
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
        voicemail.hangup();

        // --- IVR Menu Service ---
        // Same pattern, different composition. prompt() + transfer() are
        // auto-vivified verb methods on Service.
        var ivr = new Service("ivr", "/ivr");

        ivr.answer(null);
        ivr.prompt(Map.of(
                "play", "say:Press 1 for sales, 2 for support, or 3 to leave a message.",
                "max_digits", 1,
                "terminators", "#"));
        ivr.transfer(Map.of("dest", "main_menu"));

        // --- Call Transfer Service ---
        // connect() fans out to multiple destinations in parallel.
        var transfer = new Service("transfer", "/transfer");

        transfer.answer(null);
        transfer.play(Map.of("url", "say:Connecting you with the next available agent."));
        transfer.connect(Map.of(
                "from", "+15551234567",
                "timeout", 30,
                "parallel", List.of(
                        Map.of("to", "+15552223333"),
                        Map.of("to", "+15554445555"))));
        transfer.record(Map.of("format", "mp3", "beep", true, "max_length", 120));
        transfer.hangup();

        // Run the voicemail service by default.
        System.out.println("Starting voicemail service on port 3000...");
        voicemail.serve();
    }
}
