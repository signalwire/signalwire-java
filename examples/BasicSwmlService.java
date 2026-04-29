/**
 * BasicSwmlService -- proves that {@link com.signalwire.sdk.swml.Service}
 * is independently runnable: a user can construct {@code Service} directly
 * (no {@code AgentBase}), emit any SWML verb, and serve the resulting
 * document on {@code GET /<route>} with no AI components in the loop.
 *
 * <p>Mirror of Python's {@code examples/basic_swml_service.py}. Shows four
 * canonical "no AI" call-control flows: voicemail, IVR menu, call
 * transfer, and call recording. Pick one with the first CLI arg
 * ({@code voicemail}, {@code ivr}, {@code transfer}, or {@code record}) --
 * defaults to {@code voicemail}.
 *
 * <pre>
 *   java -cp ... BasicSwmlService voicemail
 *   java -cp ... BasicSwmlService ivr
 *   java -cp ... BasicSwmlService transfer
 *   java -cp ... BasicSwmlService record
 * </pre>
 */

import com.signalwire.sdk.swml.Service;

import java.util.Map;

public class BasicSwmlService {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "voicemail";

        switch (mode) {
            case "voicemail" -> startVoicemail();
            case "ivr"       -> startIvr();
            case "transfer"  -> startTransfer();
            case "record"    -> startRecording();
            default -> {
                System.out.println(
                        "Usage: BasicSwmlService [voicemail|ivr|transfer|record]");
                System.exit(1);
            }
        }
    }

    static void startVoicemail() throws Exception {
        var svc = new Service("voicemail", "/voicemail");
        svc.answer(null);
        svc.play(Map.of("url",
                "say:Hello, you've reached the voicemail service. "
                        + "Please leave a message after the beep."));
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

        System.out.println("Starting voicemail service on /voicemail ...");
        svc.serve();
    }

    static void startIvr() throws Exception {
        var svc = new Service("ivr", "/ivr");
        svc.answer(null);
        svc.prompt(Map.of(
                "play", "say:Welcome. Press 1 for sales, 2 for support, "
                        + "or 3 to leave a message.",
                "max_digits", 1,
                "terminators", "#",
                "digit_timeout", 5.0,
                "initial_timeout", 10.0
        ));
        svc.hangup();

        System.out.println("Starting IVR service on /ivr ...");
        svc.serve();
    }

    static void startTransfer() throws Exception {
        var svc = new Service("transfer", "/transfer");
        svc.answer(null);
        svc.play(Map.of("url",
                "say:Thank you for calling. "
                        + "We'll connect you with the next available agent."));
        svc.connect(Map.of(
                "from", "+15551234567",
                "timeout", 30,
                "answer_on_bridge", true
        ));
        svc.play(Map.of("url",
                "say:All agents are busy. Please leave a message."));
        svc.record(Map.of(
                "format", "mp3",
                "max_length", 120,
                "terminators", "#"
        ));
        svc.hangup();

        System.out.println("Starting transfer service on /transfer ...");
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
                "say:This call is being recorded. "
                        + "Please tell us about your experience."));
        svc.sleep(30000);
        svc.stopRecordCall(Map.of("control_id", "call_recording"));
        svc.play(Map.of("url", "say:Thank you for your time. Goodbye!"));
        svc.hangup();

        System.out.println("Starting recording service on /record ...");
        svc.serve();
    }
}
