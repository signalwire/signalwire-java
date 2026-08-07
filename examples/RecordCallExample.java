/*
 * Record Call Example.
 *
 * <p>Demonstrates using the FunctionResult recording helpers to: - Start background call recording
 * with various configurations - Stop recordings by control ID - Chain recording with other actions
 */
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.List;
import java.util.Map;

public class RecordCallExample {

  public static void main(String[] args) throws Exception {
    var agent = AgentBase.builder().name("recording-agent").route("/").port(3000).build();

    agent.promptAddSection(
        "Role", "You are a customer service agent. Calls may be recorded for quality purposes.");
    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Use start_recording to begin recording",
            "Use stop_recording to end recording",
            "Always inform callers about recording"));

    // Start recording tool
    agent.defineTool(
        "start_recording",
        "Start recording the call",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) ->
            new FunctionResult("Starting call recording")
                .recordCall("support_call_001", true, "mp3", "both")
                .say("This call is now being recorded for quality purposes."));

    // Stop recording tool
    agent.defineTool(
        "stop_recording",
        "Stop the call recording",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) ->
            new FunctionResult("Ending call recording")
                .stopRecordCall("support_call_001")
                .say("Recording has been stopped. Thank you."));

    // Voicemail-style recording
    agent.defineTool(
        "take_voicemail",
        "Take a voicemail message",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) ->
            new FunctionResult("Please leave your message after the beep")
                .recordCall("voicemail_001", false, "wav", "speak")
                .setEndOfSpeechTimeout(2000));

    System.out.println("Starting recording agent on port 3000...");
    agent.run();
  }
}
