/**
 * Basic SWML Service Example.
 *
 * Uses the Service class directly to build and serve SWML documents
 * without AI components -- voicemail, IVR menu, call transfer, and recording.
 */

import com.signalwire.sdk.swml.Service;

import java.util.Map;

public class SwmlServiceExample {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "voicemail";

        switch (mode) {
            case "voicemail" -> startVoicemail();
            case "ivr"       -> startIvr();
            case "transfer"  -> startTransfer();
            case "record"    -> startRecording();
            default -> {
                System.out.println("Usage: SwmlService [voicemail|ivr|transfer|record]");
                System.exit(1);
            }
        }
    }

    static void startVoicemail() throws Exception {
        var svc = new Service("voicemail", "/voicemail");
        svc.answer(null);
        svc.play(Map.of("url",
                "say:Hello, you've reached the voicemail service. Please leave a message after the beep."));
        svc.sleep(1000);
        svc.record(Map.of(
                "format", "mp3",
                "stereo", false,
                "beep", true,
                "max_length", 120,
                "terminators", "#"
        ));
        svc.play(Map.of("url", "say:Thank you for your message. Goodbye!"));
        svc.hangup();

        System.out.println("Starting voicemail service...");
        svc.serve();
    }

    static void startIvr() throws Exception {
        var svc = new Service("ivr", "/ivr");
        svc.answer(null);
        svc.prompt(Map.of(
                "play", "say:Welcome. Press 1 for sales, 2 for support, or 3 to leave a message.",
                "max_digits", 1,
                "terminators", "#",
                "digit_timeout", 5.0,
                "initial_timeout", 10.0
        ));
        svc.hangup();

        System.out.println("Starting IVR service...");
        svc.serve();
    }

    static void startTransfer() throws Exception {
        var svc = new Service("transfer", "/transfer");
        svc.answer(null);
        svc.play(Map.of("url",
                "say:Thank you for calling. We'll connect you with the next available agent."));
        svc.connect(Map.of(
                "from", "+15551234567",
                "timeout", 30,
                "answer_on_bridge", true
        ));
        svc.play(Map.of("url",
                "say:All agents are busy. Please leave a message."));
        svc.record(Map.of("format", "mp3", "max_length", 120, "terminators", "#"));
        svc.hangup();

        System.out.println("Starting transfer service...");
        svc.serve();
    }

    static void startRecording() throws Exception {
        var svc = new Service("record", "/record");
        svc.answer(null);
        svc.recordCall(Map.of(
                "control_id", "call_recording",
                "format", "mp3",
                "stereo", true,
                "direction", "both",
                "beep", true
        ));
        svc.play(Map.of("url",
                "say:This call is being recorded. Please tell us about your experience."));
        svc.sleep(30000);
        svc.stopRecordCall(Map.of("control_id", "call_recording"));
        svc.play(Map.of("url", "say:Thank you for your time. Goodbye!"));
        svc.hangup();

        System.out.println("Starting recording service...");
        svc.serve();
    }
}
